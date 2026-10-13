package laseta.bac.compat.tomsstorage;

import laseta.bac.stock.InventoryStockReader;
import laseta.bac.stock.StockReading;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Tom's Simple Storage: reads the whole network through its Inventory Connector, which exposes every connected
 * inventory as one item handler. Needs no compile dependency, but only exists when the mod is installed.
 */
public class TomsStorageStockReader extends InventoryStockReader {
    private static final ResourceLocation CONNECTOR = ResourceLocation.fromNamespaceAndPath("toms_storage", "inventory_connector");

    @Override
    public boolean canLink(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlockHolder().is(CONNECTOR);
    }

    @Override
    public Component getTargetDescription() {
        return Component.translatable("item.better_auto_crafter.toms_storage_link_upgrade.target");
    }

    @Override
    public StockReading read(ServerLevel level, BlockPos pos) {
        if (!canLink(level, pos)) {
            return StockReading.missing();
        }
        IItemHandler network = findHandler(level, pos);
        return network == null ? StockReading.offline() : StockReading.active((stack, limit) -> count(network, stack, limit));
    }
}
