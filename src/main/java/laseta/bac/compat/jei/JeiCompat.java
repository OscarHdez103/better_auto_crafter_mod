package laseta.bac.compat.jei;

import java.util.List;
import java.util.Objects;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.client.screen.AutoMachineScreen;
import laseta.bac.client.screen.BetterAutoCrafterScreen;
import laseta.bac.client.screen.BetterAutoBrewerScreen;
import laseta.bac.client.screen.BetterAutoMoverScreen;
import laseta.bac.client.screen.BetterAutoSmelterScreen;
import laseta.bac.compat.MachineFuel;
import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.material.Materials;
import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.menu.BetterAutoSmelterMenu;
import laseta.bac.registry.ModItems;
import laseta.bac.registry.ModMenuTypes;
import laseta.bac.registry.ModRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
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
        // Keep JEI's ingredient list and bookmarks clear of our side tabs, and let its items be dragged onto the
        // machine tab's upgrade settings.
        addMachineScreen(registration, BetterAutoCrafterScreen.class);
        addMachineScreen(registration, BetterAutoMoverScreen.class);
        addMachineScreen(registration, BetterAutoSmelterScreen.class);
        addMachineScreen(registration, BetterAutoBrewerScreen.class);
        // Clicking the arrow shows all recipes the crafter makes, like on the vanilla crafting table.
        registration.addRecipeClickArea(BetterAutoCrafterScreen.class,
                BetterAutoCrafterScreen.ARROW_X, BetterAutoCrafterScreen.ARROW_Y,
                BetterAutoCrafterScreen.ARROW_WIDTH, BetterAutoCrafterScreen.ARROW_HEIGHT,
                RecipeTypes.CRAFTING, AutoCraftingCategory.TYPE);
        registration.addRecipeClickArea(BetterAutoSmelterScreen.class,
                BetterAutoSmelterScreen.ARROW_X, BetterAutoSmelterScreen.ARROW_Y,
                BetterAutoSmelterScreen.ARROW_WIDTH, BetterAutoSmelterScreen.ARROW_HEIGHT,
                AutoSmeltingCategory.TYPE, RecipeTypes.CAMPFIRE_COOKING, RecipeTypes.BLASTING, RecipeTypes.SMOKING,
                RecipeTypes.SMELTING, MachineFuelCategory.SMELTER);
        registration.addRecipeClickArea(BetterAutoBrewerScreen.class,
                BetterAutoBrewerScreen.ARROW_X, BetterAutoBrewerScreen.ARROW_Y,
                BetterAutoBrewerScreen.ARROW_WIDTH, BetterAutoBrewerScreen.ARROW_HEIGHT,
                AutoBrewingCategory.TYPE, RecipeTypes.BREWING, MachineFuelCategory.BREWER);
    }

    private static <S extends AutoMachineScreen<?>> void addMachineScreen(IGuiHandlerRegistration registration, Class<S> screenClass) {
        registration.addGuiContainerHandler(screenClass, new IGuiContainerHandler<S>() {
            @Override
            public @NotNull List<Rect2i> getGuiExtraAreas(@NotNull S screen) {
                return screen.getExtraAreas();
            }
        });
        registration.addGhostIngredientHandler(screenClass, new MachineGhostIngredientHandler<>());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AutoCraftingCategory(registration.getJeiHelpers().getGuiHelper()),
                new AutoSmeltingCategory(registration.getJeiHelpers().getGuiHelper()),
                new AutoBrewingCategory(registration.getJeiHelpers().getGuiHelper()),
                MachineFuelCategory.smelter(registration.getJeiHelpers().getGuiHelper()),
                MachineFuelCategory.brewer(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Objects.requireNonNull(Minecraft.getInstance().level);
        registration.addRecipes(AutoCraftingCategory.TYPE, level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.AUTO_CRAFTING.get()).stream()
                .filter(holder -> !holder.value().isIncomplete())
                .toList());
        registration.addRecipes(AutoSmeltingCategory.TYPE, level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.AUTO_SMELTING.get()).stream()
                .filter(holder -> !holder.value().isIncomplete())
                .toList());
        registration.addRecipes(AutoBrewingCategory.TYPE, level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.AUTO_BREWING.get()).stream()
                .filter(holder -> !holder.value().isIncomplete())
                .toList());
        registration.addRecipes(MachineFuelCategory.SMELTER, MachineFuel.smelterFuels());
        registration.addRecipes(MachineFuelCategory.BREWER, MachineFuel.brewerFuels());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
        addTransfer(registration, new MachineRecipeTransferHandler<>(helper, BetterAutoCrafterMenu.class,
                ModMenuTypes.BETTER_AUTO_CRAFTER, RecipeTypes.CRAFTING));
        addTransfer(registration, new MachineRecipeTransferHandler<>(helper, BetterAutoCrafterMenu.class,
                ModMenuTypes.BETTER_AUTO_CRAFTER, AutoCraftingCategory.TYPE));
        addTransfer(registration, new MachineRecipeTransferHandler<>(helper, BetterAutoSmelterMenu.class,
                ModMenuTypes.BETTER_AUTO_SMELTER, AutoSmeltingCategory.TYPE));
        addTransfer(registration, new MachineRecipeTransferHandler<>(helper, BetterAutoSmelterMenu.class,
                ModMenuTypes.BETTER_AUTO_SMELTER, RecipeTypes.CAMPFIRE_COOKING));
        addTransfer(registration, new MachineRecipeTransferHandler<>(helper, BetterAutoSmelterMenu.class,
                ModMenuTypes.BETTER_AUTO_SMELTER, RecipeTypes.BLASTING));
        addTransfer(registration, new MachineRecipeTransferHandler<>(helper, BetterAutoSmelterMenu.class,
                ModMenuTypes.BETTER_AUTO_SMELTER, RecipeTypes.SMOKING));
        addTransfer(registration, new MachineRecipeTransferHandler<>(helper, BetterAutoSmelterMenu.class,
                ModMenuTypes.BETTER_AUTO_SMELTER, RecipeTypes.SMELTING));
    }

    private static <C extends AutoMachineMenu, R extends Recipe<?>> void addTransfer(IRecipeTransferRegistration registration,
                                                                                     MachineRecipeTransferHandler<C, R> handler) {
        registration.addRecipeTransferHandler(handler, handler.getRecipeType());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModItems.BETTER_AUTO_CRAFTER.get(), RecipeTypes.CRAFTING, AutoCraftingCategory.TYPE);
        registration.addRecipeCatalyst(ModItems.BETTER_AUTO_SMELTER.get(), AutoSmeltingCategory.TYPE, RecipeTypes.CAMPFIRE_COOKING,
                RecipeTypes.BLASTING, RecipeTypes.SMOKING, RecipeTypes.SMELTING, MachineFuelCategory.SMELTER);
        registration.addRecipeCatalyst(ModItems.BETTER_AUTO_BREWER.get(), AutoBrewingCategory.TYPE, RecipeTypes.BREWING,
                MachineFuelCategory.BREWER);
    }

    /** The mod's items of materials another mod provides instead (its steel, with another steel mod) aren't listed. */
    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime runtime) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        List<ItemStack> hidden = ModItems.ITEMS.getEntries().stream()
                .map(item -> new ItemStack(item.get()))
                .filter(stack -> Materials.isHidden(stack.getItem(), level.registryAccess()))
                .toList();
        if (!hidden.isEmpty()) {
            runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
        }
    }
}
