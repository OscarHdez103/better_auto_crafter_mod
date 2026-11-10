package laseta.bac.block.entity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import laseta.bac.CommonConfig;
import laseta.bac.machine.AutoMachineBlock;
import laseta.bac.machine.AutoMachineBlockEntity;
import laseta.bac.machine.HostTabs;
import laseta.bac.machine.MachineHost;
import laseta.bac.machine.RedstoneMode;
import laseta.bac.machine.TabHost;
import laseta.bac.menu.BetterAutoHubMenu;
import laseta.bac.registry.ModBlockEntities;
import laseta.bac.registry.ModDataComponents;
import laseta.bac.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Better Auto Hub: holds the mod's machines in its tabs, up to {@link CommonConfig#hubMachinesPerTab} of one kind per tab,
 * and runs all of them from its own block. Each tab's machines are one normal machine block entity, not placed in the
 * world, which runs as that many machines (see {@link MachineHost}): it has its own template, upgrades, side
 * configuration and settings, works through the hub's faces and redstone signal, and its menu is its own menu with the
 * hub's tabs above it (see {@link HostTabs}).
 *
 * <p>Taking a tab's machines out, or putting machines of another kind in, gives back everything that was in its
 * machine to the player who did it, like breaking a machine drops it. Changing how many there are keeps it all.</p>
 *
 * <p>Only the config's starting tabs are unlocked at first: each Hub Expansion used on it unlocks the next one
 * ({@link #expand}), which its item keeps when it is broken. Its machines run at the config's percentage of their speed,
 * and, if the config says so, use energy it receives from any side.</p>
 *
 * <p>The block's front has a lamp per tab, lit green while that tab's machines run and red when they just failed,
 * like the machines' own blocks show it, and covered while the tab is locked ({@link #getLamp}, drawn by the client's
 * renderer).</p>
 */
public class BetterAutoHubBlockEntity extends BlockEntity implements TabHost {
    public static final int TABS = CommonConfig.HUB_TABS;
    // Lamp values, see getLamp
    public static final byte LAMP_OFF = 0;
    public static final byte LAMP_RUNNING = 1;
    public static final byte LAMP_FAILED = 2;
    public static final byte LAMP_LOCKED = 3;

    private final HostTabs.TabItems tabItems = new HostTabs.TabItems(TABS, CommonConfig::hubMachinesPerTab, this::isUnlocked) {
        @Override
        protected void onContentsChanged(int slot) {
            updateTab(slot);
            setChanged();
        }
    };
    /** The machine running each tab's machines, null for an empty tab. Server only. */
    private final AutoMachineBlockEntity[] machines = new AutoMachineBlockEntity[TABS];
    /** What was in machines taken out of their tab, until the player who did it gets it (or it drops). */
    private final List<ItemStack> givenBack = new ArrayList<>();
    /** Each tab's lamp: computed on the server, synced to clients. Not saved. */
    private final byte[] lamps = new byte[TABS];
    /** The tab the hub's menu opens in: the last one shown. */
    private int selectedTab;
    /** Set when a tab's machines changed kind: menus showing it must open again. */
    private boolean viewsChanged;
    /** Hub Expansions used on it: each unlocked a tab more than the config's starting ones. */
    private int expansions;
    /** FE stored, when the config makes the hub use energy. */
    private int energy;

    /** Receives energy from any side; only exposed when the config makes the hub use it. */
    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int received = Math.max(0, Math.min(toReceive, getMaxEnergyStored() - energy));
            if (!simulate && received > 0) {
                energy += received;
                setChanged();
            }
            return received;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return CommonConfig.hubEnergyCapacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    private final ContainerData tabStates = new ContainerData() {
        @Override
        public int get(int index) {
            int tabState = 0;
            if (index < TABS) {
                AutoMachineBlockEntity machine = machines[index];
                tabState = !isUnlocked(index) ? HostTabs.LOCKED : machine == null ? 0
                        : HostTabs.encodeState(machine.getMachineState(), machine.getShownProblem());
            }
            return HostTabs.stateData(index, TABS, tabState, energy);
        }

        @Override
        public void set(int index, int value) {
            // Read-only: derived from the tabs' machines.
        }

        @Override
        public int getCount() {
            return HostTabs.dataCount(TABS);
        }
    };

    public BetterAutoHubBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BETTER_AUTO_HUB.get(), pos, state);
    }

    /** One tab, as the machine in it sees the hub. */
    private final class Tab implements MachineHost {
        private final int index;

        private Tab(int index) {
            this.index = index;
        }

        @Override
        public TabHost getTabHost() {
            return BetterAutoHubBlockEntity.this;
        }

        @Override
        public int getTab() {
            return index;
        }

        @Override
        public Direction getFacing() {
            return getBlockState().getValue(AutoMachineBlock.FACING);
        }

        @Override
        public boolean hasSignal() {
            return getBlockState().getValue(AutoMachineBlock.TRIGGERED);
        }

        @Override
        public int getCopies() {
            // A tab may hold more than it can since the config was lowered: those don't run.
            return Math.min(tabItems.getStackInSlot(index).getCount(), tabItems.getSlotLimit(index));
        }

        @Override
        public int getSpeedPercent() {
            return CommonConfig.hubSpeedPercent();
        }

        @Override
        public boolean allowsExtraSpeedUpgrades() {
            return CommonConfig.hubExtraSpeedUpgrades();
        }

        @Override
        public int getAffordableOperations(int operations) {
            int perOperation = getEnergyPerOperation();
            return !usesEnergy() || perOperation <= 0 ? operations : Math.min(operations, energy / perOperation);
        }

        @Override
        public void onOperations(int operations) {
            if (usesEnergy()) {
                energy = (int) Math.max(0, energy - (long) operations * getEnergyPerOperation());
                setChanged();
            }
        }

        @Override
        public void onMachineChanged() {
            setChanged();
        }

        @Override
        public void onMachineStateChanged() {
            updateLamps();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tabs
    // ---------------------------------------------------------------------------------------------------------------

    /** Server only: the machine running a tab's machines, null for an empty tab. */
    @Nullable
    public AutoMachineBlockEntity getMachine(int tab) {
        return machines[tab];
    }

    /** The tab the hub's menu opens in: the last one shown, if it is still unlocked. */
    public int getSelectedTab() {
        return isUnlocked(selectedTab) ? selectedTab : 0;
    }

    /** Server only: how many tabs are unlocked, the first ones. */
    public int getUnlockedTabs() {
        return Math.min(TABS, CommonConfig.hubStartingTabs() + expansions);
    }

    /** Server only. A locked tab can't hold machines nor be shown, and the machines it may still hold don't run. */
    public boolean isUnlocked(int tab) {
        return tab >= 0 && tab < getUnlockedTabs();
    }

    /** Whether a Hub Expansion would unlock a tab. On both sides: the client goes by the lamps. */
    public boolean canExpand() {
        if (level != null && level.isClientSide) {
            for (byte lamp : lamps) {
                if (lamp == LAMP_LOCKED) {
                    return true;
                }
            }
            return false;
        }
        return getUnlockedTabs() < TABS;
    }

    /**
     * Server only: unlocks the next tab, for a Hub Expansion.
     *
     * @return false if every tab was unlocked already (the expansion isn't used)
     */
    public boolean expand() {
        if (!canExpand()) {
            return false;
        }
        expansions++;
        setChanged();
        updateLamps();
        return true;
    }

    public int getExpansions() {
        return expansions;
    }

    /** A tab's lamp: {@link #LAMP_OFF}, {@link #LAMP_RUNNING}, {@link #LAMP_FAILED} or {@link #LAMP_LOCKED}. On both sides. */
    public byte getLamp(int tab) {
        return lamps[tab];
    }

    /**
     * Server only, when a tab's machines changed: the same kind keeps its machine (only how many there are changed),
     * another kind (or none) gives back everything that was in it and gets a new one.
     */
    private void updateTab(int tab) {
        if (level != null && level.isClientSide) {
            return;
        }
        AutoMachineBlock kind = AutoMachineBlock.hostable(tabItems.getStackInSlot(tab));
        AutoMachineBlockEntity machine = machines[tab];
        if (machine != null && machine.getBlockState().is(kind)) {
            return;
        }
        if (machine != null) {
            givenBack.addAll(machine.takeContents());
        }
        machines[tab] = kind == null ? null : createMachine(kind, tab);
        viewsChanged = true;
        updateLamps();
    }

    private AutoMachineBlockEntity createMachine(AutoMachineBlock kind, int tab) {
        AutoMachineBlockEntity machine = kind.createHosted(worldPosition);
        machine.setHost(new Tab(tab));
        if (level != null) {
            machine.setLevel(level);
        }
        return machine;
    }

    private boolean isHosting(AutoMachineBlockEntity machine) {
        for (AutoMachineBlockEntity hosted : machines) {
            if (hosted == machine) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IItemHandler getTabItems() {
        return tabItems;
    }

    @Override
    public ContainerData getTabStates() {
        return tabStates;
    }

    @Override
    public int getEnergyCapacity() {
        return usesEnergy() ? CommonConfig.hubEnergyCapacity() : 0;
    }

    @Override
    public int getEnergyPerOperation() {
        return CommonConfig.hubEnergyPerOperation();
    }

    private static boolean usesEnergy() {
        return CommonConfig.hubUsesEnergy();
    }

    /** The energy storage other mods' cables fill; null when the config doesn't make the hub use energy. */
    @Nullable
    public IEnergyStorage getEnergyStorage() {
        return usesEnergy() ? energyStorage : null;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.BETTER_AUTO_HUB.get(),
                (hub, side) -> hub.getEnergyStorage());
    }

    @Override
    public boolean stillValid(Player player, int tab, @Nullable AutoMachineBlockEntity shown) {
        return !isRemoved() && level != null && level.getBlockEntity(worldPosition) == this
                && player.canInteractWithBlock(worldPosition, 4.0);
    }

    @Override
    public void openTab(ServerPlayer player, int tab) {
        if (tab < 0 || tab >= TABS || isRemoved()) {
            return;
        }
        AbstractContainerMenu open = player.containerMenu;
        ItemStack carried = open.getCarried();
        if (!isUnlocked(tab)) {
            // Clicking a locked tab with a Hub Expansion uses it, and shows the tab it unlocked.
            if (!carried.is(ModItems.HUB_EXPANSION.get()) || !expand()) {
                return;
            }
            carried.consume(1, player);
            playExpandSound();
            openTab(player, getUnlockedTabs() - 1);
            return;
        }
        selectedTab = tab;
        setChanged();
        open.setCarried(ItemStack.EMPTY);
        if (open != player.inventoryMenu) {
            // Without telling the client to close its screen: it swaps it for the new one, and the mouse stays put.
            player.doCloseContainer();
        }
        AutoMachineBlockEntity machine = machines[tab];
        if (machine != null) {
            player.openMenu(machine, machine::writeMenuData);
        } else {
            player.openMenu(new SimpleMenuProvider((containerId, inventory, menuPlayer) ->
                    new BetterAutoHubMenu(containerId, inventory, this, tab), getDisplayName()),
                    buffer -> HostTabs.write(buffer, this, tab));
        }
        if (carried.isEmpty()) {
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.setCarried(carried);
            player.containerMenu.broadcastChanges();
        } else {
            player.getInventory().placeItemBackInInventory(carried);
        }
    }

    @Override
    public void afterClick(Player player) {
        for (ItemStack stack : givenBack) {
            player.getInventory().placeItemBackInInventory(stack);
        }
        givenBack.clear();
    }

    @Override
    public void menuClosed(Player player, @Nullable AutoMachineBlockEntity shown) {
        if (shown != null && !isHosting(shown)) {
            shown.takeContents().forEach(player.getInventory()::placeItemBackInInventory);
        }
    }

    /** The sound of a Hub Expansion unlocking a tab. */
    public void playExpandSound() {
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.6F);
        }
    }

    public Component getDisplayName() {
        return Component.translatable("container.better_auto_crafter.better_auto_hub");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Running
    // ---------------------------------------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, BetterAutoHubBlockEntity hub) {
        hub.tick(level);
    }

    /** Server only, every tick: runs the tabs' machines, and catches up with what changed in the tabs. */
    private void tick(Level level) {
        for (int tab = 0; tab < TABS; tab++) {
            if (machines[tab] != null && isUnlocked(tab)) {
                machines[tab].tick(level);
            }
        }
        // Only left when no player took the tab's machines out (e.g. another mod did): it mustn't be lost.
        for (ItemStack stack : givenBack) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), stack);
        }
        givenBack.clear();
        if (viewsChanged) {
            viewsChanged = false;
            reopenChangedTabs();
        }
    }

    /** Menus showing a tab whose machines changed kind show what it holds now. */
    private void reopenChangedTabs() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (ServerPlayer player : List.copyOf(serverLevel.players())) {
            if (player.containerMenu instanceof HostTabs.Holder holder && holder.getHostTabs() != null
                    && holder.getHostTabs().host() == this
                    && holder.getHostTabs().shown() != machines[holder.getHostTabs().current()]) {
                openTab(player, holder.getHostTabs().current());
            }
        }
    }

    /** Whether a redstone pulse makes any tab's machines run (see {@link #runPulse}). */
    public boolean runsOnPulse() {
        for (int tab = 0; tab < TABS; tab++) {
            if (machines[tab] != null && isUnlocked(tab)
                    && machines[tab].getSettings().getRedstoneMode() == RedstoneMode.ON_POWERED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Server only, a tick after a redstone pulse: runs the machines of every tab in the "on powered" mode that aren't
     * cooling down, each tab playing its machine's sound (once per sound, however many tabs play it).
     */
    public void runPulse(ServerLevel level) {
        Set<Integer> sounds = new LinkedHashSet<>();
        for (int tab = 0; tab < TABS; tab++) {
            AutoMachineBlockEntity machine = machines[tab];
            if (machine != null && isUnlocked(tab) && machine.getSettings().getRedstoneMode() == RedstoneMode.ON_POWERED
                    && !machine.isCoolingDown()
                    && machine.getBlockState().getBlock() instanceof AutoMachineBlock block) {
                sounds.add(machine.attemptOperation() ? block.getSuccessSound() : block.getFailSound());
            }
        }
        sounds.forEach(sound -> level.levelEvent(sound, worldPosition, 0));
    }

    /** Server only: works out the lamps again, and sends them to clients if they changed. */
    private void updateLamps() {
        if (level != null && !level.isClientSide && computeLamps()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** @return whether any lamp changed */
    private boolean computeLamps() {
        boolean changed = false;
        for (int tab = 0; tab < TABS; tab++) {
            AutoMachineBlockEntity machine = machines[tab];
            byte lamp = !isUnlocked(tab) ? LAMP_LOCKED
                    : machine == null ? LAMP_OFF
                    : machine.isShownFailed() ? LAMP_FAILED
                    : machine.isShownRunning() ? LAMP_RUNNING
                    : LAMP_OFF;
            changed |= lamps[tab] != lamp;
            lamps[tab] = lamp;
        }
        return changed;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public void setLevel(@NotNull Level level) {
        super.setLevel(level);
        for (AutoMachineBlockEntity machine : machines) {
            if (machine != null) {
                machine.setLevel(level);
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        for (AutoMachineBlockEntity machine : machines) {
            if (machine != null) {
                machine.onLoad();
            }
        }
    }

    /** When the block is broken: its machines, and everything in them. */
    public void dropContents() {
        if (level == null) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>(givenBack);
        givenBack.clear();
        for (int tab = 0; tab < TABS; tab++) {
            drops.add(tabItems.getStackInSlot(tab));
            if (machines[tab] != null) {
                drops.addAll(machines[tab].takeContents());
            }
        }
        for (ItemStack stack : drops) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("tabs", tabItems.serializeNBT(registries));
        tag.putInt("selected_tab", selectedTab);
        tag.putInt("expansions", expansions);
        if (energy > 0) {
            tag.putInt("energy", energy);
        }
        ListTag saved = new ListTag();
        for (int tab = 0; tab < TABS; tab++) {
            if (machines[tab] != null) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("tab", tab);
                entry.put("machine", machines[tab].saveWithoutMetadata(registries));
                saved.add(entry);
            }
        }
        tag.put("machines", saved);
        if (!givenBack.isEmpty()) {
            ListTag stacks = new ListTag();
            givenBack.forEach(stack -> stacks.add(stack.save(registries)));
            tag.put("given_back", stacks);
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        // The client only gets the lamps (see getUpdateTag), never the machines.
        if (tag.contains("lamps")) {
            byte[] loaded = tag.getByteArray("lamps");
            System.arraycopy(loaded, 0, lamps, 0, Math.min(loaded.length, TABS));
        }
        if (!tag.contains("machines")) {
            return;
        }
        givenBack.clear();
        for (Tag saved : tag.getList("given_back", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, saved).ifPresent(givenBack::add);
        }
        expansions = Math.max(0, tag.getInt("expansions"));
        energy = Math.max(0, tag.getInt("energy"));
        tabItems.deserializeNBT(registries, tag.getCompound("tabs"));
        fitTabs();
        selectedTab = Math.clamp(tag.getInt("selected_tab"), 0, TABS - 1);
        for (int tab = 0; tab < TABS; tab++) {
            AutoMachineBlock kind = AutoMachineBlock.hostable(tabItems.getStackInSlot(tab));
            machines[tab] = kind == null ? null : createMachine(kind, tab);
        }
        for (Tag saved : tag.getList("machines", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) saved;
            int tab = entry.getInt("tab");
            if (tab >= 0 && tab < TABS && machines[tab] != null) {
                machines[tab].loadWithComponents(entry.getCompound("machine"), registries);
            }
        }
    }

    /** ItemStackHandler resizes itself to the slot count it was saved with: keep {@link #TABS}, giving back the rest. */
    private void fitTabs() {
        if (tabItems.getSlots() == TABS) {
            return;
        }
        List<ItemStack> loaded = new ArrayList<>();
        for (int i = 0; i < tabItems.getSlots(); i++) {
            loaded.add(tabItems.getStackInSlot(i));
        }
        tabItems.setSize(TABS);
        for (int i = 0; i < loaded.size(); i++) {
            if (i < TABS) {
                tabItems.setStackInSlot(i, loaded.get(i));
            } else if (!loaded.get(i).isEmpty()) {
                givenBack.add(loaded.get(i));
            }
        }
    }

    // The Hub Expansions used on it go with its item (see the loot table), and come back when it is placed.

    @Override
    protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
        super.applyImplicitComponents(input);
        expansions = input.getOrDefault(ModDataComponents.HUB_EXPANSIONS.get(), 0);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.@NotNull Builder components) {
        super.collectImplicitComponents(components);
        if (expansions > 0) {
            components.set(ModDataComponents.HUB_EXPANSIONS.get(), expansions);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(@NotNull CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("expansions");
    }

    /** Clients only need the lamps. */
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        if (level != null && !level.isClientSide) {
            computeLamps();
        }
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("lamps", lamps.clone());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
