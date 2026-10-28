package laseta.bac.compat.jei;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.entity.BetterAutoBrewerBlockEntity;
import laseta.bac.recipe.AutoBrewingRecipe;
import laseta.bac.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;

/**
 * JEI category for {@link AutoBrewingRecipe}s, laid out like the brewer: its row of ingredients in brewing order, below
 * it the starter, an arrow and the result, and under them the fuel each brew uses. The starter and the result cycle
 * through 1 to {@value BetterAutoBrewerBlockEntity#BREW_SIZE} together: a brew makes one per starter.
 */
public class AutoBrewingCategory extends AbstractRecipeCategory<RecipeHolder<AutoBrewingRecipe>> {
    public static final RecipeType<RecipeHolder<AutoBrewingRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "auto_brewing"));

    private static final int SLOT = 18;
    private static final int WIDTH = AutoBrewingRecipe.MAX_INGREDIENTS * SLOT;
    private static final int HEIGHT = 56;
    private static final int BREW_Y = 24;
    private static final int STARTER_X = 45;
    private static final int ARROW_X = 68;
    private static final int RESULT_X = 99;
    private static final int TEXT_Y = 46;
    private static final int TEXT_COLOR = 0xFF808080;

    private final IGuiHelper guiHelper;

    public AutoBrewingCategory(IGuiHelper guiHelper) {
        super(TYPE, Component.translatable("gui.better_auto_crafter.jei.auto_brewing"),
                guiHelper.createDrawableItemLike(ModItems.BETTER_AUTO_BREWER.get()), WIDTH, HEIGHT);
        this.guiHelper = guiHelper;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, RecipeHolder<AutoBrewingRecipe> holder, @NotNull IFocusGroup focuses) {
        AutoBrewingRecipe recipe = holder.value();
        List<Ingredient> ingredients = recipe.getBrewingIngredients();
        for (int i = 0; i < ingredients.size(); i++) {
            builder.addInputSlot(i * SLOT + 1, 1).setStandardSlotBackground()
                    .addItemStacks(List.of(ingredients.get(i).getItems()));
        }
        // Lists of the same length cycle in step: each starter 1, 2 and 3 times, next to as many results.
        ItemStack result = recipe.getResultItem(Objects.requireNonNull(Minecraft.getInstance().level).registryAccess());
        List<ItemStack> starters = List.of(recipe.getStarter().getItems());
        builder.addInputSlot(STARTER_X, BREW_Y).setStandardSlotBackground()
                .addItemStacks(starters.stream().flatMap(starter -> byBrewSize(starter, 1)).toList());
        builder.addOutputSlot(RESULT_X, BREW_Y).setOutputSlotBackground()
                .addItemStacks(starters.stream().flatMap(starter -> byBrewSize(result, result.getCount())).toList());
    }

    /** {@code stack} for each brew size, 1 to {@value BetterAutoBrewerBlockEntity#BREW_SIZE} starters. */
    private static Stream<ItemStack> byBrewSize(ItemStack stack, int perStarter) {
        return IntStream.rangeClosed(1, BetterAutoBrewerBlockEntity.BREW_SIZE)
                .mapToObj(starters -> stack.copyWithCount(starters * perStarter));
    }

    @Override
    public void draw(@NotNull RecipeHolder<AutoBrewingRecipe> holder, @NotNull IRecipeSlotsView recipeSlotsView,
                     @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        guiHelper.getRecipeArrow().draw(graphics, ARROW_X, BREW_Y);
        Font font = Minecraft.getInstance().font;
        int fuel = holder.value().getFuel();
        Component text = fuel == 0
                ? Component.translatable("gui.better_auto_crafter.jei.no_fuel")
                : Component.translatable("gui.better_auto_crafter.jei.fuel", fuel);
        graphics.drawString(font, text, (WIDTH - font.width(text)) / 2, TEXT_Y, TEXT_COLOR, false);
    }
}
