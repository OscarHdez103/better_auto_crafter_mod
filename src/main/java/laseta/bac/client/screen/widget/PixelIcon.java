package laseta.bac.client.screen.widget;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A small icon drawn from a text pixel pattern with fills, so it needs no texture file.
 * Every character is mapped to an ARGB colour by a {@link Palette} (0 = transparent); the pixel's position is passed
 * along so patterns can be shaded.
 */
public final class PixelIcon {
    @FunctionalInterface
    public interface Palette {
        int color(char symbol, int x, int y);
    }

    /** Horizontal runs of same-coloured pixels: {x, y, length, argb}. Merged so a frame needs few fill calls. */
    private final List<int[]> runs = new ArrayList<>();

    public PixelIcon(String[] pattern, Palette palette) {
        for (int y = 0; y < pattern.length; y++) {
            String line = pattern[y];
            int x = 0;
            while (x < line.length()) {
                int color = palette.color(line.charAt(x), x, y);
                int start = x;
                while (x < line.length() && palette.color(line.charAt(x), x, y) == color) {
                    x++;
                }
                if (color != 0) {
                    runs.add(new int[]{start, y, x - start, color});
                }
            }
        }
    }

    public void render(GuiGraphics graphics, int x, int y) {
        for (int[] run : runs) {
            graphics.fill(x + run[0], y + run[1], x + run[0] + run[2], y + run[1] + 1, run[3]);
        }
    }
}
