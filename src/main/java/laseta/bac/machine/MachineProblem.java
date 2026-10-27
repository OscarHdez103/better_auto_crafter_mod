package laseta.bac.machine;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * Why a machine's operation didn't happen, or why it can't happen at all right now. Shown in the status icon of the
 * machine's GUI. Every problem is a failure except {@link #STOCKED}, which only means there is nothing to do.
 *
 * <p>GUI texts: "problem.&lt;name&gt;", from the client's {@code MachineText} (so they can be worded per machine).</p>
 */
public enum MachineProblem {
    /** Nothing in the template grid. */
    EMPTY_TEMPLATE,
    /** The template isn't something the machine can make (e.g. not a recipe). */
    NO_RECIPE,
    /** An installed upgrade doesn't work; the upgrades tab says which and why. */
    BROKEN_UPGRADE,
    /** No inventory to pull from (no input face with an inventory, or no linked block that can be used). */
    NO_INPUTS,
    /** No inventory to push into. */
    NO_OUTPUTS,
    /** The input inventories don't hold everything the template needs. */
    MISSING_ITEMS,
    /** The items found can't be processed (e.g. with NBT matching off, a variant the recipe doesn't accept). */
    CANNOT_PROCESS,
    /** No room in the output inventories for what comes out. */
    OUTPUT_FULL,
    /** An input inventory didn't hand over the items it said it had; everything was put back. */
    INPUTS_CHANGED,
    /** The outputs already hold the stocking upgrades' amount of everything: nothing to do. */
    STOCKED,
    /** Not enough fuel stored, and none (or not enough) on the fuel faces. */
    NO_FUEL,
    /** The starter faces don't hold what each operation starts from (e.g. water bottles). */
    MISSING_STARTER,
    /** No room on the leftover faces for what is left of the used fuel (e.g. an empty bucket). */
    LEFTOVER_FULL,
    /** An upgrade in the machine's own tab needs an item set before it works. */
    UNSET_UPGRADE;

    private static final MachineProblem[] VALUES = values();

    /** False for problems that only mean there is nothing to do: the machine isn't shown as failed for them. */
    public boolean isFailure() {
        return this != STOCKED;
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Menu data value: 0 for no problem, otherwise the ordinal + 1. */
    public static int toId(@Nullable MachineProblem problem) {
        return problem == null ? 0 : problem.ordinal() + 1;
    }

    @Nullable
    public static MachineProblem byId(int id) {
        return id > 0 && id <= VALUES.length ? VALUES[id - 1] : null;
    }
}
