package laseta.bac.machine;

import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * An upgrade slot only some machines have, in their own tab (compare the upgrade slots every machine has,
 * {@link laseta.bac.upgrade.UpgradeSlotType}). A machine lists them in its {@link MachineFeatures}; upgrades that
 * exclude each other share one slot.
 *
 * @param name         saved under this key (the slot's position in the list is its index), and used in the GUI texts'
 *                     keys ("gui.better_auto_crafter.machine_upgrade_slot.&lt;name&gt;")
 * @param icon         item drawn faded in the empty slot, to show what goes there
 * @param limit        how many upgrades the slot holds
 * @param configurable whether upgrades that go there can have an item set in the machine's tab (see
 *                     {@link laseta.bac.item.ItemSettingUpgradeItem}); the tab then has room for it next to the slot
 * @param accepts      which upgrades go there
 */
public record MachineUpgradeSlotType(String name, Supplier<? extends Item> icon, int limit, boolean configurable,
                                     Predicate<ItemStack> accepts) {
    public boolean canHold(ItemStack stack) {
        return !stack.isEmpty() && accepts.test(stack);
    }

    public ItemStack getIcon() {
        return new ItemStack(icon.get());
    }

    public Component getDisplayName() {
        return Component.translatable("gui.better_auto_crafter.machine_upgrade_slot." + name);
    }

    /** What the slot accepts, shown while it is empty. */
    public Component getDescription() {
        return Component.translatable("gui.better_auto_crafter.machine_upgrade_slot." + name + ".desc", limit);
    }
}
