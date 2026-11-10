package laseta.bac.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;

import laseta.bac.menu.slot.HostTabSlot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The row of tabs above a menu shown in a {@link TabHost} (the Better Auto Hub): one slot per tab, holding the host's
 * machines of one kind, and what they are doing. Like a browser's tabs, one of them is always the menu's own: only that
 * one's slot can be used, clicking another one shows that tab instead. Added to the menu with
 * {@link #createSlots(int)} and {@link #states()}, the same on both sides. A host may keep some tabs locked: they can't
 * be shown nor hold machines. It may also need energy, whose amount is part of the same data.
 *
 * <p>Positions are relative to the GUI's top-left corner, like all slot positions: the tabs stick out above the GUI,
 * sharing its whole width.</p>
 */
public final class HostTabs {
    /** Space between two tabs, like the creative inventory's. */
    public static final int TAB_GAP = 1;
    /** How far the tabs stick out above the GUI. */
    public static final int TAB_HEIGHT = 28;
    /** Y of the tabs' slot items: where the creative inventory puts its tabs' icons. */
    public static final int SLOT_Y = -TAB_HEIGHT + 9;

    // Tab state data values: 0 without machines, otherwise the MachineState ordinal + 1, plus the problem's id above it;
    // LOCKED for a locked tab. After the tabs' values, the host's energy, in two halves (menu data are only 16 bits).
    public static final int LOCKED = -1;
    private static final int ENERGY_DATA = 2;
    private static final int HALF_BITS = 16;
    private static final int HALF_MASK = (1 << HALF_BITS) - 1;
    private static final int STATE_BITS = 3;
    private static final int STATE_MASK = (1 << STATE_BITS) - 1;
    private static final MachineState[] STATES = MachineState.values();

    /** A menu that may be shown in a host's tab. */
    public interface Holder {
        /** The tabs above the menu, null when it isn't in a host's tab. */
        @Nullable
        HostTabs getHostTabs();
    }

    /**
     * The machines of every tab: only machines that may be in a tab, at most {@code perTab} of them each, and only in
     * unlocked tabs.
     */
    public static class TabItems extends ItemStackHandler {
        private final IntSupplier perTab;
        private final IntPredicate unlocked;

        public TabItems(int tabs, IntSupplier perTab, IntPredicate unlocked) {
            super(tabs);
            this.perTab = perTab;
            this.unlocked = unlocked;
        }

        @Override
        public int getSlotLimit(int slot) {
            return perTab.getAsInt();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return unlocked.test(slot) && canHold(stack);
        }
    }

    private final IItemHandler items;
    private final ContainerData states;
    private final int current;
    /** FE the host stores, 0 if it doesn't use energy. */
    private final int energyCapacity;
    private final int energyPerOperation;
    /** Width of the GUI the tabs are above, set by {@link #createSlots}. */
    private int guiWidth;
    /** Server only. */
    @Nullable
    private final TabHost host;
    /** Server only: the machine the menu shows, null for an empty tab's menu. */
    @Nullable
    private final AutoMachineBlockEntity shown;

    private HostTabs(IItemHandler items, ContainerData states, int current, int energyCapacity, int energyPerOperation,
                     @Nullable TabHost host, @Nullable AutoMachineBlockEntity shown) {
        this.items = items;
        this.states = states;
        this.current = current;
        this.energyCapacity = energyCapacity;
        this.energyPerOperation = energyPerOperation;
        this.host = host;
        this.shown = shown;
    }

    /** Server side, backed by the host. */
    public static HostTabs of(TabHost host, int tab, @Nullable AutoMachineBlockEntity shown) {
        return new HostTabs(host.getTabItems(), host.getTabStates(), tab, host.getEnergyCapacity(),
                host.getEnergyPerOperation(), host, shown);
    }

    /** Server side: what {@link #read} needs, written when the menu opens. */
    public static void write(RegistryFriendlyByteBuf buffer, TabHost host, int tab) {
        buffer.writeVarInt(host.getTabItems().getSlots());
        buffer.writeVarInt(host.getTabItems().getSlotLimit(0));
        buffer.writeVarInt(tab);
        buffer.writeVarInt(host.getEnergyCapacity());
        buffer.writeVarInt(host.getEnergyPerOperation());
    }

    /** Client side, as {@link #write} wrote it: plain copies that vanilla's container syncing keeps up to date. */
    public static HostTabs read(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        int perTab = buffer.readVarInt();
        int current = buffer.readVarInt();
        int energyCapacity = buffer.readVarInt();
        int energyPerOperation = buffer.readVarInt();
        ContainerData states = new SimpleContainerData(dataCount(count));
        TabItems items = new TabItems(count, () -> perTab, tab -> states.get(tab) != LOCKED);
        return new HostTabs(items, states, current, energyCapacity, energyPerOperation, null, null);
    }

    /** How many values a host's tab state data has, for {@code tabs} tabs: see {@link #stateData}. */
    public static int dataCount(int tabs) {
        return tabs + ENERGY_DATA;
    }

    /**
     * A value of a host's tab state data: the tab's {@link #encodeState state} (or {@link #LOCKED}) for each tab, then
     * the host's energy.
     *
     * @param tabState the state of the tab {@code index} is, when it is a tab's
     */
    public static int stateData(int index, int tabs, int tabState, int energy) {
        if (index < tabs) {
            return tabState;
        }
        return index == tabs ? energy & HALF_MASK : energy >>> HALF_BITS;
    }

    /** X of a tab's left side: the tabs share the GUI's width, any pixel left over going to every other one. */
    public int tabLeft(int tab) {
        return tab * (guiWidth + TAB_GAP) / count();
    }

    public int tabWidth(int tab) {
        return tabLeft(tab + 1) - tabLeft(tab) - TAB_GAP;
    }

    /** X of a tab's slot item, centered in the tab. */
    public int slotX(int tab) {
        return tabLeft(tab) + (tabWidth(tab) - 16) / 2;
    }

    /** Whether {@code stack} may go into a tab: machines of this mod that can be hosted (see {@link AutoMachineBlock}). */
    public static boolean canHold(ItemStack stack) {
        return AutoMachineBlock.hostable(stack) != null;
    }

    /** Tab state data value, see {@link #getState} and {@link #getProblem}. */
    public static int encodeState(@Nullable MachineState state, @Nullable MachineProblem problem) {
        return state == null ? 0 : state.ordinal() + 1 | MachineProblem.toId(problem) << STATE_BITS;
    }

    public int count() {
        return items.getSlots();
    }

    /** The menu's own tab. */
    public int current() {
        return current;
    }

    public ItemStack getMachines(int tab) {
        return items.getStackInSlot(tab);
    }

    /** Whether the tab is locked: it can't be shown nor hold machines until the host unlocks it. */
    public boolean isLocked(int tab) {
        return states.get(tab) == LOCKED;
    }

    /** Whether the host needs energy to run its machines: only then does it show it. */
    public boolean usesEnergy() {
        return energyCapacity > 0;
    }

    public int getEnergy() {
        // Each half arrives as a signed 16-bit value.
        return states.get(count()) & HALF_MASK | (states.get(count() + 1) & HALF_MASK) << HALF_BITS;
    }

    public int getEnergyCapacity() {
        return energyCapacity;
    }

    public int getEnergyPerOperation() {
        return energyPerOperation;
    }

    /** What the tab's machines are doing; null for an empty or locked tab. */
    @Nullable
    public MachineState getState(int tab) {
        if (isLocked(tab)) {
            return null;
        }
        int state = (states.get(tab) & STATE_MASK) - 1;
        return state >= 0 && state < STATES.length ? STATES[state] : null;
    }

    /** What stops the tab's machines right now, or else what stopped their last run; null if nothing. */
    @Nullable
    public MachineProblem getProblem(int tab) {
        return isLocked(tab) ? null : MachineProblem.byId(states.get(tab) >>> STATE_BITS);
    }

    /** One slot per tab, above a GUI {@code guiWidth} wide, to add to the menu after all its other slots. */
    public List<Slot> createSlots(int guiWidth) {
        this.guiWidth = guiWidth;
        List<Slot> slots = new ArrayList<>(count());
        for (int tab = 0; tab < count(); tab++) {
            int index = tab;
            slots.add(new HostTabSlot(items, tab, tab == current, () -> isLocked(index), slotX(tab), SLOT_Y));
        }
        return slots;
    }

    /** The tab states, to add to the menu's data slots. */
    public ContainerData states() {
        return states;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Server side (nothing happens on the client)
    // ---------------------------------------------------------------------------------------------------------------

    /** The host, on the server; null on the client. */
    @Nullable
    public TabHost host() {
        return host;
    }

    /** The machine the menu shows, on the server; null for an empty tab's menu, and on the client. */
    @Nullable
    public AutoMachineBlockEntity shown() {
        return shown;
    }

    public boolean stillValid(Player player) {
        return host == null || host.stillValid(player, current, shown);
    }

    public void afterClick(Player player) {
        if (host != null) {
            host.afterClick(player);
        }
    }

    public void menuClosed(Player player) {
        if (host != null) {
            host.menuClosed(player, shown);
        }
    }
}
