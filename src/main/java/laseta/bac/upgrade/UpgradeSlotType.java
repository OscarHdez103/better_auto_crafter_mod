package laseta.bac.upgrade;

import java.util.function.Predicate;
import java.util.function.Supplier;

import laseta.bac.item.StockingUpgradeItem;
import laseta.bac.item.StorageLinkUpgradeItem;
import laseta.bac.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The upgrade slots of every auto machine, in order. Every slot accepts one kind of upgrade, up to its own
 * stack limit.
 *
 * <p>To add a slot, add a constant <b>at the end</b> (the order is the slot index saved in the world, so never
 * reorder or remove constants). The upgrades tab, the menu and the saved inventory all size themselves from this
 * enum; blocks saved with fewer slots simply get the new slots empty.</p>
 */
public enum UpgradeSlotType {
    TRANSLOCATION_PULL("translocation_pull", ModItems.TRANSLOCATION_UPGRADE_PULL, 1,
            stack -> stack.getItem() instanceof TranslocationUpgrade upgrade && upgrade.getMode() == TranslocationMode.PULL),
    TRANSLOCATION_PUSH("translocation_push", ModItems.TRANSLOCATION_UPGRADE_PUSH, 1,
            stack -> stack.getItem() instanceof TranslocationUpgrade upgrade && upgrade.getMode() == TranslocationMode.PUSH),
    STOCKING("stocking", ModItems.STOCKING_UPGRADE, 64, stack -> stack.getItem() instanceof StockingUpgradeItem),
    STORAGE_LINK("storage_link", ModItems.STORAGE_LINK_UPGRADE, 1, stack -> stack.getItem() instanceof StorageLinkUpgradeItem);

    public static final int COUNT = values().length;
    private static final UpgradeSlotType[] VALUES = values();

    private final String key;
    private final Supplier<? extends Item> icon;
    private final int maxStackSize;
    private final Predicate<ItemStack> accepts;

    UpgradeSlotType(String key, Supplier<? extends Item> icon, int maxStackSize, Predicate<ItemStack> accepts) {
        this.key = key;
        this.icon = icon;
        this.maxStackSize = maxStackSize;
        this.accepts = accepts;
    }

    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && accepts.test(stack);
    }

    /** How many upgrades the slot holds. */
    public int getMaxStackSize() {
        return maxStackSize;
    }

    /** Item drawn faded in the empty slot, to show what goes there. */
    public ItemStack getIcon() {
        return new ItemStack(icon.get());
    }

    public Component getDisplayName() {
        return Component.translatable("gui.better_auto_crafter.upgrade_slot." + key);
    }

    /** What the slot accepts, shown while it is empty. */
    public Component getDescription() {
        return Component.translatable("gui.better_auto_crafter.upgrade_slot." + key + ".desc");
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
