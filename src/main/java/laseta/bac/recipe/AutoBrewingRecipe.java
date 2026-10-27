package laseta.bac.recipe;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * A recipe only the Better Auto Brewer makes: a whole brew at once, from what it starts from and its ingredients in
 * order, for brews no brewing stand makes. Data packs add them like any recipe, e.g.
 * {@code data/<namespace>/recipe/<name>.json}:
 *
 * <pre>{@code
 * {
 *   "type": "better_auto_crafter:auto_brewing",
 *   "starter": { "item": "minecraft:glass_bottle" },
 *   "ingredients": [ { "item": "minecraft:honey_block" }, { "item": "minecraft:sugar" } ],
 *   "result": { "id": "minecraft:honey_bottle" },
 *   "fuel": 1
 * }
 * }</pre>
 *
 * <p>Up to {@value #MAX_INGREDIENTS} ingredients. Like a brewing stand, each operation turns the brewer's starters into
 * one result each, using one of each ingredient and {@code "fuel"} fuel (default: one per ingredient). The brewer tries
 * these before the brewing stand's recipes.</p>
 */
public class AutoBrewingRecipe implements Recipe<BrewingInput> {
    /** The Better Auto Brewer's ingredient slots. */
    public static final int MAX_INGREDIENTS = 9;

    private final String group;
    private final Ingredient starter;
    private final List<Ingredient> ingredients;
    private final ItemStack result;
    private final Optional<Integer> fuel;

    public AutoBrewingRecipe(String group, Ingredient starter, List<Ingredient> ingredients, ItemStack result, Optional<Integer> fuel) {
        this.group = group;
        this.starter = starter;
        this.ingredients = List.copyOf(ingredients);
        this.result = result;
        this.fuel = fuel;
    }

    public Ingredient getStarter() {
        return starter;
    }

    public List<Ingredient> getBrewingIngredients() {
        return ingredients;
    }

    /** Fuel each operation uses: the recipe's, or one per ingredient. */
    public int getFuel() {
        return fuel.orElse(ingredients.size());
    }

    @Override
    public boolean matches(@NotNull BrewingInput input, @NotNull Level level) {
        if (input.ingredients().size() != ingredients.size() || !starter.test(input.starter())) {
            return false;
        }
        for (int i = 0; i < ingredients.size(); i++) {
            if (!ingredients.get(i).test(input.ingredients().get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull BrewingInput input, HolderLookup.@NotNull Provider registries) {
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

    /** The starter, then the ingredients in order. */
    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> all = NonNullList.create();
        all.add(starter);
        all.addAll(ingredients);
        return all;
    }

    @Override
    public boolean isIncomplete() {
        return starter.hasNoItems() || ingredients.stream().anyMatch(Ingredient::hasNoItems);
    }

    /** Not in the recipe book: no brewing stand makes these. */
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
        return new ItemStack(ModItems.BETTER_AUTO_BREWER.get());
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.AUTO_BREWING.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModRecipeTypes.AUTO_BREWING.get();
    }

    public static class Serializer implements RecipeSerializer<AutoBrewingRecipe> {
        private static final Codec<List<Ingredient>> INGREDIENTS_CODEC = ExtraCodecs.nonEmptyList(Ingredient.CODEC_NONEMPTY.listOf())
                .validate(list -> list.size() <= MAX_INGREDIENTS
                        ? DataResult.success(list)
                        : DataResult.error(() -> "Too many ingredients, " + MAX_INGREDIENTS + " is maximum"));
        private static final MapCodec<AutoBrewingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.group),
                Ingredient.CODEC_NONEMPTY.fieldOf("starter").forGetter(recipe -> recipe.starter),
                INGREDIENTS_CODEC.fieldOf("ingredients").forGetter(recipe -> recipe.ingredients),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("fuel").forGetter(recipe -> recipe.fuel)
        ).apply(instance, AutoBrewingRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, AutoBrewingRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, recipe -> recipe.group,
                Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.starter,
                Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), recipe -> recipe.ingredients,
                ItemStack.STREAM_CODEC, recipe -> recipe.result,
                ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), recipe -> recipe.fuel,
                AutoBrewingRecipe::new);

        @Override
        public @NotNull MapCodec<AutoBrewingRecipe> codec() {
            return CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, AutoBrewingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
