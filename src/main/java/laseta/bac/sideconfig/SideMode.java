package laseta.bac.sideconfig;

import java.util.Locale;

import net.minecraft.network.chat.Component;

/**
 * What a single face of a machine does with items.
 * The order of the constants is the order the UI cycles through.
 */
public enum SideMode {
    NONE(0x8B8B8B),
    INPUT(0x3B82F6),
    OUTPUT(0xF59E0B),
    INPUT_OUTPUT(0x22C55E);

    private static final SideMode[] VALUES = values();

    private final int color;

    SideMode(int color) {
        this.color = color;
    }

    public boolean canInput() {
        return this == INPUT || this == INPUT_OUTPUT;
    }

    public boolean canOutput() {
        return this == OUTPUT || this == INPUT_OUTPUT;
    }

    /** RGB colour used to highlight this mode in the UI (no alpha). */
    public int getColor() {
        return color;
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component getDisplayName() {
        return Component.translatable("gui.better_auto_crafter.side_mode." + getSerializedName());
    }

    public Component getDescription() {
        return Component.translatable("gui.better_auto_crafter.side_mode." + getSerializedName() + ".desc");
    }

    public SideMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public SideMode previous() {
        return VALUES[(ordinal() + VALUES.length - 1) % VALUES.length];
    }

    public static SideMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    public static SideMode byName(String name) {
        for (SideMode mode : VALUES) {
            if (mode.getSerializedName().equals(name)) {
                return mode;
            }
        }
        return NONE;
    }
}
