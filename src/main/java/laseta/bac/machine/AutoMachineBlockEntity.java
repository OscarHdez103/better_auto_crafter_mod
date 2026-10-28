package laseta.bac.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import laseta.bac.item.CoprocessingUpgradeItem;
import laseta.bac.item.MultiTranslocationUpgradeItem;
import laseta.bac.item.SpeedUpgradeItem;
import laseta.bac.item.StockingUpgradeItem;
import laseta.bac.item.StorageLinkUpgradeItem;
import laseta.bac.item.TranslocationUpgradeItem;
import laseta.bac.sideconfig.SideConfiguration;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.stock.StockReading;
import laseta.bac.stock.StorageLink;
import laseta.bac.upgrade.MultiTranslocation;
import laseta.bac.upgrade.Translocation;
import laseta.bac.upgrade.TranslocationMode;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An auto machine: on redstone, it pulls the items of its template grid from the inventories on its input faces, does
 * something with them ({@link #operate}) and pushes the outcome into the inventories on its output faces. Everything
 * else is shared by every machine: the template grid (any size), the side configuration, the settings, the upgrades
 * (translocation, stocking, storage link) and the redstone handling. A machine can also have {@link MachineFeatures}
 * of its own (options, face channels and upgrade slots), shown in a tab of their own.
 *
 * <p>Every successful operation takes {@link #getCooldownTicks()} ticks (its base speed, see {@link #getBaseSpeed()}),
 * during which nothing makes it run again: redstone pulses are ignored and the continuous modes wait. The block shows
 * it as {@link AutoMachineBlock#POWERED}. Failed attempts take no time, so a machine waiting for items runs as soon as
 * they are there. See {@link MachineStatus}.</p>
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
    private final MachineFeatures features;
    private final MachineOptions options;
    private final ChannelFaces channelFaces;
    private final MachineUpgradeInventory machineUpgrades;
    private final UpgradeInventory upgrades = new UpgradeInventory(this::setChanged, this::getUpgradeLimit);
    private final MachineStatus status = new MachineStatus(this::setChanged);
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
        this(type, pos, state, gridWidth, gridHeight, MachineFeatures.ofOptions(options));
    }

    /** @param features what only this kind of machine has, shown in its own tab */
    protected AutoMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int gridWidth, int gridHeight,
                                     MachineFeatures features) {
        super(type, pos, state);
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.features = features;
        this.options = new MachineOptions(features.options(), this::setChanged);
        this.channelFaces = new ChannelFaces(features.channels(), this::setChanged);
        this.machineUpgrades = new MachineUpgradeInventory(features.upgradeSlots(), () -> {
            onMachineUpgradesChanged();
            setChanged();
        });
        this.template = new ItemStackHandler(gridWidth * gridHeight) {
            @Override
            public int getSlotLimit(int slot) {
                return getTemplateSlotLimit();
            }

            @Override
            protected void onContentsChanged(int slot) {
                // The last attempt's problem may not apply to the new template.
                status.clearLastProblem();
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

    /**
     * Cheap check whether the template can work at all (e.g. it is a valid recipe), before any inventory is looked up.
     * Also shown in the GUI while it applies, before the machine even tries.
     *
     * @return why it can't, or null if it can. By default only an empty template can't.
     */
    @Nullable
    protected MachineProblem templateProblem() {
        for (int i = 0; i < template.getSlots(); i++) {
            if (!template.getStackInSlot(i).isEmpty()) {
                return null;
            }
        }
        return MachineProblem.EMPTY_TEMPLATE;
    }

    /** The machine's base speed: ticks each operation takes, before any upgrade. Usually from its config section. */
    protected abstract int getBaseSpeed();

    /** Ticks after a successful operation before the machine may run again: its base speed, less the speed upgrades'. */
    protected int getCooldownTicks() {
        return SpeedUpgradeItem.getOperationTicks(getBaseSpeed(), upgrades.get(UpgradeSlotType.SPEED).getCount());
    }

    /** Called whenever the template changes, e.g. to forget something computed from it. */
    protected void onTemplateChanged() {
    }

    /** Called whenever the machine's own upgrade slots change, e.g. to forget something computed from them. */
    protected void onMachineUpgradesChanged() {
    }

    /**
     * Server only: the recipes the template could make and which one the machine makes, for the GUI to offer the others
     * when there are several. None by default (machines without recipes).
     */
    public RecipeChoices getRecipeChoices() {
        return RecipeChoices.NONE;
    }

    /**
     * Server only: makes the machine use the recipe at {@code index} of {@link #getRecipeChoices()} from now on.
     *
     * @return false if there is no such choice
     */
    public boolean chooseRecipe(int index) {
        return false;
    }

    /**
     * Server only: every recipe of {@code type} matching {@code input}, the one the recipe manager picks first (what a
     * vanilla block would make) and then the others.
     */
    protected <I extends RecipeInput, R extends Recipe<I>> List<RecipeHolder<R>> matchingRecipes(RecipeType<R> type, I input) {
        if (level == null) {
            return List.of();
        }
        List<RecipeHolder<R>> matching = new ArrayList<>(level.getRecipeManager().getRecipesFor(type, input, level));
        level.getRecipeManager().getRecipeFor(type, input, level).ifPresent(first -> {
            matching.remove(first);
            matching.addFirst(first);
        });
        return matching;
    }

    /**
     * Server only: makes the machine use the recipe with this id, if it is one of {@link #getRecipeChoices()} (e.g. the
     * one filled in from JEI / EMI).
     *
     * @return false if it isn't
     */
    public boolean chooseRecipe(ResourceLocation id) {
        return false;
    }

    /** Call when the chosen recipe changed: it is worked out again like after a template change. */
    protected void onRecipeChosen() {
        status.clearLastProblem();
        onTemplateChanged();
        setChanged();
    }

    /** How many items a template slot holds. Machines whose template only says which items it takes can make it 1. */
    protected int getTemplateSlotLimit() {
        return Item.ABSOLUTE_MAX_STACK_SIZE;
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

    public MachineFeatures getFeatures() {
        return features;
    }

    public MachineOptions getOptions() {
        return options;
    }

    public ChannelFaces getChannelFaces() {
        return channelFaces;
    }

    public MachineUpgradeInventory getMachineUpgrades() {
        return machineUpgrades;
    }

    public UpgradeInventory getUpgrades() {
        return upgrades;
    }

    public MachineStatus getStatus() {
        return status;
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
     * How many upgrades a slot of this machine holds: the slot's own limit, except speed upgrades, which stop where
     * they would no longer make this machine faster.
     */
    public int getUpgradeLimit(UpgradeSlotType type) {
        return type == UpgradeSlotType.SPEED ? SpeedUpgradeItem.getUsefulCount(getBaseSpeed()) : type.getMaxStackSize();
    }

    /**
     * How many of a product an output inventory (or the storage linked by a Storage Link Upgrade) may hold before
     * the machine stops pushing it there; 0 means no limit.
     */
    public int getStockLimit() {
        return StockingUpgradeItem.getStockLimit(upgrades.get(UpgradeSlotType.STOCKING));
    }

    /**
     * Server only: the inventories on the faces that are on for one of this machine's channels, around the Translocator
     * when a working translocation upgrade of the channel's direction is installed (see {@link FaceChannel}).
     */
    public List<IItemHandler> getChannelInventories(ServerLevel level, FaceChannel channel) {
        MachineAnchor anchor = new MachineAnchor(level, worldPosition, getFacing());
        Translocation.Result translocation = resolveUpgrade(channel.direction() == TranslocationMode.PULL
                ? UpgradeSlotType.TRANSLOCATION_PULL : UpgradeSlotType.TRANSLOCATION_PUSH);
        if (translocation.status() == UpgradeStatus.ACTIVE && translocation.anchor() != null) {
            anchor = translocation.anchor();
        }
        return ItemTransfer.facesAround(anchor, side -> channelFaces.isOn(channel, side)).stream()
                .map(InventoryFace::handler)
                .toList();
    }

    /** Server only: why the machine can't run right now, whatever the inputs hold; null if nothing stops it. */
    @Nullable
    public MachineProblem findProblem() {
        MachineProblem problem = templateProblem();
        if (problem != null) {
            return problem;
        }
        for (UpgradeSlotType type : UpgradeSlotType.values()) {
            if (getUpgradeStatus(type).isProblem()) {
                return MachineProblem.BROKEN_UPGRADE;
            }
        }
        return null;
    }

    /**
     * The status icon's data (see {@link MachineStatus}'s DATA_* indices) as menu data. The problem is the one that
     * stops the machine right now if there is one, otherwise the last attempt's. Computed on demand.
     */
    public ContainerData getStatusData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case MachineStatus.DATA_COOLDOWN_LEFT -> level == null ? 0 : status.getCooldownLeft(level.getGameTime());
                    case MachineStatus.DATA_COOLDOWN_TOTAL -> status.getCooldownTotal();
                    case MachineStatus.DATA_PROBLEM -> {
                        MachineProblem problem = findProblem();
                        yield MachineProblem.toId(problem != null ? problem : status.getLastProblem());
                    }
                    case MachineStatus.DATA_SIGNAL -> getBlockState().getValue(AutoMachineBlock.TRIGGERED) ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Read-only: derived from the machine's state.
            }

            @Override
            public int getCount() {
                return MachineStatus.DATA_COUNT;
            }
        };
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
        // Also catches a cooldown that ended or was loaded, not only the ones attemptOperation starts.
        boolean coolingDown = blockEntity.isCoolingDown();
        blockEntity.setStateFlag(AutoMachineBlock.POWERED, coolingDown);
        RedstoneMode mode = blockEntity.settings.getRedstoneMode();
        if (!mode.isContinuous() || !mode.shouldRunContinuously(state.getValue(AutoMachineBlock.TRIGGERED)) || coolingDown) {
            return;
        }
        long now = level.getGameTime();
        if (now - blockEntity.lastContinuousAttempt >= RedstoneMode.CONTINUOUS_INTERVAL) {
            blockEntity.lastContinuousAttempt = now;
            blockEntity.attemptOperation();
        }
    }

    /** Whether the machine is in the cooldown after an operation, so nothing may make it run. */
    public boolean isCoolingDown() {
        return level != null && status.isCoolingDown(level.getGameTime());
    }

    /**
     * Server only. Runs the machine once, unless it is cooling down. A success starts the cooldown, shown in the block
     * state ({@link AutoMachineBlock#POWERED}) until it ends; a failure is shown ({@link AutoMachineBlock#FAILED}) for
     * {@link #FAILED_DISPLAY_TICKS} ticks, and a success or having nothing to do (see {@link MachineProblem#isFailure()})
     * clears it at once.
     *
     * @return true if the machine did something
     */
    public boolean attemptOperation() {
        if (!(level instanceof ServerLevel serverLevel) || isCoolingDown()) {
            return false;
        }
        MachineProblem problem = runOperation(serverLevel);
        long now = level.getGameTime();
        status.finishAttempt(now, problem, getCooldownTicks());
        boolean failed = problem != null && problem.isFailure();
        // Every failure (re)starts the timer, so a block that keeps failing stays in the failed state.
        failedResetTime = failed ? now + FAILED_DISPLAY_TICKS : -1;
        setStateFlag(AutoMachineBlock.FAILED, failed);
        setStateFlag(AutoMachineBlock.POWERED, isCoolingDown());
        return problem == null;
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
            setStateFlag(AutoMachineBlock.FAILED, false);
        }
    }

    /** Only changes the block state (and sends it to clients) when the value actually differs. */
    private void setStateFlag(BooleanProperty property, boolean value) {
        BlockState state = getBlockState();
        if (level != null && state.getValue(property) != value) {
            level.setBlock(worldPosition, state.setValue(property, value), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Resolves the faces and upgrades and lets the machine {@link #operate} with them, once plus once per coprocessing
     * upgrade. Those repeats stop at the first one that fails, so a machine with nothing to do costs one operation.
     *
     * @return why nothing happened, or null if at least one operation did
     */
    @Nullable
    private MachineProblem runOperation(ServerLevel serverLevel) {
        MachineProblem templateProblem = templateProblem();
        if (templateProblem != null) {
            return templateProblem;
        }
        // Translocation upgrades move the input / output faces to a Translocator, multi translocation upgrades replace
        // them with their linked blocks. An installed upgrade that doesn't work (not linked, Translocator unloaded or
        // gone, ...) stops the machine instead of silently using the local faces.
        MachineAnchor self = new MachineAnchor(serverLevel, worldPosition, getFacing());
        List<InventoryFace> inputs = facesFor(UpgradeSlotType.TRANSLOCATION_PULL, self, SideMode::canInput);
        List<InventoryFace> outputs = facesFor(UpgradeSlotType.TRANSLOCATION_PUSH, self, SideMode::canOutput);
        if (inputs == null || outputs == null) {
            return MachineProblem.BROKEN_UPGRADE;
        }
        // A Storage Link Upgrade makes the stocking upgrades count in the linked storage; a broken one stops it too.
        StockReading storageLink = resolveStorageLink();
        if (storageLink.status().isProblem()) {
            return MachineProblem.BROKEN_UPGRADE;
        }
        int operations = CoprocessingUpgradeItem.getOperations(upgrades.get(UpgradeSlotType.COPROCESSING));
        int stockLimit = getStockLimit();
        for (int i = 0; i < operations; i++) {
            // A new transfer for each one: it orders the faces by the round robin, which moves on after each.
            ItemTransfer transfer = new ItemTransfer(serverLevel, worldPosition, inputs, outputs, settings,
                    stockLimit, storageLink.counter());
            if (!operate(serverLevel, transfer)) {
                if (i > 0) {
                    return null;
                }
                return transfer.getProblem() != null ? transfer.getProblem() : MachineProblem.CANNOT_PROCESS;
            }
        }
        return null;
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
        for (int i = 0; i < machineUpgrades.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    machineUpgrades.getStackInSlot(i));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Items saved in slots that no longer exist (only if slot types were ever removed or the grid shrunk) aren't lost.
        if (level != null && !level.isClientSide) {
            List<ItemStack> orphans = new ArrayList<>(upgrades.takeOrphans());
            orphans.addAll(machineUpgrades.takeOrphans());
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
        if (!features.channels().isEmpty()) {
            tag.put("channel_faces", channelFaces.save());
        }
        if (!features.upgradeSlots().isEmpty()) {
            tag.put("machine_upgrades", machineUpgrades.serializeNBT(registries));
        }
        if (level != null) {
            tag.put("status", status.save(level.getGameTime()));
        }
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
        channelFaces.load(tag.getCompound("channel_faces"));
        if (tag.contains("machine_upgrades")) {
            machineUpgrades.deserializeNBT(registries, tag.getCompound("machine_upgrades"));
        }
        status.load(tag.getCompound("status"));
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
     * up the block's facing and face textures), the grid's size and the upgrade slots' limits (which the config can
     * change, on the server).
     */
    public void writeMenuData(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(worldPosition);
        buffer.writeVarInt(gridWidth);
        buffer.writeVarInt(gridHeight);
        for (UpgradeSlotType type : UpgradeSlotType.values()) {
            buffer.writeVarInt(getUpgradeLimit(type));
        }
    }

    /** "container.better_auto_crafter.&lt;block id&gt;", so every machine has its own GUI title. */
    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable(Util.makeDescriptionId("container", BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock())));
    }
}
