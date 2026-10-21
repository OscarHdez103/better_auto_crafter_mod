package laseta.bac.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A recipe only the Better Auto Crafter makes. Shaped like a crafting recipe (up to 3x3, also mirrored), but every key
 * can ask for several items: the grid holds one of the item as usual, and each craft pulls the key's count of it.
 * Data packs add them like any recipe, e.g. {@code data/<namespace>/recipe/<name>.json}:
 *
 * <pre>{@code
 * {
 *   "type": "better_auto_crafter:auto_crafting",
 *   "pattern": ["CRC", "RIR", "CRC"],
 *   "key": {
 *     "C": { "tag": "c:ingots/copper", "count": 3 },
 *     "R": { "tag": "c:dusts/redstone", "count": 2 },
 *     "I": { "item": "minecraft:iron_ingot" }
 *   },
 *   "result": { "id": "better_auto_crafter:better_material" }
 * }
 * }</pre>
 *
 * <p>A key is any ingredient plus an optional {@code "count"} (default 1), NeoForge's sized ingredient. When a vanilla
 * crafting recipe has the same layout, the crafter makes that one instead.</p>
 */
public class AutoCraftingRecipe implements Recipe<CraftingInput> {
    /** The Better Auto Crafter's grid size. */
    public static final int MAX_SIZE = 3;

    private final String group;
    private final Pattern pattern;
    private final ItemStack result;

    public AutoCraftingRecipe(String group, Pattern pattern, ItemStack result) {
        this.group = group;
        this.pattern = pattern;
        this.result = result;
    }

    public int getWidth() {
        return pattern.width();
    }

    public int getHeight() {
        return pattern.height();
    }

    /** The pattern's cells, row by row ({@link #getWidth()} per row); empty for a blank cell. */
    public List<Optional<SizedIngredient>> getCells() {
        return pattern.cells();
    }

    /**
     * How many items each slot of {@code input} takes per craft, in the input's slot order (0 for empty slots), or null
     * if the recipe doesn't match it. Counts are ignored: one item in a slot is enough to match.
     */
    public int @Nullable [] amounts(CraftingInput input) {
        if (input.width() != pattern.width() || input.height() != pattern.height()) {
            return null;
        }
        int[] amounts = amounts(input, false);
        return amounts != null ? amounts : amounts(input, true);
    }

    private int @Nullable [] amounts(CraftingInput input, boolean mirrored) {
        int width = pattern.width();
        int[] amounts = new int[input.size()];
        for (int y = 0; y < pattern.height(); y++) {
            for (int x = 0; x < width; x++) {
                Optional<SizedIngredient> cell = pattern.cells().get((mirrored ? width - 1 - x : x) + y * width);
                ItemStack stack = input.getItem(x + y * width);
                if (cell.isEmpty()) {
                    if (!stack.isEmpty()) {
                        return null;
                    }
                } else if (cell.get().ingredient().test(stack)) {
                    amounts[x + y * width] = cell.get().count();
                } else {
                    return null;
                }
            }
        }
        return amounts;
    }

    /** The crafting remainders of everything that was pulled (an empty bucket for every bucket, not one per slot). */
    public static List<ItemStack> remainders(List<List<ItemStack>> pulled) {
        List<ItemStack> remainders = new ArrayList<>();
        for (List<ItemStack> slot : pulled) {
            for (ItemStack stack : slot) {
                if (stack.hasCraftingRemainingItem()) {
                    ItemStack remainder = stack.getCraftingRemainingItem();
                    for (int i = 0; i < stack.getCount(); i++) {
                        remainders.add(remainder.copy());
                    }
                }
            }
        }
        return remainders;
    }

    @Override
    public boolean matches(@NotNull CraftingInput input, @NotNull Level level) {
        return amounts(input) != null;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingInput input, HolderLookup.@NotNull Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= pattern.width() && height >= pattern.height();
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
        return result;
    }

    /** The pattern's ingredients without their counts, for mods that only look at the ingredients. */
    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        pattern.cells().forEach(cell -> ingredients.add(cell.map(SizedIngredient::ingredient).orElse(Ingredient.EMPTY)));
        return ingredients;
    }

    /** Blank cells are fine; an ingredient without any item (e.g. an empty tag) isn't. */
    @Override
    public boolean isIncomplete() {
        return pattern.cells().stream().flatMap(Optional::stream).anyMatch(cell -> cell.ingredient().hasNoItems());
    }

    /** Not in the recipe book: these can't be crafted by hand. Recipe viewers show them in their own category. */
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
        return new ItemStack(ModItems.BETTER_AUTO_CRAFTER.get());
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.AUTO_CRAFTING.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModRecipeTypes.AUTO_CRAFTING.get();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Pattern
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The recipe's layout, trimmed to its used rows and columns like vanilla's shaped recipes.
     *
     * @param cells row by row; empty for a blank cell
     * @param data  what it was read from, kept to write it back (absent on clients, which get it from the network)
     */
    public record Pattern(int width, int height, List<Optional<SizedIngredient>> cells, Optional<Data> data) {
        public static final MapCodec<Pattern> MAP_CODEC = Data.MAP_CODEC.flatXmap(Pattern::unpack,
                pattern -> pattern.data().map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "Cannot encode an unpacked auto crafting pattern")));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pattern> STREAM_CODEC =
                StreamCodec.of((buffer, pattern) -> pattern.toNetwork(buffer), Pattern::fromNetwork);
        private static final StreamCodec<RegistryFriendlyByteBuf, Optional<SizedIngredient>> CELL_STREAM_CODEC =
                ByteBufCodecs.optional(SizedIngredient.STREAM_CODEC);

        private static DataResult<Pattern> unpack(Data data) {
            // Trim blank rows and columns around the used area.
            int left = Integer.MAX_VALUE;
            int right = -1;
            int top = -1;
            int bottom = -1;
            for (int row = 0; row < data.pattern().size(); row++) {
                String line = data.pattern().get(row);
                for (int column = 0; column < line.length(); column++) {
                    if (line.charAt(column) != ' ') {
                        left = Math.min(left, column);
                        right = Math.max(right, column);
                        top = top < 0 ? row : top;
                        bottom = row;
                    }
                }
            }
            if (right < 0) {
                return DataResult.error(() -> "Invalid pattern: it has no ingredients");
            }
            int width = right - left + 1;
            int height = bottom - top + 1;
            List<Optional<SizedIngredient>> cells = new ArrayList<>(width * height);
            List<Character> unused = new ArrayList<>(data.key().keySet());
            for (int row = top; row <= bottom; row++) {
                for (int column = left; column <= right; column++) {
                    char symbol = data.pattern().get(row).charAt(column);
                    if (symbol == ' ') {
                        cells.add(Optional.empty());
                        continue;
                    }
                    SizedIngredient ingredient = data.key().get(symbol);
                    if (ingredient == null) {
                        return DataResult.error(() -> "Pattern references symbol '" + symbol + "' but it's not defined in the key");
                    }
                    unused.remove((Character) symbol);
                    cells.add(Optional.of(ingredient));
                }
            }
            if (!unused.isEmpty()) {
                return DataResult.error(() -> "Key defines symbols that aren't used in pattern: " + unused);
            }
            return DataResult.success(new Pattern(width, height, List.copyOf(cells), Optional.of(data)));
        }

        private void toNetwork(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(width);
            buffer.writeVarInt(height);
            for (Optional<SizedIngredient> cell : cells) {
                CELL_STREAM_CODEC.encode(buffer, cell);
            }
        }

        private static Pattern fromNetwork(RegistryFriendlyByteBuf buffer) {
            int width = buffer.readVarInt();
            int height = buffer.readVarInt();
            List<Optional<SizedIngredient>> cells = new ArrayList<>(width * height);
            for (int i = 0; i < width * height; i++) {
                cells.add(CELL_STREAM_CODEC.decode(buffer));
            }
            return new Pattern(width, height, List.copyOf(cells), Optional.empty());
        }
    }

    /** The pattern as written in JSON: rows of symbols, and what each symbol stands for. */
    public record Data(Map<Character, SizedIngredient> key, List<String> pattern) {
        private static final Codec<List<String>> PATTERN_CODEC = Codec.STRING.listOf().validate(rows -> {
            if (rows.isEmpty()) {
                return DataResult.error(() -> "Invalid pattern: empty pattern not allowed");
            }
            if (rows.size() > MAX_SIZE) {
                return DataResult.error(() -> "Invalid pattern: too many rows, " + MAX_SIZE + " is maximum");
            }
            int width = rows.getFirst().length();
            for (String row : rows) {
                if (row.length() > MAX_SIZE) {
                    return DataResult.error(() -> "Invalid pattern: too many columns, " + MAX_SIZE + " is maximum");
                }
                if (row.length() != width) {
                    return DataResult.error(() -> "Invalid pattern: each row must be the same width");
                }
            }
            return DataResult.success(rows);
        });
        private static final Codec<Character> SYMBOL_CODEC = Codec.STRING.comapFlatMap(symbol -> {
            if (symbol.length() != 1) {
                return DataResult.error(() -> "Invalid key entry: '" + symbol + "' is an invalid symbol (must be 1 character only).");
            }
            return " ".equals(symbol)
                    ? DataResult.error(() -> "Invalid key entry: ' ' is a reserved symbol.")
                    : DataResult.success(symbol.charAt(0));
        }, String::valueOf);
        public static final MapCodec<Data> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ExtraCodecs.strictUnboundedMap(SYMBOL_CODEC, SizedIngredient.FLAT_CODEC).fieldOf("key").forGetter(Data::key),
                PATTERN_CODEC.fieldOf("pattern").forGetter(Data::pattern)
        ).apply(instance, Data::new));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Serializer
    // ---------------------------------------------------------------------------------------------------------------

    public static class Serializer implements RecipeSerializer<AutoCraftingRecipe> {
        private static final MapCodec<AutoCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.group),
                Pattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
        ).apply(instance, AutoCraftingRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, AutoCraftingRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, recipe -> recipe.group,
                Pattern.STREAM_CODEC, recipe -> recipe.pattern,
                ItemStack.STREAM_CODEC, recipe -> recipe.result,
                AutoCraftingRecipe::new);

        @Override
        public @NotNull MapCodec<AutoCraftingRecipe> codec() {
            return CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, AutoCraftingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
