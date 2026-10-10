package laseta.bac.client.screen.widget;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 16x16 cog icon drawn from a pixel pattern, so it needs no texture file.
 * '#' outline, 'o' light, '-' mid, 'x' dark (shading lit from the top left), '.' transparent.
 */
public final class CogIcon {
    public static final int SIZE = 16;

    private static final String[] PATTERN = {
            "......####......",
            "..##..#oo#..##..",
            ".#oo##oooo##--#.",
            ".#ooooooooo---#.",
            "..#oooo##o---#..",
            "..#ooo#..#---#..",
            "##ooo#....#---##",
            "#ooo-#....#-xxx#",
            "#oo--#....#xxxx#",
            "##---#....#xxx##",
            "..#---#..#xxx#..",
            "..#----##xxxx#..",
            ".#----xxxxxxxx#.",
            ".#--##xxxx##xx#.",
            "..##..#xx#..##..",
            "......####......",
    };

    /** Horizontal runs of same-coloured pixels: {x, y, length, argb}. Merged so a frame needs few fill calls. */
    private static final List<int[]> RUNS = buildRuns();

    private CogIcon() {
    }

    public static void render(GuiGraphics graphics, int x, int y) {
        for (int[] run : RUNS) {
            graphics.fill(x + run[0], y + run[1], x + run[0] + run[2], y + run[1] + 1, run[3]);
        }
    }

    private static List<int[]> buildRuns() {
        List<int[]> runs = new ArrayList<>();
        for (int row = 0; row < PATTERN.length; row++) {
            String line = PATTERN[row];
            int col = 0;
            while (col < line.length()) {
                char c = line.charAt(col);
                int start = col;
                while (col < line.length() && line.charAt(col) == c) {
                    col++;
                }
                int color = colorOf(c);
                if (color != 0) {
                    runs.add(new int[]{start, row, col - start, color});
                }
            }
        }
        return runs;
    }

    private static int colorOf(char c) {
        return switch (c) {
            case '#' -> 0xFF2E2E2E;
            case 'o' -> 0xFFEDEDED;
            case '-' -> 0xFFBDBDBD;
            case 'x' -> 0xFF8C8C8C;
            default -> 0;
        };
    }
}
