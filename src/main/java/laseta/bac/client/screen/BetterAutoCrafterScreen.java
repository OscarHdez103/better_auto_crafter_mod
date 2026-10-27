package laseta.bac.client.screen;

import java.util.ArrayList;
import java.util.List;

import laseta.bac.menu.BetterAutoCrafterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The crafter's screen: the vanilla crafting table's background. For auto crafting recipes, grid slots that take more
 * than one item per craft show how many in their corner.
 */
public class BetterAutoCrafterScreen extends AutoMachineScreen<BetterAutoCrafterMenu> {
    // Vanilla crafting table background: 3x3 grid, arrow and result slot in exactly the positions the menu uses.
    // Replace with "better_auto_crafter:textures/gui/container/better_auto_crafter.png" if you make your own.
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");

    /** The arrow between the grid and the result, relative to the GUI's top-left corner (used by JEI's click area). */
    public static final int ARROW_X = 90;
    public static final int ARROW_Y = 35;
    public static final int ARROW_WIDTH = 22;
    public static final int ARROW_HEIGHT = 15;

    public BetterAutoCrafterScreen(BetterAutoCrafterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.titleLabelX = 29;
    }

    @Override
    protected void renderMachineBackground(GuiGraphics graphics) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        for (Slot slot : menu.slots) {
            int amount = menu.getAmount(slot);
            if (amount > 1) {
                drawSlotAmount(graphics, slot, amount);
            }
        }
    }

    @Override
    protected @NotNull List<Component> getTooltipFromContainerItem(@NotNull ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int amount = hoveredSlot == null ? 0 : menu.getAmount(hoveredSlot);
        if (amount > 1) {
            tooltip.add(Component.translatable("gui.better_auto_crafter.better_auto_crafter.amount.tooltip", amount)
                    .withStyle(ChatFormatting.GOLD));
        }
        return tooltip;
    }
}
