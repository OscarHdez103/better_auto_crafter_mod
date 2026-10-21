package laseta.bac.machine;

import java.util.Locale;

import net.minecraft.network.chat.Component;

/** When a machine runs, depending on its redstone signal. */
public enum RedstoneMode {
    /** Never runs. */
    OFF,
    /** Runs once every time the signal turns on (rising edge). */
    ON_POWERED,
    /** Runs every {@link #CONTINUOUS_INTERVAL} ticks while powered. */
    CONSTANT,
    /** Runs every {@link #CONTINUOUS_INTERVAL} ticks while NOT powered. */
    INVERTED;

    /** Game ticks between attempts in the continuous modes. */
    public static final int CONTINUOUS_INTERVAL = 2;

    private static final RedstoneMode[] VALUES = values();

    /** True for the modes that keep running on a timer instead of reacting to a signal change. */
    public boolean isContinuous() {
        return this == CONSTANT || this == INVERTED;
    }

    /** For continuous modes: whether to run right now given the current signal. */
    public boolean shouldRunContinuously(boolean powered) {
        return (this == CONSTANT && powered) || (this == INVERTED && !powered);
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component getDisplayName() {
        return Component.translatable("gui.better_auto_crafter.redstone_mode." + getSerializedName());
    }

    public RedstoneMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public RedstoneMode previous() {
        return VALUES[(ordinal() + VALUES.length - 1) % VALUES.length];
    }

    public static RedstoneMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : OFF;
    }

    public static RedstoneMode byName(String name) {
        for (RedstoneMode mode : VALUES) {
            if (mode.getSerializedName().equals(name)) {
                return mode;
            }
        }
        return OFF;
    }
}
