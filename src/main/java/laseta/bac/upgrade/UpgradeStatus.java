package laseta.bac.upgrade;

import java.util.Locale;

import net.minecraft.network.chat.Component;

/**
 * State of an installed upgrade, shown in the upgrades tab. Every state except ACTIVE, PARTIALLY_ACTIVE (and EMPTY)
 * stops crafting.
 */
public enum UpgradeStatus {
    EMPTY,
    ACTIVE,
    NOT_LINKED,
    WRONG_DIMENSION,
    DIMENSION_UNAVAILABLE,
    UNLOADED,
    TRANSLOCATOR_MISSING,
    STORAGE_MISSING,
    NETWORK_OFFLINE,
    /** A block linked by a Multi Translocation Upgrade has no inventory on the linked face (anymore). */
    INVENTORY_MISSING,
    /** Some of the blocks linked by a Multi Translocation Upgrade can't be used; the crafter works with the others. */
    PARTIALLY_ACTIVE;

    private static final UpgradeStatus[] VALUES = values();

    /** The upgrade doesn't work, so the crafter doesn't craft. */
    public boolean isProblem() {
        return this != EMPTY && this != ACTIVE && this != PARTIALLY_ACTIVE;
    }

    /** The upgrade works, but not completely. */
    public boolean isWarning() {
        return this == PARTIALLY_ACTIVE;
    }

    public Component getDescription() {
        return Component.translatable("gui.better_auto_crafter.upgrade_status." + name().toLowerCase(Locale.ROOT));
    }

    public static UpgradeStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : EMPTY;
    }
}
