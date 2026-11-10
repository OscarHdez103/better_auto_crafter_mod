package laseta.bac.machine;

import net.minecraft.core.Direction;

/**
 * Where a machine runs when it isn't a block of its own: a tab of a {@link TabHost} (the Better Auto Hub). The machine
 * works through the host's block (its position, faces and redstone signal), and runs as many times as the host holds
 * machines of its kind. See {@link AutoMachineBlockEntity#setHost}.
 */
public interface MachineHost {
    /** The host's block, holding the machine in one of its tabs. */
    TabHost getTabHost();

    /** The tab the machine is in. */
    int getTab();

    /** Direction the host's front faces: the machine's sides are relative to it. */
    Direction getFacing();

    /** Whether the host's block receives a redstone signal. */
    boolean hasSignal();

    /** How many machines of this kind the host holds: each run does that many operations, as that many machines would. */
    int getCopies();

    /** Speed of the machine, in percent of its speed on its own. */
    int getSpeedPercent();

    /** Whether more Speed Upgrades make up for {@link #getSpeedPercent()}, see {@link laseta.bac.item.SpeedUpgradeItem}. */
    boolean allowsExtraSpeedUpgrades();

    /** How many operations the host can power right now (as many as asked when it doesn't need energy). */
    int getAffordableOperations(int operations);

    /** The machine just did that many operations: the host uses their energy. */
    void onOperations(int operations);

    /** Something of the machine that must be saved changed. */
    void onMachineChanged();

    /** The machine started or stopped running, or failed: what the host shows of it may change. */
    void onMachineStateChanged();
}
