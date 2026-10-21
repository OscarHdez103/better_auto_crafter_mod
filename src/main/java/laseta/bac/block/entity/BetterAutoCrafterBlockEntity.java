package laseta.bac.block.entity;

import java.util.ArrayList;
import java.util.List;

import laseta.bac.machine.AutoMachineBlockEntity;
import laseta.bac.machine.ItemTransfer;
import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.recipe.AutoCraftingRecipe;
import laseta.bac.registry.ModBlockEntities;
import laseta.bac.registry.ModRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Better Auto Crafter: its 3x3 template grid is a recipe's layout. Each operation pulls the recipe's ingredients,
 * crafts it and pushes the result (and any crafting remainders, e.g. empty buckets). The recipe is a crafting recipe
 * (one item per slot) or, for layouts no crafting recipe has, an {@link AutoCraftingRecipe} (possibly several items
 * per slot).
 */
public class BetterAutoCrafterBlockEntity extends AutoMachineBlockEntity {
    public static final int GRID_WIDTH = 3;
    public static final int GRID_HEIGHT = 3;
    public static final int GRID_SIZE = GRID_WIDTH * GRID_HEIGHT;

    /** A crafting or an auto crafting recipe. */
    @Nullable
    private Recipe<CraftingInput> cachedRecipe;
    /** Items each grid slot takes per craft (0 for empty slots); all 0 without a recipe. */
    private int[] cachedAmounts = new int[GRID_SIZE];
    private ItemStack cachedResult = ItemStack.EMPTY;
    private boolean resultDirty = true;

    public BetterAutoCrafterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BETTER_AUTO_CRAFTER.get(), pos, state, GRID_WIDTH, GRID_HEIGHT);
    }

    @Override
    protected void onTemplateChanged() {
        resultDirty = true;
    }

    /**
     * Returns the item the current 3x3 grid would craft. Looks the recipe up through the
     * recipe manager, so any crafting recipe (vanilla, datapack or modded) is supported.
     * The result is cached and only recomputed when the grid changes.
     */
    public ItemStack getCraftingResult() {
        if (level == null || level.isClientSide) {
            return ItemStack.EMPTY;
        }
        updateRecipeCache();
        return cachedResult;
    }

    /** Server only: how many items a grid slot takes per craft (more than 1 only for auto crafting recipes). */
    public int getAmount(int slot) {
        if (level == null || level.isClientSide) {
            return 0;
        }
        updateRecipeCache();
        return cachedAmounts[slot];
    }

    private void updateRecipeCache() {
        if (!resultDirty || level == null) {
            return;
        }
        resultDirty = false;
        cachedRecipe = null;
        cachedAmounts = new int[GRID_SIZE];
        cachedResult = ItemStack.EMPTY;

        List<ItemStack> grid = getTemplateStacks();
        CraftingInput.Positioned positioned = CraftingInput.ofPositioned(GRID_WIDTH, GRID_HEIGHT, grid);
        CraftingInput input = positioned.input();
        // Crafting recipes first: the crafter keeps making whatever a crafting table makes.
        CraftingRecipe crafting = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(RecipeHolder::value).orElse(null);
        if (crafting != null) {
            cachedRecipe = crafting;
            for (int i = 0; i < GRID_SIZE; i++) {
                cachedAmounts[i] = grid.get(i).isEmpty() ? 0 : 1;
            }
        } else {
            AutoCraftingRecipe autoCrafting = level.getRecipeManager().getRecipeFor(ModRecipeTypes.AUTO_CRAFTING.get(), input, level)
                    .map(RecipeHolder::value).orElse(null);
            int[] amounts = autoCrafting == null ? null : autoCrafting.amounts(input);
            if (amounts == null) {
                return;
            }
            cachedRecipe = autoCrafting;
            // The input is the grid trimmed to its used area: put its amounts back where they are in the grid.
            for (int i = 0; i < amounts.length; i++) {
                int x = positioned.left() + i % input.width();
                int y = positioned.top() + i / input.width();
                cachedAmounts[x + y * GRID_WIDTH] = amounts[i];
            }
        }
        cachedResult = cachedRecipe.assemble(input, level.registryAccess());
    }

    @Override
    protected boolean canOperate() {
        updateRecipeCache();
        return cachedRecipe != null;
    }

    /** The recipe's amount of every grid item, whatever their count in the grid: the grid is only the recipe's layout. */
    @Override
    protected boolean operate(ServerLevel level, ItemTransfer transfer) {
        if (cachedRecipe == null) {
            return false;
        }
        Recipe<CraftingInput> recipe = cachedRecipe;
        List<ItemStack> grid = getTemplateStacks();
        List<ItemStack> ingredients = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            ingredients.add(grid.get(i).isEmpty() ? ItemStack.EMPTY : grid.get(i).copyWithCount(cachedAmounts[i]));
        }
        return transfer.run(ingredients, pulled -> craft(level, recipe, pulled));
    }

    /**
     * Crafts from the items that would actually be pulled, which only differ from the grid when NBT matching is off: the
     * recipe must still accept them, and the result is assembled from them (so data-driven recipes work).
     */
    @Nullable
    private static ItemTransfer.Output craft(ServerLevel level, Recipe<CraftingInput> recipe, List<List<ItemStack>> pulled) {
        List<ItemStack> items = pulled.stream().map(stacks -> stacks.isEmpty() ? ItemStack.EMPTY : stacks.getFirst().copyWithCount(1)).toList();
        CraftingInput input = CraftingInput.of(GRID_WIDTH, GRID_HEIGHT, items);
        if (!recipe.matches(input, level)) {
            return null;
        }
        ItemStack result = recipe.assemble(input, level.registryAccess());
        if (result.isEmpty()) {
            return null;
        }
        List<ItemStack> remainders = new ArrayList<>();
        if (recipe instanceof AutoCraftingRecipe) {
            remainders.addAll(ItemTransfer.merge(List.of(AutoCraftingRecipe.remainders(pulled))));
        } else {
            for (ItemStack remainder : recipe.getRemainingItems(input)) {
                if (!remainder.isEmpty()) {
                    remainders.add(remainder.copy());
                }
            }
        }
        return new ItemTransfer.Output(List.of(result), remainders, () -> result.onCraftedBySystem(level));
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        return new BetterAutoCrafterMenu(containerId, playerInventory, this);
    }
}
