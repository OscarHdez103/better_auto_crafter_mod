package laseta.bac.stock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Reads how much of an item a storage holds, for a Storage Link Upgrade. Every Storage Link Upgrade item has one:
 * the generic one reads any inventory, the mod-specific ones read a storage network through its controller.
 *
 * <p>Implementations for other mods live in their own {@code compat} package and are only loaded when that mod is
 * installed (see {@link laseta.bac.compat.StorageLinkIntegrations}).</p>
 */
public interface StockReader {
    /** Whether the upgrade may be linked to this block. Called on both sides, so it should only look at the block. */
    boolean canLink(Level level, BlockPos pos);

    /** What the upgrade has to be linked to, e.g. "an ME Controller". Shown in its tooltip and messages. */
    Component getTargetDescription();

    /**
     * Server only; {@code pos} is loaded. Reads the storage at the linked position.
     *
     * @return {@link StockReading#missing()} if the block there can't be read by this reader anymore,
     * {@link StockReading#offline()} if it can but its network doesn't work right now
     */
    StockReading read(ServerLevel level, BlockPos pos);
}
