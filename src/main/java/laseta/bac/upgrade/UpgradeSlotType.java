package laseta.bac.upgrade;

import java.util.function.Predicate;
import java.util.function.Supplier;

import laseta.bac.item.CoprocessingUpgradeItem;
import laseta.bac.item.SpeedUpgradeItem;
import laseta.bac.item.StockingUpgradeItem;
import laseta.bac.item.StorageLinkUpgradeItem;
import laseta.bac.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The upgrade slots of every auto machine, in order. Every slot accepts one kind of upgrade, up to its own
 * stack limit, and has its own place in the upgrades tab's grid (related upgrades share a column).
 *
 * <p>To add a slot, add a constant <b>at the end</b> (the order is the slot index saved in the world, so never
 * reorder or remove constants) with a free column and row. The upgrades tab, the menu and the saved inventory all size
 * themselves from this enum; blocks saved with fewer slots simply get the new slots empty.</p>
 */
public enum UpgradeSlotType {
    TRANSLOCATION_PULL("translocation_pull", 0, 0, ModItems.TRANSLOCATION_UPGRADE_PULL, 1,
            stack -> stack.getItem() instanceof TranslocationUpgrade upgrade && upgrade.getMode() == TranslocationMode.PULL),
    TRANSLOCATION_PUSH("translocation_push", 0, 1, ModItems.TRANSLOCATION_UPGRADE_PUSH, 1,
            stack -> stack.getItem() instanceof TranslocationUpgrade upgrade && upgrade.getMode() == TranslocationMode.PUSH),
    STOCKING("stocking", 1, 0, ModItems.STOCKING_UPGRADE, 64, stack -> stack.getItem() instanceof StockingUpgradeItem),
    STORAGE_LINK("storage_link", 1, 1, ModItems.STORAGE_LINK_UPGRADE, 1, stack -> stack.getItem() instanceof StorageLinkUpgradeItem),
    /** Its limit depends on the machine's base speed, see {@link SpeedUpgradeItem#getUsefulCount}. */
    SPEED("speed", 2, 0, ModItems.SPEED_UPGRADE, 64, stack -> stack.getItem() instanceof SpeedUpgradeItem),
    COPROCESSING("coprocessing", 2, 1, ModItems.COPROCESSING_UPGRADE, 64, stack -> stack.getItem() instanceof CoprocessingUpgradeItem);

    public static final int COUNT = values().length;
    private static final UpgradeSlotType[] VALUES = values();

    private final String key;
    private final int column;
    private final int row;
    private final Supplier<? extends Item> icon;
    private final int maxStackSize;
    private final Predicate<ItemStack> accepts;

    UpgradeSlotType(String key, int column, int row, Supplier<? extends Item> icon, int maxStackSize, Predicate<ItemStack> accepts) {
        this.key = key;
        this.column = column;
        this.row = row;
        this.icon = icon;
        this.maxStackSize = maxStackSize;
        this.accepts = accepts;
    }

    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && accepts.test(stack);
    }

    /** How many upgrades the slot holds, unless the machine sets a lower limit (see AutoMachineBlockEntity#getUpgradeLimit). */
    public int getMaxStackSize() {
        return maxStackSize;
    }

    /** Column of the slot in the upgrades tab, see {@link UpgradeLayout}. */
    public int getColumn() {
        return column;
    }

    public int getRow() {
        return row;
    }

    /** Item drawn faded in the empty slot, to show what goes there. */
    public ItemStack getIcon() {
        return new ItemStack(icon.get());
    }

    public Component getDisplayName() {
        return Component.translatable("gui.better_auto_crafter.upgrade_slot." + key);
    }

    /** What the slot accepts, shown while it is empty. */
    public Component getDescription(int limit) {
        return Component.translatable("gui.better_auto_crafter.upgrade_slot." + key + ".desc", limit);
    }

    /** The slot that accepts {@code stack}, or null if it isn't an upgrade. */
    @Nullable
    public static UpgradeSlotType forStack(ItemStack stack) {
        for (UpgradeSlotType type : VALUES) {
            if (type.accepts(stack)) {
                return type;
            }
        }
        return null;
    }

    public static UpgradeSlotType byIndex(int index) {
        return VALUES[index];
    }
}
