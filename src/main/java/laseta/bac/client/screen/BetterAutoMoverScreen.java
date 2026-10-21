package laseta.bac.client.screen;

import java.util.List;

import laseta.bac.block.entity.BetterAutoMoverBlockEntity;
import laseta.bac.client.screen.widget.PixelIcon;
import laseta.bac.machine.MachineOption;
import laseta.bac.machine.MachineLayout;
import laseta.bac.menu.BetterAutoMoverMenu;
import laseta.bac.sideconfig.SideMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * The mover's screen: the machine's default panel, with an arrow on each side of the grid showing which way the items
 * go, in the side configuration's input and output colours, and the grid mode's icons for the machine tab.
 */
public class BetterAutoMoverScreen extends AutoMachineScreen<BetterAutoMoverMenu> {
    private static final int ARROW_SIZE = 16;
    private static final PixelIcon INPUT_ARROW = arrow(SideMode.INPUT.getColor());
    private static final PixelIcon OUTPUT_ARROW = arrow(SideMode.OUTPUT.getColor());
    private static final Component INPUT_TOOLTIP = Component.translatable("gui.better_auto_crafter.better_auto_mover.input_arrow");
    private static final Component OUTPUT_TOOLTIP = Component.translatable("gui.better_auto_crafter.better_auto_mover.output_arrow");

    /** All together: the grid's cells joined into one block, which moves as a whole. */
    private static final PixelIcon ALL_TOGETHER_ICON = new PixelIcon(new String[]{
            "################",
            "#oooo=oooo=oooo#",
            "#oooo=oooo=oooo#",
            "#oooo=oooo=oooo#",
            "#oooo=oooo=oooo#",
            "#==============#",
            "#oooo=oooo=oooo#",
            "#oooo=oooo=oooo#",
            "#oooo=oooo=oooo#",
            "#oooo=oooo=oooo#",
            "#==============#",
            "#oooo=oooo=oooo#",
            "#oooo=oooo=oooo#",
            "#oooo=oooo=oooo#",
            "#oooo=oooo=oooo#",
            "################",
    }, BetterAutoMoverScreen::gridIconColor);

    /** Each slot: separate cells, some full and some empty, each moving on its own. */
    private static final PixelIcon EACH_SLOT_ICON = new PixelIcon(new String[]{
            "####..####..####",
            "#oo#..#xx#..#oo#",
            "#oo#..#xx#..#oo#",
            "####..####..####",
            "................",
            "................",
            "####..####..####",
            "#xx#..#oo#..#oo#",
            "#xx#..#oo#..#oo#",
            "####..####..####",
            "................",
            "................",
            "####..####..####",
            "#oo#..#oo#..#xx#",
            "#oo#..#oo#..#xx#",
            "####..####..####",
    }, BetterAutoMoverScreen::gridIconColor);

    public BetterAutoMoverScreen(BetterAutoMoverMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /** Right-pointing arrow: '#' dark outline, 'o' the colour, lighter on its upper half. */
    private static PixelIcon arrow(int rgb) {
        int light = 0xFF000000 | blend(rgb, 0xFFFFFF, 0.35F);
        int base = 0xFF000000 | rgb;
        int outline = 0xFF000000 | blend(rgb, 0x000000, 0.65F);
        return new PixelIcon(new String[]{
                "................",
                "........#.......",
                "........##......",
                "........#o#.....",
                "........#oo#....",
                "#########ooo#...",
                "#oooooooooooo#..",
                "#ooooooooooooo#.",
                "#ooooooooooooo#.",
                "#oooooooooooo#..",
                "#########ooo#...",
                "........#oo#....",
                "........#o#.....",
                "........##......",
                "........#.......",
                "................",
        }, (symbol, x, y) -> switch (symbol) {
            case '#' -> outline;
            case 'o' -> y < 8 ? light : base;
            default -> 0;
        });
    }

    private static int blend(int rgb, int target, float amount) {
        int r = (int) (((rgb >> 16) & 0xFF) + ((((target >> 16) & 0xFF) - ((rgb >> 16) & 0xFF)) * amount));
        int g = (int) (((rgb >> 8) & 0xFF) + ((((target >> 8) & 0xFF) - ((rgb >> 8) & 0xFF)) * amount));
        int b = (int) ((rgb & 0xFF) + (((target & 0xFF) - (rgb & 0xFF)) * amount));
        return (r << 16) | (g << 8) | b;
    }

    @Override
    protected void renderOptionIcon(GuiGraphics graphics, MachineOption<?> option, Enum<?> value, int x, int y) {
        if (option == BetterAutoMoverBlockEntity.GRID_MODE) {
            (value == BetterAutoMoverBlockEntity.GridMode.EACH_SLOT ? EACH_SLOT_ICON : ALL_TOGETHER_ICON).render(graphics, x, y);
        }
    }

    /** Colours of the grid mode icons: '#' outline, 'o' a filled cell (lighter at the top), '=' seams, 'x' an empty cell. */
    private static int gridIconColor(char symbol, int x, int y) {
        return switch (symbol) {
            case '#' -> 0xFF3B2A06;
            case 'o' -> y < 8 ? 0xFFFFDE7A : 0xFFFFC13A;
            case '=' -> 0xFFC98A12;
            case 'x' -> 0xFF5A4630;
            default -> 0;
        };
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
        INPUT_ARROW.render(graphics, inputArrowX(), arrowY());
        OUTPUT_ARROW.render(graphics, outputArrowX(), arrowY());
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
