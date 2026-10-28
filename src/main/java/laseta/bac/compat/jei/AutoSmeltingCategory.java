package laseta.bac.compat.jei;

import java.util.List;
import java.util.Objects;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.recipe.AutoSmeltingRecipe;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;

/**
 * JEI category for {@link AutoSmeltingRecipe}s, laid out like JEI's furnace categories: the ingredient (with its count),
 * an arrow and the result, and below them the fuel each smelt uses.
 */
public class AutoSmeltingCategory extends AbstractRecipeCategory<RecipeHolder<AutoSmeltingRecipe>> {
    public static final RecipeType<RecipeHolder<AutoSmeltingRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "auto_smelting"));

    private static final int WIDTH = 82;
    private static final int HEIGHT = 40;
    private static final int INPUT_X = 1;
    private static final int SLOTS_Y = 5;
    private static final int ARROW_X = 26;
    private static final int RESULT_X = 60;
    private static final int TEXT_Y = 30;
    private static final int TEXT_COLOR = 0xFF808080;

    private final IGuiHelper guiHelper;

    public AutoSmeltingCategory(IGuiHelper guiHelper) {
        super(TYPE, Component.translatable("gui.better_auto_crafter.jei.auto_smelting"),
                guiHelper.createDrawableItemLike(ModItems.BETTER_AUTO_SMELTER.get()), WIDTH, HEIGHT);
        this.guiHelper = guiHelper;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, RecipeHolder<AutoSmeltingRecipe> holder, @NotNull IFocusGroup focuses) {
        AutoSmeltingRecipe recipe = holder.value();
        // The stacks carry the count, which JEI shows on the slot.
        builder.addInputSlot(INPUT_X, SLOTS_Y).setStandardSlotBackground()
                .addItemStacks(List.of(recipe.getIngredient().getItems()));
        builder.addOutputSlot(RESULT_X, SLOTS_Y).setOutputSlotBackground()
                .addItemStack(recipe.getResultItem(Objects.requireNonNull(Minecraft.getInstance().level).registryAccess()));
    }

    @Override
    public void draw(@NotNull RecipeHolder<AutoSmeltingRecipe> holder, @NotNull IRecipeSlotsView recipeSlotsView,
                     @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        guiHelper.getRecipeArrow().draw(graphics, ARROW_X, SLOTS_Y);
        Font font = Minecraft.getInstance().font;
        int fuel = holder.value().getFuel();
        Component text = fuel == 0
                ? Component.translatable("gui.better_auto_crafter.jei.no_fuel")
                : Component.translatable("gui.better_auto_crafter.jei.fuel", fuel);
        graphics.drawString(font, text, (WIDTH - font.width(text)) / 2, TEXT_Y, TEXT_COLOR, false);
    }
}
