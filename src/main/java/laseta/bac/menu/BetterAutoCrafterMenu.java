package laseta.bac.menu;

import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.crafting.CraftingSettings;
import laseta.bac.crafting.RedstoneMode;
import laseta.bac.crafting.TransferPriority;
import laseta.bac.menu.slot.ResultPreviewSlot;
import laseta.bac.menu.slot.UpgradeSlot;
import laseta.bac.menu.transfer.GridFillPlanner;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModMenuTypes;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.upgrade.UpgradeInventory;
import laseta.bac.upgrade.UpgradeLayout;
import laseta.bac.upgrade.UpgradeSlotType;
import laseta.bac.upgrade.UpgradeStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BetterAutoCrafterMenu extends AbstractContainerMenu {
    // Slot index layout
    private static final int GRID_START = 0;
    private static final int GRID_END = GRID_START + BetterAutoCrafterBlockEntity.GRID_SIZE; // exclusive
    private static final int RESULT_SLOT = GRID_END;
    private static final int PLAYER_INV_START = RESULT_SLOT + 1;
    private static final int PLAYER_INV_END = PLAYER_INV_START + 36; // exclusive
    // Upgrade slots come last so the indices above never change (recipe viewers rely on them).
    private static final int UPGRADES_START = PLAYER_INV_END;
    private static final int UPGRADES_END = UPGRADES_START + UpgradeSlotType.COUNT; // exclusive

    // Menu button ids (sent with vanilla's ServerboundContainerButtonClickPacket):
    // [0, 6) next mode, [6, 12) previous mode, [12, 18) reset to NONE. Offset by RelativeSide ordinal.
    // Then one id per crafting setting action.
    private static final int BUTTON_NEXT = 0;
    private static final int BUTTON_PREVIOUS = RelativeSide.COUNT;
    private static final int BUTTON_RESET = RelativeSide.COUNT * 2;
    private static final int SIDE_BUTTON_COUNT = RelativeSide.COUNT * 3;
    public static final int BUTTON_TOGGLE_MATCH_NBT = SIDE_BUTTON_COUNT;
    public static final int BUTTON_INPUT_PRIORITY_NEXT = SIDE_BUTTON_COUNT + 1;
    public static final int BUTTON_INPUT_PRIORITY_PREVIOUS = SIDE_BUTTON_COUNT + 2;
    public static final int BUTTON_OUTPUT_PRIORITY_NEXT = SIDE_BUTTON_COUNT + 3;
    public static final int BUTTON_OUTPUT_PRIORITY_PREVIOUS = SIDE_BUTTON_COUNT + 4;
    public static final int BUTTON_REDSTONE_MODE_NEXT = SIDE_BUTTON_COUNT + 5;
    public static final int BUTTON_REDSTONE_MODE_PREVIOUS = SIDE_BUTTON_COUNT + 6;
    private static final int BUTTON_COUNT = SIDE_BUTTON_COUNT + 7;

    @Nullable
    private final BetterAutoCrafterBlockEntity blockEntity;
    private final BlockPos blockPos;
    private final ContainerLevelAccess access;
    private final SimpleContainer resultContainer = new SimpleContainer(1);
    /** One data slot per RelativeSide holding a SideMode ordinal. Backed by the block entity on the server. */
    private final ContainerData sideModes;
    /** CraftingSettings values (see CraftingSettings.DATA_*). Backed by the block entity on the server. */
    private final ContainerData settings;
    /** UpgradeStatus per upgrade slot, computed by the block entity on the server. */
    private final ContainerData upgradeStatus;
    /** Client only: set by the screen while the upgrades tab is open, which shows the upgrade slots. */
    private boolean upgradeSlotsVisible;

    /** Client-side constructor (called by the MenuType with the data written in {@code openMenu}). */
    public BetterAutoCrafterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, new ItemStackHandler(BetterAutoCrafterBlockEntity.GRID_SIZE),
                new UpgradeInventory(() -> {
                }),
                new SimpleContainerData(RelativeSide.COUNT), new SimpleContainerData(CraftingSettings.DATA_COUNT),
                new SimpleContainerData(UpgradeSlotType.COUNT), extraData.readBlockPos(), ContainerLevelAccess.NULL, null);
    }

    /** Server-side constructor (called by the block entity). */
    public BetterAutoCrafterMenu(int containerId, Inventory playerInventory, BetterAutoCrafterBlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity.getInventory(), blockEntity.getUpgrades(),
                blockEntity.getSideConfig().asContainerData(), blockEntity.getCraftingSettings().asContainerData(),
                blockEntity.getUpgradeStatusData(), blockEntity.getBlockPos(),
                ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), blockEntity.getBlockPos()), blockEntity);
    }

    private BetterAutoCrafterMenu(int containerId, Inventory playerInventory, IItemHandler grid, IItemHandler upgrades,
                                  ContainerData sideModes, ContainerData settings, ContainerData upgradeStatus,
                                  BlockPos blockPos, ContainerLevelAccess access,
                                  @Nullable BetterAutoCrafterBlockEntity blockEntity) {
        super(ModMenuTypes.BETTER_AUTO_CRAFTER.get(), containerId);
        checkContainerDataCount(sideModes, RelativeSide.COUNT);
        checkContainerDataCount(settings, CraftingSettings.DATA_COUNT);
        checkContainerDataCount(upgradeStatus, UpgradeSlotType.COUNT);
        this.access = access;
        this.blockEntity = blockEntity;
        this.blockPos = blockPos;
        this.sideModes = sideModes;
        this.settings = settings;
        this.upgradeStatus = upgradeStatus;
        // The server has no screen: there the slots are always "visible" (it doesn't use positions anyway).
        this.upgradeSlotsVisible = blockEntity != null;
        addDataSlots(sideModes);
        addDataSlots(settings);
        addDataSlots(upgradeStatus);

        // 3x3 input grid
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotItemHandler(grid, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }

        // Display-only result slot
        addSlot(new ResultPreviewSlot(resultContainer, 0, 124, 35));

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Player hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        // Upgrade slots, in the upgrades tab on the left of the GUI
        for (UpgradeSlotType type : UpgradeSlotType.values()) {
            addSlot(new UpgradeSlot(upgrades, type, UpgradeLayout.slotX(type.ordinal()), UpgradeLayout.slotY(type.ordinal()),
                    () -> upgradeSlotsVisible));
        }
    }

    public void setUpgradeSlotsVisible(boolean visible) {
        this.upgradeSlotsVisible = visible;
    }

    public UpgradeStatus getUpgradeStatus(UpgradeSlotType type) {
        return UpgradeStatus.byId(upgradeStatus.get(type.ordinal()));
    }

    public Slot getUpgradeSlot(UpgradeSlotType type) {
        return slots.get(UPGRADES_START + type.ordinal());
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public SideMode getSideMode(RelativeSide side) {
        return SideMode.byId(sideModes.get(side.ordinal()));
    }

    public boolean isMatchNbt() {
        return settings.get(CraftingSettings.DATA_MATCH_NBT) != 0;
    }

    public TransferPriority getInputPriority() {
        return TransferPriority.byId(settings.get(CraftingSettings.DATA_INPUT_PRIORITY));
    }

    public TransferPriority getOutputPriority() {
        return TransferPriority.byId(settings.get(CraftingSettings.DATA_OUTPUT_PRIORITY));
    }

    public RedstoneMode getRedstoneMode() {
        return RedstoneMode.byId(settings.get(CraftingSettings.DATA_REDSTONE_MODE));
    }

    public static int nextModeButton(RelativeSide side) {
        return BUTTON_NEXT + side.ordinal();
    }

    public static int previousModeButton(RelativeSide side) {
        return BUTTON_PREVIOUS + side.ordinal();
    }

    public static int resetModeButton(RelativeSide side) {
        return BUTTON_RESET + side.ordinal();
    }

    /**
     * Called on the server when the client sends a button click, and on the client for an instant (predicted) update.
     * On the server, writing to {@link #sideModes} / {@link #settings} writes straight into the block entity.
     */
    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (id < 0 || id >= BUTTON_COUNT) {
            return false;
        }
        if (id >= SIDE_BUTTON_COUNT) {
            if (id == BUTTON_TOGGLE_MATCH_NBT) {
                settings.set(CraftingSettings.DATA_MATCH_NBT, isMatchNbt() ? 0 : 1);
            } else if (id == BUTTON_INPUT_PRIORITY_NEXT) {
                settings.set(CraftingSettings.DATA_INPUT_PRIORITY, getInputPriority().next().ordinal());
            } else if (id == BUTTON_INPUT_PRIORITY_PREVIOUS) {
                settings.set(CraftingSettings.DATA_INPUT_PRIORITY, getInputPriority().previous().ordinal());
            } else if (id == BUTTON_OUTPUT_PRIORITY_NEXT) {
                settings.set(CraftingSettings.DATA_OUTPUT_PRIORITY, getOutputPriority().next().ordinal());
            } else if (id == BUTTON_OUTPUT_PRIORITY_PREVIOUS) {
                settings.set(CraftingSettings.DATA_OUTPUT_PRIORITY, getOutputPriority().previous().ordinal());
            } else if (id == BUTTON_REDSTONE_MODE_NEXT) {
                settings.set(CraftingSettings.DATA_REDSTONE_MODE, getRedstoneMode().next().ordinal());
            } else {
                settings.set(CraftingSettings.DATA_REDSTONE_MODE, getRedstoneMode().previous().ordinal());
            }
            return true;
        }
        RelativeSide side = RelativeSide.byId(id % RelativeSide.COUNT);
        SideMode current = getSideMode(side);
        SideMode updated;
        if (id < BUTTON_PREVIOUS) {
            updated = current.next();
        } else if (id < BUTTON_RESET) {
            updated = current.previous();
        } else {
            updated = SideMode.NONE;
        }
        sideModes.set(side.ordinal(), updated.ordinal());
        return true;
    }

    /** Plans filling the grid with a recipe from the player's inventory. Pure: changes nothing. Works on both sides. */
    public GridFillPlanner.Plan planFill(Player player, List<List<ItemStack>> wanted, boolean fillMaximum) {
        List<ItemStack> grid = new ArrayList<>(BetterAutoCrafterBlockEntity.GRID_SIZE);
        for (int i = GRID_START; i < GRID_END; i++) {
            grid.add(slots.get(i).getItem());
        }
        return GridFillPlanner.plan(wanted, grid, player.getInventory().items, fillMaximum);
    }

    /** Server side: fills the grid (recipe transfer from JEI / EMI). Does nothing if the plan isn't valid. */
    public void fillGrid(Player player, List<List<ItemStack>> wanted, boolean fillMaximum) {
        GridFillPlanner.Plan plan = planFill(player, wanted, fillMaximum);
        if (!plan.canApply()) {
            return;
        }
        for (int i = 0; i < BetterAutoCrafterBlockEntity.GRID_SIZE; i++) {
            slots.get(GRID_START + i).set(plan.grid().get(i));
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            inventory.items.set(i, plan.inventory().get(i));
        }
        inventory.setChanged();
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        // Server only: refresh the preview before syncing slots to the client.
        if (blockEntity != null) {
            resultContainer.setItem(0, blockEntity.getCraftingResult().copy());
        }
        super.broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
        // Ignore every interaction with the preview slot (pickup, swap, clone, throw, ...).
        if (slotId == RESULT_SLOT) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean canTakeItemForPickAll(@NotNull ItemStack stack, @NotNull Slot slot) {
        return !(slot instanceof ResultPreviewSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(@NotNull Slot slot) {
        return !(slot instanceof ResultPreviewSlot) && super.canDragTo(slot);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slots.get(index);
        if (index == RESULT_SLOT || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < GRID_END || index >= UPGRADES_START) {
            // Grid or upgrade -> player inventory
            if (!moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inventory -> one item into its upgrade slot if it's an upgrade and there is room, otherwise -> grid
            if (moveOneToUpgrades(stack)) {
                if (stack.isEmpty()) {
                    slot.setByPlayer(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
                // EMPTY stops vanilla's quick-move loop, so each shift-click installs exactly one upgrade.
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(stack, GRID_START, GRID_END, false)) {
                return ItemStack.EMPTY;
            }
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


    /** Moves a single item of {@code stack} into the upgrade slot that accepts it, if there is room. */
    private boolean moveOneToUpgrades(ItemStack stack) {
        if (UpgradeSlotType.forStack(stack) == null) {
            return false;
        }
        ItemStack one = stack.copyWithCount(1);
        if (!moveItemStackTo(one, UPGRADES_START, UPGRADES_END, false)) {
            return false;
        }
        stack.shrink(1);
        return true;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.BETTER_AUTO_CRAFTER.get());
    }
}
