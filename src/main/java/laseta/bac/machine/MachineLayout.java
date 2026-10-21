package laseta.bac.machine;

/**
 * Where a machine GUI's slots are, shared by the menu (slot positions) and the screen (background, labels). Positions
 * are those of the slots' items (inside their 18x18 frames), relative to the GUI's top-left corner, like all slot
 * positions.
 *
 * @param gridLeft      x of the template grid's top-left slot
 * @param gridTop       y of the template grid's top-left slot
 * @param inventoryLeft x of the player inventory's top-left slot
 * @param inventoryTop  y of the player inventory's top-left slot; the hotbar is 58 below it
 */
public record MachineLayout(int width, int height, int gridColumns, int gridRows, int gridLeft, int gridTop,
                            int inventoryLeft, int inventoryTop) {
    public static final int SLOT_SIZE = 18;
    /** Vanilla's container size, the smallest a machine GUI gets. */
    private static final int MIN_WIDTH = 176;
    private static final int MIN_INVENTORY_TOP = 84;
    private static final int GRID_TOP = 17;
    /** Space between the grid and the inventory, for the inventory label. */
    private static final int LABEL_SPACE = 13;
    /** From the inventory's top to the GUI's bottom: 3 rows, the gap, the hotbar and the border. */
    private static final int INVENTORY_HEIGHT = 82;
    private static final int SIDE_BORDER = 7;
    private static final int INVENTORY_WIDTH = 9 * SLOT_SIZE;

    /** A grid of {@code columns} x {@code rows} slots centered above the player inventory; the GUI grows to fit it. */
    public static MachineLayout centered(int columns, int rows) {
        return centered(columns, rows, SIDE_BORDER);
    }

    /**
     * Like {@link #centered(int, int)}, keeping at least {@code sideSpace} free on both sides of the grid (e.g. for
     * something drawn there); the GUI gets wider if needed.
     */
    public static MachineLayout centered(int columns, int rows, int sideSpace) {
        int width = Math.max(MIN_WIDTH, columns * SLOT_SIZE + Math.max(SIDE_BORDER, sideSpace) * 2);
        int inventoryTop = Math.max(MIN_INVENTORY_TOP, GRID_TOP + rows * SLOT_SIZE + LABEL_SPACE);
        return new MachineLayout(width, inventoryTop + INVENTORY_HEIGHT, columns, rows,
                (width - columns * SLOT_SIZE) / 2 + 1, GRID_TOP,
                (width - INVENTORY_WIDTH) / 2 + 1, inventoryTop);
    }

    public int gridSize() {
        return gridColumns * gridRows;
    }

    public int gridSlotX(int index) {
        return gridLeft + (index % gridColumns) * SLOT_SIZE;
    }

    public int gridSlotY(int index) {
        return gridTop + (index / gridColumns) * SLOT_SIZE;
    }

    public int hotbarTop() {
        return inventoryTop + 58;
    }

    /** Y of the "Inventory" label, where vanilla puts it. */
    public int inventoryLabelY() {
        return inventoryTop - 12;
    }
}
