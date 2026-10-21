package laseta.bac.client.screen.widget;

import java.util.List;

import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.machine.MachineOption;
import laseta.bac.upgrade.UpgradeLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Purple tab on the left of the GUI, below the upgrades, for what only this kind of machine has: its
 * {@link MachineOption}s. Only machines that have options get it, and it looks the same on all of them.
 *
 * <p>Like the settings tab, each option is one icon button showing its current value; the tooltip names the option
 * and explains the value. Left-click selects the next value, right-click the previous one.</p>
 *
 * <p>Texts, from {@link MachineText} (so worded per machine): "option.&lt;option&gt;" for the option's name,
 * "option.&lt;option&gt;.&lt;value&gt;" for a value's name and "...&lt;value&gt;.desc" for its explanation; the tab itself
 * uses "machine_tab" and "machine_tab.description".</p>
 */
public class MachineTab extends SideTab {
    private static final Palette PALETTE = new Palette(0xFF1C0F2B, 0xFF6B4499, 0xFF9A78C6, 0xFF48296E);

    private static final int BUTTON_SIZE = 22;
    private static final int BUTTON_GAP = 4;
    private static final int MAX_COLUMNS = 2;
    private static final int ICON_INSET = (BUTTON_SIZE - 16) / 2;

    private static final int BUTTON_OUTLINE = 0xFF1C0F2B;
    private static final int BUTTON_FILL = 0xFF4E2F75;
    private static final int BUTTON_FILL_HOVER = 0xFF5E3A8A;
    private static final int BUTTON_HIGHLIGHT = 0xFF66449A;
    private static final int BUTTON_SHADOW = 0xFF3A2058;

    /** Draws the 16x16 icon of one value of one of the machine's options. */
    @FunctionalInterface
    public interface ValueIcons {
        void render(GuiGraphics graphics, MachineOption<?> option, Enum<?> value, int x, int y);
    }

    private final AutoMachineMenu menu;
    private final MachineText text;
    private final ValueIcons icons;
    private final ItemStack machine;

    public MachineTab(AutoMachineMenu menu, MachineText text, Font font, ValueIcons icons) {
        // At least as wide as the upgrades tab above it, so the tab column keeps one width.
        super(text.get("machine_tab"), text.get("machine_tab.description"), PALETTE, font,
                Math.max(UpgradeLayout.GRID_WIDTH, gridWidth(menu)), gridHeight(menu));
        this.menu = menu;
        this.text = text;
        this.icons = icons;
        this.machine = new ItemStack(menu.getMachineBlock());
    }

    private static int columns(AutoMachineMenu menu) {
        return Math.min(MAX_COLUMNS, menu.getOptionTypes().size());
    }

    private static int rows(AutoMachineMenu menu) {
        return (menu.getOptionTypes().size() + MAX_COLUMNS - 1) / MAX_COLUMNS;
    }

    private static int gridWidth(AutoMachineMenu menu) {
        return BUTTON_SIZE * columns(menu) + BUTTON_GAP * (columns(menu) - 1);
    }

    private static int gridHeight(AutoMachineMenu menu) {
        return BUTTON_SIZE * rows(menu) + BUTTON_GAP * (rows(menu) - 1);
    }

    private static String serializedName(Enum<?> value) {
        return ((StringRepresentable) value).getSerializedName();
    }

    @Override
    protected void renderIcon(GuiGraphics graphics, int x, int y) {
        graphics.renderFakeItem(machine, x, y);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------------------------

    private ScreenRectangle buttonRect(int optionIndex) {
        int column = optionIndex % MAX_COLUMNS;
        int row = optionIndex / MAX_COLUMNS;
        int left = x + (expandedWidth() - gridWidth(menu)) / 2 + column * (BUTTON_SIZE + BUTTON_GAP);
        int top = contentTop() + row * (BUTTON_SIZE + BUTTON_GAP);
        return new ScreenRectangle(left, top, BUTTON_SIZE, BUTTON_SIZE);
    }

    /** Index of the option whose button is under the mouse, or -1. */
    private int optionAt(double mouseX, double mouseY) {
        for (int i = 0; i < menu.getOptionTypes().size(); i++) {
            ScreenRectangle rect = buttonRect(i);
            if (isInside(mouseX, mouseY, rect.left(), rect.top(), rect.width(), rect.height())) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Enum<?> selected(MachineOption<?> option) {
        return menu.getOption((MachineOption) option);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int hovered = optionAt(mouseX, mouseY);
        List<MachineOption<?>> options = menu.getOptionTypes();
        for (int i = 0; i < options.size(); i++) {
            ScreenRectangle rect = buttonRect(i);
            drawButtonBackground(graphics, rect, i == hovered);
            MachineOption<?> option = options.get(i);
            icons.render(graphics, option, selected(option), rect.left() + ICON_INSET, rect.top() + ICON_INSET);
        }
    }

    private static void drawButtonBackground(GuiGraphics graphics, ScreenRectangle rect, boolean hovered) {
        int left = rect.left();
        int top = rect.top();
        int right = rect.right();
        int bottom = rect.bottom();
        graphics.fill(left, top, right, bottom, BUTTON_OUTLINE);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, hovered ? BUTTON_FILL_HOVER : BUTTON_FILL);
        graphics.fill(left + 1, top + 1, right - 1, top + 2, BUTTON_HIGHLIGHT);
        graphics.fill(left + 1, top + 2, left + 2, bottom - 1, BUTTON_HIGHLIGHT);
        graphics.fill(left + 2, bottom - 2, right - 1, bottom - 1, BUTTON_SHADOW);
        graphics.fill(right - 2, top + 2, right - 1, bottom - 2, BUTTON_SHADOW);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tooltips and input
    // ---------------------------------------------------------------------------------------------------------------

    @Nullable
    @Override
    protected Hover getContentTooltip(int mouseX, int mouseY) {
        int index = optionAt(mouseX, mouseY);
        if (index < 0) {
            return null;
        }
        MachineOption<?> option = menu.getOptionTypes().get(index);
        String key = "option." + option.name() + "." + serializedName(selected(option));
        Component title = Component.translatable("gui.better_auto_crafter.setting.with_value",
                text.get("option." + option.name()), text.get(key).withStyle(ChatFormatting.LIGHT_PURPLE));
        Component hint = Component.translatable(option.values().size() == 2
                ? "gui.better_auto_crafter.setting.hint.toggle"
                : "gui.better_auto_crafter.setting.hint.cycle");
        return new Hover(List.of(title,
                text.get(key + ".desc").withStyle(ChatFormatting.GRAY),
                hint.copy().withStyle(ChatFormatting.DARK_GRAY)), buttonRect(index));
    }

    @Override
    protected boolean contentClicked(double mouseX, double mouseY, int button) {
        int index = optionAt(mouseX, mouseY);
        if (index < 0 || (button != 0 && button != 1)) {
            return true;
        }
        MachineOption<?> option = menu.getOptionTypes().get(index);
        List<? extends Enum<?>> values = option.values();
        int step = button == 1 ? values.size() - 1 : 1;
        Enum<?> next = values.get((values.indexOf(selected(option)) + step) % values.size());
        sendMenuButton(menu, AutoMachineMenu.selectOptionButton(index, next));
        return true;
    }
}
