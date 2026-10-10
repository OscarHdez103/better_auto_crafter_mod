package laseta.bac.registry;

import java.util.function.Supplier;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.recipe.TranslocationUpgradeResetRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, BetterAutoCrafter.MODID);

    public static final Supplier<SimpleCraftingRecipeSerializer<TranslocationUpgradeResetRecipe>> TRANSLOCATION_UPGRADE_RESET =
            RECIPE_SERIALIZERS.register("translocation_upgrade_reset",
                    () -> new SimpleCraftingRecipeSerializer<>(TranslocationUpgradeResetRecipe::new));

    private ModRecipeSerializers() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
