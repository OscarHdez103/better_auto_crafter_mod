package laseta.bac.compat.emi;

import java.util.function.BiConsumer;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import laseta.bac.BetterAutoCrafter;
import laseta.bac.compat.BrewerFuelBar;
import laseta.bac.compat.MachineFuel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * One of a machine's fuels in EMI, laid out like EMI's own fuel recipes: the machine's fuel indicator (the smelter's
 * flame, the brewer's blaze bar), the fuel's slot and the fuel it gives.
 */
public class MachineFuelEmiRecipe extends BasicEmiRecipe {
    private static final int WIDTH = 128;
    private static final int HEIGHT = 18;
    private static final int SLOT_X = 22;
    private static final int TEXT_X = 42;

    /** Smelter: the flame burns for as long as the fuel does, like EMI's furnace fuels. */
    public static final BiConsumer<WidgetHolder, MachineFuel> FLAME = (widgets, fuel) -> {
        widgets.addTexture(EmiTexture.EMPTY_FLAME, 3, 1);
        widgets.addAnimatedTexture(EmiTexture.FULL_FLAME, 3, 1, 1000 * fuel.fuel() / 20, false, true, true);
    };
    public static final BiConsumer<WidgetHolder, MachineFuel> BLAZE_BAR = (widgets, fuel) ->
            widgets.addDrawable(0, (HEIGHT - BrewerFuelBar.HEIGHT) / 2, BrewerFuelBar.WIDTH, BrewerFuelBar.HEIGHT,
                    (graphics, mouseX, mouseY, delta) -> BrewerFuelBar.render(graphics, 0, 0));

    private final MachineFuel fuel;
    private final BiConsumer<WidgetHolder, MachineFuel> indicator;

    public MachineFuelEmiRecipe(EmiRecipeCategory category, MachineFuel fuel, BiConsumer<WidgetHolder, MachineFuel> indicator) {
        super(category, syntheticId(category, fuel), WIDTH, HEIGHT);
        this.fuel = fuel;
        this.indicator = indicator;
        inputs.add(EmiStack.of(fuel.stack()));
    }

    /** EMI's convention for recipes that aren't recipe files: an id starting with a slash. */
    private static ResourceLocation syntheticId(EmiRecipeCategory category, MachineFuel fuel) {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(fuel.stack().getItem());
        return ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID,
                "/" + category.getId().getPath() + "/" + item.getNamespace() + "/" + item.getPath());
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        indicator.accept(widgets, fuel);
        widgets.addSlot(inputs.getFirst(), SLOT_X, 0).recipeContext(this);
        widgets.addText(Component.translatable("gui.better_auto_crafter.jei.gives_fuel", fuel.fuel()), TEXT_X, 5, -1, true);
    }
}
