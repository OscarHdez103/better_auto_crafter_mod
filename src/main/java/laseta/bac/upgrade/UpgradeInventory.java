package laseta.bac.upgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

/**
 * The upgrade slots: one per {@link UpgradeSlotType}, each accepting only its kind of upgrade, up to the limit the
 * machine sets for it.
 * Not exposed as a capability (like the rest of the block), so only players can change it.
 */
public class UpgradeInventory extends ItemStackHandler {
    private final Runnable onChanged;
    private final ToIntFunction<UpgradeSlotType> limits;
    /** Items loaded into slots that no longer exist; the owner drops them in the world. */
    private final List<ItemStack> orphans = new ArrayList<>();

    /** @param limits how many upgrades each slot holds */
    public UpgradeInventory(Runnable onChanged, ToIntFunction<UpgradeSlotType> limits) {
        super(UpgradeSlotType.COUNT);
        this.onChanged = onChanged;
        this.limits = limits;
    }

    public ItemStack get(UpgradeSlotType type) {
        return getStackInSlot(type.ordinal());
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return slot >= 0 && slot < UpgradeSlotType.COUNT && UpgradeSlotType.byIndex(slot).accepts(stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot >= 0 && slot < UpgradeSlotType.COUNT ? limits.applyAsInt(UpgradeSlotType.byIndex(slot)) : 0;
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChanged.run();
    }

    /**
     * ItemStackHandler resizes itself to the slot count it was saved with. Keep the current number of slots instead,
     * so adding slot types later works with existing worlds.
     */
    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider provider, @NotNull CompoundTag nbt) {
        super.deserializeNBT(provider, nbt);
        if (stacks.size() == UpgradeSlotType.COUNT) {
            return;
        }
        List<ItemStack> loaded = new ArrayList<>(stacks);
        setSize(UpgradeSlotType.COUNT);
        for (int i = 0; i < loaded.size(); i++) {
            if (i < UpgradeSlotType.COUNT) {
                stacks.set(i, loaded.get(i));
            } else if (!loaded.get(i).isEmpty()) {
                orphans.add(loaded.get(i));
            }
        }
    }

    /** Returns and forgets the items that didn't fit after loading. */
    public List<ItemStack> takeOrphans() {
        List<ItemStack> taken = List.copyOf(orphans);
        orphans.clear();
        return taken;
    }
}
