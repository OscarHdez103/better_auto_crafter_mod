package laseta.bac.menu;

import laseta.bac.block.entity.BetterAutoHubBlockEntity;
import laseta.bac.machine.HostTabs;
import laseta.bac.machine.MachineLayout;
import laseta.bac.menu.slot.HostTabSlot;
import laseta.bac.registry.ModMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The hub's menu for an empty tab: only the player inventory and the hub's tabs (see {@link HostTabs}). A tab with
 * machines shows their own menu instead. Shift-clicking machines puts them into the tab. Slot indices: player
 * inventory 0-35, tabs after that.
 */
public class BetterAutoHubMenu extends AbstractContainerMenu implements HostTabs.Holder {
    /** Vanilla's container size; nothing in the machine area. */
    public static final MachineLayout LAYOUT = new MachineLayout(176, 166, 0, 0, 0, 0, 8, 84);
    private static final int INVENTORY_START = 0;
    private static final int INVENTORY_END = 36;

    private final HostTabs tabs;

    /** Client-side constructor (called by the MenuType with the data written in {@code openMenu}). */
    public BetterAutoHubMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, HostTabs.read(extraData));
    }

    /** Server-side constructor (called by the hub). */
    public BetterAutoHubMenu(int containerId, Inventory playerInventory, BetterAutoHubBlockEntity hub, int tab) {
        this(containerId, playerInventory, HostTabs.of(hub, tab, null));
    }

    private BetterAutoHubMenu(int containerId, Inventory playerInventory, HostTabs tabs) {
        super(ModMenuTypes.BETTER_AUTO_HUB.get(), containerId);
        this.tabs = tabs;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, LAYOUT.inventoryLeft() + col * MachineLayout.SLOT_SIZE,
                        LAYOUT.inventoryTop() + row * MachineLayout.SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, LAYOUT.inventoryLeft() + col * MachineLayout.SLOT_SIZE, LAYOUT.hotbarTop()));
        }
        tabs.createSlots(LAYOUT.width()).forEach(this::addSlot);
        addDataSlots(tabs.states());
    }

    @Override
    public @Nullable HostTabs getHostTabs() {
        return tabs;
    }

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
        // Other tabs' slots are only there to be seen: clicking one shows that tab instead (see the screen).
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof HostTabSlot tabSlot && !tabSlot.isCurrent()) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
        tabs.afterClick(player);
    }

    /** Collecting items with a double click never takes machines out of the tabs. */
    @Override
    public boolean canTakeItemForPickAll(@NotNull ItemStack stack, @NotNull Slot slot) {
        return !(slot instanceof HostTabSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(@NotNull Slot slot) {
        return !(slot instanceof HostTabSlot) && super.canDragTo(slot);
    }

    /** Machines go into the tab, its machines back into the inventory, anything else between inventory and hotbar. */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int tabSlot = INVENTORY_END + tabs.current();
        if (index >= INVENTORY_END) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (HostTabs.canHold(stack) && moveItemStackTo(stack, tabSlot, tabSlot + 1, false)) {
            // Moved what fits; EMPTY stops vanilla's quick-move loop.
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            return ItemStack.EMPTY;
        } else if (index < INVENTORY_END - 9) {
            if (!moveItemStackTo(stack, INVENTORY_END - 9, INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END - 9, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        tabs.menuClosed(player);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return tabs.stillValid(player);
    }
}
