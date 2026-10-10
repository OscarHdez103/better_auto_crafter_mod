package laseta.bac.menu.slot;

import java.util.function.BooleanSupplier;

import laseta.bac.upgrade.UpgradeSlotType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * An upgrade slot. It sits in the upgrades tab outside the GUI, so it is only shown and clickable while that tab is
 * open ({@link #isActive()}); shift-clicking upgrades still moves them in when the tab is closed.
 * What it accepts (and the limit of one item) comes from the upgrade inventory.
 */
public class UpgradeSlot extends SlotItemHandler {
    private final UpgradeSlotType type;
    private final BooleanSupplier visible;

    public UpgradeSlot(IItemHandler upgrades, UpgradeSlotType type, int x, int y, BooleanSupplier visible) {
        super(upgrades, type.ordinal(), x, y);
        this.type = type;
        this.visible = visible;
    }

    public UpgradeSlotType getType() {
        return type;
    }

    @Override
    public boolean isActive() {
        return visible.getAsBoolean();
    }
}
