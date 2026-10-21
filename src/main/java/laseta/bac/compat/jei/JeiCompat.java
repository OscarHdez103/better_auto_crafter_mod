package laseta.bac.compat.jei;

import java.util.List;
import java.util.Objects;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.client.screen.AutoMachineScreen;
import laseta.bac.client.screen.BetterAutoCrafterScreen;
import laseta.bac.client.screen.BetterAutoMoverScreen;
import laseta.bac.registry.ModItems;
import laseta.bac.registry.ModRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
        addTabAreas(registration, BetterAutoCrafterScreen.class);
        addTabAreas(registration, BetterAutoMoverScreen.class);
        // Clicking the arrow shows all recipes the crafter makes, like on the vanilla crafting table.
        registration.addRecipeClickArea(BetterAutoCrafterScreen.class,
                BetterAutoCrafterScreen.ARROW_X, BetterAutoCrafterScreen.ARROW_Y,
                BetterAutoCrafterScreen.ARROW_WIDTH, BetterAutoCrafterScreen.ARROW_HEIGHT,
                RecipeTypes.CRAFTING, AutoCraftingCategory.TYPE);
    }

    private static <S extends AutoMachineScreen<?>> void addTabAreas(IGuiHandlerRegistration registration, Class<S> screenClass) {
        registration.addGuiContainerHandler(screenClass, new IGuiContainerHandler<S>() {
            @Override
            public @NotNull List<Rect2i> getGuiExtraAreas(@NotNull S screen) {
                return screen.getExtraAreas();
            }
        });
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AutoCraftingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Objects.requireNonNull(Minecraft.getInstance().level);
        registration.addRecipes(AutoCraftingCategory.TYPE, level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.AUTO_CRAFTING.get()).stream()
                .filter(holder -> !holder.value().isIncomplete())
                .toList());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new AutoCrafterRecipeTransferHandler<>(registration.getTransferHelper(),
                RecipeTypes.CRAFTING), RecipeTypes.CRAFTING);
        registration.addRecipeTransferHandler(new AutoCrafterRecipeTransferHandler<>(registration.getTransferHelper(),
                AutoCraftingCategory.TYPE), AutoCraftingCategory.TYPE);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModItems.BETTER_AUTO_CRAFTER.get(), RecipeTypes.CRAFTING, AutoCraftingCategory.TYPE);
    }
}
