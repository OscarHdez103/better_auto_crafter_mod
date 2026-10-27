package laseta.bac.recipe;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import org.jetbrains.annotations.NotNull;

/**
 * What a brew is made of: the item it starts from (e.g. a water bottle) and its ingredients in the order they go in.
 * As a recipe input: the starter first, then the ingredients.
 */
public record BrewingInput(ItemStack starter, List<ItemStack> ingredients) implements RecipeInput {
    public BrewingInput {
        ingredients = List.copyOf(ingredients);
    }

    @Override
    public @NotNull ItemStack getItem(int index) {
        return index == 0 ? starter : ingredients.get(index - 1);
    }

    @Override
    public int size() {
        return 1 + ingredients.size();
    }
}
