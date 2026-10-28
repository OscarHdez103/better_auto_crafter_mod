package laseta.bac.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The brewing stand's blaze fuel bar in a recessed frame, slowly running out: what the recipe viewers' brewer fuel
 * pages show where a furnace fuel page shows its flame.
 */
public final class BrewerFuelBar {
    public static final int WIDTH = 20;
    public static final int HEIGHT = 6;

    private static final ResourceLocation SPRITE = ResourceLocation.withDefaultNamespace("container/brewing_stand/fuel_length");
    private static final int SPRITE_WIDTH = 18;
    private static final int SPRITE_HEIGHT = 4;
    private static final long CYCLE_MILLIS = 4000;
    // Vanilla slot colours
    private static final int FRAME_DARK = 0xFF373737;
    private static final int FRAME_LIGHT = 0xFFFFFFFF;
    private static final int TRACK = 0xFF3B3B3B;

    private BrewerFuelBar() {
    }

    /** Draws the bar with its frame at the given corner. */
    public static void render(GuiGraphics graphics, int x, int y) {
        int right = x + WIDTH;
        int bottom = y + HEIGHT;
        graphics.fill(x, y, right - 1, y + 1, FRAME_DARK);
        graphics.fill(x, y + 1, x + 1, bottom - 1, FRAME_DARK);
        graphics.fill(x + 1, bottom - 1, right, bottom, FRAME_LIGHT);
        graphics.fill(right - 1, y + 1, right, bottom - 1, FRAME_LIGHT);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, TRACK);
        // Full to empty, from the right like the brewing stand's.
        float left = 1 - (float) (System.currentTimeMillis() % CYCLE_MILLIS) / CYCLE_MILLIS;
        int length = Math.round(left * SPRITE_WIDTH);
        if (length > 0) {
            graphics.blitSprite(SPRITE, SPRITE_WIDTH, SPRITE_HEIGHT, 0, 0, x + 1, y + 1, length, SPRITE_HEIGHT);
        }
    }
}
