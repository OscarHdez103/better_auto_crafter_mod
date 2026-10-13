package laseta.bac.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import laseta.bac.block.BetterAutoCrafterBlock;
import laseta.bac.crafting.CraftingAnchor;
import laseta.bac.crafting.CraftingSettings;
import laseta.bac.crafting.ExternalCraftingOperation;
import laseta.bac.crafting.InventoryFace;
import laseta.bac.crafting.RedstoneMode;
import laseta.bac.item.MultiTranslocationUpgradeItem;
import laseta.bac.item.StockingUpgradeItem;
import laseta.bac.item.StorageLinkUpgradeItem;
import laseta.bac.stock.StockReading;
import laseta.bac.stock.StorageLink;
import laseta.bac.item.TranslocationUpgradeItem;
import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.registry.ModBlockEntities;
import laseta.bac.sideconfig.SideConfiguration;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.upgrade.MultiTranslocation;
import laseta.bac.upgrade.Translocation;
import laseta.bac.upgrade.UpgradeInventory;
import laseta.bac.upgrade.UpgradeSlotType;
import laseta.bac.upgrade.UpgradeStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the 3x3 template grid, the upgrades and the block's configuration.
 * Deliberately exposes no item capability: hoppers, pipes and other blocks can't insert into or extract from it.
 * It only reads/writes its neighbours' inventories itself, when crafting.
 */
public class BetterAutoCrafterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int GRID_WIDTH = 3;
    public static final int GRID_HEIGHT = 3;
    public static final int GRID_SIZE = GRID_WIDTH * GRID_HEIGHT;

    private final ItemStackHandler inventory = new ItemStackHandler(GRID_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            resultDirty = true;
            setChanged();
        }
    };

    private final SideConfiguration sideConfig = new SideConfiguration(this::setChanged);
    private final CraftingSettings craftingSettings = new CraftingSettings(this::setChanged);
    private final UpgradeInventory upgrades = new UpgradeInventory(this::setChanged);

    @Nullable
    private RecipeHolder<CraftingRecipe> cachedRecipe;
    private ItemStack cachedResult = ItemStack.EMPTY;
    private boolean resultDirty = true;
    /** Game time of the last attempt in a continuous redstone mode (not saved: only spaces attempts out). */
    private long lastContinuousAttempt = -RedstoneMode.CONTINUOUS_INTERVAL;
    /** Ticks the failed state stays visible after a failed attempt (redstone works in steps of 2 game ticks, so 5 redstone ticks). */
    public static final int FAILED_DISPLAY_TICKS = 10;
    /** Game time at which the failed state is cleared again; -1 = not scheduled (not saved, see serverTick). */
    private long failedResetTime = -1;

    public BetterAutoCrafterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BETTER_AUTO_CRAFTER.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public SideConfiguration getSideConfig() {
        return sideConfig;
    }

    public CraftingSettings getCraftingSettings() {
        return craftingSettings;
    }

    public UpgradeInventory getUpgrades() {
        return upgrades;
    }

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
     * How many of the result an output inventory (or the storage linked by a Storage Link Upgrade) may hold before
     * the crafter stops crafting into it; 0 means no limit.
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

    public Direction getFacing() {
        return getBlockState().getValue(BetterAutoCrafterBlock.FACING);
    }

    /**
     * Returns the item the current 3x3 grid would craft. Looks the recipe up through the
     * recipe manager, so any crafting recipe (vanilla, datapack or modded) is supported.
     * The result is cached and only recomputed when the grid changes.
     */
    public ItemStack getCraftingResult() {
        if (level == null || level.isClientSide) {
            return ItemStack.EMPTY;
        }
        updateRecipeCache();
        return cachedResult;
    }

    private void updateRecipeCache() {
        if (!resultDirty || level == null) {
            return;
        }
        resultDirty = false;
        CraftingInput input = createCraftingInput();
        cachedRecipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
        cachedResult = cachedRecipe == null ? ItemStack.EMPTY : cachedRecipe.value().assemble(input, level.registryAccess());
    }

    /** Server ticker: drives the continuous redstone modes (the rising-edge mode is handled by the block). */
    public static void serverTick(Level level, BlockPos pos, BlockState state, BetterAutoCrafterBlockEntity blockEntity) {
        blockEntity.tickFailedState(state, level.getGameTime());
        RedstoneMode mode = blockEntity.craftingSettings.getRedstoneMode();
        if (!mode.isContinuous() || !mode.shouldRunContinuously(state.getValue(BetterAutoCrafterBlock.POWERED))) {
            return;
        }
        long now = level.getGameTime();
        if (now - blockEntity.lastContinuousAttempt >= RedstoneMode.CONTINUOUS_INTERVAL) {
            blockEntity.lastContinuousAttempt = now;
            blockEntity.attemptCraft();
        }
    }

    /**
     * Server only. Tries one craft from the neighbouring inventories. A failure is shown in the block state
     * ({@link BetterAutoCrafterBlock#FAILED}) for {@link #FAILED_DISPLAY_TICKS} ticks; a success clears it at once.
     *
     * @return true if something was crafted
     */
    public boolean attemptCraft() {
        boolean crafted = craftFromNeighbours();
        if (level != null) {
            // Every failure (re)starts the timer, so a block that keeps failing stays in the failed state.
            failedResetTime = crafted ? -1 : level.getGameTime() + FAILED_DISPLAY_TICKS;
            setFailed(!crafted);
        }
        return crafted;
    }

    /** Clears the failed state once its time is up. */
    private void tickFailedState(BlockState state, long now) {
        if (!state.getValue(BetterAutoCrafterBlock.FAILED)) {
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
        if (level != null && state.getValue(BetterAutoCrafterBlock.FAILED) != failed) {
            level.setBlock(worldPosition, state.setValue(BetterAutoCrafterBlock.FAILED, failed), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Server only. Crafts the grid's recipe once using items from the input faces' inventories and puts the result
     * in an output face's inventory. The grid itself is never consumed: it is only the template.
     *
     * @return true if something was crafted
     */
    private boolean craftFromNeighbours() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        updateRecipeCache();
        if (cachedRecipe == null) {
            return false;
        }
        // Translocation upgrades move the input / output faces to a Translocator, multi translocation upgrades replace
        // them with their linked blocks. An installed upgrade that doesn't work (not linked, Translocator unloaded or
        // gone, ...) stops crafting instead of silently using the local faces.
        CraftingAnchor self = new CraftingAnchor(serverLevel, worldPosition, getFacing());
        List<InventoryFace> inputs = facesFor(UpgradeSlotType.TRANSLOCATION_PULL, self, SideMode::canInput);
        List<InventoryFace> outputs = facesFor(UpgradeSlotType.TRANSLOCATION_PUSH, self, SideMode::canOutput);
        if (inputs == null || outputs == null) {
            return false;
        }
        // A Storage Link Upgrade makes the stocking upgrades count in the linked storage; a broken one stops crafting too.
        StockReading storageLink = resolveStorageLink();
        if (storageLink.status().isProblem()) {
            return false;
        }
        List<ItemStack> template = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            template.add(inventory.getStackInSlot(i));
        }
        return ExternalCraftingOperation.tryCraft(serverLevel, worldPosition, inputs, outputs, craftingSettings,
                getStockLimit(), storageLink.counter(), template, cachedRecipe.value());
    }

    /**
     * The inventories on one side of the craft: the configured faces of the crafter itself without an upgrade, of the
     * Translocator with a working translocation upgrade, the linked blocks with a working multi translocation upgrade
     * (ignoring the side configuration); null with a broken upgrade.
     */
    @Nullable
    private List<InventoryFace> facesFor(UpgradeSlotType type, CraftingAnchor self, Predicate<SideMode> filter) {
        ItemStack upgrade = upgrades.get(type);
        if (upgrade.getItem() instanceof MultiTranslocationUpgradeItem) {
            MultiTranslocation.Result result = MultiTranslocation.resolve(upgrade, self.level());
            return result.status().isProblem() ? null : result.faces();
        }
        Translocation.Result result = resolveUpgrade(type);
        return switch (result.status()) {
            case EMPTY -> ExternalCraftingOperation.facesAround(self, sideConfig, filter);
            case ACTIVE -> ExternalCraftingOperation.facesAround(result.anchor(), sideConfig, filter);
            default -> null;
        };
    }

    private CraftingInput createCraftingInput() {
        List<ItemStack> items = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            items.add(inventory.getStackInSlot(i));
        }
        return CraftingInput.of(GRID_WIDTH, GRID_HEIGHT, items);
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < inventory.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    inventory.getStackInSlot(i));
        }
        for (int i = 0; i < upgrades.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    upgrades.getStackInSlot(i));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Upgrades saved in slots that no longer exist (only if slot types were ever removed) aren't lost.
        if (level != null && !level.isClientSide) {
            for (ItemStack orphan : upgrades.takeOrphans()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), orphan);
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.put("side_config", sideConfig.save());
        tag.put("crafting_settings", craftingSettings.save());
        tag.put("upgrades", upgrades.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        sideConfig.load(tag.getCompound("side_config"));
        craftingSettings.load(tag.getCompound("crafting_settings"));
        upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
        resultDirty = true;
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.better_auto_crafter.better_auto_crafter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        return new BetterAutoCrafterMenu(containerId, playerInventory, this);
    }
}
