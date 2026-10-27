package laseta.bac.machine;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * What a machine is doing, as the status icon of its GUI shows it. Worked out from what the menu syncs (redstone mode
 * and signal, cooldown, problem), so it is the same on both sides and needs no data of its own.
 */
public enum MachineState {
    /** The redstone mode is off: it never runs. */
    OFF,
    /** Ready, but waiting for its redstone signal, or with nothing to do (outputs stocked). */
    WAITING,
    /** In the cooldown after an operation, or running continuously. */
    WORKING,
    /** It can't run, or its last operation failed; the problem says why. */
    FAILED;

    public static MachineState of(RedstoneMode mode, boolean signal, boolean coolingDown, @Nullable MachineProblem problem) {
        if (mode == RedstoneMode.OFF) {
            return OFF;
        }
        if (coolingDown) {
            return WORKING;
        }
        if (problem != null) {
            return problem.isFailure() ? FAILED : WAITING;
        }
        return mode.isContinuous() && mode.shouldRunContinuously(signal) ? WORKING : WAITING;
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
