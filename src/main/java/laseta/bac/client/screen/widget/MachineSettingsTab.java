package laseta.bac.client.screen.widget;

import java.util.List;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.item.MultiTranslocationUpgradeItem;
import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.machine.RedstoneMode;
import laseta.bac.machine.TransferPriority;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.upgrade.UpgradeSlotType;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Machine settings as a compact 2x2 grid of icon buttons (Match NBT, Redstone, Input First, Output First).
 * Each icon shows the current value; the tooltip names the setting and explains the value.
 * Left-click selects the next value, right-click the previous one.
 */
public class MachineSettingsTab extends SideTab {
    private static final Palette PALETTE = new Palette(0xFF2A0E0C, 0xFF9A3B33, 0xFFC9675E, 0xFF6B241F);

    private static final int BUTTON_SIZE = 22;
    private static final int BUTTON_GAP = 4;
    private static final int COLUMNS = 2;
    private static final int GRID_WIDTH = BUTTON_SIZE * COLUMNS + BUTTON_GAP * (COLUMNS - 1);
    private static final int ICON_INSET = (BUTTON_SIZE - 16) / 2;

    private static final int BUTTON_OUTLINE = 0xFF2A0E0C;
    private static final int BUTTON_FILL = 0xFF6E2721;
    private static final int BUTTON_FILL_HOVER = 0xFF8A352E;
    private static final int BUTTON_HIGHLIGHT = 0xFFA14F47;
    private static final int BUTTON_SHADOW = 0xFF4E1A16;
    private static final int ON_COLOR = 0x8CF08C;
    private static final int OFF_COLOR = 0xFF9C94;
    private static final int NET_CELL = 0xFF2B1210;
    /** Red cross drawn over the name tag while NBT matching is off. */
    private static final ResourceLocation CROSSED_OUT = ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "icon/crossed_out");
    private static final long ROUND_ROBIN_STEP_MS = 400;
    /** Above items rendered in the GUI, so overlays drawn on top of an item icon aren't hidden behind it. */
    private static final float OVERLAY_Z = 200.0F;

    private enum Setting {
        MATCH_NBT("match_nbt"),
        REDSTONE("redstone_mode"),
        INPUT_PRIORITY("input_priority"),
        OUTPUT_PRIORITY("output_priority");

        private final String key;

        Setting(String key) {
            this.key = key;
        }

        Component label() {
            return Component.translatable("gui.better_auto_crafter.setting." + key);
        }
    }

    /** Positions of the faces in the mini cube net icon: the same layout as the side configuration tab. */
    private static final RelativeSide[][] NET_LAYOUT = {
            {null, RelativeSide.TOP, null},
            {RelativeSide.LEFT, RelativeSide.FRONT, RelativeSide.RIGHT},
            {null, RelativeSide.BOTTOM, RelativeSide.BACK},
    };

    private final AutoMachineMenu menu;
    private final MachineText text;
    private final ItemStack nameTag = new ItemStack(Items.NAME_TAG);
    private final ItemStack barrier = new ItemStack(Items.BARRIER);
    private final ItemStack repeater = new ItemStack(Items.REPEATER);
    private final ItemStack redstone = new ItemStack(Items.REDSTONE);
    private final ItemStack redstoneTorch = new ItemStack(Items.REDSTONE_TORCH);
    private final ItemStack comparator = new ItemStack(Items.COMPARATOR);

    public MachineSettingsTab(AutoMachineMenu menu, MachineText text, Font font) {
        // Same content width as the side configuration tab, so the tab column never gets wider (EMI hides its
        // whole panel when the free space beside the GUI gets too narrow).
        super(text.get("settings"), text.get("settings.description"), PALETTE, font, SideConfigTab.CONTENT_WIDTH,
                BUTTON_SIZE * rows() + BUTTON_GAP * (rows() - 1));
        this.menu = menu;
        this.text = text;
    }

    private static int rows() {
        return (Setting.values().length + COLUMNS - 1) / COLUMNS;
    }

    @Override
    protected void renderIcon(GuiGraphics graphics, int x, int y) {
        graphics.renderFakeItem(comparator, x, y);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------------------------

    private ScreenRectangle buttonRect(Setting setting) {
        int column = setting.ordinal() % COLUMNS;
        int row = setting.ordinal() / COLUMNS;
        int left = x + (expandedWidth() - GRID_WIDTH) / 2 + column * (BUTTON_SIZE + BUTTON_GAP);
        int top = contentTop() + row * (BUTTON_SIZE + BUTTON_GAP);
        return new ScreenRectangle(left, top, BUTTON_SIZE, BUTTON_SIZE);
    }

    @Nullable
    private Setting settingAt(double mouseX, double mouseY) {
        for (Setting setting : Setting.values()) {
            ScreenRectangle rect = buttonRect(setting);
            if (isInside(mouseX, mouseY, rect.left(), rect.top(), rect.width(), rect.height())) {
                return setting;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        Setting hovered = settingAt(mouseX, mouseY);
        for (Setting setting : Setting.values()) {
            ScreenRectangle rect = buttonRect(setting);
            drawButtonBackground(graphics, rect, setting == hovered);
            int iconX = rect.left() + ICON_INSET;
            int iconY = rect.top() + ICON_INSET;
            switch (setting) {
                case MATCH_NBT -> drawMatchNbtIcon(graphics, iconX, iconY);
                case REDSTONE -> graphics.renderFakeItem(redstoneIcon(menu.getRedstoneMode()), iconX, iconY);
                case INPUT_PRIORITY -> drawPriorityIcon(graphics, iconX, iconY, menu.getInputPriority(), SideMode.INPUT);
                case OUTPUT_PRIORITY -> drawPriorityIcon(graphics, iconX, iconY, menu.getOutputPriority(), SideMode.OUTPUT);
            }
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

    /** Name tag ("item data"); crossed out in red when NBT matching is off. */
    private void drawMatchNbtIcon(GuiGraphics graphics, int x, int y) {
        graphics.renderFakeItem(nameTag, x, y);
        if (menu.isMatchNbt()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, OVERLAY_Z);
        graphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, 0x60000000);
        graphics.blitSprite(CROSSED_OUT, x, y, ICON_SIZE, ICON_SIZE);
        graphics.pose().popPose();
    }

    private ItemStack redstoneIcon(RedstoneMode mode) {
        return switch (mode) {
            case OFF -> barrier;
            case ON_POWERED -> repeater;
            case CONSTANT -> redstone;
            case INVERTED -> redstoneTorch;
        };
    }

    /**
     * Mini version of the side configuration layout (a cube net). The preferred face is lit in the input / output
     * colour; for round robin every face is tinted and the lit face moves around.
     */
    private static void drawPriorityIcon(GuiGraphics graphics, int x, int y, TransferPriority priority, SideMode mode) {
        int color = mode.getColor();
        RelativeSide lit = priority.getPreferredSide();
        boolean roundRobin = lit == null;
        if (roundRobin) {
            RelativeSide[] sides = RelativeSide.values();
            lit = sides[(int) ((Util.getMillis() / ROUND_ROBIN_STEP_MS) % sides.length)];
        }
        int cell = 4;
        int step = cell + 1;
        int offset = (16 - (step * 3 - 1)) / 2;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                RelativeSide side = NET_LAYOUT[row][column];
                if (side == null) {
                    continue;
                }
                int cellX = x + offset + column * step;
                int cellY = y + offset + row * step;
                int fill;
                if (side == lit) {
                    fill = 0xFF000000 | color;
                } else {
                    fill = roundRobin ? 0x80000000 | color : NET_CELL;
                }
                graphics.fill(cellX, cellY, cellX + cell, cellY + cell, fill);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tooltips and input
    // ---------------------------------------------------------------------------------------------------------------

    private Component value(Setting setting) {
        return switch (setting) {
            case MATCH_NBT -> Component.translatable(menu.isMatchNbt() ? "options.on" : "options.off");
            case REDSTONE -> menu.getRedstoneMode().getDisplayName();
            case INPUT_PRIORITY -> menu.getInputPriority().getDisplayName();
            case OUTPUT_PRIORITY -> menu.getOutputPriority().getDisplayName();
        };
    }

    private int valueColor(Setting setting) {
        return switch (setting) {
            case MATCH_NBT -> menu.isMatchNbt() ? ON_COLOR : OFF_COLOR;
            case REDSTONE -> menu.getRedstoneMode() == RedstoneMode.OFF ? OFF_COLOR : 0xFFFFFF;
            case INPUT_PRIORITY -> SideMode.INPUT.getColor();
            case OUTPUT_PRIORITY -> SideMode.OUTPUT.getColor();
        };
    }

    private Component explanation(Setting setting) {
        return switch (setting) {
            case MATCH_NBT -> text.get("setting.match_nbt." + (menu.isMatchNbt() ? "on" : "off"));
            case REDSTONE -> text.get("redstone_mode." + menu.getRedstoneMode().getSerializedName() + ".desc");
            case INPUT_PRIORITY -> priorityExplanation(menu.getInputPriority(), "input", UpgradeSlotType.TRANSLOCATION_PULL);
            case OUTPUT_PRIORITY -> priorityExplanation(menu.getOutputPriority(), "output", UpgradeSlotType.TRANSLOCATION_PUSH);
        };
    }

    /** With a multi translocation upgrade there are no faces: round robin goes through the linked blocks, otherwise their link order is used. */
    private Component priorityExplanation(TransferPriority priority, String direction, UpgradeSlotType upgradeSlot) {
        if (menu.getUpgradeSlot(upgradeSlot).getItem().getItem() instanceof MultiTranslocationUpgradeItem) {
            return text.get("priority." + direction + ".linked." + (priority.getPreferredSide() == null ? "round_robin" : "in_order"));
        }
        return priority.getPreferredSide() == null
                ? text.get("priority." + direction + ".round_robin")
                : text.get("priority." + direction + ".face", priority.getDisplayName());
    }

    @Nullable
    @Override
    protected Hover getContentTooltip(int mouseX, int mouseY) {
        Setting setting = settingAt(mouseX, mouseY);
        if (setting == null) {
            return null;
        }
        Component title = Component.translatable("gui.better_auto_crafter.setting.with_value", setting.label(),
                value(setting).copy().withStyle(style -> style.withColor(valueColor(setting))));
        Component hint = Component.translatable(setting == Setting.MATCH_NBT
                ? "gui.better_auto_crafter.setting.hint.toggle"
                : "gui.better_auto_crafter.setting.hint.cycle");
        return new Hover(List.of(title,
                explanation(setting).copy().withStyle(ChatFormatting.GRAY),
                hint.copy().withStyle(ChatFormatting.DARK_GRAY)), buttonRect(setting));
    }

    @Override
    protected boolean contentClicked(double mouseX, double mouseY, int button) {
        Setting setting = settingAt(mouseX, mouseY);
        if (setting == null || (button != 0 && button != 1)) {
            return true;
        }
        boolean previous = button == 1;
        int buttonId = switch (setting) {
            case MATCH_NBT -> AutoMachineMenu.BUTTON_TOGGLE_MATCH_NBT;
            case REDSTONE -> previous
                    ? AutoMachineMenu.BUTTON_REDSTONE_MODE_PREVIOUS
                    : AutoMachineMenu.BUTTON_REDSTONE_MODE_NEXT;
            case INPUT_PRIORITY -> previous
                    ? AutoMachineMenu.BUTTON_INPUT_PRIORITY_PREVIOUS
                    : AutoMachineMenu.BUTTON_INPUT_PRIORITY_NEXT;
            case OUTPUT_PRIORITY -> previous
                    ? AutoMachineMenu.BUTTON_OUTPUT_PRIORITY_PREVIOUS
                    : AutoMachineMenu.BUTTON_OUTPUT_PRIORITY_NEXT;
        };
        sendMenuButton(menu, buttonId);
        return true;
    }
}
