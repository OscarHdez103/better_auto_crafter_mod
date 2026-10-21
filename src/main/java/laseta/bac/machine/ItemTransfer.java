package laseta.bac.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.mojang.logging.LogUtils;

import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideConfiguration;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.stock.InventoryStockReader;
import laseta.bac.stock.StockReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * One operation of an auto machine: pulls the items of its template from the inventories on its input faces, turns them
 * into something with a {@link Process} (a craft, or nothing at all), and pushes that into the inventories on its output
 * faces. The machine's own template is never consumed.
 *
 * <p>Everything goes through NeoForge's {@link Capabilities.ItemHandler#BLOCK} capability, the same one hoppers and item
 * pipes use, so any modded inventory that pipes can use (drawers and controllers, AE2, Refined Storage, machines, ...)
 * works.</p>
 *
 * <p>The inventories come as {@link InventoryFace}s, so the operation doesn't care where they are: the faces around a
 * {@link MachineAnchor} (the machine itself, or a Translocator when a translocation upgrade moves them there, possibly
 * to another dimension; see {@link #facesAround}), or the blocks linked by a Multi Translocation Upgrade.</p>
 *
 * <p>With a stock limit (stocking upgrades), an output inventory that already holds that many of a product doesn't
 * receive it; if every output inventory is stocked, nothing happens. With a linked storage (Storage Link Upgrade) the
 * stock is counted there instead.</p>
 *
 * <p>Order of operations, so items are never lost or duplicated:</p>
 * <ol>
 *     <li>Plan: find every template item with simulated extractions, counting what was already reserved per slot.</li>
 *     <li>Process the items that would actually be pulled (a craft checks its recipe still matches them).</li>
 *     <li>Check (simulated) that everything that comes out fits into the output inventories.</li>
 *     <li>Extract for real, verifying each stack. If an inventory doesn't deliver what it promised (e.g. two faces
 *     touching the same storage network), everything taken so far is put back and the operation is cancelled.</li>
 *     <li>Insert what comes out. Only if the outputs refuse what their simulation accepted does it go back to the input
 *     inventories, and only if those refuse it too is it dropped in the world, as a last resort.</li>
 * </ol>
 */
public final class ItemTransfer {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * What comes out of an operation.
     *
     * @param products       what the machine makes; subject to the stock limit
     * @param byproducts     what is left over (e.g. the empty buckets of a craft); always pushed, never stock limited
     * @param beforeDelivery called once the operation is certain to happen, right before anything is pushed
     */
    public record Output(List<ItemStack> products, List<ItemStack> byproducts, Runnable beforeDelivery) {
        public static Output of(List<ItemStack> products) {
            return new Output(products, List.of(), () -> {
            });
        }
    }

    /** Turns the items an operation pulls into what it pushes. */
    @FunctionalInterface
    public interface Process {
        /**
         * @param pulled per template slot, the stacks that would be pulled for it: none for an empty slot, usually one
         *               stack of the template's count, several when NBT matching is off and different variants are mixed
         * @return what comes out, or null if nothing can be made from these items (then nothing is pulled)
         */
        @Nullable
        Output apply(List<List<ItemStack>> pulled);
    }

    private record Take(IItemHandler handler, int slot, ItemStack expected) {
    }

    private record Extracted(IItemHandler handler, int slot, ItemStack stack) {
    }

    /** A stack and the inventories it goes to, in the order they are tried. */
    private record Delivery(ItemStack stack, List<IItemHandler> targets) {
    }

    private final ServerLevel level;
    private final BlockPos pos;
    private final MachineSettings settings;
    private final List<IItemHandler> inputs;
    private final List<IItemHandler> outputs;
    private final int stockLimit;
    @Nullable
    private final StockReading.Counter linkedStock;

    /**
     * @param level       the machine's level (leftovers are dropped there as a last resort)
     * @param pos         the machine's position
     * @param inputs      where the template's items are pulled from
     * @param outputs     where the products and byproducts are pushed to
     * @param stockLimit  output inventories holding at least this many of a product don't receive it; 0 = no limit
     * @param linkedStock counts the stock in a linked storage instead of in each output inventory; null = per output
     */
    public ItemTransfer(ServerLevel level, BlockPos pos, List<InventoryFace> inputs, List<InventoryFace> outputs,
                        MachineSettings settings, int stockLimit, @Nullable StockReading.Counter linkedStock) {
        this.level = level;
        this.pos = pos;
        this.settings = settings;
        this.stockLimit = stockLimit;
        this.linkedStock = linkedStock;
        this.inputs = handlersInOrder(inputs, settings.getInputPriority(), settings.getInputCursor());
        this.outputs = handlersInOrder(outputs, settings.getOutputPriority(), settings.getOutputCursor());
    }

    /**
     * Pulls the items of {@code template} (one entry per template slot, with its count), turns them into the
     * {@code process}'s output and pushes that out. All or nothing: either everything happens or nothing is touched.
     *
     * @return true if the operation happened
     */
    public boolean run(List<ItemStack> template, Process process) {
        if (!runOnce(template, process)) {
            return false;
        }
        settings.advanceRoundRobin();
        return true;
    }

    /**
     * Like {@link #run}, but every template slot is an operation of its own: each slot whose items are there is
     * pulled, processed and pushed, whether or not the other slots' items are. The process gets the template's slots
     * with only that one filled.
     *
     * @return true if at least one slot's operation happened
     */
    public boolean runEach(List<ItemStack> template, Process process) {
        boolean any = false;
        for (int i = 0; i < template.size(); i++) {
            if (template.get(i).isEmpty()) {
                continue;
            }
            List<ItemStack> single = new ArrayList<>(Collections.nCopies(template.size(), ItemStack.EMPTY));
            single.set(i, template.get(i));
            any |= runOnce(single, process);
        }
        if (any) {
            // Once per run, so round robin moves on by one face like after any other operation.
            settings.advanceRoundRobin();
        }
        return any;
    }

    private boolean runOnce(List<ItemStack> template, Process process) {
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return false;
        }

        // 1. Plan which stacks to take for every template slot.
        Map<IItemHandler, Map<Integer, Integer>> reserved = new IdentityHashMap<>();
        List<Take> takes = new ArrayList<>();
        List<List<ItemStack>> pulled = new ArrayList<>(template.size());
        for (ItemStack wanted : template) {
            if (wanted.isEmpty()) {
                pulled.add(List.of());
                continue;
            }
            List<Take> slotTakes = planTakes(wanted, reserved);
            if (slotTakes == null) {
                return false;
            }
            takes.addAll(slotTakes);
            pulled.add(slotTakes.stream().map(Take::expected).toList());
        }
        if (takes.isEmpty()) {
            return false;
        }

        // 2. What comes out of these items.
        Output output = process.apply(pulled);
        if (output == null || output.products().isEmpty()) {
            return false;
        }

        // 3. There must be room for everything that comes out (products only where they aren't stocked yet).
        List<Delivery> deliveries = new ArrayList<>();
        for (ItemStack product : output.products()) {
            List<IItemHandler> targets = planDelivery(product, unstockedOutputs(product));
            if (targets == null) {
                return false;
            }
            deliveries.add(new Delivery(product, targets));
        }
        for (ItemStack byproduct : output.byproducts()) {
            List<IItemHandler> targets = planDelivery(byproduct, outputs);
            if (targets == null) {
                return false;
            }
            deliveries.add(new Delivery(byproduct, targets));
        }

        // 4. Take the items for real.
        List<Extracted> extracted = new ArrayList<>(takes.size());
        for (Take take : takes) {
            int count = take.expected().getCount();
            ItemStack got = take.handler().extractItem(take.slot(), count, false);
            if (!got.isEmpty()) {
                extracted.add(new Extracted(take.handler(), take.slot(), got));
            }
            if (got.getCount() != count || !ItemStack.isSameItemSameComponents(got, take.expected())) {
                rollback(extracted);
                return false;
            }
        }

        // 5. Deliver.
        output.beforeDelivery().run();
        for (Delivery delivery : deliveries) {
            deliver(delivery);
        }
        return true;
    }

    /**
     * Whether no output may receive {@code stack} because it is stocked everywhere (always false without a stock
     * limit). Machines use it to leave stocked items out of an operation.
     */
    public boolean isStocked(ItemStack stack) {
        return stockLimit > 0 && unstockedOutputs(stack).isEmpty();
    }

    /** Combines the stacks of the same item and data components, so each kind is planned and pushed as one stack. */
    public static List<ItemStack> merge(List<List<ItemStack>> stacks) {
        List<ItemStack> merged = new ArrayList<>();
        for (List<ItemStack> slot : stacks) {
            for (ItemStack stack : slot) {
                ItemStack same = merged.stream().filter(m -> ItemStack.isSameItemSameComponents(m, stack)).findFirst().orElse(null);
                if (same != null) {
                    same.grow(stack.getCount());
                } else {
                    merged.add(stack.copy());
                }
            }
        }
        return merged;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Planning
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Finds {@code wanted}'s count of matching items in the input inventories, from as many slots as needed, taking
     * earlier reservations into account. Exact matches (same data components) are always preferred; with NBT matching
     * off, a second pass accepts any stack of the same item.
     *
     * @return null if there aren't enough
     */
    @Nullable
    private List<Take> planTakes(ItemStack wanted, Map<IItemHandler, Map<Integer, Integer>> reserved) {
        int needed = wanted.getCount();
        List<Take> takes = new ArrayList<>(1);
        int passes = settings.isMatchNbt() ? 1 : 2;
        for (int pass = 0; pass < passes && needed > 0; pass++) {
            boolean exact = pass == 0;
            for (IItemHandler handler : inputs) {
                Map<Integer, Integer> handlerReserved = reserved.computeIfAbsent(handler, h -> new HashMap<>());
                for (int slot = 0; slot < handler.getSlots() && needed > 0; slot++) {
                    // Cheap pre-check before asking the inventory to simulate anything.
                    if (!matches(wanted, handler.getStackInSlot(slot), exact)) {
                        continue;
                    }
                    int alreadyReserved = handlerReserved.getOrDefault(slot, 0);
                    ItemStack simulated = handler.extractItem(slot, alreadyReserved + needed, true);
                    int available = simulated.getCount() - alreadyReserved;
                    if (available <= 0 || !matches(wanted, simulated, exact)) {
                        continue;
                    }
                    int amount = Math.min(available, needed);
                    handlerReserved.put(slot, alreadyReserved + amount);
                    takes.add(new Take(handler, slot, simulated.copyWithCount(amount)));
                    needed -= amount;
                }
            }
        }
        return needed > 0 ? null : takes;
    }

    private static boolean matches(ItemStack wanted, ItemStack candidate, boolean exact) {
        if (candidate.isEmpty()) {
            return false;
        }
        return exact ? ItemStack.isSameItemSameComponents(wanted, candidate) : ItemStack.isSameItem(wanted, candidate);
    }

    /**
     * The output inventories that may receive {@code product}: all of them without a stock limit, none once the linked
     * storage is stocked, otherwise those that hold fewer than the stock limit.
     */
    private List<IItemHandler> unstockedOutputs(ItemStack product) {
        if (stockLimit <= 0) {
            return outputs;
        }
        if (linkedStock != null) {
            return linkedStock.count(product, stockLimit) < stockLimit ? outputs : List.of();
        }
        return outputs.stream().filter(handler -> InventoryStockReader.count(handler, product, stockLimit) < stockLimit).toList();
    }

    /**
     * Where {@code stack} goes: preferably whole into the first of {@code targets} (in priority order) that takes all
     * of it, otherwise split over them in order.
     *
     * @return the targets in the order to fill them, or null if it doesn't fit even split up
     */
    @Nullable
    private static List<IItemHandler> planDelivery(ItemStack stack, List<IItemHandler> targets) {
        for (int i = 0; i < targets.size(); i++) {
            if (ItemHandlerHelper.insertItemStacked(targets.get(i), stack.copy(), true).isEmpty()) {
                List<IItemHandler> ordered = new ArrayList<>(targets);
                ordered.addFirst(ordered.remove(i));
                return ordered;
            }
        }
        ItemStack rest = stack.copy();
        for (IItemHandler handler : targets) {
            rest = ItemHandlerHelper.insertItemStacked(handler, rest, true);
            if (rest.isEmpty()) {
                return targets;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Moving items
    // ---------------------------------------------------------------------------------------------------------------

    private void deliver(Delivery delivery) {
        ItemStack rest = insertInto(delivery.stack().copy(), delivery.targets());
        if (!rest.isEmpty()) {
            // Possible when several stacks go into the same inventory: each fitted on its own when simulated.
            LOGGER.debug("Output inventories refused {} that they accepted when simulated; returning it to the inputs at {}", rest, pos);
            rest = insertInto(rest, inputs);
        }
        if (!rest.isEmpty()) {
            LOGGER.debug("No inventory accepts {}; dropping it at {}", rest, pos);
            drop(rest);
        }
    }

    /** Puts extracted items back where they came from (or anywhere in the inputs), newest first. */
    private void rollback(List<Extracted> extracted) {
        for (int i = extracted.size() - 1; i >= 0; i--) {
            Extracted entry = extracted.get(i);
            ItemStack rest = entry.handler().insertItem(entry.slot(), entry.stack(), false);
            if (!rest.isEmpty()) {
                rest = ItemHandlerHelper.insertItemStacked(entry.handler(), rest, false);
            }
            rest = insertInto(rest, inputs);
            if (!rest.isEmpty()) {
                LOGGER.debug("Could not return {} to its inventory after a cancelled operation; dropping it at {}", rest, pos);
                drop(rest);
            }
        }
    }

    /** @return what didn't fit */
    private static ItemStack insertInto(ItemStack stack, List<IItemHandler> targets) {
        ItemStack rest = stack;
        for (IItemHandler handler : targets) {
            if (rest.isEmpty()) {
                break;
            }
            rest = ItemHandlerHelper.insertItemStacked(handler, rest, false);
        }
        return rest;
    }

    private void drop(ItemStack stack) {
        Containers.dropItemStack(level, pos.getX(), pos.getY() + 1, pos.getZ(), stack);
    }

    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Applies the priority, then drops duplicates (two faces exposing the very same handler object). Faces without a
     * machine side (linked blocks) are never preferred, so a face priority keeps them in link order.
     */
    private static List<IItemHandler> handlersInOrder(List<InventoryFace> faces, TransferPriority priority, int cursor) {
        List<IItemHandler> handlers = new ArrayList<>();
        Map<IItemHandler, Boolean> seen = new IdentityHashMap<>();
        for (InventoryFace face : priority.order(faces, InventoryFace::side, cursor)) {
            if (seen.put(face.handler(), Boolean.TRUE) == null) {
                handlers.add(face.handler());
            }
        }
        return Collections.unmodifiableList(handlers);
    }

    /**
     * Item handlers next to {@code anchor} on the faces accepted by {@code filter}, in {@link RelativeSide} order.
     * Neighbours in unloaded chunks are skipped (never loaded), like hoppers and pipes do.
     */
    public static List<InventoryFace> facesAround(MachineAnchor anchor, SideConfiguration sides, Predicate<SideMode> filter) {
        ServerLevel level = anchor.level();
        List<InventoryFace> faces = new ArrayList<>();
        for (RelativeSide side : RelativeSide.values()) {
            if (!filter.test(sides.getMode(side))) {
                continue;
            }
            Direction direction = side.toDirection(anchor.facing());
            BlockPos neighbourPos = anchor.pos().relative(direction);
            if (!level.isLoaded(neighbourPos)) {
                continue;
            }
            // The context is the neighbour's side that touches us, exactly like a hopper or pipe would ask.
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighbourPos, direction.getOpposite());
            if (handler != null) {
                faces.add(new InventoryFace(side, handler));
            }
        }
        return faces;
    }
}
