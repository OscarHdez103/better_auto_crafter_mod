package laseta.bac.stock;

import laseta.bac.upgrade.UpgradeStatus;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The state of a linked storage, and how to count items in it while it works.
 *
 * @param counter only set when {@code status} is ACTIVE
 */
public record StockReading(UpgradeStatus status, @Nullable Counter counter) {
    /** Counts one kind of item in a storage. Only used right away (in the same tick it was read). */
    @FunctionalInterface
    public interface Counter {
        /**
         * How many of {@code stack} (same item and data components) the storage holds. May stop counting once
         * {@code limit} is reached, so the result is only exact below it.
         */
        long count(ItemStack stack, long limit);
    }

    public static StockReading of(UpgradeStatus problem) {
        return new StockReading(problem, null);
    }

    public static StockReading active(Counter counter) {
        return new StockReading(UpgradeStatus.ACTIVE, counter);
    }

    public static StockReading missing() {
        return of(UpgradeStatus.STORAGE_MISSING);
    }

    public static StockReading offline() {
        return of(UpgradeStatus.NETWORK_OFFLINE);
    }
}
