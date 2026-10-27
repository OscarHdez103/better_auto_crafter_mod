package laseta.bac.client.screen;

import java.util.List;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.entity.BetterAutoMoverBlockEntity;
import laseta.bac.machine.MachineOption;
import laseta.bac.machine.MachineLayout;
import laseta.bac.menu.BetterAutoMoverMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * The mover's screen: the machine's default panel, with an arrow on each side of the grid showing which way the items
 * go, in the side configuration's input and output colours, and the grid mode's icons for the machine tab.
 */
public class BetterAutoMoverScreen extends AutoMachineScreen<BetterAutoMoverMenu> {
    private static final int ARROW_SIZE = 16;
    private static final int ICON_SIZE = 16;
    /** Right-pointing arrows in the side configuration's input and output colours, lighter on their upper half. */
    private static final ResourceLocation INPUT_ARROW = sprite("input_arrow");
    private static final ResourceLocation OUTPUT_ARROW = sprite("output_arrow");
    private static final Component INPUT_TOOLTIP = Component.translatable("gui.better_auto_crafter.better_auto_mover.input_arrow");
    private static final Component OUTPUT_TOOLTIP = Component.translatable("gui.better_auto_crafter.better_auto_mover.output_arrow");

    /** All together: the grid's cells joined into one block, which moves as a whole. */
    private static final ResourceLocation ALL_TOGETHER_ICON = sprite("all_together");
    /** Each slot: separate cells, some full and some empty, each moving on its own. */
    private static final ResourceLocation EACH_SLOT_ICON = sprite("each_slot");

    public BetterAutoMoverScreen(BetterAutoMoverMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "container/better_auto_mover/" + name);
    }

    @Override
    protected void renderOptionIcon(GuiGraphics graphics, MachineOption<?> option, Enum<?> value, int x, int y) {
        if (option == BetterAutoMoverBlockEntity.GRID_MODE) {
            graphics.blitSprite(value == BetterAutoMoverBlockEntity.GridMode.EACH_SLOT ? EACH_SLOT_ICON : ALL_TOGETHER_ICON,
                    x, y, ICON_SIZE, ICON_SIZE);
        }
    }

    // Arrows centered in the space left and right of the grid, at its middle row.
    private int inputArrowX() {
        MachineLayout layout = menu.getLayout();
        return leftPos + (layout.gridLeft() - 1 - ARROW_SIZE) / 2;
    }

    private int outputArrowX() {
        MachineLayout layout = menu.getLayout();
        int gridRight = layout.gridLeft() - 1 + layout.gridColumns() * MachineLayout.SLOT_SIZE;
        return leftPos + gridRight + (imageWidth - gridRight - ARROW_SIZE) / 2;
    }

    private int arrowY() {
        MachineLayout layout = menu.getLayout();
        return topPos + layout.gridTop() - 1 + (layout.gridRows() * MachineLayout.SLOT_SIZE - ARROW_SIZE) / 2;
    }

    @Override
    protected void renderMachineBackground(GuiGraphics graphics) {
        super.renderMachineBackground(graphics);
        graphics.blitSprite(INPUT_ARROW, inputArrowX(), arrowY(), ARROW_SIZE, ARROW_SIZE);
        graphics.blitSprite(OUTPUT_ARROW, outputArrowX(), arrowY(), ARROW_SIZE, ARROW_SIZE);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (menu.getCarried().isEmpty() && isOverArrow(mouseX, mouseY)) {
            Component tooltip = mouseX < leftPos + imageWidth / 2 ? INPUT_TOOLTIP : OUTPUT_TOOLTIP;
            graphics.renderComponentTooltip(font, List.of(tooltip.copy().withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    private boolean isOverArrow(int mouseX, int mouseY) {
        if (mouseY < arrowY() || mouseY >= arrowY() + ARROW_SIZE) {
            return false;
        }
        return (mouseX >= inputArrowX() && mouseX < inputArrowX() + ARROW_SIZE)
                || (mouseX >= outputArrowX() && mouseX < outputArrowX() + ARROW_SIZE);
    }
}
