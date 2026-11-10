package laseta.bac.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import java.util.List;
import java.util.Set;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.entity.BetterAutoBrewerBlockEntity;
import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.client.screen.AutoMachineScreen;
import laseta.bac.client.screen.BetterAutoBrewerScreen;
import laseta.bac.client.screen.BetterAutoCrafterScreen;
import laseta.bac.client.screen.BetterAutoHubScreen;
import laseta.bac.client.screen.BetterAutoMoverScreen;
import laseta.bac.client.screen.BetterAutoSmelterScreen;
import laseta.bac.compat.MachineFuel;
import laseta.bac.material.Materials;
import laseta.bac.recipe.AutoBrewingRecipe;
import laseta.bac.recipe.AutoCraftingRecipe;
import laseta.bac.recipe.AutoSmeltingRecipe;
import laseta.bac.registry.ModItems;
import laseta.bac.registry.ModMenuTypes;
import laseta.bac.registry.ModRecipeTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
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
    /** Recipes only the Better Auto Smelter makes, see {@link AutoSmeltingRecipe}. */
    public static final EmiRecipeCategory AUTO_SMELTING = new EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "auto_smelting"),
            EmiStack.of(ModItems.BETTER_AUTO_SMELTER.get()));
    /** Recipes only the Better Auto Brewer makes, see {@link AutoBrewingRecipe}. */
    public static final EmiRecipeCategory AUTO_BREWING = new EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "auto_brewing"),
            EmiStack.of(ModItems.BETTER_AUTO_BREWER.get()));
    /** What the smelter burns and the fuel each gives. */
    public static final EmiRecipeCategory SMELTER_FUEL = new EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "smelter_fuel"), EmiStack.of(Items.COAL));
    /** What the brewer burns and the fuel each gives. */
    public static final EmiRecipeCategory BREWER_FUEL = new EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "brewer_fuel"), EmiStack.of(Items.BLAZE_POWDER));

    @Override
    public void register(EmiRegistry registry) {
        // Keep EMI's panels clear of our side tabs, on every machine's screen, and of the hub's tabs.
        registry.addGenericExclusionArea((screen, consumer) -> {
            List<Rect2i> areas = screen instanceof AutoMachineScreen<?> machineScreen ? machineScreen.getExtraAreas()
                    : screen instanceof BetterAutoHubScreen hubScreen ? hubScreen.getExtraAreas()
                    : List.of();
            areas.forEach(area -> consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight())));
        });
        // Items dragged from EMI can be dropped on the machine tab's upgrade settings.
        registry.addDragDropHandler(BetterAutoCrafterScreen.class, new MachineDragDropHandler<>());
        registry.addDragDropHandler(BetterAutoMoverScreen.class, new MachineDragDropHandler<>());
        registry.addDragDropHandler(BetterAutoSmelterScreen.class, new MachineDragDropHandler<>());
        registry.addDragDropHandler(BetterAutoBrewerScreen.class, new MachineDragDropHandler<>());

        // The mod's items of materials another mod provides instead (its steel, with another steel mod) aren't listed.
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            registry.removeEmiStacks(stack -> !stack.getItemStack().isEmpty()
                    && Materials.isHidden(stack.getItemStack().getItem(), level.registryAccess()));
        }

        registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, EmiStack.of(ModItems.BETTER_AUTO_CRAFTER.get()));
        EmiStack smelter = EmiStack.of(ModItems.BETTER_AUTO_SMELTER.get());
        registry.addWorkstation(VanillaEmiRecipeCategories.CAMPFIRE_COOKING, smelter);
        registry.addWorkstation(VanillaEmiRecipeCategories.BLASTING, smelter);
        registry.addWorkstation(VanillaEmiRecipeCategories.SMOKING, smelter);
        registry.addWorkstation(VanillaEmiRecipeCategories.SMELTING, smelter);
        EmiStack brewer = EmiStack.of(ModItems.BETTER_AUTO_BREWER.get());
        registry.addWorkstation(VanillaEmiRecipeCategories.BREWING, brewer);
        registry.addCategory(AUTO_CRAFTING);
        registry.addWorkstation(AUTO_CRAFTING, EmiStack.of(ModItems.BETTER_AUTO_CRAFTER.get()));
        for (RecipeHolder<AutoCraftingRecipe> holder : registry.getRecipeManager().getAllRecipesFor(ModRecipeTypes.AUTO_CRAFTING.get())) {
            if (!holder.value().isIncomplete()) {
                registry.addRecipe(new AutoCraftingEmiRecipe(AUTO_CRAFTING, holder));
            }
        }
        registry.addRecipeHandler(ModMenuTypes.BETTER_AUTO_CRAFTER.get(), new MachineEmiRecipeHandler<>(
                Set.of(VanillaEmiRecipeCategories.CRAFTING, AUTO_CRAFTING), BetterAutoCrafterBlockEntity.GRID_SIZE));
        registry.addRecipeHandler(ModMenuTypes.BETTER_AUTO_SMELTER.get(), new MachineEmiRecipeHandler<>(
                Set.of(AUTO_SMELTING, VanillaEmiRecipeCategories.CAMPFIRE_COOKING, VanillaEmiRecipeCategories.BLASTING,
                        VanillaEmiRecipeCategories.SMOKING, VanillaEmiRecipeCategories.SMELTING), 1));
        registry.addRecipeHandler(ModMenuTypes.BETTER_AUTO_BREWER.get(), new MachineEmiRecipeHandler<>(
                Set.of(AUTO_BREWING), BetterAutoBrewerBlockEntity.GRID_SIZE,
                recipe -> recipe instanceof AutoBrewingEmiRecipe brewing ? brewing.getBrewingIngredients() : List.of()));

        registry.addCategory(AUTO_SMELTING);
        registry.addWorkstation(AUTO_SMELTING, smelter);
        for (RecipeHolder<AutoSmeltingRecipe> holder : registry.getRecipeManager().getAllRecipesFor(ModRecipeTypes.AUTO_SMELTING.get())) {
            if (!holder.value().isIncomplete()) {
                registry.addRecipe(new AutoSmeltingEmiRecipe(AUTO_SMELTING, holder));
            }
        }

        registry.addCategory(AUTO_BREWING);
        registry.addWorkstation(AUTO_BREWING, brewer);
        for (RecipeHolder<AutoBrewingRecipe> holder : registry.getRecipeManager().getAllRecipesFor(ModRecipeTypes.AUTO_BREWING.get())) {
            if (!holder.value().isIncomplete()) {
                registry.addRecipe(new AutoBrewingEmiRecipe(AUTO_BREWING, holder));
            }
        }

        registry.addCategory(SMELTER_FUEL);
        registry.addWorkstation(SMELTER_FUEL, smelter);
        MachineFuel.smelterFuels().forEach(fuel ->
                registry.addRecipe(new MachineFuelEmiRecipe(SMELTER_FUEL, fuel, MachineFuelEmiRecipe.FLAME)));
        registry.addCategory(BREWER_FUEL);
        registry.addWorkstation(BREWER_FUEL, brewer);
        MachineFuel.brewerFuels().forEach(fuel ->
                registry.addRecipe(new MachineFuelEmiRecipe(BREWER_FUEL, fuel, MachineFuelEmiRecipe.BLAZE_BAR)));

        // The hub does all of it, with those machines in its tabs.
        EmiStack hub = EmiStack.of(ModItems.BETTER_AUTO_HUB.get());
        for (EmiRecipeCategory category : List.of(VanillaEmiRecipeCategories.CRAFTING, AUTO_CRAFTING, AUTO_SMELTING,
                VanillaEmiRecipeCategories.CAMPFIRE_COOKING, VanillaEmiRecipeCategories.BLASTING, VanillaEmiRecipeCategories.SMOKING,
                VanillaEmiRecipeCategories.SMELTING, SMELTER_FUEL, AUTO_BREWING, VanillaEmiRecipeCategories.BREWING, BREWER_FUEL)) {
            registry.addWorkstation(category, hub);
        }
    }
}
