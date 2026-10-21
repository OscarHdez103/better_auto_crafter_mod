package laseta.bac.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import laseta.bac.item.MultiTranslocationUpgradeItem;
import laseta.bac.item.StockingUpgradeItem;
import laseta.bac.item.StorageLinkUpgradeItem;
import laseta.bac.item.TranslocationUpgradeItem;
import laseta.bac.sideconfig.SideConfiguration;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.stock.StockReading;
import laseta.bac.stock.StorageLink;
import laseta.bac.upgrade.MultiTranslocation;
import laseta.bac.upgrade.Translocation;
import laseta.bac.upgrade.UpgradeInventory;
import laseta.bac.upgrade.UpgradeSlotType;
import laseta.bac.upgrade.UpgradeStatus;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An auto machine: on redstone, it pulls the items of its template grid from the inventories on its input faces, does
 * something with them ({@link #operate}) and pushes the outcome into the inventories on its output faces. Everything
 * else is shared by every machine: the template grid (any size), the side configuration, the settings, the upgrades
 * (translocation, stocking, storage link) and the redstone handling. A machine can also have its own
 * {@link MachineOption}s, shown in a tab of their own.
 *
 * <p>Deliberately exposes no item capability: hoppers, pipes and other blocks can't insert into or extract from it.
 * It only reads/writes its neighbours' inventories itself, when it runs.</p>
 *
 * <p>A new machine extends this (and {@link AutoMachineBlock}, {@link AutoMachineMenu} and the client's
 * {@code AutoMachineScreen}) and implements {@link #operate}, usually as one {@link ItemTransfer#run} call.</p>
 */
public abstract class AutoMachineBlockEntity extends BlockEntity implements MenuProvider {
    /** Ticks the failed state stays visible after a failed attempt (redstone works in steps of 2 game ticks, so 5 redstone ticks). */
    public static final int FAILED_DISPLAY_TICKS = 10;

    private final int gridWidth;
    private final int gridHeight;
    /** The template: what one operation pulls. Never consumed. Saved as "inventory", its name from before. */
    private final ItemStackHandler template;
    private final SideConfiguration sideConfig = new SideConfiguration(this::setChanged);
    private final MachineSettings settings = new MachineSettings(this::setChanged);
    private final MachineOptions options;
    private final UpgradeInventory upgrades = new UpgradeInventory(this::setChanged);
    /** Template items loaded into slots the grid no longer has (only if its size was ever reduced); dropped on load. */
    private final List<ItemStack> templateOrphans = new ArrayList<>();

    /** Game time of the last attempt in a continuous redstone mode (not saved: only spaces attempts out). */
    private long lastContinuousAttempt = -RedstoneMode.CONTINUOUS_INTERVAL;
    /** Game time at which the failed state is cleared again; -1 = not scheduled (not saved, see serverTick). */
    private long failedResetTime = -1;

    protected AutoMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int gridWidth, int gridHeight) {
        this(type, pos, state, gridWidth, gridHeight, List.of());
    }

    /** @param options this machine's own options, in the order its tab shows them */
    protected AutoMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int gridWidth, int gridHeight,
                                     List<MachineOption<?>> options) {
        super(type, pos, state);
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.options = new MachineOptions(options, this::setChanged);
        this.template = new ItemStackHandler(gridWidth * gridHeight) {
            @Override
            protected void onContentsChanged(int slot) {
                onTemplateChanged();
                setChanged();
            }
        };
    }

    /**
     * Server only. Does the machine's work once with the inventories the faces and upgrades resolved to.
     *
     * @return true if it did something
     */
    protected abstract boolean operate(ServerLevel level, ItemTransfer transfer);

    /** Cheap check whether an operation can work at all (e.g. the template is a valid recipe), before any inventory is looked up. */
    protected boolean canOperate() {
        return true;
    }

    /** Called whenever the template changes, e.g. to forget something computed from it. */
    protected void onTemplateChanged() {
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Parts
    // ---------------------------------------------------------------------------------------------------------------

    public ItemStackHandler getTemplate() {
        return template;
    }

    /** The template's stacks, one per slot, row by row. */
    public List<ItemStack> getTemplateStacks() {
        List<ItemStack> stacks = new ArrayList<>(template.getSlots());
        for (int i = 0; i < template.getSlots(); i++) {
            stacks.add(template.getStackInSlot(i));
        }
        return stacks;
    }

    public int getGridWidth() {
        return gridWidth;
    }

    public int getGridHeight() {
        return gridHeight;
    }

    public SideConfiguration getSideConfig() {
        return sideConfig;
    }

    public MachineSettings getSettings() {
        return settings;
    }

    public MachineOptions getOptions() {
        return options;
    }

    public UpgradeInventory getUpgrades() {
        return upgrades;
    }

    public Direction getFacing() {
        return getBlockState().getValue(AutoMachineBlock.FACING);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Upgrades
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Server only: where an installed translocation upgrade moves the faces to (or why it doesn't work). EMPTY for a
     * multi translocation upgrade, see {@link MultiTranslocation}.
     */
    public Translocation.Result resolveUpgrade(UpgradeSlotType type) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return new Translocation.Result(UpgradeStatus.EMPTY, null);
        }
        return Translocation.resolve(upgrades.get(type), serverLevel);
    }

    /** Server only: whether the upgrade in a slot works. Upgrades that need nothing from the world always do. */
    public UpgradeStatus getUpgradeStatus(UpgradeSlotType type) {
        ItemStack stack = upgrades.get(type);
        if (stack.isEmpty()) {
            return UpgradeStatus.EMPTY;
        }
        if (stack.getItem() instanceof TranslocationUpgradeItem) {
            return resolveUpgrade(type).status();
        }
        if (stack.getItem() instanceof MultiTranslocationUpgradeItem && level instanceof ServerLevel serverLevel) {
            return MultiTranslocation.resolve(stack, serverLevel).status();
        }
        if (stack.getItem() instanceof StorageLinkUpgradeItem) {
            return resolveStorageLink().status();
        }
        return UpgradeStatus.ACTIVE;
    }

    /** Server only: the storage an installed Storage Link Upgrade counts the stock in (or why it can't). */
    public StockReading resolveStorageLink() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return StockReading.of(UpgradeStatus.EMPTY);
        }
        return StorageLink.resolve(upgrades.get(UpgradeSlotType.STORAGE_LINK), serverLevel);
    }

    /**
     * How many of a product an output inventory (or the storage linked by a Storage Link Upgrade) may hold before
     * the machine stops pushing it there; 0 means no limit.
     */
    public int getStockLimit() {
        return StockingUpgradeItem.getStockLimit(upgrades.get(UpgradeSlotType.STOCKING));
    }

    /** Upgrade status per slot as menu data, so the upgrades tab can show problems. Computed on demand. */
    public ContainerData getUpgradeStatusData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return getUpgradeStatus(UpgradeSlotType.byIndex(index)).ordinal();
            }

            @Override
            public void set(int index, int value) {
                // Read-only: derived from the installed upgrades and the world.
            }

            @Override
            public int getCount() {
                return UpgradeSlotType.COUNT;
            }
        };
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Running
    // ---------------------------------------------------------------------------------------------------------------

    /** Server ticker: drives the continuous redstone modes (the rising-edge mode is handled by the block). */
    public static void serverTick(Level level, BlockState state, AutoMachineBlockEntity blockEntity) {
        blockEntity.tickFailedState(state, level.getGameTime());
        RedstoneMode mode = blockEntity.settings.getRedstoneMode();
        if (!mode.isContinuous() || !mode.shouldRunContinuously(state.getValue(AutoMachineBlock.POWERED))) {
            return;
        }
        long now = level.getGameTime();
        if (now - blockEntity.lastContinuousAttempt >= RedstoneMode.CONTINUOUS_INTERVAL) {
            blockEntity.lastContinuousAttempt = now;
            blockEntity.attemptOperation();
        }
    }

    /**
     * Server only. Runs the machine once. A failure is shown in the block state ({@link AutoMachineBlock#FAILED}) for
     * {@link #FAILED_DISPLAY_TICKS} ticks; a success clears it at once.
     *
     * @return true if the machine did something
     */
    public boolean attemptOperation() {
        boolean success = runOperation();
        if (level != null) {
            // Every failure (re)starts the timer, so a block that keeps failing stays in the failed state.
            failedResetTime = success ? -1 : level.getGameTime() + FAILED_DISPLAY_TICKS;
            setFailed(!success);
        }
        return success;
    }

    /** Clears the failed state once its time is up. */
    private void tickFailedState(BlockState state, long now) {
        if (!state.getValue(AutoMachineBlock.FAILED)) {
            return;
        }
        if (failedResetTime < 0) {
            // Failed state loaded from disk (the timer isn't saved): show it for the normal time from now on.
            failedResetTime = now + FAILED_DISPLAY_TICKS;
        } else if (now >= failedResetTime) {
            failedResetTime = -1;
            setFailed(false);
        }
    }

    /** Only changes the block state (and sends it to clients) when the value actually differs. */
    private void setFailed(boolean failed) {
        BlockState state = getBlockState();
        if (level != null && state.getValue(AutoMachineBlock.FAILED) != failed) {
            level.setBlock(worldPosition, state.setValue(AutoMachineBlock.FAILED, failed), Block.UPDATE_CLIENTS);
        }
    }

    /** Resolves the faces and upgrades into an {@link ItemTransfer} and lets the machine {@link #operate} with it. */
    private boolean runOperation() {
        if (!(level instanceof ServerLevel serverLevel) || !canOperate()) {
            return false;
        }
        // Translocation upgrades move the input / output faces to a Translocator, multi translocation upgrades replace
        // them with their linked blocks. An installed upgrade that doesn't work (not linked, Translocator unloaded or
        // gone, ...) stops the machine instead of silently using the local faces.
        MachineAnchor self = new MachineAnchor(serverLevel, worldPosition, getFacing());
        List<InventoryFace> inputs = facesFor(UpgradeSlotType.TRANSLOCATION_PULL, self, SideMode::canInput);
        List<InventoryFace> outputs = facesFor(UpgradeSlotType.TRANSLOCATION_PUSH, self, SideMode::canOutput);
        if (inputs == null || outputs == null) {
            return false;
        }
        // A Storage Link Upgrade makes the stocking upgrades count in the linked storage; a broken one stops it too.
        StockReading storageLink = resolveStorageLink();
        if (storageLink.status().isProblem()) {
            return false;
        }
        return operate(serverLevel, new ItemTransfer(serverLevel, worldPosition, inputs, outputs, settings,
                getStockLimit(), storageLink.counter()));
    }

    /**
     * The inventories on one side of the operation: the configured faces of the machine itself without an upgrade, of
     * the Translocator with a working translocation upgrade, the linked blocks with a working multi translocation
     * upgrade (ignoring the side configuration); null with a broken upgrade.
     */
    @Nullable
    private List<InventoryFace> facesFor(UpgradeSlotType type, MachineAnchor self, Predicate<SideMode> filter) {
        ItemStack upgrade = upgrades.get(type);
        if (upgrade.getItem() instanceof MultiTranslocationUpgradeItem) {
            MultiTranslocation.Result result = MultiTranslocation.resolve(upgrade, self.level());
            return result.status().isProblem() ? null : result.faces();
        }
        Translocation.Result result = resolveUpgrade(type);
        return switch (result.status()) {
            case EMPTY -> ItemTransfer.facesAround(self, sideConfig, filter);
            case ACTIVE -> ItemTransfer.facesAround(result.anchor(), sideConfig, filter);
            default -> null;
        };
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------------------------

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < template.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    template.getStackInSlot(i));
        }
        for (int i = 0; i < upgrades.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    upgrades.getStackInSlot(i));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Items saved in slots that no longer exist (only if slot types were ever removed or the grid shrunk) aren't lost.
        if (level != null && !level.isClientSide) {
            List<ItemStack> orphans = new ArrayList<>(upgrades.takeOrphans());
            orphans.addAll(templateOrphans);
            templateOrphans.clear();
            for (ItemStack orphan : orphans) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), orphan);
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", template.serializeNBT(registries));
        tag.put("side_config", sideConfig.save());
        tag.put("crafting_settings", settings.save());
        tag.put("machine_options", options.save());
        tag.put("upgrades", upgrades.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        template.deserializeNBT(registries, tag.getCompound("inventory"));
        fitTemplateToGrid();
        sideConfig.load(tag.getCompound("side_config"));
        settings.load(tag.getCompound("crafting_settings"));
        options.load(tag.getCompound("machine_options"));
        upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
        onTemplateChanged();
    }

    /** ItemStackHandler resizes itself to the slot count it was saved with: keep the grid's size instead. */
    private void fitTemplateToGrid() {
        int size = gridWidth * gridHeight;
        if (template.getSlots() == size) {
            return;
        }
        List<ItemStack> loaded = getTemplateStacks();
        template.setSize(size);
        for (int i = 0; i < loaded.size(); i++) {
            if (i < size) {
                template.setStackInSlot(i, loaded.get(i));
            } else if (!loaded.get(i).isEmpty()) {
                templateOrphans.add(loaded.get(i));
            }
        }
    }

    /**
     * What the client's menu needs before its first sync, written when the menu opens: the position (the screen looks
     * up the block's facing and face textures) and the grid's size (which the config can change, on the server).
     */
    public void writeMenuData(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(worldPosition);
        buffer.writeVarInt(gridWidth);
        buffer.writeVarInt(gridHeight);
    }

    /** "container.better_auto_crafter.&lt;block id&gt;", so every machine has its own GUI title. */
    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable(Util.makeDescriptionId("container", BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock())));
    }
}
