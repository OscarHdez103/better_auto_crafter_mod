package laseta.bac.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 16x16 cog icon of the side configuration tab.
 * '#' outline, 'o' light, '-' mid, 'x' dark (shading lit from the top left), '.' transparent.
 */
public final class CogIcon {
    public static final int SIZE = 16;

    private static final PixelIcon ICON = new PixelIcon(new String[]{
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
    }, (symbol, x, y) -> switch (symbol) {
        case '#' -> 0xFF2E2E2E;
        case 'o' -> 0xFFEDEDED;
        case '-' -> 0xFFBDBDBD;
        case 'x' -> 0xFF8C8C8C;
        default -> 0;
    });

    private CogIcon() {
    }

    public static void render(GuiGraphics graphics, int x, int y) {
        ICON.render(graphics, x, y);
    }
}
