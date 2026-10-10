package laseta.bac.sideconfig;

import java.util.Locale;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * A face of the block relative to the way it is facing (like Thermal / Mekanism do it).
 * Storing the configuration relative to the front means it keeps making sense if the block is rotated.
 * Left and right are as seen by a player standing in front of the block, looking at it.
 */
public enum RelativeSide {
    FRONT,
    BACK,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM;

    public static final int COUNT = values().length;
    private static final RelativeSide[] VALUES = values();

    /** Converts this relative side into a world direction, for a block whose front points towards {@code facing}. */
    public Direction toDirection(Direction facing) {
        return switch (this) {
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getClockWise();
            case RIGHT -> facing.getCounterClockWise();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }

    /** Converts a world direction into the matching relative side, for a block whose front points towards {@code facing}. */
    public static RelativeSide fromDirection(Direction facing, Direction direction) {
        for (RelativeSide side : VALUES) {
            if (side.toDirection(facing) == direction) {
                return side;
            }
        }
        // Only reachable if facing is vertical, which this block never is.
        return FRONT;
    }

    public static RelativeSide byId(int id) {
        return VALUES[id];
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component getDisplayName() {
        return Component.translatable("gui.better_auto_crafter.side." + getSerializedName());
    }
}
