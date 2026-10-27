package laseta.bac.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Slots drawn by the side tabs (the menu's slots in them don't draw a background of their own). */
final class SlotWidgets {
    public static final int SIZE = 18;
    // Vanilla slot colours
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int GHOST_FADE = 0xB08B8B8B;
    /** Above items rendered in the GUI, so the fade drawn over a ghost item isn't hidden behind it. */
    private static final float OVERLAY_Z = 200.0F;

    private SlotWidgets() {
    }

    /** A vanilla slot: recessed frame and fill, at the frame's top-left corner. */
    public static void drawSlot(GuiGraphics graphics, int left, int top) {
        int right = left + SIZE;
        int bottom = top + SIZE;
        graphics.fill(left, top, right - 1, top + 1, SLOT_DARK);
        graphics.fill(left, top + 1, left + 1, bottom - 1, SLOT_DARK);
        graphics.fill(left + 1, bottom - 1, right, bottom, SLOT_LIGHT);
        graphics.fill(right - 1, top + 1, right, bottom - 1, SLOT_LIGHT);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, SLOT_FILL);
    }

    /** A slot with a coloured frame instead of the bevel (e.g. an upgrade's status). */
    public static void drawColoredSlot(GuiGraphics graphics, int left, int top, int frameColor) {
        graphics.fill(left, top, left + SIZE, top + SIZE, frameColor);
        graphics.fill(left + 1, top + 1, left + SIZE - 1, top + SIZE - 1, SLOT_FILL);
    }

    /** {@code stack} faded into the slot whose frame is at the given corner, to show what goes there. */
    public static void drawGhostItem(GuiGraphics graphics, ItemStack stack, int left, int top) {
        graphics.renderFakeItem(stack, left + 1, top + 1);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, OVERLAY_Z);
        graphics.fill(left + 1, top + 1, left + SIZE - 1, top + SIZE - 1, GHOST_FADE);
        graphics.pose().popPose();
    }
}
