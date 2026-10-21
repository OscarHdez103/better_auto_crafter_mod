package laseta.bac.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;

import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.compat.RecipeFillHelper;
import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.menu.transfer.GridFillPlanner;
import laseta.bac.registry.ModMenuTypes;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * JEI "+" button for crafting and auto crafting recipes. Places whatever ingredients the player has (one of each: the
 * grid is only the recipe's layout); slots it can't fill are highlighted in red but the button still works. It is only
 * disabled when nothing at all can be placed.
 */
public class AutoCrafterRecipeTransferHandler<R extends Recipe<?>>
        implements IRecipeTransferHandler<BetterAutoCrafterMenu, RecipeHolder<R>> {
    private static final int MISSING_SLOT_COLOR = 0x66FF0000;
    private static final int PARTIAL_BUTTON_COLOR = 0x80FFA500;

    private final IRecipeTransferHandlerHelper helper;
    private final RecipeType<RecipeHolder<R>> recipeType;

    /** @param recipeType a category that always lays its inputs out as the 3x3 grid, in order */
    public AutoCrafterRecipeTransferHandler(IRecipeTransferHandlerHelper helper, RecipeType<RecipeHolder<R>> recipeType) {
        this.helper = helper;
        this.recipeType = recipeType;
    }

    @Override
    public @NotNull Class<? extends BetterAutoCrafterMenu> getContainerClass() {
        return BetterAutoCrafterMenu.class;
    }

    @Override
    public @NotNull Optional<MenuType<BetterAutoCrafterMenu>> getMenuType() {
        return Optional.of(ModMenuTypes.BETTER_AUTO_CRAFTER.get());
    }

    @Override
    public @NotNull RecipeType<RecipeHolder<R>> getRecipeType() {
        return recipeType;
    }

    // Still the abstract method in JEI 19 (the newer context-based overload just forwards here), so it must be implemented.
    @SuppressWarnings("removal")
    @Override
    public @Nullable IRecipeTransferError transferRecipe(@NotNull BetterAutoCrafterMenu menu, @NotNull RecipeHolder<R> recipe,
                                                         IRecipeSlotsView recipeSlots, @NotNull Player player,
                                                         boolean maxTransfer, boolean doTransfer) {
        // JEI's crafting category (and the auto crafting one) always lays its inputs out as the 3x3 grid, in order.
        List<IRecipeSlotView> inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
        if (inputs.size() > BetterAutoCrafterBlockEntity.GRID_SIZE) {
            return helper.createUserErrorWithTooltip(Component.translatable("gui.better_auto_crafter.transfer.too_large"));
        }

        List<List<ItemStack>> wanted = inputs.stream()
                .map(view -> RecipeFillHelper.candidates(view.getItemStacks()))
                .toList();

        // Checked without fillMaximum: this runs every frame while hovering, and the one-craft plan already
        // tells us everything needed for the button state. The server does the real (possibly maximum) fill.
        GridFillPlanner.Plan plan = menu.planFill(player, wanted, false);
        if (!plan.fits()) {
            return helper.createUserErrorWithTooltip(Component.translatable("gui.better_auto_crafter.transfer.inventory_full"));
        }

        List<IRecipeSlotView> missing = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            if (plan.missing()[i]) {
                missing.add(inputs.get(i));
            }
        }
        if (plan.placedSlots() == 0) {
            return helper.createUserErrorForMissingSlots(Component.translatable("gui.better_auto_crafter.transfer.missing"), missing);
        }

        if (doTransfer) {
            RecipeFillHelper.requestFill(menu, wanted, maxTransfer);
        }
        return missing.isEmpty() ? null : new PartialTransferError(missing);
    }

    /** "Cosmetic" error: JEI still allows the transfer, but shows which slots will stay empty. */
    private record PartialTransferError(List<IRecipeSlotView> missingSlots) implements IRecipeTransferError {
        @Override
        public @NotNull Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public int getButtonHighlightColor() {
            return PARTIAL_BUTTON_COLOR;
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip) {
            tooltip.add(Component.translatable("gui.better_auto_crafter.transfer.partial"));
            tooltip.add(Component.translatable("gui.better_auto_crafter.transfer.partial.detail").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public void showError(GuiGraphics graphics, int mouseX, int mouseY, @NotNull IRecipeSlotsView recipeSlotsView,
                              int recipeX, int recipeY) {
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(recipeX, recipeY, 0);
            for (IRecipeSlotView slot : missingSlots) {
                slot.drawHighlight(graphics, MISSING_SLOT_COLOR);
            }
            pose.popPose();
        }
    }
}
