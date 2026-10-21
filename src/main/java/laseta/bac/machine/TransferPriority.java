package laseta.bac.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import laseta.bac.sideconfig.RelativeSide;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Which face's inventory is used first when pulling items (input) or pushing them (output).
 * After the preferred face, the other faces follow in {@link RelativeSide} order.
 */
public enum TransferPriority {
    /** Every successful operation starts one face further, spreading the load over all inventories. */
    ROUND_ROBIN(null),
    FRONT(RelativeSide.FRONT),
    BACK(RelativeSide.BACK),
    LEFT(RelativeSide.LEFT),
    RIGHT(RelativeSide.RIGHT),
    TOP(RelativeSide.TOP),
    BOTTOM(RelativeSide.BOTTOM);

    private static final TransferPriority[] VALUES = values();

    @Nullable
    private final RelativeSide preferredSide;

    TransferPriority(@Nullable RelativeSide preferredSide) {
        this.preferredSide = preferredSide;
    }

    /**
     * Orders the faces (given in {@link RelativeSide} order) for one operation.
     *
     * @param sideOf           face of each element
     * @param roundRobinCursor number of successful operations so far (only used by {@link #ROUND_ROBIN})
     */
    public <T> List<T> order(List<T> faces, Function<T, RelativeSide> sideOf, int roundRobinCursor) {
        List<T> ordered = new ArrayList<>(faces);
        if (ordered.size() < 2) {
            return ordered;
        }
        if (preferredSide == null) {
            Collections.rotate(ordered, -Math.floorMod(roundRobinCursor, ordered.size()));
        } else {
            // Stable sort: the preferred face moves to the front, the rest keep their order.
            ordered.sort((a, b) -> Boolean.compare(sideOf.apply(b) == preferredSide, sideOf.apply(a) == preferredSide));
        }
        return ordered;
    }

    @Nullable
    public RelativeSide getPreferredSide() {
        return preferredSide;
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component getDisplayName() {
        return preferredSide == null
                ? Component.translatable("gui.better_auto_crafter.priority.round_robin")
                : preferredSide.getDisplayName();
    }

    public TransferPriority next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public TransferPriority previous() {
        return VALUES[(ordinal() + VALUES.length - 1) % VALUES.length];
    }

    public static TransferPriority byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : ROUND_ROBIN;
    }

    public static TransferPriority byName(String name) {
        for (TransferPriority priority : VALUES) {
            if (priority.getSerializedName().equals(name)) {
                return priority;
            }
        }
        // Values saved by the earlier version of this setting: "ordered" was face order starting at the front.
        return "ordered".equals(name) ? FRONT : ROUND_ROBIN;
    }
}
