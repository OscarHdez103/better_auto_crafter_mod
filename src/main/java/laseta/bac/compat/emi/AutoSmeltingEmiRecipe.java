package laseta.bac.compat.emi;

import java.util.Objects;

import dev.emi.emi.api.neoforge.NeoForgeEmiIngredient;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import laseta.bac.recipe.AutoSmeltingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * An {@link AutoSmeltingRecipe} in EMI, laid out like EMI's furnace recipes: the ingredient (with its count), an arrow
 * and the result, and below them the fuel each smelt uses.
 */
public class AutoSmeltingEmiRecipe extends BasicEmiRecipe {
    private static final int WIDTH = 82;
    private static final int HEIGHT = 38;
    private static final int TEXT_COLOR = 0xFF808080;

    private final int fuel;

    public AutoSmeltingEmiRecipe(EmiRecipeCategory category, RecipeHolder<AutoSmeltingRecipe> holder) {
        super(category, holder.id(), WIDTH, HEIGHT);
        AutoSmeltingRecipe recipe = holder.value();
        inputs.add(NeoForgeEmiIngredient.of(recipe.getIngredient()));
        outputs.add(EmiStack.of(recipe.getResultItem(Objects.requireNonNull(Minecraft.getInstance().level).registryAccess())));
        fuel = recipe.getFuel();
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(inputs.getFirst(), 0, 4);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 24, 5);
        widgets.addSlot(outputs.getFirst(), 56, 0).large(true).recipeContext(this);
        Component text = fuel == 0
                ? Component.translatable("gui.better_auto_crafter.jei.no_fuel")
                : Component.translatable("gui.better_auto_crafter.jei.fuel", fuel);
        widgets.addText(text, WIDTH / 2 - Minecraft.getInstance().font.width(text) / 2, 29, TEXT_COLOR, false);
    }
}
