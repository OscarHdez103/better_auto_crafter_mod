package laseta.bac.machine;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Server side: a block holding machines in tabs (the Better Auto Hub), as the menus shown in its tabs see it. Each tab
 * holds machines of one kind, which run as one {@link AutoMachineBlockEntity} hosted by it (see {@link MachineHost});
 * the menu of a tab is that machine's own menu, or the host's empty one for an empty tab, with the tabs above it (see
 * {@link HostTabs}).
 */
public interface TabHost {
    /** The machines in each tab. */
    IItemHandler getTabItems();

    /** What the machines of each tab are doing, and the host's energy: see {@link HostTabs#stateData}. Read-only. */
    ContainerData getTabStates();

    /** FE the host stores; 0 if it doesn't use energy. */
    int getEnergyCapacity();

    /** FE each operation of its machines uses. */
    int getEnergyPerOperation();

    /**
     * Whether a player may still use a menu shown in one of the tabs.
     *
     * @param shown the machine the menu shows, null for an empty tab's
     */
    boolean stillValid(Player player, int tab, @Nullable AutoMachineBlockEntity shown);

    /**
     * Opens a tab's menu for a player instead of the one they have open, keeping the item they carry. A locked tab
     * can't be opened, unless the player carries what unlocks it.
     */
    void openTab(ServerPlayer player, int tab);

    /**
     * After a click in a menu shown in one of the tabs: gives the player what was in the machines it took out of their
     * tab (their template, upgrades, ...), so nothing is lost.
     */
    void afterClick(Player player);

    /**
     * A menu shown in one of the tabs was closed. If its machine was taken out of its tab meanwhile, whatever was put
     * into it since goes back to the player.
     */
    void menuClosed(Player player, @Nullable AutoMachineBlockEntity shown);
}
