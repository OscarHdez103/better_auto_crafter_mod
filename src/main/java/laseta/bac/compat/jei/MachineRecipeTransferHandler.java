package laseta.bac.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.blaze3d.vertex.PoseStack;

import laseta.bac.compat.RecipeFillHelper;
import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.menu.transfer.GridFillPlanner;
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
 * JEI "+" button that fills a machine's template with a recipe's inputs, in order: the crafter's grid with crafting and
 * auto crafting recipes, the smelter's input with any cooking recipe. Places whatever ingredients the player has, as
 * many as one operation takes (or as many operations' worth as fit, with shift); slots it can't fill are highlighted in
 * red but the button still works. It is only disabled when nothing at all can be placed. The machine then makes that
 * very recipe, if its template makes several.
 */
public class MachineRecipeTransferHandler<C extends AutoMachineMenu, R extends Recipe<?>>
        implements IRecipeTransferHandler<C, RecipeHolder<R>> {
    private static final int MISSING_SLOT_COLOR = 0x66FF0000;
    private static final int PARTIAL_BUTTON_COLOR = 0x80FFA500;

    private final IRecipeTransferHandlerHelper helper;
    private final Class<C> menuClass;
    private final Supplier<MenuType<C>> menuType;
    private final RecipeType<RecipeHolder<R>> recipeType;

    /** @param recipeType a category whose inputs are laid out in the template's slot order */
    public MachineRecipeTransferHandler(IRecipeTransferHandlerHelper helper, Class<C> menuClass, Supplier<MenuType<C>> menuType,
                                        RecipeType<RecipeHolder<R>> recipeType) {
        this.helper = helper;
        this.menuClass = menuClass;
        this.menuType = menuType;
        this.recipeType = recipeType;
    }

    @Override
    public @NotNull Class<? extends C> getContainerClass() {
        return menuClass;
    }

    @Override
    public @NotNull Optional<MenuType<C>> getMenuType() {
        return Optional.of(menuType.get());
    }

    @Override
    public @NotNull RecipeType<RecipeHolder<R>> getRecipeType() {
        return recipeType;
    }

    // Still the abstract method in JEI 19 (the newer context-based overload just forwards here), so it must be implemented.
    @SuppressWarnings("removal")
    @Override
    public @Nullable IRecipeTransferError transferRecipe(@NotNull C menu, @NotNull RecipeHolder<R> recipe,
                                                         IRecipeSlotsView recipeSlots, @NotNull Player player,
                                                         boolean maxTransfer, boolean doTransfer) {
        // JEI's crafting categories lay their inputs out as the 3x3 grid, in order; cooking ones have a single input.
        List<IRecipeSlotView> inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
        if (inputs.size() > menu.templateSize()) {
            return helper.createUserErrorWithTooltip(Component.translatable("gui.better_auto_crafter.transfer.too_large"));
        }

        List<List<ItemStack>> wanted = inputs.stream()
                .map(view -> RecipeFillHelper.candidates(view.getItemStacks()))
                .toList();

        // Checked without fillMaximum: this runs every frame while hovering, and the one-operation plan already
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
            RecipeFillHelper.requestFill(menu, wanted, maxTransfer, recipe.id());
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
