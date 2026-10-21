package laseta.bac.compat.jei;

import java.util.List;
import java.util.Optional;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.recipe.AutoCraftingRecipe;
import laseta.bac.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.NotNull;

/**
 * JEI category for {@link AutoCraftingRecipe}s, laid out like JEI's crafting category: the 3x3 grid (always 9 slots, in
 * grid order, which the recipe transfer relies on), an arrow and the result. Every ingredient shows its count.
 */
public class AutoCraftingCategory extends AbstractRecipeCategory<RecipeHolder<AutoCraftingRecipe>> {
    public static final RecipeType<RecipeHolder<AutoCraftingRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "auto_crafting"));

    private static final int WIDTH = 116;
    private static final int HEIGHT = 54;
    private static final int ARROW_X = 61;
    private static final int ARROW_Y = 19;
    private static final int RESULT_X = 95;
    private static final int RESULT_Y = 19;

    private final IGuiHelper guiHelper;

    public AutoCraftingCategory(IGuiHelper guiHelper) {
        super(TYPE, Component.translatable("gui.better_auto_crafter.jei.auto_crafting"),
                guiHelper.createDrawableItemLike(ModItems.BETTER_AUTO_CRAFTER.get()), WIDTH, HEIGHT);
        this.guiHelper = guiHelper;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, RecipeHolder<AutoCraftingRecipe> holder, @NotNull IFocusGroup focuses) {
        AutoCraftingRecipe recipe = holder.value();
        int size = AutoCraftingRecipe.MAX_SIZE;
        for (int i = 0; i < size * size; i++) {
            int x = i % size;
            int y = i / size;
            IRecipeSlotBuilder slot = builder.addInputSlot(x * 18 + 1, y * 18 + 1).setStandardSlotBackground();
            Optional<SizedIngredient> cell = x < recipe.getWidth() && y < recipe.getHeight()
                    ? recipe.getCells().get(x + y * recipe.getWidth())
                    : Optional.empty();
            // The stacks carry the count, which JEI shows on the slot.
            cell.ifPresent(ingredient -> slot.addItemStacks(List.of(ingredient.getItems())));
        }
        builder.addOutputSlot(RESULT_X, RESULT_Y).setOutputSlotBackground()
                .addItemStack(recipe.getResultItem(Minecraft.getInstance().level.registryAccess()));
    }

    @Override
    public void draw(@NotNull RecipeHolder<AutoCraftingRecipe> recipe, @NotNull IRecipeSlotsView recipeSlotsView,
                     @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        guiHelper.getRecipeArrow().draw(graphics, ARROW_X, ARROW_Y);
    }
}
