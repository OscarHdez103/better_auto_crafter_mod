package laseta.bac.stock;

import laseta.bac.item.StorageLinkUpgradeItem;
import laseta.bac.registry.ModDataComponents;
import laseta.bac.upgrade.UpgradeStatus;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Resolves an installed Storage Link Upgrade to the storage the stocking upgrades should count in. Server only. */
public final class StorageLink {
    private StorageLink() {
    }

    /**
     * Never loads chunks: an unloaded storage (or one in an unloaded dimension) makes the upgrade inactive until it is
     * loaded again. Works across dimensions: the storage is only read, nothing is moved there.
     */
    public static StockReading resolve(ItemStack upgrade, ServerLevel machineLevel) {
        if (upgrade.isEmpty() || !(upgrade.getItem() instanceof StorageLinkUpgradeItem item)) {
            return StockReading.of(UpgradeStatus.EMPTY);
        }
        GlobalPos link = upgrade.get(ModDataComponents.STORAGE_LINK.get());
        if (link == null) {
            return StockReading.of(UpgradeStatus.NOT_LINKED);
        }
        ServerLevel targetLevel = machineLevel.getServer().getLevel(link.dimension());
        if (targetLevel == null) {
            return StockReading.of(UpgradeStatus.DIMENSION_UNAVAILABLE);
        }
        if (!targetLevel.isLoaded(link.pos())) {
            return StockReading.of(UpgradeStatus.UNLOADED);
        }
        return item.getReader().read(targetLevel, link.pos());
    }
}
