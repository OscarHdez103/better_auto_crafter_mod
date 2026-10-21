package laseta.bac.compat.emi;

import java.util.Objects;

import dev.emi.emi.api.neoforge.NeoForgeEmiIngredient;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import laseta.bac.recipe.AutoCraftingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * An {@link AutoCraftingRecipe} in EMI, laid out like EMI's crafting recipes. Its inputs are the 3x3 grid in order,
 * blank cells included (the fill handler relies on it), each with its count.
 */
public class AutoCraftingEmiRecipe extends BasicEmiRecipe {
    private static final int SIZE = AutoCraftingRecipe.MAX_SIZE;

    public AutoCraftingEmiRecipe(EmiRecipeCategory category, RecipeHolder<AutoCraftingRecipe> holder) {
        super(category, holder.id(), 118, 54);
        AutoCraftingRecipe recipe = holder.value();
        for (int i = 0; i < SIZE * SIZE; i++) {
            int x = i % SIZE;
            int y = i / SIZE;
            inputs.add(x < recipe.getWidth() && y < recipe.getHeight()
                    ? recipe.getCells().get(x + y * recipe.getWidth()).map(NeoForgeEmiIngredient::of).orElse(EmiStack.EMPTY)
                    : EmiStack.EMPTY);
        }
        outputs.add(EmiStack.of(recipe.getResultItem(Objects.requireNonNull(Minecraft.getInstance().level).registryAccess())));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (int i = 0; i < inputs.size(); i++) {
            EmiIngredient input = inputs.get(i);
            widgets.addSlot(input, (i % SIZE) * 18, (i / SIZE) * 18);
        }
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 60, 18);
        widgets.addSlot(outputs.getFirst(), 92, 14).large(true).recipeContext(this);
    }
}
