package laseta.bac.machine;

import java.util.List;
import java.util.Objects;

import laseta.bac.menu.slot.ResultPreviewSlot;
import laseta.bac.menu.slot.UpgradeSlot;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.upgrade.UpgradeInventory;
import laseta.bac.upgrade.UpgradeLayout;
import laseta.bac.upgrade.UpgradeSlotType;
import laseta.bac.upgrade.UpgradeStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Menu of an auto machine: the template grid, the player inventory and the upgrade slots, plus the side configuration,
 * settings, machine options and upgrade statuses as data slots, changed with menu buttons.
 *
 * <p>Subclasses add the slots in their constructor, in this order so slot indices never change: the template grid
 * ({@link #addTemplateSlots}), their own slots if any, the player inventory ({@link #addPlayerInventory}), then the
 * upgrades ({@link #addUpgradeSlots}).</p>
 */
public abstract class AutoMachineMenu extends AbstractContainerMenu {
    // Menu button ids (sent with vanilla's ServerboundContainerButtonClickPacket):
    // [0, 6) next mode, [6, 12) previous mode, [12, 18) reset to NONE. Offset by RelativeSide ordinal.
    // Then one id per setting action, then MachineOptions.MAX_VALUES ids per machine option (select that value).
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
    private static final int BUTTON_OPTIONS_START = SIDE_BUTTON_COUNT + 7;

    /**
     * What the menu shows: the block entity's parts on the server, plain copies on the client that vanilla's container
     * syncing keeps up to date.
     */
    protected record Source(IItemHandler template, int gridWidth, int gridHeight, IItemHandler upgrades,
                            ContainerData sideModes, ContainerData settings, List<MachineOption<?>> optionTypes,
                            ContainerData options, ContainerData upgradeStatus, BlockPos pos, ContainerLevelAccess access,
                            @Nullable AutoMachineBlockEntity blockEntity) {
        /** Server side, backed by the block entity. */
        public static Source of(AutoMachineBlockEntity blockEntity) {
            return new Source(blockEntity.getTemplate(), blockEntity.getGridWidth(), blockEntity.getGridHeight(),
                    blockEntity.getUpgrades(), blockEntity.getSideConfig().asContainerData(),
                    blockEntity.getSettings().asContainerData(), blockEntity.getOptions().definitions(),
                    blockEntity.getOptions().asContainerData(), blockEntity.getUpgradeStatusData(), blockEntity.getBlockPos(),
                    ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), blockEntity.getBlockPos()),
                    blockEntity);
        }

        /**
         * Client side, with the data written by {@link AutoMachineBlockEntity#writeMenuData}.
         *
         * @param optionTypes the machine's options, the same list its block entity uses
         */
        public static Source client(RegistryFriendlyByteBuf extraData, List<MachineOption<?>> optionTypes) {
            BlockPos pos = extraData.readBlockPos();
            int gridWidth = extraData.readVarInt();
            int gridHeight = extraData.readVarInt();
            return new Source(new ItemStackHandler(gridWidth * gridHeight), gridWidth, gridHeight, new UpgradeInventory(() -> {
            }), new SimpleContainerData(RelativeSide.COUNT), new SimpleContainerData(MachineSettings.DATA_COUNT),
                    optionTypes, new SimpleContainerData(optionTypes.size()), new SimpleContainerData(UpgradeSlotType.COUNT),
                    pos, ContainerLevelAccess.NULL, null);
        }
    }

    private final Block block;
    private final MachineLayout layout;
    @Nullable
    private final AutoMachineBlockEntity blockEntity;
    private final BlockPos blockPos;
    private final ContainerLevelAccess access;
    /** One data slot per RelativeSide holding a SideMode ordinal. */
    private final ContainerData sideModes;
    /** MachineSettings values (see MachineSettings.DATA_*). */
    private final ContainerData settings;
    private final List<MachineOption<?>> optionTypes;
    /** The ordinal of each machine option's value, in {@link #optionTypes} order. */
    private final ContainerData options;
    /** UpgradeStatus per upgrade slot, computed by the block entity on the server. */
    private final ContainerData upgradeStatus;
    /** Client only: set by the screen while the upgrades tab is open, which shows the upgrade slots. */
    private boolean upgradeSlotsVisible;

    // Slot index ranges, end exclusive; set while the subclass adds the slots.
    private int templateStart;
    private int templateEnd;
    private int playerInventoryStart;
    private int playerInventoryEnd;
    private int upgradesStart;
    private int upgradesEnd;

    /** @param block the machine's block: the menu closes when it is gone */
    protected AutoMachineMenu(MenuType<?> type, int containerId, Block block, MachineLayout layout, Source source) {
        super(type, containerId);
        checkContainerDataCount(source.sideModes(), RelativeSide.COUNT);
        checkContainerDataCount(source.settings(), MachineSettings.DATA_COUNT);
        checkContainerDataCount(source.upgradeStatus(), UpgradeSlotType.COUNT);
        checkContainerDataCount(source.options(), source.optionTypes().size());
        if (layout.gridSize() != source.template().getSlots()) {
            throw new IllegalArgumentException("The layout's grid has " + layout.gridSize() + " slots, the machine's "
                    + source.template().getSlots());
        }
        this.block = block;
        this.layout = layout;
        this.blockEntity = source.blockEntity();
        this.blockPos = source.pos();
        this.access = source.access();
        this.sideModes = source.sideModes();
        this.settings = source.settings();
        this.optionTypes = source.optionTypes();
        this.options = source.options();
        this.upgradeStatus = source.upgradeStatus();
        // The server has no screen: there the slots are always "visible" (it doesn't use positions anyway).
        this.upgradeSlotsVisible = blockEntity != null;
        addDataSlots(sideModes);
        addDataSlots(settings);
        addDataSlots(options);
        addDataSlots(upgradeStatus);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Slots
    // ---------------------------------------------------------------------------------------------------------------

    protected void addTemplateSlots(Source source) {
        templateStart = slots.size();
        for (int i = 0; i < layout.gridSize(); i++) {
            addSlot(new SlotItemHandler(source.template(), i, layout.gridSlotX(i), layout.gridSlotY(i)));
        }
        templateEnd = slots.size();
    }

    protected void addPlayerInventory(Inventory playerInventory) {
        playerInventoryStart = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        layout.inventoryLeft() + col * MachineLayout.SLOT_SIZE, layout.inventoryTop() + row * MachineLayout.SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, layout.inventoryLeft() + col * MachineLayout.SLOT_SIZE, layout.hotbarTop()));
        }
        playerInventoryEnd = slots.size();
    }

    /** Upgrade slots, in the upgrades tab on the left of the GUI. */
    protected void addUpgradeSlots(Source source) {
        upgradesStart = slots.size();
        for (UpgradeSlotType type : UpgradeSlotType.values()) {
            addSlot(new UpgradeSlot(source.upgrades(), type, UpgradeLayout.slotX(type.ordinal()), UpgradeLayout.slotY(type.ordinal()),
                    () -> upgradeSlotsVisible));
        }
        upgradesEnd = slots.size();
    }

    protected int templateStart() {
        return templateStart;
    }

    protected int templateEnd() {
        return templateEnd;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------------------------------

    /** The block entity on the server, null on the client. */
    @Nullable
    protected AutoMachineBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public Block getMachineBlock() {
        return block;
    }

    public MachineLayout getLayout() {
        return layout;
    }

    public void setUpgradeSlotsVisible(boolean visible) {
        this.upgradeSlotsVisible = visible;
    }

    public UpgradeStatus getUpgradeStatus(UpgradeSlotType type) {
        return UpgradeStatus.byId(upgradeStatus.get(type.ordinal()));
    }

    public Slot getUpgradeSlot(UpgradeSlotType type) {
        return slots.get(upgradesStart + type.ordinal());
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public SideMode getSideMode(RelativeSide side) {
        return SideMode.byId(sideModes.get(side.ordinal()));
    }

    public boolean isMatchNbt() {
        return settings.get(MachineSettings.DATA_MATCH_NBT) != 0;
    }

    public TransferPriority getInputPriority() {
        return TransferPriority.byId(settings.get(MachineSettings.DATA_INPUT_PRIORITY));
    }

    public TransferPriority getOutputPriority() {
        return TransferPriority.byId(settings.get(MachineSettings.DATA_OUTPUT_PRIORITY));
    }

    public RedstoneMode getRedstoneMode() {
        return RedstoneMode.byId(settings.get(MachineSettings.DATA_REDSTONE_MODE));
    }

    /** The machine's own options, in the order its tab shows them; empty for machines without any. */
    public List<MachineOption<?>> getOptionTypes() {
        return optionTypes;
    }

    public <T extends Enum<T> & StringRepresentable> T getOption(MachineOption<T> option) {
        return option.byId(options.get(optionTypes.indexOf(option)));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Buttons
    // ---------------------------------------------------------------------------------------------------------------

    public static int nextModeButton(RelativeSide side) {
        return BUTTON_NEXT + side.ordinal();
    }

    public static int previousModeButton(RelativeSide side) {
        return BUTTON_PREVIOUS + side.ordinal();
    }

    public static int resetModeButton(RelativeSide side) {
        return BUTTON_RESET + side.ordinal();
    }

    /** Selects a value of one of the machine's options. */
    public static int selectOptionButton(int optionIndex, Enum<?> value) {
        return BUTTON_OPTIONS_START + optionIndex * MachineOptions.MAX_VALUES + value.ordinal();
    }

    /**
     * Called on the server when the client sends a button click, and on the client for an instant (predicted) update.
     * On the server, writing to {@link #sideModes} / {@link #settings} / {@link #options} writes straight into the
     * block entity.
     */
    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (id < 0 || id >= BUTTON_OPTIONS_START + optionTypes.size() * MachineOptions.MAX_VALUES) {
            return false;
        }
        if (id >= BUTTON_OPTIONS_START) {
            int optionIndex = (id - BUTTON_OPTIONS_START) / MachineOptions.MAX_VALUES;
            int value = (id - BUTTON_OPTIONS_START) % MachineOptions.MAX_VALUES;
            if (value >= optionTypes.get(optionIndex).values().size()) {
                return false;
            }
            options.set(optionIndex, value);
            return true;
        }
        if (id >= SIDE_BUTTON_COUNT) {
            if (id == BUTTON_TOGGLE_MATCH_NBT) {
                settings.set(MachineSettings.DATA_MATCH_NBT, isMatchNbt() ? 0 : 1);
            } else if (id == BUTTON_INPUT_PRIORITY_NEXT) {
                settings.set(MachineSettings.DATA_INPUT_PRIORITY, getInputPriority().next().ordinal());
            } else if (id == BUTTON_INPUT_PRIORITY_PREVIOUS) {
                settings.set(MachineSettings.DATA_INPUT_PRIORITY, getInputPriority().previous().ordinal());
            } else if (id == BUTTON_OUTPUT_PRIORITY_NEXT) {
                settings.set(MachineSettings.DATA_OUTPUT_PRIORITY, getOutputPriority().next().ordinal());
            } else if (id == BUTTON_OUTPUT_PRIORITY_PREVIOUS) {
                settings.set(MachineSettings.DATA_OUTPUT_PRIORITY, getOutputPriority().previous().ordinal());
            } else if (id == BUTTON_REDSTONE_MODE_NEXT) {
                settings.set(MachineSettings.DATA_REDSTONE_MODE, getRedstoneMode().next().ordinal());
            } else {
                settings.set(MachineSettings.DATA_REDSTONE_MODE, getRedstoneMode().previous().ordinal());
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

    // ---------------------------------------------------------------------------------------------------------------
    // Clicks
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
        // Ignore every interaction with preview slots (pickup, swap, clone, throw, ...).
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof ResultPreviewSlot) {
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
        if (slot instanceof ResultPreviewSlot || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < playerInventoryStart || index >= playerInventoryEnd) {
            // Template or upgrade -> player inventory
            if (!moveItemStackTo(stack, playerInventoryStart, playerInventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inventory -> one item into its upgrade slot if it's an upgrade and there is room, otherwise -> template
            if (moveOneToUpgrades(stack)) {
                if (stack.isEmpty()) {
                    slot.setByPlayer(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
                // EMPTY stops vanilla's quick-move loop, so each shift-click installs exactly one upgrade.
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(stack, templateStart, templateEnd, false)) {
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
        if (!moveItemStackTo(one, upgradesStart, upgradesEnd, false)) {
            return false;
        }
        stack.shrink(1);
        return true;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, block);
    }
}
