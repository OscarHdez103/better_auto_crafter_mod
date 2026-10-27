package laseta.bac.machine;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * The cooldown after each operation of a machine, and the problem of its last attempt. Owned by a block entity;
 * {@code onChanged} is called whenever something that must be saved changes.
 *
 * <p>The cooldown ends at a game time, so nothing has to count it down and nothing can shorten it: turning the redstone
 * signal off and on, changing modes or reloading the chunk all leave it running. It is saved as the ticks left.</p>
 */
public class MachineStatus {
    // Menu data slot indices (see AutoMachineBlockEntity.getStatusData)
    public static final int DATA_COOLDOWN_LEFT = 0;
    public static final int DATA_COOLDOWN_TOTAL = 1;
    public static final int DATA_PROBLEM = 2;
    /** Whether the block receives a redstone signal. */
    public static final int DATA_SIGNAL = 3;
    public static final int DATA_COUNT = 4;

    private final Runnable onChanged;
    /** Game time the cooldown ends at. */
    private long cooldownEnd;
    /** Length of the current (or last) cooldown, for the GUI's progress. */
    private int cooldownTotal;
    /** Ticks left as loaded, until the game time is known; -1 once it is. */
    private int loadedCooldown = -1;
    /** Not saved: the next attempt finds it again. */
    @Nullable
    private MachineProblem lastProblem;

    public MachineStatus(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public boolean isCoolingDown(long now) {
        return getCooldownLeft(now) > 0;
    }

    public int getCooldownLeft(long now) {
        if (loadedCooldown >= 0) {
            cooldownEnd = now + loadedCooldown;
            loadedCooldown = -1;
        }
        return (int) Math.max(0, cooldownEnd - now);
    }

    public int getCooldownTotal() {
        return cooldownTotal;
    }

    @Nullable
    public MachineProblem getLastProblem() {
        return lastProblem;
    }

    /** Forgets the last problem, e.g. when the template changes and it may not apply any more. */
    public void clearLastProblem() {
        lastProblem = null;
    }

    /**
     * Records an attempt: its problem (null if it worked) and, after a successful one, starts the cooldown.
     *
     * @param cooldown ticks until the machine may run again; only used when the attempt worked
     */
    public void finishAttempt(long now, @Nullable MachineProblem problem, int cooldown) {
        lastProblem = problem;
        if (problem == null && cooldown > 0) {
            loadedCooldown = -1;
            cooldownEnd = now + cooldown;
            cooldownTotal = cooldown;
            onChanged.run();
        }
    }

    /** Ends the cooldown right away (e.g. for tests that run a machine several times in a row). */
    public void endCooldown() {
        loadedCooldown = -1;
        cooldownEnd = 0;
        onChanged.run();
    }

    public CompoundTag save(long now) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cooldown", getCooldownLeft(now));
        tag.putInt("cooldown_total", cooldownTotal);
        return tag;
    }

    public void load(CompoundTag tag) {
        // The game time isn't known yet: the cooldown is resolved on first use.
        loadedCooldown = Math.max(0, tag.getInt("cooldown"));
        cooldownTotal = Math.max(loadedCooldown, tag.getInt("cooldown_total"));
    }
}
