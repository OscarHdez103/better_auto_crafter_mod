package laseta.bac.block.entity;

import java.util.ArrayList;
import java.util.List;

import laseta.bac.CommonConfig;
import laseta.bac.machine.AutoMachineBlockEntity;
import laseta.bac.machine.ItemTransfer;
import laseta.bac.machine.MachineProblem;
import laseta.bac.machine.RecipeChoices;
import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.recipe.AutoCraftingRecipe;
import laseta.bac.registry.ModBlockEntities;
import laseta.bac.registry.ModRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
 * (one item per slot) or an {@link AutoCraftingRecipe} (possibly several items per slot, once the grid slots hold that
 * many).
 *
 * <p>Recipes are tried in this order: auto crafting recipes taking several of an item (the grid only holds that many
 * for them), crafting recipes, then the other auto crafting recipes. The player can pick any other recipe that matches
 * the grid instead ({@link #chooseRecipe}).</p>
 */
public class BetterAutoCrafterBlockEntity extends AutoMachineBlockEntity {
    public static final int GRID_WIDTH = 3;
    public static final int GRID_HEIGHT = 3;
    public static final int GRID_SIZE = GRID_WIDTH * GRID_HEIGHT;

    /**
     * A crafting or an auto crafting recipe the grid makes.
     *
     * @param amounts items each grid slot takes per craft (0 for empty slots)
     */
    private record Craft(ResourceLocation id, Recipe<CraftingInput> recipe, int[] amounts, ItemStack result) {
    }

    /** Every recipe the grid makes, in the order they are tried. */
    private List<Craft> cachedCrafts = List.of();
    /** Index in {@link #cachedCrafts} of the one used, -1 for none. */
    private int cachedChosen = -1;
    private boolean resultDirty = true;
    /** The recipe the player picked, used whenever the grid makes it. */
    @Nullable
    private ResourceLocation chosenRecipe;

    public BetterAutoCrafterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BETTER_AUTO_CRAFTER.get(), pos, state, GRID_WIDTH, GRID_HEIGHT);
    }

    @Override
    protected int getBaseSpeed() {
        return CommonConfig.crafterBaseSpeed();
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
        Craft craft = craft();
        return craft == null ? ItemStack.EMPTY : craft.result();
    }

    /** Server only: how many items a grid slot takes per craft (more than 1 only for auto crafting recipes). */
    public int getAmount(int slot) {
        Craft craft = craft();
        return craft == null ? 0 : craft.amounts()[slot];
    }

    /** The recipe the crafter makes: the player's choice if the grid makes it, otherwise the first one tried. */
    @Nullable
    private Craft craft() {
        updateRecipeCache();
        return cachedChosen < 0 ? null : cachedCrafts.get(cachedChosen);
    }

    private void updateRecipeCache() {
        if (!resultDirty || level == null || level.isClientSide) {
            return;
        }
        resultDirty = false;
        cachedCrafts = findCrafts();
        cachedChosen = cachedCrafts.isEmpty() ? -1 : 0;
        for (int i = 0; i < cachedCrafts.size(); i++) {
            if (cachedCrafts.get(i).id().equals(chosenRecipe)) {
                cachedChosen = i;
            }
        }
    }

    private List<Craft> findCrafts() {
        List<ItemStack> grid = getTemplateStacks();
        CraftingInput.Positioned positioned = CraftingInput.ofPositioned(GRID_WIDTH, GRID_HEIGHT, grid);
        CraftingInput input = positioned.input();
        List<Craft> crafts = new ArrayList<>();
        List<Craft> singleAutoCrafts = new ArrayList<>();
        for (RecipeHolder<AutoCraftingRecipe> holder : matchingRecipes(ModRecipeTypes.AUTO_CRAFTING.get(), input)) {
            int[] amounts = holder.value().amounts(input);
            if (amounts == null) {
                continue;
            }
            // The input is the grid trimmed to its used area: put its amounts back where they are in the grid.
            int[] gridAmounts = new int[GRID_SIZE];
            for (int i = 0; i < amounts.length; i++) {
                int x = positioned.left() + i % input.width();
                int y = positioned.top() + i / input.width();
                gridAmounts[x + y * GRID_WIDTH] = amounts[i];
            }
            addCraft(holder.value().needsSeveral() ? crafts : singleAutoCrafts, holder.id(), holder.value(), gridAmounts, input);
        }
        int[] ones = new int[GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++) {
            ones[i] = grid.get(i).isEmpty() ? 0 : 1;
        }
        for (RecipeHolder<CraftingRecipe> holder : matchingRecipes(RecipeType.CRAFTING, input)) {
            addCraft(crafts, holder.id(), holder.value(), ones, input);
        }
        crafts.addAll(singleAutoCrafts);
        return crafts;
    }

    private void addCraft(List<Craft> crafts, ResourceLocation id, Recipe<CraftingInput> recipe, int[] amounts, CraftingInput input) {
        ItemStack result = recipe.assemble(input, level.registryAccess());
        if (!result.isEmpty()) {
            crafts.add(new Craft(id, recipe, amounts, result));
        }
    }

    @Override
    public RecipeChoices getRecipeChoices() {
        updateRecipeCache();
        return new RecipeChoices(cachedCrafts.stream().map(craft -> new RecipeChoices.Choice(craft.result(), 0)).toList(),
                cachedChosen);
    }

    @Override
    public boolean chooseRecipe(ResourceLocation id) {
        updateRecipeCache();
        for (int i = 0; i < cachedCrafts.size(); i++) {
            if (cachedCrafts.get(i).id().equals(id)) {
                return chooseRecipe(i);
            }
        }
        return false;
    }

    @Override
    public boolean chooseRecipe(int index) {
        updateRecipeCache();
        if (index < 0 || index >= cachedCrafts.size()) {
            return false;
        }
        chosenRecipe = cachedCrafts.get(index).id();
        onRecipeChosen();
        return true;
    }

    @Override
    protected @Nullable MachineProblem templateProblem() {
        if (craft() != null) {
            return null;
        }
        MachineProblem empty = super.templateProblem();
        return empty != null ? empty : MachineProblem.NO_RECIPE;
    }

    /** The recipe's amount of every grid item (one for crafting recipes), however many more the grid slot holds. */
    @Override
    protected boolean operate(ServerLevel level, ItemTransfer transfer) {
        Craft craft = craft();
        if (craft == null) {
            return transfer.fail(MachineProblem.NO_RECIPE);
        }
        List<ItemStack> grid = getTemplateStacks();
        List<ItemStack> ingredients = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            ingredients.add(grid.get(i).isEmpty() ? ItemStack.EMPTY : grid.get(i).copyWithCount(craft.amounts()[i]));
        }
        return transfer.run(ingredients, pulled -> craft(level, craft, pulled));
    }

    /**
     * Crafts from the items that would actually be pulled, which only differ from the grid when NBT matching is off: the
     * recipe must still accept them, and the result is assembled from them (so data-driven recipes work).
     */
    @Nullable
    private static ItemTransfer.Output craft(ServerLevel level, Craft craft, List<List<ItemStack>> pulled) {
        Recipe<CraftingInput> recipe = craft.recipe();
        List<ItemStack> items = new ArrayList<>(pulled.size());
        for (int i = 0; i < pulled.size(); i++) {
            List<ItemStack> stacks = pulled.get(i);
            items.add(stacks.isEmpty() ? ItemStack.EMPTY : stacks.getFirst().copyWithCount(Math.max(1, craft.amounts()[i])));
        }
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

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        if (chosenRecipe != null) {
            tag.putString("chosen_recipe", chosenRecipe.toString());
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        chosenRecipe = tag.contains("chosen_recipe") ? ResourceLocation.tryParse(tag.getString("chosen_recipe")) : null;
        resultDirty = true;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        return new BetterAutoCrafterMenu(containerId, playerInventory, this);
    }
}
