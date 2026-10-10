package laseta.bac.menu.transfer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * Works out how to fill the crafting grid with a recipe using the items the player has (like a crafting table's
 * "+" button in JEI / EMI). Only ever works on copies: the result is a {@link Plan} describing the new grid and
 * inventory contents, which the server applies only if it is valid. Items are never created or destroyed.
 *
 * <p>Items already in the grid are returned to the inventory, but are also the first choice when filling,
 * so re-filling the same recipe doesn't shuffle items around.</p>
 */
public final class GridFillPlanner {
    /** Most alternatives considered per grid slot (e.g. all items of a tag). Also caps the network packet size. */
    public static final int MAX_CANDIDATES = 256;
    private static final int PLAYER_MAIN_INVENTORY_SIZE = 36;
    private static final int HOTBAR_SIZE = 9;

    private GridFillPlanner() {
    }

    /**
     * @param grid        new grid contents
     * @param inventory   new player inventory contents (same indices as the input list)
     * @param missing     per grid slot: the recipe wants an item there but the player has none
     * @param placedSlots number of grid slots that received an item
     * @param fits        false if items currently in the grid can't be put back in the inventory
     */
    public record Plan(List<ItemStack> grid, List<ItemStack> inventory, boolean[] missing, int placedSlots,
                       int requestedSlots, boolean fits) {
        public boolean isComplete() {
            return placedSlots == requestedSlots;
        }

        public boolean canApply() {
            return fits && placedSlots > 0;
        }
    }

    /**
     * @param wanted       per grid slot, the items that are accepted there (empty list = slot stays empty)
     * @param currentGrid  current grid contents
     * @param inventory    player's main inventory (36 slots, hotbar first, like {@code Inventory.items})
     * @param fillMaximum  fill every slot as far as possible instead of one craft's worth (shift-click)
     */
    public static Plan plan(List<List<ItemStack>> wanted, List<ItemStack> currentGrid, List<ItemStack> inventory,
                            boolean fillMaximum) {
        int gridSize = currentGrid.size();
        List<ItemStack> pool = new ArrayList<>();
        for (ItemStack stack : currentGrid) {
            if (!stack.isEmpty()) {
                pool.add(stack.copy());
            }
        }
        List<ItemStack> inv = copyAll(inventory);
        ItemStack[] grid = new ItemStack[gridSize];
        Arrays.fill(grid, ItemStack.EMPTY);
        boolean[] missing = new boolean[gridSize];

        int requested = 0;
        int placed = 0;
        for (int i = 0; i < Math.min(gridSize, wanted.size()); i++) {
            List<ItemStack> candidates = wanted.get(i);
            if (candidates.isEmpty()) {
                continue;
            }
            requested++;
            ItemStack taken = takeMatching(candidates, pool, inv);
            if (taken.isEmpty()) {
                missing[i] = true;
            } else {
                grid[i] = taken;
                placed++;
            }
        }

        if (fillMaximum && placed > 0) {
            fillMaximum(grid, pool, inv);
        }

        boolean fits = true;
        for (ItemStack leftover : pool) {
            if (!leftover.isEmpty() && !insert(inv, leftover)) {
                fits = false;
                break;
            }
        }
        return new Plan(List.of(grid), inv, missing, placed, requested, fits);
    }

    /** Adds one more item to every filled slot per round, while every slot can still get one. */
    private static void fillMaximum(ItemStack[] grid, List<ItemStack> pool, List<ItemStack> inv) {
        while (true) {
            ItemStack[] gridBefore = copyAll(Arrays.asList(grid)).toArray(ItemStack[]::new);
            List<ItemStack> poolBefore = copyAll(pool);
            List<ItemStack> invBefore = copyAll(inv);

            boolean roundComplete = true;
            for (ItemStack stack : grid) {
                if (stack.isEmpty()) {
                    continue;
                }
                if (stack.getCount() >= stack.getMaxStackSize() || takeMatching(List.of(stack), pool, inv).isEmpty()) {
                    roundComplete = false;
                    break;
                }
                stack.grow(1);
            }

            if (!roundComplete) {
                // Undo the partial round so every slot keeps the same count.
                System.arraycopy(gridBefore, 0, grid, 0, grid.length);
                restore(pool, poolBefore);
                restore(inv, invBefore);
                return;
            }
        }
    }

    /** Removes and returns one item matching any candidate, preferring the grid, then the main inventory, then the hotbar. */
    private static ItemStack takeMatching(List<ItemStack> candidates, List<ItemStack> pool, List<ItemStack> inv) {
        for (ItemStack stack : pool) {
            if (matchesAny(candidates, stack)) {
                return stack.split(1);
            }
        }
        for (int index : inventorySearchOrder(inv.size())) {
            ItemStack stack = inv.get(index);
            if (matchesAny(candidates, stack)) {
                return stack.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean matchesAny(List<ItemStack> candidates, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (ItemStack candidate : candidates) {
            // Candidates without extra data (most ingredients) match any stack of that item, e.g. renamed ones.
            if (ItemStack.isSameItem(candidate, stack)
                    && (candidate.getComponentsPatch().isEmpty() || ItemStack.isSameItemSameComponents(candidate, stack))) {
                return true;
            }
        }
        return false;
    }

    /** Puts a stack into the inventory, merging with existing stacks first. Returns false if it didn't all fit. */
    private static boolean insert(List<ItemStack> inv, ItemStack stack) {
        for (ItemStack existing : inv) {
            if (stack.isEmpty()) {
                return true;
            }
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    stack.shrink(moved);
                }
            }
        }
        for (int i = 0; i < inv.size() && !stack.isEmpty(); i++) {
            if (inv.get(i).isEmpty()) {
                inv.set(i, stack.copy());
                stack.setCount(0);
            }
        }
        return stack.isEmpty();
    }

    /** Main inventory (9-35) before the hotbar (0-8), so the items in the player's hands are used last. */
    private static int[] inventorySearchOrder(int size) {
        int[] order = new int[size];
        if (size != PLAYER_MAIN_INVENTORY_SIZE) {
            Arrays.setAll(order, i -> i);
            return order;
        }
        int next = 0;
        for (int i = HOTBAR_SIZE; i < size; i++) {
            order[next++] = i;
        }
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            order[next++] = i;
        }
        return order;
    }

    private static List<ItemStack> copyAll(List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copy.add(stack.copy());
        }
        return copy;
    }

    private static void restore(List<ItemStack> target, List<ItemStack> snapshot) {
        target.clear();
        target.addAll(snapshot);
    }
}
