package laseta.bac.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import laseta.bac.BetterAutoCrafter;
import laseta.bac.client.screen.AutoMachineScreen;
import laseta.bac.recipe.AutoCraftingRecipe;
import laseta.bac.registry.ModItems;
import laseta.bac.registry.ModMenuTypes;
import laseta.bac.registry.ModRecipeTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Only loaded by EMI itself (found through the {@link EmiEntrypoint} annotation), so the mod works fine without EMI.
 */
@EmiEntrypoint
public class EmiCompat implements EmiPlugin {
    /** Recipes only the Better Auto Crafter makes, see {@link AutoCraftingRecipe}. */
    public static final EmiRecipeCategory AUTO_CRAFTING = new EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "auto_crafting"),
            EmiStack.of(ModItems.BETTER_AUTO_CRAFTER.get()));

    @Override
    public void register(EmiRegistry registry) {
        // Keep EMI's panels clear of our side tabs, on every machine's screen.
        registry.addGenericExclusionArea((screen, consumer) -> {
            if (screen instanceof AutoMachineScreen<?> machineScreen) {
                machineScreen.getExtraAreas().forEach(area ->
                        consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight())));
            }
        });

        registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, EmiStack.of(ModItems.BETTER_AUTO_CRAFTER.get()));
        EmiStack smelter = EmiStack.of(ModItems.BETTER_AUTO_SMELTER.get());
        registry.addWorkstation(VanillaEmiRecipeCategories.CAMPFIRE_COOKING, smelter);
        registry.addWorkstation(VanillaEmiRecipeCategories.BLASTING, smelter);
        registry.addWorkstation(VanillaEmiRecipeCategories.SMOKING, smelter);
        registry.addWorkstation(VanillaEmiRecipeCategories.SMELTING, smelter);
        registry.addWorkstation(VanillaEmiRecipeCategories.FUEL, smelter);
        registry.addWorkstation(VanillaEmiRecipeCategories.BREWING, EmiStack.of(ModItems.BETTER_AUTO_BREWER.get()));
        registry.addCategory(AUTO_CRAFTING);
        registry.addWorkstation(AUTO_CRAFTING, EmiStack.of(ModItems.BETTER_AUTO_CRAFTER.get()));
        for (RecipeHolder<AutoCraftingRecipe> holder : registry.getRecipeManager().getAllRecipesFor(ModRecipeTypes.AUTO_CRAFTING.get())) {
            if (!holder.value().isIncomplete()) {
                registry.addRecipe(new AutoCraftingEmiRecipe(AUTO_CRAFTING, holder));
            }
        }
        registry.addRecipeHandler(ModMenuTypes.BETTER_AUTO_CRAFTER.get(), new AutoCrafterEmiRecipeHandler());
    }
}
