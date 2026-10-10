package laseta.bac.block.entity;

import java.util.ArrayList;
import java.util.List;

import laseta.bac.block.BetterAutoCrafterBlock;
import laseta.bac.crafting.CraftingSettings;
import laseta.bac.crafting.ExternalCraftingOperation;
import laseta.bac.crafting.RedstoneMode;
import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.registry.ModBlockEntities;
import laseta.bac.sideconfig.SideConfiguration;
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
 * Holds the 3x3 template grid and the block's configuration.
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
        List<ItemStack> template = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            template.add(inventory.getStackInSlot(i));
        }
        return ExternalCraftingOperation.tryCraft(serverLevel, worldPosition, getFacing(), sideConfig, craftingSettings,
                template, cachedRecipe.value());
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
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.put("side_config", sideConfig.save());
        tag.put("crafting_settings", craftingSettings.save());
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        sideConfig.load(tag.getCompound("side_config"));
        craftingSettings.load(tag.getCompound("crafting_settings"));
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
