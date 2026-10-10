package laseta.bac.compat.emi;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.compat.RecipeFillHelper;
import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.menu.slot.ResultPreviewSlot;
import laseta.bac.menu.transfer.GridFillPlanner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * EMI fill button for crafting recipes. Places whatever ingredients the player has; slots it can't fill are
 * highlighted in red but the button still works. It is only disabled when nothing at all can be placed.
 */
public class AutoCrafterEmiRecipeHandler implements EmiRecipeHandler<BetterAutoCrafterMenu> {
    /** Same colour EMI uses for missing ingredients on its own crafting handlers. */
    private static final int MISSING_SLOT_COLOR = 0x44FF0000;

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<BetterAutoCrafterMenu> screen) {
        // Everything the grid can be filled from: the grid itself and the player's inventory.
        List<EmiStack> stacks = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (!(slot instanceof ResultPreviewSlot) && slot.hasItem()) {
                stacks.add(EmiStack.of(slot.getItem()));
            }
        }
        return new EmiPlayerInventory(stacks);
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING
                && recipe.getInputs().size() <= BetterAutoCrafterBlockEntity.GRID_SIZE;
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<BetterAutoCrafterMenu> context) {
        GridFillPlanner.Plan plan = plan(recipe, context);
        if (context.getType() == EmiCraftContext.Type.CRAFTABLE) {
            // EMI's "craftables" list should only show recipes the player can fully make.
            return plan.fits() && plan.isComplete();
        }
        return plan.canApply();
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<BetterAutoCrafterMenu> context) {
        // The result can't be taken out of this block, so "craft straight to cursor/inventory" isn't supported.
        if (context.getDestination() != EmiCraftContext.Destination.NONE || !plan(recipe, context).canApply()) {
            return false;
        }
        RecipeFillHelper.requestFill(context.getScreenHandler(), wanted(recipe), context.getAmount() > 1);
        Minecraft.getInstance().setScreen(context.getScreen());
        return true;
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<BetterAutoCrafterMenu> context) {
        GridFillPlanner.Plan plan = plan(recipe, context);
        List<ClientTooltipComponent> tooltip = new ArrayList<>(EmiRecipeHandler.super.getTooltip(recipe, context));
        if (!plan.fits()) {
            tooltip.add(line(Component.translatable("gui.better_auto_crafter.transfer.inventory_full").withStyle(ChatFormatting.RED)));
        } else if (plan.canApply() && !plan.isComplete()) {
            tooltip.add(line(Component.translatable("gui.better_auto_crafter.transfer.partial").withStyle(ChatFormatting.GOLD)));
            tooltip.add(line(Component.translatable("gui.better_auto_crafter.transfer.partial.detail").withStyle(ChatFormatting.GRAY)));
        }
        return tooltip;
    }

    @Override
    public void render(EmiRecipe recipe, EmiCraftContext<BetterAutoCrafterMenu> context, List<Widget> widgets, GuiGraphics graphics) {
        boolean[] missing = plan(recipe, context).missing();
        List<EmiIngredient> inputs = recipe.getInputs();
        for (Widget widget : widgets) {
            if (widget instanceof SlotWidget slotWidget) {
                int index = indexOfSame(inputs, slotWidget.getStack());
                if (index >= 0 && index < missing.length && missing[index]) {
                    Bounds bounds = slotWidget.getBounds();
                    graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), MISSING_SLOT_COLOR);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static GridFillPlanner.Plan plan(EmiRecipe recipe, EmiCraftContext<BetterAutoCrafterMenu> context) {
        Minecraft minecraft = Minecraft.getInstance();
        return context.getScreenHandler().planFill(minecraft.player, wanted(recipe), false);
    }

    /** EMI crafting recipes list their inputs in 3x3 grid order (shaped recipes are padded with empty slots). */
    private static List<List<ItemStack>> wanted(EmiRecipe recipe) {
        return recipe.getInputs().stream()
                .map(ingredient -> RecipeFillHelper.candidates(ingredient.getEmiStacks().stream().map(EmiStack::getItemStack)))
                .toList();
    }

    /** Recipe widgets are built from the recipe's own input objects, so identity tells us which grid slot a widget is. */
    private static int indexOfSame(List<EmiIngredient> inputs, EmiIngredient ingredient) {
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i) == ingredient) {
                return i;
            }
        }
        return -1;
    }

    private static ClientTooltipComponent line(Component text) {
        return ClientTooltipComponent.create(text.getVisualOrderText());
    }
}
