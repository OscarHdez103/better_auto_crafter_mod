package laseta.bac.upgrade;

import java.util.Arrays;

/**
 * Position of the upgrade slots, shared by the menu (slot positions) and the screen (the upgrades tab).
 * The tab hangs off the left edge of the GUI; each slot sits at its {@link UpgradeSlotType}'s column and row, in a grid
 * right-aligned against the GUI so its position doesn't depend on the tab's (translated) title width.
 * Coordinates are relative to the GUI's top-left corner, like all slot positions.
 */
public final class UpgradeLayout {
    public static final int COLUMNS = Arrays.stream(UpgradeSlotType.values()).mapToInt(UpgradeSlotType::getColumn).max().orElse(0) + 1;
    public static final int ROWS = Arrays.stream(UpgradeSlotType.values()).mapToInt(UpgradeSlotType::getRow).max().orElse(0) + 1;
    public static final int SLOT_SIZE = 18;
    /** Vertical position of the side tabs (both sides). */
    public static final int TAB_TOP = 4;
    /** Tab header height and inner padding; must match the side tab widget. */
    public static final int TAB_HEADER_HEIGHT = 24;
    public static final int TAB_PADDING = 8;

    public static final int GRID_WIDTH = COLUMNS * SLOT_SIZE;
    public static final int GRID_HEIGHT = ROWS * SLOT_SIZE;
    /** Left edge of the slot grid (the outer edge of the slot frames). */
    public static final int GRID_LEFT = -TAB_PADDING - GRID_WIDTH;
    public static final int GRID_TOP = TAB_TOP + TAB_HEADER_HEIGHT;

    private UpgradeLayout() {
    }

    /** X of the slot's item (inside its 18x18 frame), as used by {@link net.minecraft.world.inventory.Slot}. */
    public static int slotX(UpgradeSlotType type) {
        return GRID_LEFT + type.getColumn() * SLOT_SIZE + 1;
    }

    public static int slotY(UpgradeSlotType type) {
        return GRID_TOP + type.getRow() * SLOT_SIZE + 1;
    }
}
