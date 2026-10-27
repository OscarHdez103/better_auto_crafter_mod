package laseta.bac.registry;

import java.util.function.Supplier;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.recipe.AutoBrewingRecipe;
import laseta.bac.recipe.AutoCraftingRecipe;
import laseta.bac.recipe.AutoSmeltingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, BetterAutoCrafter.MODID);

    /** Recipes only the Better Auto Crafter makes, see {@link AutoCraftingRecipe}. */
    public static final Supplier<RecipeType<AutoCraftingRecipe>> AUTO_CRAFTING = RECIPE_TYPES.register("auto_crafting",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "auto_crafting")));

    /** Recipes only the Better Auto Smelter makes, see {@link AutoSmeltingRecipe}. */
    public static final Supplier<RecipeType<AutoSmeltingRecipe>> AUTO_SMELTING = RECIPE_TYPES.register("auto_smelting",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "auto_smelting")));

    /** Recipes only the Better Auto Brewer makes, see {@link AutoBrewingRecipe}. */
    public static final Supplier<RecipeType<AutoBrewingRecipe>> AUTO_BREWING = RECIPE_TYPES.register("auto_brewing",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "auto_brewing")));

    private ModRecipeTypes() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
    }
}
