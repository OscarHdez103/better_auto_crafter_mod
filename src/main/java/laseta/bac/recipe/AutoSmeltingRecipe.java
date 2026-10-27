package laseta.bac.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import laseta.bac.registry.ModItems;
import laseta.bac.registry.ModRecipeSerializers;
import laseta.bac.registry.ModRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.NotNull;

/**
 * A recipe only the Better Auto Smelter makes: like a furnace recipe, but it can take several items at once. Its input
 * slot holds one of the item as usual, and each operation pulls the ingredient's count of it. Data packs add them like
 * any recipe, e.g. {@code data/<namespace>/recipe/<name>.json}:
 *
 * <pre>{@code
 * {
 *   "type": "better_auto_crafter:auto_smelting",
 *   "ingredient": { "tag": "c:sands", "count": 4 },
 *   "result": { "id": "minecraft:glass", "count": 4 },
 *   "fuel": 600
 * }
 * }</pre>
 *
 * <p>{@code "count"} defaults to 1, {@code "fuel"} to a furnace's {@value #DEFAULT_FUEL} burn ticks (0 for none). The
 * smelter tries these before any vanilla cooking recipe, since they could never be reached otherwise.</p>
 */
public class AutoSmeltingRecipe implements Recipe<SingleRecipeInput> {
    /** What a furnace burns while smelting one item. */
    public static final int DEFAULT_FUEL = 200;

    private final String group;
    private final SizedIngredient ingredient;
    private final ItemStack result;
    private final int fuel;

    public AutoSmeltingRecipe(String group, SizedIngredient ingredient, ItemStack result, int fuel) {
        this.group = group;
        this.ingredient = ingredient;
        this.result = result;
        this.fuel = fuel;
    }

    public SizedIngredient getIngredient() {
        return ingredient;
    }

    /** Furnace burn ticks each operation uses. */
    public int getFuel() {
        return fuel;
    }

    /** Only the item matters: the smelter pulls the ingredient's count of it. */
    @Override
    public boolean matches(@NotNull SingleRecipeInput input, @NotNull Level level) {
        return ingredient.ingredient().test(input.item());
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull SingleRecipeInput input, HolderLookup.@NotNull Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
        return result;
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, ingredient.ingredient());
    }

    @Override
    public boolean isIncomplete() {
        return ingredient.ingredient().hasNoItems();
    }

    /** Not in the recipe book: no furnace makes these. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public @NotNull String getGroup() {
        return group;
    }

    @Override
    public @NotNull ItemStack getToastSymbol() {
        return new ItemStack(ModItems.BETTER_AUTO_SMELTER.get());
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.AUTO_SMELTING.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModRecipeTypes.AUTO_SMELTING.get();
    }

    public static class Serializer implements RecipeSerializer<AutoSmeltingRecipe> {
        private static final MapCodec<AutoSmeltingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.group),
                SizedIngredient.FLAT_CODEC.fieldOf("ingredient").forGetter(recipe -> recipe.ingredient),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("fuel", DEFAULT_FUEL).forGetter(recipe -> recipe.fuel)
        ).apply(instance, AutoSmeltingRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, AutoSmeltingRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, recipe -> recipe.group,
                SizedIngredient.STREAM_CODEC, recipe -> recipe.ingredient,
                ItemStack.STREAM_CODEC, recipe -> recipe.result,
                ByteBufCodecs.VAR_INT, recipe -> recipe.fuel,
                AutoSmeltingRecipe::new);

        @Override
        public @NotNull MapCodec<AutoSmeltingRecipe> codec() {
            return CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, AutoSmeltingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
