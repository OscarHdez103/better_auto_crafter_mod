package laseta.bac.recipe;

import laseta.bac.item.ItemSettingUpgradeItem;
import laseta.bac.item.LinkingUpgradeItem;
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
 * An upgrade holding data alone in a crafting grid gives the same upgrade without it: a linked upgrade (translocation,
 * multi translocation or storage link) loses its link, an upgrade set to an item (fuel filter, potion starter) its
 * setting. A "special" recipe (like vanilla's map cloning), so it only matches upgrades holding data and recipe viewers
 * don't list it as a confusing "upgrade -> upgrade" recipe.
 */
public class UpgradeResetRecipe extends CustomRecipe {
    public UpgradeResetRecipe(CraftingBookCategory category) {
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
        if (upgrade.getItem() instanceof LinkingUpgradeItem linking) {
            linking.clearLinks(reset);
        }
        ItemSettingUpgradeItem.setSetting(reset, ItemStack.EMPTY);
        return reset;
    }

    /** Whether {@code stack} is an upgrade holding data this recipe clears. */
    public static boolean holdsData(ItemStack stack) {
        return LinkingUpgradeItem.isLinked(stack) || !ItemSettingUpgradeItem.getSetting(stack).isEmpty();
    }

    /** The single upgrade holding data in the grid, or EMPTY if the grid holds anything else (or more than one item). */
    private static ItemStack findUpgrade(CraftingInput input) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!found.isEmpty() || !holdsData(stack)) {
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
        return ModRecipeSerializers.UPGRADE_RESET.get();
    }
}
