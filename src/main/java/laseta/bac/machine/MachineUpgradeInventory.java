package laseta.bac.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

/**
 * The upgrade slots only this kind of machine has: one per {@link MachineUpgradeSlotType} of its features, each
 * accepting only its upgrades, up to its limit. Like the other upgrades, not exposed as a capability.
 */
public class MachineUpgradeInventory extends ItemStackHandler {
    private final List<MachineUpgradeSlotType> types;
    private final Runnable onChanged;
    /** Items loaded into slots that no longer exist; the owner drops them in the world. */
    private final List<ItemStack> orphans = new ArrayList<>();

    public MachineUpgradeInventory(List<MachineUpgradeSlotType> types, Runnable onChanged) {
        super(types.size());
        this.types = List.copyOf(types);
        this.onChanged = onChanged;
    }

    public List<MachineUpgradeSlotType> types() {
        return types;
    }

    public ItemStack get(MachineUpgradeSlotType type) {
        return getStackInSlot(types.indexOf(type));
    }

    /** Index of the slot that accepts {@code stack}, or -1. */
    public int slotFor(ItemStack stack) {
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).canHold(stack)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return slot >= 0 && slot < types.size() && types.get(slot).canHold(stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot >= 0 && slot < types.size() ? types.get(slot).limit() : 0;
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChanged.run();
    }

    /** Keeps the current number of slots (ItemStackHandler resizes itself to the saved one), like the other upgrades. */
    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider provider, @NotNull CompoundTag nbt) {
        super.deserializeNBT(provider, nbt);
        if (stacks.size() == types.size()) {
            return;
        }
        List<ItemStack> loaded = new ArrayList<>(stacks);
        setSize(types.size());
        for (int i = 0; i < loaded.size(); i++) {
            if (i < types.size()) {
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
