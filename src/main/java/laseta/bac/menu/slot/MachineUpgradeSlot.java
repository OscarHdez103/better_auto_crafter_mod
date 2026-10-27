package laseta.bac.menu.slot;

import java.util.function.BooleanSupplier;

import laseta.bac.machine.MachineUpgradeSlotType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * An upgrade slot only this kind of machine has. It sits in the machine's own tab outside the GUI, so it is only shown
 * and clickable while that tab is open ({@link #isActive()}); shift-clicking upgrades still moves them in when the tab
 * is closed. What it accepts and its limit come from the machine's upgrade inventory.
 */
public class MachineUpgradeSlot extends SlotItemHandler {
    private final MachineUpgradeSlotType type;
    private final BooleanSupplier visible;

    public MachineUpgradeSlot(IItemHandler upgrades, int index, MachineUpgradeSlotType type, int x, int y, BooleanSupplier visible) {
        super(upgrades, index, x, y);
        this.type = type;
        this.visible = visible;
    }

    public MachineUpgradeSlotType getType() {
        return type;
    }

    @Override
    public boolean isActive() {
        return visible.getAsBoolean();
    }
}
