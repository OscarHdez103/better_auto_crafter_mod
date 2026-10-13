package laseta.bac.stock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Reads any block that exposes its items to pipes ({@link Capabilities.ItemHandler#BLOCK}): chests, barrels, drawer
 * controllers, Sophisticated Storage controllers, Tom's Simple Storage connectors, ...
 *
 * <p>Storage networks like AE2 or Refined Storage don't expose their whole contents this way, so they need their
 * own reader.</p>
 */
public class InventoryStockReader implements StockReader {
    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    public boolean canLink(Level level, BlockPos pos) {
        return findHandler(level, pos) != null;
    }

    @Override
    public Component getTargetDescription() {
        return Component.translatable("item.better_auto_crafter.storage_link_upgrade.target");
    }

    @Override
    public StockReading read(ServerLevel level, BlockPos pos) {
        IItemHandler handler = findHandler(level, pos);
        return handler == null ? StockReading.missing() : StockReading.active((stack, limit) -> count(handler, stack, limit));
    }

    /** The block's whole inventory if it has one without a side, otherwise the one seen from the first side that has one. */
    @Nullable
    protected static IItemHandler findHandler(Level level, BlockPos pos) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        for (int i = 0; handler == null && i < DIRECTIONS.length; i++) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, DIRECTIONS[i]);
        }
        return handler;
    }

    /** How many of {@code stack} (same item and data) the inventory holds, counting no further than {@code limit}. */
    public static long count(IItemHandler handler, ItemStack stack, long limit) {
        long count = 0;
        for (int slot = 0; slot < handler.getSlots() && count < limit; slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(inSlot, stack)) {
                count += inSlot.getCount();
            }
        }
        return count;
    }
}
