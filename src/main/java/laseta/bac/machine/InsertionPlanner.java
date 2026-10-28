package laseta.bac.machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Plans where the stacks an operation pushes go, slot by slot, as if they were inserted one after the other. Simulated
 * insertions alone can't tell whether several stacks fit together: nothing is inserted yet, so each of them would count
 * on the same free space (three potions, which don't stack, would all "fit" into a single empty slot). The planner
 * remembers what it already planned into every slot and simulates on top of it, the same way
 * {@link ItemTransfer} counts what it already reserved when it plans extractions.
 *
 * <p>Slots are filled like {@link net.neoforged.neoforge.items.ItemHandlerHelper#insertItemStacked} does: first those
 * that already hold (or will hold) the same item, then the others. A slot planned for one item is never counted on for
 * another.</p>
 */
final class InsertionPlanner {
    /** Part of a stack planned into one slot. */
    record Insert(IItemHandler handler, int slot, ItemStack stack) {
    }

    /** Per inventory and slot, everything planned into it so far (one kind of item per slot). */
    private final Map<IItemHandler, Map<Integer, ItemStack>> planned = new IdentityHashMap<>();

    /**
     * Plans {@code stack} into {@code targets}: preferably whole into the first one (in priority order) that takes all
     * of it, otherwise split over them in order. Nothing is planned if it doesn't fit.
     *
     * @return the planned inserts, or null if it doesn't fit even split up
     */
    @Nullable
    List<Insert> plan(ItemStack stack, List<IItemHandler> targets) {
        if (stack.isEmpty()) {
            return List.of();
        }
        List<IItemHandler> distinct = distinct(targets);
        for (IItemHandler target : distinct) {
            List<Insert> inserts = new ArrayList<>(1);
            if (fit(stack, target, inserts).isEmpty()) {
                commit(inserts);
                return inserts;
            }
        }
        List<Insert> inserts = new ArrayList<>();
        ItemStack rest = stack;
        for (IItemHandler target : distinct) {
            rest = fit(rest, target, inserts);
            if (rest.isEmpty()) {
                commit(inserts);
                return inserts;
            }
        }
        return null;
    }

    /**
     * Adds to {@code inserts} as much of {@code stack} as {@code handler} takes on top of what is already planned in it.
     *
     * @return what doesn't fit
     */
    private ItemStack fit(ItemStack stack, IItemHandler handler, List<Insert> inserts) {
        Map<Integer, ItemStack> handlerPlanned = planned.getOrDefault(handler, Map.of());
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            boolean sameItemPass = pass == 0;
            for (int slot = 0; slot < handler.getSlots() && !rest.isEmpty(); slot++) {
                ItemStack already = handlerPlanned.getOrDefault(slot, ItemStack.EMPTY);
                boolean sameItem = already.isEmpty()
                        ? ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), rest)
                        : ItemStack.isSameItemSameComponents(already, rest);
                if (sameItem != sameItemPass || (!already.isEmpty() && !sameItem)) {
                    continue;
                }
                // Simulate what is already planned there plus this stack: the slot's answer covers both.
                int base = already.getCount();
                ItemStack attempt = rest.copyWithCount(base + rest.getCount());
                int accepted = attempt.getCount() - handler.insertItem(slot, attempt, true).getCount() - base;
                if (accepted > 0) {
                    inserts.add(new Insert(handler, slot, rest.copyWithCount(accepted)));
                    rest.shrink(accepted);
                }
            }
        }
        return rest;
    }

    private void commit(List<Insert> inserts) {
        for (Insert insert : inserts) {
            planned.computeIfAbsent(insert.handler(), handler -> new HashMap<>())
                    .merge(insert.slot(), insert.stack().copy(), (already, added) -> already.copyWithCount(already.getCount() + added.getCount()));
        }
    }

    /** The same inventory twice (two faces exposing the very same handler object) is planned into once. */
    private static List<IItemHandler> distinct(List<IItemHandler> handlers) {
        Map<IItemHandler, Boolean> seen = new IdentityHashMap<>();
        return handlers.stream().filter(handler -> seen.put(handler, Boolean.TRUE) == null).toList();
    }
}
