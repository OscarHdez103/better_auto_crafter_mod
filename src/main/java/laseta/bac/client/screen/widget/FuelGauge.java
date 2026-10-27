package laseta.bac.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * A machine's fuel, as a vertical bar in a recessed slot-like frame that empties from the top (see
 * {@link laseta.bac.machine.FuelTank}). Each machine picks colours that fit its fuel.
 *
 * @param fill      colour of the fuel
 * @param highlight lighter colour of the fuel's left column and top row, so it reads as a solid bar
 * @param shade     darker colour of the fuel's right column
 */
public record FuelGauge(int fill, int highlight, int shade) {
    // Vanilla slot colours
    private static final int FRAME_DARK = 0xFF373737;
    private static final int FRAME_LIGHT = 0xFFFFFFFF;
    private static final int TRACK = 0xFF8B8B8B;

    /** Blaze powder: orange to yellow. */
    public static final FuelGauge BLAZE = new FuelGauge(0xFFF39A1E, 0xFFFFD84A, 0xFFC4610F);
    /** Coal: black, for any furnace fuel. */
    public static final FuelGauge COAL = new FuelGauge(0xFF262626, 0xFF4A4A4A, 0xFF121212);

    /** Draws the gauge with its frame at the given corner; {@code fraction} is how full it is, 0 to 1. */
    public void render(GuiGraphics graphics, int x, int y, int width, int height, float fraction) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right - 1, y + 1, FRAME_DARK);
        graphics.fill(x, y + 1, x + 1, bottom - 1, FRAME_DARK);
        graphics.fill(x + 1, bottom - 1, right, bottom, FRAME_LIGHT);
        graphics.fill(right - 1, y + 1, right, bottom - 1, FRAME_LIGHT);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, TRACK);

        int inner = height - 2;
        // Any fuel at all shows at least a pixel.
        int level = fraction <= 0 ? 0 : Math.max(1, Mth.ceil(Mth.clamp(fraction, 0.0F, 1.0F) * inner));
        if (level == 0) {
            return;
        }
        int top = bottom - 1 - level;
        graphics.fill(x + 1, top, right - 1, bottom - 1, fill);
        graphics.fill(x + 1, top, x + 2, bottom - 1, highlight);
        graphics.fill(x + 1, top, right - 1, top + 1, highlight);
        graphics.fill(right - 2, top + 1, right - 1, bottom - 1, shade);
    }

    public static boolean isMouseOver(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
