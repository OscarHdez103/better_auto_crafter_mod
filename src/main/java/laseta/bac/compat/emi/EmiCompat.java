package laseta.bac.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import laseta.bac.client.screen.BetterAutoCrafterScreen;
import laseta.bac.registry.ModItems;
import laseta.bac.registry.ModMenuTypes;

/**
 * Only loaded by EMI itself (found through the {@link EmiEntrypoint} annotation), so the mod works fine without EMI.
 */
@EmiEntrypoint
public class EmiCompat implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        // Keep EMI's panels clear of our side tabs.
        registry.addExclusionArea(BetterAutoCrafterScreen.class, (screen, consumer) ->
                screen.getExtraAreas().forEach(area ->
                        consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()))));

        registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, EmiStack.of(ModItems.BETTER_AUTO_CRAFTER.get()));
        registry.addRecipeHandler(ModMenuTypes.BETTER_AUTO_CRAFTER.get(), new AutoCrafterEmiRecipeHandler());
    }
}
