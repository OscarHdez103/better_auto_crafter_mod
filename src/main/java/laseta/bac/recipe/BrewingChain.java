package laseta.bac.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * What a row of ingredients brews, step after step as in a brewing stand: each step brews what the previous ones made
 * with the next ingredients, through either one of the brewer's own recipes ({@link AutoBrewingRecipe}, which may take
 * several ingredients at once) or a brewing stand recipe (one ingredient, vanilla's and every mod's). So recipes chain:
 * if A + x makes B and B + y makes C, then A + x, y makes C.
 *
 * <p>At each step the brewer's own recipes are tried first, those taking more ingredients first, then the brewing stand;
 * if a choice leads to a dead end further on, the next one is tried. When several choices lead to different potions,
 * all of them are brews the row makes, in that order.</p>
 */
public final class BrewingChain {
    /** Most different brews looked for. */
    public static final int MAX_BREWS = 32;

    /**
     * A whole brew.
     *
     * @param result what each starter becomes
     * @param fuel   fuel it uses: each own recipe's, plus one per ingredient brewed in a brewing stand's way
     */
    public record Brew(ItemStack result, int fuel) {
    }

    private BrewingChain() {
    }

    /** The first brew {@code starter} makes with {@code ingredients} in order; null if they don't make one. */
    @Nullable
    public static Brew find(Level level, List<AutoBrewingRecipe> ownRecipes, ItemStack starter, List<ItemStack> ingredients) {
        List<Brew> brews = findAll(level, ownRecipes, starter, ingredients);
        return brews.isEmpty() ? null : brews.getFirst();
    }

    /** Every brew {@code starter} makes with {@code ingredients} in order, one per different result, the first first. */
    public static List<Brew> findAll(Level level, List<AutoBrewingRecipe> ownRecipes, ItemStack starter, List<ItemStack> ingredients) {
        List<Brew> found = new ArrayList<>();
        if (starter.isEmpty() || ingredients.isEmpty()) {
            return found;
        }
        List<AutoBrewingRecipe> longestFirst = ownRecipes.stream()
                .sorted(Comparator.comparingInt((AutoBrewingRecipe recipe) -> recipe.getBrewingIngredients().size()).reversed())
                .toList();
        brewFrom(level, longestFirst, starter.copyWithCount(1), ingredients, 0, found);
        return found;
    }

    private static void brewFrom(Level level, List<AutoBrewingRecipe> ownRecipes, ItemStack brewed, List<ItemStack> rest, int fuel,
                                 List<Brew> found) {
        for (AutoBrewingRecipe recipe : ownRecipes) {
            int size = recipe.getBrewingIngredients().size();
            if (found.size() >= MAX_BREWS || size > rest.size()) {
                continue;
            }
            BrewingInput input = new BrewingInput(brewed, rest.subList(0, size));
            if (recipe.matches(input, level)) {
                next(level, ownRecipes, recipe.assemble(input, level.registryAccess()), rest.subList(size, rest.size()),
                        fuel + recipe.getFuel(), found);
            }
        }
        PotionBrewing brewing = level.potionBrewing();
        ItemStack ingredient = rest.getFirst();
        if (found.size() < MAX_BREWS && brewing.hasMix(brewed, ingredient)) {
            next(level, ownRecipes, brewing.mix(ingredient, brewed), rest.subList(1, rest.size()), fuel + 1, found);
        }
    }

    /** Goes on from a step's result; only the last step's count matters, a starter is always one item. */
    private static void next(Level level, List<AutoBrewingRecipe> ownRecipes, ItemStack result, List<ItemStack> rest, int fuel,
                             List<Brew> found) {
        if (result.isEmpty()) {
            return;
        }
        if (!rest.isEmpty()) {
            brewFrom(level, ownRecipes, result.copyWithCount(1), rest, fuel, found);
        } else if (found.stream().noneMatch(brew -> ItemStack.matches(brew.result(), result))) {
            found.add(new Brew(result, fuel));
        }
    }
}
