package laseta.bac.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import laseta.bac.item.ItemSettingUpgradeItem;
import laseta.bac.menu.slot.MachineUpgradeSlot;
import laseta.bac.menu.slot.ResultPreviewSlot;
import laseta.bac.menu.slot.UpgradeSlot;
import laseta.bac.menu.transfer.GridFillPlanner;
import laseta.bac.network.RecipeChoicesPayload;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.upgrade.UpgradeInventory;
import laseta.bac.upgrade.UpgradeLayout;
import laseta.bac.upgrade.UpgradeSlotType;
import laseta.bac.upgrade.UpgradeStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Menu of an auto machine: the template grid, the player inventory and the upgrade slots (every machine's, then the
 * machine's own), plus the side configuration, settings, machine options, channel faces, upgrade statuses and machine
 * status as data slots, changed with menu buttons.
 *
 * <p>Subclasses add the slots in their constructor, in this order so slot indices never change: the template grid
 * ({@link #addTemplateSlots}), their own slots if any, the player inventory ({@link #addPlayerInventory}), then the
 * upgrades ({@link #addUpgradeSlots}).</p>
 */
public abstract class AutoMachineMenu extends AbstractContainerMenu {
    // Menu button ids (sent with vanilla's ServerboundContainerButtonClickPacket):
    // [0, 6) next mode, [6, 12) previous mode, [12, 18) reset to NONE. Offset by RelativeSide ordinal.
    // Then one id per setting action, then MachineOptions.MAX_VALUES ids per machine option (select that value), then
    // RelativeSide.COUNT ids per face channel (toggle that face), then one id per machine upgrade slot (set its upgrade's
    // item setting to the carried item).
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
                            IItemHandler machineUpgrades, ContainerData sideModes, ContainerData settings,
                            MachineFeatures features, ContainerData options, ContainerData channelFaces,
                            ContainerData upgradeStatus, ContainerData status, BlockPos pos, ContainerLevelAccess access,
                            @Nullable AutoMachineBlockEntity blockEntity) {
        /** Server side, backed by the block entity. */
        public static Source of(AutoMachineBlockEntity blockEntity) {
            return new Source(blockEntity.getTemplate(), blockEntity.getGridWidth(), blockEntity.getGridHeight(),
                    blockEntity.getUpgrades(), blockEntity.getMachineUpgrades(), blockEntity.getSideConfig().asContainerData(),
                    blockEntity.getSettings().asContainerData(), blockEntity.getFeatures(),
                    blockEntity.getOptions().asContainerData(), blockEntity.getChannelFaces().asContainerData(),
                    blockEntity.getUpgradeStatusData(), blockEntity.getStatusData(), blockEntity.getBlockPos(),
                    ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), blockEntity.getBlockPos()),
                    blockEntity);
        }

        /**
         * Client side, with the data written by {@link AutoMachineBlockEntity#writeMenuData}.
         *
         * @param optionTypes the machine's options, the same list its block entity uses
         */
        public static Source client(RegistryFriendlyByteBuf extraData, List<MachineOption<?>> optionTypes) {
            return client(extraData, MachineFeatures.ofOptions(optionTypes));
        }

        /**
         * Client side, with the data written by {@link AutoMachineBlockEntity#writeMenuData}.
         *
         * @param features the machine's own features, the same its block entity uses
         */
        public static Source client(RegistryFriendlyByteBuf extraData, MachineFeatures features) {
            BlockPos pos = extraData.readBlockPos();
            int gridWidth = extraData.readVarInt();
            int gridHeight = extraData.readVarInt();
            int[] upgradeLimits = new int[UpgradeSlotType.COUNT];
            for (int i = 0; i < upgradeLimits.length; i++) {
                upgradeLimits[i] = extraData.readVarInt();
            }
            return new Source(new ItemStackHandler(gridWidth * gridHeight), gridWidth, gridHeight, new UpgradeInventory(() -> {
            }, type -> upgradeLimits[type.ordinal()]), new MachineUpgradeInventory(features.upgradeSlots(), () -> {
            }), new SimpleContainerData(RelativeSide.COUNT), new SimpleContainerData(MachineSettings.DATA_COUNT), features,
                    new SimpleContainerData(features.options().size()), new SimpleContainerData(features.channels().size()),
                    new SimpleContainerData(UpgradeSlotType.COUNT), new SimpleContainerData(MachineStatus.DATA_COUNT), pos,
                    ContainerLevelAccess.NULL, null);
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
    private final MachineFeatures features;
    private final MachineTabLayout machineTabLayout;
    private final List<MachineOption<?>> optionTypes;
    /** The ordinal of each machine option's value, in {@link #optionTypes} order. */
    private final ContainerData options;
    /** The faces that are on for each face channel, a bit per RelativeSide. */
    private final ContainerData channelFaces;
    /** UpgradeStatus per upgrade slot, computed by the block entity on the server. */
    private final ContainerData upgradeStatus;
    /** Cooldown, problem and redstone signal for the status icon (see MachineStatus.DATA_*). */
    private final ContainerData status;
    /** The player the menu is open for, set with the player inventory: the server sends it the recipe choices. */
    @Nullable
    private Player player;
    /** Client: as last received. Server: as last sent, null before the first time. */
    @Nullable
    private RecipeChoices recipeChoices;
    /** Client only: set by the screen while the upgrades tab is open, which shows the upgrade slots. */
    private boolean upgradeSlotsVisible;
    /** Client only: set by the screen while the machine's own tab is open, which shows its upgrade slots. */
    private boolean machineUpgradeSlotsVisible;

    // Slot index ranges, end exclusive; set while the subclass adds the slots.
    private int templateStart;
    private int templateEnd;
    private int playerInventoryStart;
    private int playerInventoryEnd;
    private int upgradesStart;
    private int upgradesEnd;
    private int machineUpgradesStart;
    private int machineUpgradesEnd;

    /** @param block the machine's block: the menu closes when it is gone */
    protected AutoMachineMenu(MenuType<?> type, int containerId, Block block, MachineLayout layout, Source source) {
        super(type, containerId);
        checkContainerDataCount(source.sideModes(), RelativeSide.COUNT);
        checkContainerDataCount(source.settings(), MachineSettings.DATA_COUNT);
        checkContainerDataCount(source.upgradeStatus(), UpgradeSlotType.COUNT);
        checkContainerDataCount(source.options(), source.features().options().size());
        checkContainerDataCount(source.channelFaces(), source.features().channels().size());
        checkContainerDataCount(source.status(), MachineStatus.DATA_COUNT);
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
        this.features = source.features();
        this.machineTabLayout = new MachineTabLayout(features);
        this.optionTypes = features.options();
        this.options = source.options();
        this.channelFaces = source.channelFaces();
        this.upgradeStatus = source.upgradeStatus();
        this.status = source.status();
        // The server has no screen: there the slots are always "visible" (it doesn't use positions anyway).
        this.upgradeSlotsVisible = blockEntity != null;
        this.machineUpgradeSlotsVisible = blockEntity != null;
        addDataSlots(sideModes);
        addDataSlots(settings);
        addDataSlots(options);
        addDataSlots(channelFaces);
        addDataSlots(upgradeStatus);
        addDataSlots(status);
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
        player = playerInventory.player;
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

    /** Upgrade slots, in the upgrades tab on the left of the GUI, then the machine's own ones, in its tab. */
    protected void addUpgradeSlots(Source source) {
        upgradesStart = slots.size();
        for (UpgradeSlotType type : UpgradeSlotType.values()) {
            addSlot(new UpgradeSlot(source.upgrades(), type, UpgradeLayout.slotX(type), UpgradeLayout.slotY(type),
                    () -> upgradeSlotsVisible));
        }
        upgradesEnd = slots.size();
        machineUpgradesStart = slots.size();
        for (int i = 0; i < features.upgradeSlots().size(); i++) {
            MachineTabLayout.Box frame = machineTabLayout.upgradeSlot(i);
            addSlot(new MachineUpgradeSlot(source.machineUpgrades(), i, features.upgradeSlots().get(i), frame.x() + 1,
                    frame.y() + 1, () -> machineUpgradeSlotsVisible));
        }
        machineUpgradesEnd = slots.size();
    }

    protected int templateStart() {
        return templateStart;
    }

    /** How many template slots there are: a recipe filled in from JEI / EMI has at most that many inputs. */
    public int templateSize() {
        return templateEnd - templateStart;
    }

    /**
     * Plans filling the template with a recipe's inputs, in slot order, from the player's inventory (see
     * {@link GridFillPlanner}). Pure: changes nothing. Works on both sides.
     */
    public GridFillPlanner.Plan planFill(Player player, List<List<ItemStack>> wanted, boolean fillMaximum) {
        List<ItemStack> template = new ArrayList<>(templateSize());
        for (int i = templateStart; i < templateEnd; i++) {
            template.add(slots.get(i).getItem());
        }
        return GridFillPlanner.plan(wanted, template, player.getInventory().items, fillMaximum);
    }

    /**
     * Server side: fills the template (recipe transfer from JEI / EMI), then makes the machine use that recipe if the
     * template makes several. Does nothing if the plan isn't valid.
     *
     * @param recipe the recipe that was transferred, if it is a real one
     */
    public void fillTemplate(Player player, List<List<ItemStack>> wanted, boolean fillMaximum, @Nullable ResourceLocation recipe) {
        GridFillPlanner.Plan plan = planFill(player, wanted, fillMaximum);
        if (!plan.canApply()) {
            return;
        }
        for (int i = 0; i < templateSize(); i++) {
            slots.get(templateStart + i).set(plan.grid().get(i));
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            inventory.items.set(i, plan.inventory().get(i));
        }
        inventory.setChanged();
        if (recipe != null && blockEntity != null) {
            blockEntity.chooseRecipe(recipe);
        }
        broadcastChanges();
    }

    protected int templateEnd() {
        return templateEnd;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------------------------------

    /** The recipes the template could make and which one the machine makes (see {@link RecipeChoicesPayload}). */
    public RecipeChoices getRecipeChoices() {
        return recipeChoices == null ? RecipeChoices.NONE : recipeChoices;
    }

    /** Client only: the choices the server sent. */
    public void setRecipeChoices(RecipeChoices choices) {
        recipeChoices = choices;
    }

    /** Server only: sends the recipe choices along with everything else vanilla syncs, when they changed. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (blockEntity != null && player instanceof ServerPlayer serverPlayer) {
            RecipeChoices current = blockEntity.getRecipeChoices();
            if (recipeChoices == null || !recipeChoices.sameAs(current)) {
                recipeChoices = current;
                PacketDistributor.sendToPlayer(serverPlayer, new RecipeChoicesPayload(containerId, current));
            }
        }
    }

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

    public void setMachineUpgradeSlotsVisible(boolean visible) {
        this.machineUpgradeSlotsVisible = visible;
    }

    public MachineFeatures getFeatures() {
        return features;
    }

    /** Where everything in the machine's own tab is. */
    public MachineTabLayout getMachineTabLayout() {
        return machineTabLayout;
    }

    public Slot getMachineUpgradeSlot(int index) {
        return slots.get(machineUpgradesStart + index);
    }

    public boolean isChannelFaceOn(int channelIndex, RelativeSide side) {
        return ChannelFaces.isOn(channelFaces.get(channelIndex), side);
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

    /** Ticks until the machine may run again; 0 when it isn't cooling down. */
    public int getCooldownLeft() {
        return status.get(MachineStatus.DATA_COOLDOWN_LEFT);
    }

    /** Length of the current cooldown. */
    public int getCooldownTotal() {
        return status.get(MachineStatus.DATA_COOLDOWN_TOTAL);
    }

    /** What stops the machine right now, or else what stopped its last attempt; null if nothing. */
    @Nullable
    public MachineProblem getProblem() {
        return MachineProblem.byId(status.get(MachineStatus.DATA_PROBLEM));
    }

    /** Whether the block receives a redstone signal. */
    public boolean hasSignal() {
        return status.get(MachineStatus.DATA_SIGNAL) != 0;
    }

    public MachineState getState() {
        return MachineState.of(getRedstoneMode(), hasSignal(), getCooldownLeft() > 0, getProblem());
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

    private int channelButtonsStart() {
        return BUTTON_OPTIONS_START + optionTypes.size() * MachineOptions.MAX_VALUES;
    }

    private int settingButtonsStart() {
        return channelButtonsStart() + features.channels().size() * RelativeSide.COUNT;
    }

    /** Turns a face on or off for one of the machine's face channels. */
    public int toggleChannelFaceButton(int channelIndex, RelativeSide side) {
        return channelButtonsStart() + channelIndex * RelativeSide.COUNT + side.ordinal();
    }

    /** Sets the item setting of the upgrade in one of the machine's upgrade slots to the carried item (EMPTY clears it). */
    public int setUpgradeSettingButton(int slotIndex) {
        return settingButtonsStart() + slotIndex;
    }

    /** Makes the machine use the recipe at {@code index} of its {@link #getRecipeChoices()}. */
    public int chooseRecipeButton(int index) {
        return recipeButtonsStart() + index;
    }

    private int recipeButtonsStart() {
        return settingButtonsStart() + features.upgradeSlots().size();
    }

    /**
     * Called on the server when the client sends a button click, and on the client for an instant (predicted) update.
     * On the server, writing to {@link #sideModes} / {@link #settings} / {@link #options} writes straight into the
     * block entity.
     */
    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (id < 0 || id >= recipeButtonsStart() + RecipeChoices.MAX) {
            return false;
        }
        if (id >= recipeButtonsStart()) {
            // Only the server knows the recipes; the client waits for the new choices to arrive.
            return blockEntity == null || blockEntity.chooseRecipe(id - recipeButtonsStart());
        }
        if (id >= settingButtonsStart()) {
            return setUpgradeSetting(id - settingButtonsStart());
        }
        if (id >= channelButtonsStart()) {
            int channelIndex = (id - channelButtonsStart()) / RelativeSide.COUNT;
            int side = (id - channelButtonsStart()) % RelativeSide.COUNT;
            channelFaces.set(channelIndex, channelFaces.get(channelIndex) ^ 1 << side);
            return true;
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

    /**
     * Sets the installed upgrade's item setting to a copy of the carried item, or clears it with an empty hand. The
     * carried item is never used up. Works the same on both sides (the carried item is synced).
     */
    private boolean setUpgradeSetting(int slotIndex) {
        return setUpgradeSetting(slotIndex, getCarried());
    }

    /**
     * Sets the item setting of the upgrade in one of the machine's upgrade slots to a copy of {@code setting} (one
     * item), or clears it with EMPTY. Nothing is used up: the setting is only a filter, which is also why it may come
     * from an item dragged from JEI / EMI.
     *
     * @return false if there is no such slot, its upgrade has no setting or can't be set to {@code setting}
     */
    public boolean setUpgradeSetting(int slotIndex, ItemStack setting) {
        if (slotIndex < 0 || slotIndex >= features.upgradeSlots().size()) {
            return false;
        }
        Slot slot = getMachineUpgradeSlot(slotIndex);
        ItemStack upgrade = slot.getItem();
        if (!(upgrade.getItem() instanceof ItemSettingUpgradeItem item) || (!setting.isEmpty() && !item.canBeSetTo(setting))) {
            return false;
        }
        ItemStack updated = upgrade.copy();
        ItemSettingUpgradeItem.setSetting(updated, setting);
        slot.set(updated);
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
            // Player inventory -> as many as fit into its upgrade slot if it's an upgrade (never the template, even
            // when that slot is full), otherwise -> template
            boolean upgrade = UpgradeSlotType.forStack(stack) != null;
            boolean machineUpgrade = !upgrade && features.upgradeSlots().stream().anyMatch(type -> type.canHold(stack));
            if (upgrade || machineUpgrade) {
                if (upgrade ? moveItemStackTo(stack, upgradesStart, upgradesEnd, false)
                        : moveItemStackTo(stack, machineUpgradesStart, machineUpgradesEnd, false)) {
                    if (stack.isEmpty()) {
                        slot.setByPlayer(ItemStack.EMPTY);
                    } else {
                        slot.setChanged();
                    }
                }
                // EMPTY stops vanilla's quick-move loop: the slot took all it could.
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

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, block);
    }
}
