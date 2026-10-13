package laseta.bac.compat.ae2;

import appeng.api.ids.AEBlockIds;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import laseta.bac.stock.StockReader;
import laseta.bac.stock.StockReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Applied Energistics 2: reads the whole ME network through its controller, from the network's cached contents
 * (the same list ME terminals show). Only loaded when AE2 is installed.
 */
public class Ae2StockReader implements StockReader {
    @Override
    public boolean canLink(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlockHolder().is(AEBlockIds.CONTROLLER);
    }

    @Override
    public Component getTargetDescription() {
        return Component.translatable("item.better_auto_crafter.me_storage_link_upgrade.target");
    }

    @Override
    public StockReading read(ServerLevel level, BlockPos pos) {
        if (!canLink(level, pos)) {
            return StockReading.missing();
        }
        IGridNode node = findNode(GridHelper.getNodeHost(level, pos));
        // Active: the network is powered and booted, like the terminals require.
        if (node == null || !node.isActive()) {
            return StockReading.offline();
        }
        KeyCounter contents = node.getGrid().getStorageService().getCachedInventory();
        return StockReading.active((stack, limit) -> {
            AEItemKey key = AEItemKey.of(stack);
            return key != null ? contents.get(key) : 0;
        });
    }

    /** AE2 only hands out a block's node for a real side it is exposed on (never for a null side). */
    @Nullable
    private static IGridNode findNode(@Nullable IInWorldGridNodeHost host) {
        if (host == null) {
            return null;
        }
        for (Direction side : Direction.values()) {
            IGridNode node = host.getGridNode(side);
            if (node != null) {
                return node;
            }
        }
        return null;
    }
}
