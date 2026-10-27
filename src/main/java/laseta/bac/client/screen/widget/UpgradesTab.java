package laseta.bac.client.screen.widget;

import java.util.List;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.upgrade.UpgradeLayout;
import laseta.bac.upgrade.UpgradeSlotType;
import laseta.bac.upgrade.UpgradeStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * Green tab on the left of the GUI holding the upgrade slots. The slots themselves are real menu slots
 * ({@link laseta.bac.menu.slot.UpgradeSlot}) that the screen shows while this tab is open; this tab draws their
 * frames, a faded icon of what goes into each empty slot, and a coloured frame showing whether the installed
 * upgrade works (green), partly works (yellow) or not (red), with the reason in the item's tooltip.
 */
public class UpgradesTab extends SideTab {
    private static final Component TITLE = Component.translatable("gui.better_auto_crafter.upgrades");
    private static final Component DESCRIPTION = Component.translatable("gui.better_auto_crafter.upgrades.description");
    private static final Palette PALETTE = new Palette(0xFF0E2410, 0xFF3E8E41, 0xFF6CC070, 0xFF2A632C);

    private static final int ACTIVE_FRAME = 0xFF55E055;
    private static final int PROBLEM_FRAME = 0xFFFF4040;
    private static final int WARNING_FRAME = 0xFFFFC020;

    /** Upward arrow, lighter on its left half. */
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "icon/upgrades");

    private final AutoMachineMenu menu;
    private int guiLeft;
    private int guiTop;

    public UpgradesTab(AutoMachineMenu menu, Font font) {
        super(TITLE, DESCRIPTION, PALETTE, font, UpgradeLayout.GRID_WIDTH, UpgradeLayout.GRID_HEIGHT);
        this.menu = menu;
    }

    /** Top-left corner of the GUI; slot positions are relative to it. */
    public void setGuiOrigin(int guiLeft, int guiTop) {
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
    }

    @Override
    protected void renderIcon(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(ICON, x, y, ICON_SIZE, ICON_SIZE);
    }

    /** The 18x18 frame around a slot, in screen coordinates. */
    private ScreenRectangle frame(UpgradeSlotType type) {
        Slot slot = menu.getUpgradeSlot(type);
        return new ScreenRectangle(guiLeft + slot.x - 1, guiTop + slot.y - 1, UpgradeLayout.SLOT_SIZE, UpgradeLayout.SLOT_SIZE);
    }

    @Nullable
    private UpgradeSlotType slotAt(double mouseX, double mouseY) {
        for (UpgradeSlotType type : UpgradeSlotType.values()) {
            ScreenRectangle frame = frame(type);
            if (isInside(mouseX, mouseY, frame.left(), frame.top(), frame.width(), frame.height())) {
                return type;
            }
        }
        return null;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        for (UpgradeSlotType type : UpgradeSlotType.values()) {
            ScreenRectangle frame = frame(type);
            UpgradeStatus status = menu.getUpgradeStatus(type);
            boolean installed = menu.getUpgradeSlot(type).hasItem();

            if (installed && status != UpgradeStatus.EMPTY) {
                // Coloured frame instead of the bevel: green = working, yellow = partly working, red = not working.
                SlotWidgets.drawColoredSlot(graphics, frame.left(), frame.top(),
                        status.isProblem() ? PROBLEM_FRAME : status.isWarning() ? WARNING_FRAME : ACTIVE_FRAME);
            } else {
                SlotWidgets.drawSlot(graphics, frame.left(), frame.top());
            }
            if (!installed) {
                // Faded icon of the upgrade that goes here.
                SlotWidgets.drawGhostItem(graphics, type.getIcon(), frame.left(), frame.top());
            }
        }
    }

    /** Empty slots explain what goes in; filled slots show the item's own tooltip (plus its status, see the screen). */
    @Nullable
    @Override
    protected Hover getContentTooltip(int mouseX, int mouseY) {
        UpgradeSlotType type = slotAt(mouseX, mouseY);
        if (type == null || menu.getUpgradeSlot(type).hasItem()) {
            return null;
        }
        return new Hover(List.of(type.getDisplayName(),
                type.getDescription(menu.getUpgradeSlot(type).getMaxStackSize()).copy().withStyle(ChatFormatting.GRAY)),
                frame(type));
    }

    /** Clicks on a slot go to the screen (normal slot handling); anything else in the tab is ignored. */
    @Override
    protected boolean contentClicked(double mouseX, double mouseY, int button) {
        return slotAt(mouseX, mouseY) == null;
    }
}
