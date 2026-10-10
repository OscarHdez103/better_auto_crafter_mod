package laseta.bac.upgrade;

import java.util.Locale;

import net.minecraft.network.chat.Component;

/** State of an installed upgrade, shown in the upgrades tab. Every state except ACTIVE (and EMPTY) stops crafting. */
public enum UpgradeStatus {
    EMPTY,
    ACTIVE,
    NOT_LINKED,
    WRONG_DIMENSION,
    DIMENSION_UNAVAILABLE,
    UNLOADED,
    TRANSLOCATOR_MISSING;

    private static final UpgradeStatus[] VALUES = values();

    public boolean isProblem() {
        return this != EMPTY && this != ACTIVE;
    }

    public Component getDescription() {
        return Component.translatable("gui.better_auto_crafter.upgrade_status." + name().toLowerCase(Locale.ROOT));
    }

    public static UpgradeStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : EMPTY;
    }
}
