package laseta.bac.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.mojang.logging.LogUtils;

import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideConfiguration;
import laseta.bac.sideconfig.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * One craft using items from the inventories next to the block instead of the block's own grid.
 *
 * <p>The grid only acts as a template. For every grid slot one matching item is pulled from an inventory on an
 * input face; the result (and any crafting remainders, e.g. empty buckets) is pushed into an inventory on an
 * output face. Everything goes through NeoForge's {@link Capabilities.ItemHandler#BLOCK} capability, the same one
 * hoppers and item pipes use, so any modded inventory that pipes can use (drawers and controllers, AE2,
 * Refined Storage, machines, ...) works.</p>
 *
 * <p>Order of operations, so items are never lost or duplicated:</p>
 * <ol>
 *     <li>Plan: find every ingredient with simulated extractions, counting what was already reserved per slot.</li>
 *     <li>Build the crafting input from the items that would actually be pulled and check the recipe still
 *     matches them; the result is assembled from those real items (so data-driven recipes work).</li>
 *     <li>Check (simulated) that the result and remainders fit into the output inventories.</li>
 *     <li>Extract for real, verifying each item. If an inventory doesn't deliver what it promised (e.g. two faces
 *     touching the same storage network), everything taken so far is put back and the craft is cancelled.</li>
 *     <li>Insert the result and remainders. Only if an inventory refuses what its simulation accepted is
 *     anything dropped in the world, as a last resort.</li>
 * </ol>
 */
public final class ExternalCraftingOperation {
    private static final Logger LOGGER = LogUtils.getLogger();

    private record Take(IItemHandler handler, int slot, ItemStack expected) {
    }

    private record Face(RelativeSide side, IItemHandler handler) {
    }

    private record Extracted(IItemHandler handler, int slot, ItemStack stack) {
    }

    private final ServerLevel level;
    private final BlockPos pos;
    private final CraftingSettings settings;
    private final List<IItemHandler> inputs;
    private final List<IItemHandler> outputs;

    private ExternalCraftingOperation(ServerLevel level, BlockPos pos, Direction facing, SideConfiguration sides,
                                      CraftingSettings settings) {
        this.level = level;
        this.pos = pos;
        this.settings = settings;
        this.inputs = handlersInOrder(findNeighbours(level, pos, facing, sides, SideMode::canInput),
                settings.getInputPriority(), settings.getInputCursor());
        this.outputs = handlersInOrder(findNeighbours(level, pos, facing, sides, SideMode::canOutput),
                settings.getOutputPriority(), settings.getOutputCursor());
    }

    /**
     * Tries to craft {@code recipe} once, as laid out by {@code template} (the 9 grid stacks).
     *
     * @return true if the craft happened
     */
    public static boolean tryCraft(ServerLevel level, BlockPos pos, Direction facing, SideConfiguration sides,
                                   CraftingSettings settings, List<ItemStack> template, CraftingRecipe recipe) {
        boolean crafted = new ExternalCraftingOperation(level, pos, facing, sides, settings).craft(template, recipe);
        if (crafted) {
            settings.advanceRoundRobin();
        }
        return crafted;
    }

    private boolean craft(List<ItemStack> template, CraftingRecipe recipe) {
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return false;
        }

        // 1. Plan which item to take for every grid slot.
        Map<IItemHandler, Map<Integer, Integer>> reserved = new IdentityHashMap<>();
        List<Take> takes = new ArrayList<>();
        List<ItemStack> actualItems = new ArrayList<>(template.size());
        for (ItemStack wanted : template) {
            if (wanted.isEmpty()) {
                actualItems.add(ItemStack.EMPTY);
                continue;
            }
            Take take = planTake(wanted, reserved);
            if (take == null) {
                return false;
            }
            takes.add(take);
            actualItems.add(take.expected());
        }
        if (takes.isEmpty()) {
            return false;
        }

        // 2. The recipe must accept the items we would actually use (matters when NBT matching is off).
        CraftingInput input = CraftingInput.of(BetterAutoCrafterBlockEntity.GRID_WIDTH, BetterAutoCrafterBlockEntity.GRID_HEIGHT, actualItems);
        if (!recipe.matches(input, level)) {
            return false;
        }
        ItemStack result = recipe.assemble(input, level.registryAccess());
        if (result.isEmpty()) {
            return false;
        }
        List<ItemStack> remainders = new ArrayList<>();
        for (ItemStack remainder : recipe.getRemainingItems(input)) {
            if (!remainder.isEmpty()) {
                remainders.add(remainder.copy());
            }
        }

        // 3. There must be room for everything that comes out.
        IItemHandler resultTarget = findOutputFor(result);
        if (resultTarget == null) {
            return false;
        }
        for (ItemStack remainder : remainders) {
            if (findOutputFor(remainder) == null) {
                return false;
            }
        }

        // 4. Take the ingredients for real.
        List<Extracted> extracted = new ArrayList<>(takes.size());
        for (Take take : takes) {
            ItemStack got = take.handler().extractItem(take.slot(), 1, false);
            if (!got.isEmpty()) {
                extracted.add(new Extracted(take.handler(), take.slot(), got));
            }
            if (got.getCount() != 1 || !ItemStack.isSameItemSameComponents(got, take.expected())) {
                rollback(extracted);
                return false;
            }
        }

        // 5. Deliver.
        result.onCraftedBySystem(level);
        deliver(result, resultTarget);
        for (ItemStack remainder : remainders) {
            deliver(remainder, findOutputFor(remainder));
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Planning
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Finds one item matching {@code wanted} in the input inventories, taking earlier reservations into account.
     * Exact matches (same data components) are always preferred; with NBT matching off, a second pass accepts any
     * stack of the same item.
     */
    @Nullable
    private Take planTake(ItemStack wanted, Map<IItemHandler, Map<Integer, Integer>> reserved) {
        int passes = settings.isMatchNbt() ? 1 : 2;
        for (int pass = 0; pass < passes; pass++) {
            boolean exact = pass == 0;
            for (IItemHandler handler : inputs) {
                Map<Integer, Integer> handlerReserved = reserved.computeIfAbsent(handler, h -> new HashMap<>());
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    // Cheap pre-check before asking the inventory to simulate anything.
                    if (!matches(wanted, handler.getStackInSlot(slot), exact)) {
                        continue;
                    }
                    int alreadyReserved = handlerReserved.getOrDefault(slot, 0);
                    ItemStack simulated = handler.extractItem(slot, alreadyReserved + 1, true);
                    if (simulated.getCount() <= alreadyReserved || !matches(wanted, simulated, exact)) {
                        continue;
                    }
                    handlerReserved.put(slot, alreadyReserved + 1);
                    return new Take(handler, slot, simulated.copyWithCount(1));
                }
            }
        }
        return null;
    }

    private static boolean matches(ItemStack wanted, ItemStack candidate, boolean exact) {
        if (candidate.isEmpty()) {
            return false;
        }
        return exact ? ItemStack.isSameItemSameComponents(wanted, candidate) : ItemStack.isSameItem(wanted, candidate);
    }

    /** First output inventory (in priority order) that accepts the whole stack. */
    @Nullable
    private IItemHandler findOutputFor(ItemStack stack) {
        for (IItemHandler handler : outputs) {
            if (ItemHandlerHelper.insertItemStacked(handler, stack.copy(), true).isEmpty()) {
                return handler;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Moving items
    // ---------------------------------------------------------------------------------------------------------------

    private void deliver(ItemStack stack, @Nullable IItemHandler preferred) {
        ItemStack rest = stack.copy();
        if (preferred != null) {
            rest = ItemHandlerHelper.insertItemStacked(preferred, rest, false);
        }
        for (IItemHandler handler : outputs) {
            if (rest.isEmpty()) {
                return;
            }
            rest = ItemHandlerHelper.insertItemStacked(handler, rest, false);
        }
        if (!rest.isEmpty()) {
            LOGGER.debug("Output inventory refused {} that it accepted when simulated; dropping it at {}", rest, pos);
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
            for (IItemHandler handler : inputs) {
                if (rest.isEmpty()) {
                    break;
                }
                rest = ItemHandlerHelper.insertItemStacked(handler, rest, false);
            }
            if (!rest.isEmpty()) {
                LOGGER.debug("Could not return {} to its inventory after a cancelled craft; dropping it at {}", rest, pos);
                drop(rest);
            }
        }
    }

    private void drop(ItemStack stack) {
        Containers.dropItemStack(level, pos.getX(), pos.getY() + 1, pos.getZ(), stack);
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** Applies the priority, then drops duplicates (two faces exposing the very same handler object). */
    private static List<IItemHandler> handlersInOrder(List<Face> faces, TransferPriority priority, int cursor) {
        List<IItemHandler> handlers = new ArrayList<>();
        Map<IItemHandler, Boolean> seen = new IdentityHashMap<>();
        for (Face face : priority.order(faces, Face::side, cursor)) {
            if (seen.put(face.handler(), Boolean.TRUE) == null) {
                handlers.add(face.handler());
            }
        }
        return Collections.unmodifiableList(handlers);
    }

    /** Neighbouring item handlers on the faces accepted by {@code filter}, in {@link RelativeSide} order. */
    private static List<Face> findNeighbours(ServerLevel level, BlockPos pos, Direction facing, SideConfiguration sides,
                                             Predicate<SideMode> filter) {
        List<Face> faces = new ArrayList<>();
        for (RelativeSide side : RelativeSide.values()) {
            if (!filter.test(sides.getMode(side))) {
                continue;
            }
            Direction direction = side.toDirection(facing);
            BlockPos neighbourPos = pos.relative(direction);
            if (!level.isLoaded(neighbourPos)) {
                continue;
            }
            // The context is the neighbour's side that touches us, exactly like a hopper or pipe would ask.
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighbourPos, direction.getOpposite());
            if (handler != null) {
                faces.add(new Face(side, handler));
            }
        }
        return faces;
    }
}
