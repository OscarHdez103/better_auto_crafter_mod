package laseta.bac.recipe;

import laseta.bac.item.TranslocationUpgradeItem;
import laseta.bac.registry.ModDataComponents;
import laseta.bac.registry.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * A linked translocation upgrade alone in a crafting grid gives the same upgrade without its link.
 * A "special" recipe (like vanilla's map cloning), so it only matches linked upgrades and recipe viewers don't list
 * it as a confusing "upgrade -> upgrade" recipe.
 */
public class TranslocationUpgradeResetRecipe extends CustomRecipe {
    public TranslocationUpgradeResetRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(@NotNull CraftingInput input, @NotNull Level level) {
        return !findUpgrade(input).isEmpty();
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingInput input, HolderLookup.@NotNull Provider registries) {
        ItemStack upgrade = findUpgrade(input);
        if (upgrade.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack reset = upgrade.copyWithCount(1);
        reset.remove(ModDataComponents.TRANSLOCATOR_LINK.get());
        return reset;
    }

    /** The single linked upgrade in the grid, or EMPTY if the grid holds anything else (or more than one item). */
    private static ItemStack findUpgrade(CraftingInput input) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!found.isEmpty() || !(stack.getItem() instanceof TranslocationUpgradeItem)
                    || TranslocationUpgradeItem.getLink(stack) == null) {
                return ItemStack.EMPTY;
            }
            found = stack;
        }
        return found;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.TRANSLOCATION_UPGRADE_RESET.get();
    }
}
