package laseta.bac.compat.refinedstorage;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.impl.node.externalstorage.ExternalStorageNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.GraphNetworkComponent;
import com.refinedmods.refinedstorage.api.network.node.container.NetworkNodeContainer;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.common.controller.AbstractControllerBlock;
import com.refinedmods.refinedstorage.common.controller.ControllerBlockEntity;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import laseta.bac.stock.StockReader;
import laseta.bac.stock.StockReading;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Refined Storage 2: reads the whole network through its controller (normal or creative), from the network's
 * root storage (the same contents grids show). Only loaded when Refined Storage is installed.
 */
public class RefinedStorageStockReader implements StockReader {
    @Override
    public boolean canLink(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof AbstractControllerBlock<?>;
    }

    @Override
    public Component getTargetDescription() {
        return Component.translatable("item.better_auto_crafter.refined_storage_link_upgrade.target");
    }

    @Override
    public StockReading read(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ControllerBlockEntity controller)) {
            return StockReading.missing();
        }
        // Without energy the network's devices (and so its storages) are inactive.
        Network network = controller.getNetworkForItem();
        if (network == null || controller.getEnergyStorage().getStored() <= 0) {
            return StockReading.offline();
        }
        StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
        if (storage == null) {
            return StockReading.offline();
        }
        return StockReading.active((stack, limit) -> {
            ItemResource resource = ItemResource.ofItemStack(stack);
            long count = storage.get(resource);
            // Only an outdated count that is too low can cause extra crafts, so only then is it worth rescanning.
            if (count < limit && rescanExternalStorages(network)) {
                count = storage.get(resource);
            }
            return count;
        });
    }

    /**
     * External Storages only see changes made around the network (like crafts put straight into the inventory) when
     * they rescan it, which they do less often (down to every 2 seconds) while nothing changes. Rescans them now, the
     * same way their own block does.
     *
     * @return whether any of them found a change
     */
    private static boolean rescanExternalStorages(Network network) {
        GraphNetworkComponent graph = network.getComponent(GraphNetworkComponent.class);
        if (graph == null) {
            return false;
        }
        boolean changed = false;
        for (NetworkNodeContainer container : graph.getContainers()) {
            if (container.getNode() instanceof ExternalStorageNetworkNode externalStorage) {
                changed |= externalStorage.detectChanges();
            }
        }
        return changed;
    }
}
