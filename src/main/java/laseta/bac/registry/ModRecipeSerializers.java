package laseta.bac.registry;

import java.util.function.Supplier;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.recipe.AutoCraftingRecipe;
import laseta.bac.recipe.UpgradeLinkResetRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, BetterAutoCrafter.MODID);

    /** Clears linked upgrades. Keeps its original id so existing recipe files and data packs still work. */
    public static final Supplier<SimpleCraftingRecipeSerializer<UpgradeLinkResetRecipe>> UPGRADE_LINK_RESET =
            RECIPE_SERIALIZERS.register("translocation_upgrade_reset",
                    () -> new SimpleCraftingRecipeSerializer<>(UpgradeLinkResetRecipe::new));

    public static final Supplier<AutoCraftingRecipe.Serializer> AUTO_CRAFTING =
            RECIPE_SERIALIZERS.register("auto_crafting", AutoCraftingRecipe.Serializer::new);

    private ModRecipeSerializers() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
