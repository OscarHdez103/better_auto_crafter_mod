package laseta.bac.compat.emi;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import laseta.bac.block.entity.BetterAutoBrewerBlockEntity;
import laseta.bac.recipe.AutoBrewingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * An {@link AutoBrewingRecipe} in EMI, laid out like the brewer: its row of ingredients in brewing order, below it the
 * starter, an arrow and the result, and under them the fuel each brew uses. The starter and the result cycle through
 * 1 to {@value BetterAutoBrewerBlockEntity#BREW_SIZE} together: a brew makes one per starter.
 */
public class AutoBrewingEmiRecipe extends BasicEmiRecipe {
    private static final int SLOT = 18;
    private static final int WIDTH = AutoBrewingRecipe.MAX_INGREDIENTS * SLOT;
    private static final int HEIGHT = 54;
    private static final int BREW_Y = 23;
    private static final int STARTER_X = 44;
    private static final int ARROW_X = 68;
    private static final int RESULT_X = 98;
    private static final int TEXT_Y = 45;
    private static final int TEXT_COLOR = 0xFF808080;

    private final List<EmiIngredient> ingredients;
    private final EmiIngredient starter;
    private final EmiIngredient result;
    private final int fuel;

    public AutoBrewingEmiRecipe(EmiRecipeCategory category, RecipeHolder<AutoBrewingRecipe> holder) {
        super(category, holder.id(), WIDTH, HEIGHT);
        AutoBrewingRecipe recipe = holder.value();
        ingredients = recipe.getBrewingIngredients().stream().map(EmiIngredient::of).toList();
        EmiIngredient starters = EmiIngredient.of(recipe.getStarter());
        EmiStack resultStack = EmiStack.of(recipe.getResultItem(Objects.requireNonNull(Minecraft.getInstance().level).registryAccess()));
        inputs.addAll(ingredients);
        inputs.add(starters);
        outputs.add(resultStack);
        // Lists of the same length cycle in step: each starter 1, 2 and 3 times, next to as many results.
        starter = new CyclingEmiIngredient(starters.getEmiStacks().stream().flatMap(stack -> byBrewSize(stack, 1)).toList());
        result = new CyclingEmiIngredient(starters.getEmiStacks().stream()
                .flatMap(stack -> byBrewSize(resultStack, resultStack.getAmount())).toList());
        fuel = recipe.getFuel();
    }

    /** {@code stack} for each brew size, 1 to {@value BetterAutoBrewerBlockEntity#BREW_SIZE} starters. */
    private static Stream<EmiStack> byBrewSize(EmiStack stack, long perStarter) {
        return IntStream.rangeClosed(1, BetterAutoBrewerBlockEntity.BREW_SIZE)
                .mapToObj(starters -> stack.copy().setAmount(starters * perStarter));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (int i = 0; i < ingredients.size(); i++) {
            widgets.addSlot(ingredients.get(i), i * SLOT, 0);
        }
        widgets.addSlot(starter, STARTER_X, BREW_Y);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, ARROW_X, BREW_Y + 1);
        widgets.addSlot(result, RESULT_X, BREW_Y).recipeContext(this);
        Component text = fuel == 0
                ? Component.translatable("gui.better_auto_crafter.jei.no_fuel")
                : Component.translatable("gui.better_auto_crafter.jei.fuel", fuel);
        widgets.addText(text, WIDTH / 2 - Minecraft.getInstance().font.width(text) / 2, TEXT_Y, TEXT_COLOR, false);
    }
}
