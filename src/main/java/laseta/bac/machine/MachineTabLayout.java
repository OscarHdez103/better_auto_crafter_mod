package laseta.bac.machine;

import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.upgrade.UpgradeLayout;

/**
 * Where everything in a machine's own tab is, shared by the menu (its upgrade slots are real slots) and the screen (the
 * tab draws the rest). Coordinates are relative to the GUI's top-left corner, like all slot positions.
 *
 * <p>The tab sits right below the upgrades tab, which is collapsed whenever this one is open (only one tab is open at a
 * time), so its position when open is fixed. Like the upgrade slots, the content is right-aligned against the GUI so
 * it doesn't depend on the tab's (translated) title width. Top to bottom, each section centered in the content:</p>
 * <ol>
 *     <li>the options, one button each, {@link #MAX_OPTION_COLUMNS} per row;</li>
 *     <li>the face channels, an icon above a cube net of the machine's faces each, {@link #MAX_CHANNEL_COLUMNS} per row;</li>
 *     <li>the upgrade slots, each followed by the box for its upgrade's item setting if it is configurable.</li>
 * </ol>
 */
public final class MachineTabLayout {
    public static final int BUTTON_SIZE = 22;
    public static final int MAX_OPTION_COLUMNS = 2;
    public static final int FACE_SIZE = 14;
    public static final int MAX_CHANNEL_COLUMNS = 2;
    public static final int ICON_SIZE = 16;
    public static final int SLOT_SIZE = 18;

    private static final int BUTTON_GAP = 4;
    private static final int FACE_GAP = 1;
    private static final int NET_SIZE = FACE_SIZE * 3 + FACE_GAP * 2;
    private static final int CHANNEL_GAP = 10;
    private static final int ICON_GAP = 2;
    private static final int CHANNEL_HEIGHT = ICON_SIZE + ICON_GAP + NET_SIZE;
    private static final int SETTING_GAP = 2;
    private static final int UPGRADE_GAP = 8;
    private static final int SECTION_GAP = 8;
    /** Collapsed tab size and gap between tabs; must match the side tab widgets. */
    private static final int COLLAPSED_TAB = 24;
    private static final int TAB_GAP = 2;

    /** Top of this tab while it is open: right below the collapsed upgrades tab. */
    public static final int TAB_TOP = UpgradeLayout.TAB_TOP + COLLAPSED_TAB + TAB_GAP;
    public static final int CONTENT_TOP = TAB_TOP + UpgradeLayout.TAB_HEADER_HEIGHT;

    /** Grid position of each face in a cube net, as seen from the front: rows top to bottom, columns left to right. */
    public static final RelativeSide[][] NET = {
            {null, RelativeSide.TOP, null},
            {RelativeSide.LEFT, RelativeSide.FRONT, RelativeSide.RIGHT},
            {null, RelativeSide.BOTTOM, RelativeSide.BACK},
    };

    /** A rectangle relative to the GUI's top-left corner. */
    public record Box(int x, int y, int width, int height) {
    }

    private final MachineFeatures features;
    private final int contentWidth;
    private final int contentHeight;
    private final int optionsTop;
    private final int channelsTop;
    private final int upgradesTop;

    public MachineTabLayout(MachineFeatures features) {
        this.features = features;
        int width = UpgradeLayout.GRID_WIDTH;
        int top = 0;
        boolean first = true;

        optionsTop = top;
        if (!features.options().isEmpty()) {
            width = Math.max(width, optionsWidth());
            top += rows(features.options().size(), MAX_OPTION_COLUMNS) * (BUTTON_SIZE + BUTTON_GAP) - BUTTON_GAP;
            first = false;
        }
        if (!features.channels().isEmpty()) {
            top += first ? 0 : SECTION_GAP;
            width = Math.max(width, channelsWidth());
            first = false;
        }
        channelsTop = top;
        top += rows(features.channels().size(), MAX_CHANNEL_COLUMNS) * (CHANNEL_HEIGHT + SECTION_GAP)
                - (features.channels().isEmpty() ? 0 : SECTION_GAP);
        if (!features.upgradeSlots().isEmpty()) {
            top += first ? 0 : SECTION_GAP;
            width = Math.max(width, upgradesWidth());
        }
        upgradesTop = top;
        top += features.upgradeSlots().isEmpty() ? 0 : SLOT_SIZE;
        this.contentWidth = width;
        this.contentHeight = top;
    }

    private static int rows(int count, int columns) {
        return (count + columns - 1) / columns;
    }

    private int optionsWidth() {
        int columns = Math.min(MAX_OPTION_COLUMNS, features.options().size());
        return columns * (BUTTON_SIZE + BUTTON_GAP) - BUTTON_GAP;
    }

    private int channelsWidth() {
        int columns = Math.min(MAX_CHANNEL_COLUMNS, features.channels().size());
        return columns * (NET_SIZE + CHANNEL_GAP) - CHANNEL_GAP;
    }

    private int upgradeWidth(MachineUpgradeSlotType type) {
        return type.configurable() ? SLOT_SIZE * 2 + SETTING_GAP : SLOT_SIZE;
    }

    private int upgradesWidth() {
        int width = 0;
        for (MachineUpgradeSlotType type : features.upgradeSlots()) {
            width += upgradeWidth(type) + UPGRADE_GAP;
        }
        return width - UPGRADE_GAP;
    }

    public int contentWidth() {
        return contentWidth;
    }

    public int contentHeight() {
        return contentHeight;
    }

    private int contentLeft() {
        return -UpgradeLayout.TAB_PADDING - contentWidth;
    }

    /** Left edge of a section of the given width, centered in the content. */
    private int sectionLeft(int width) {
        return contentLeft() + (contentWidth - width) / 2;
    }

    public Box optionButton(int index) {
        int left = sectionLeft(optionsWidth()) + index % MAX_OPTION_COLUMNS * (BUTTON_SIZE + BUTTON_GAP);
        int top = CONTENT_TOP + optionsTop + index / MAX_OPTION_COLUMNS * (BUTTON_SIZE + BUTTON_GAP);
        return new Box(left, top, BUTTON_SIZE, BUTTON_SIZE);
    }

    private int channelLeft(int index) {
        int column = index % MAX_CHANNEL_COLUMNS;
        int inRow = Math.min(MAX_CHANNEL_COLUMNS, features.channels().size() - index / MAX_CHANNEL_COLUMNS * MAX_CHANNEL_COLUMNS);
        return sectionLeft(inRow * (NET_SIZE + CHANNEL_GAP) - CHANNEL_GAP) + column * (NET_SIZE + CHANNEL_GAP);
    }

    private int channelTop(int index) {
        return CONTENT_TOP + channelsTop + index / MAX_CHANNEL_COLUMNS * (CHANNEL_HEIGHT + SECTION_GAP);
    }

    /** The channel's icon, centered above its net. */
    public Box channelIcon(int index) {
        return new Box(channelLeft(index) + (NET_SIZE - ICON_SIZE) / 2, channelTop(index), ICON_SIZE, ICON_SIZE);
    }

    /** A face's button in the channel's net. */
    public Box channelFace(int index, RelativeSide side) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (NET[row][column] == side) {
                    return new Box(channelLeft(index) + column * (FACE_SIZE + FACE_GAP),
                            channelTop(index) + ICON_SIZE + ICON_GAP + row * (FACE_SIZE + FACE_GAP), FACE_SIZE, FACE_SIZE);
                }
            }
        }
        throw new IllegalArgumentException("Side not in the net: " + side);
    }

    /** The 18x18 frame of an upgrade slot (its item is 1 pixel inside). */
    public Box upgradeSlot(int index) {
        int left = sectionLeft(upgradesWidth());
        for (int i = 0; i < index; i++) {
            left += upgradeWidth(features.upgradeSlots().get(i)) + UPGRADE_GAP;
        }
        return new Box(left, CONTENT_TOP + upgradesTop, SLOT_SIZE, SLOT_SIZE);
    }

    /** The 18x18 box of the item setting of a configurable upgrade slot's upgrade, right of the slot. */
    public Box upgradeSetting(int index) {
        Box slot = upgradeSlot(index);
        return new Box(slot.x() + SLOT_SIZE + SETTING_GAP, slot.y(), SLOT_SIZE, SLOT_SIZE);
    }
}
