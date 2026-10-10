package laseta.bac.compat.jei;

import java.util.List;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.client.screen.BetterAutoCrafterScreen;
import laseta.bac.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Only loaded by JEI itself (found through the {@link JeiPlugin} annotation), so the mod works fine without JEI.
 */
@JeiPlugin
public class JeiCompat implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "jei_plugin");

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Keep JEI's ingredient list and bookmarks clear of our side tabs.
        registration.addGuiContainerHandler(BetterAutoCrafterScreen.class, new IGuiContainerHandler<>() {
            @Override
            public @NotNull List<Rect2i> getGuiExtraAreas(@NotNull BetterAutoCrafterScreen screen) {
                return screen.getExtraAreas();
            }
        });
        // Clicking the arrow shows all crafting recipes, like on the vanilla crafting table.
        registration.addRecipeClickArea(BetterAutoCrafterScreen.class,
                BetterAutoCrafterScreen.ARROW_X, BetterAutoCrafterScreen.ARROW_Y,
                BetterAutoCrafterScreen.ARROW_WIDTH, BetterAutoCrafterScreen.ARROW_HEIGHT,
                RecipeTypes.CRAFTING);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new AutoCrafterRecipeTransferHandler(registration.getTransferHelper()),
                RecipeTypes.CRAFTING);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModItems.BETTER_AUTO_CRAFTER.get(), RecipeTypes.CRAFTING);
    }
}
