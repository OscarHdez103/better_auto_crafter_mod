package laseta.bac.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Block of an auto machine (see {@link AutoMachineBlockEntity}): faces a direction, follows its redstone signal, runs
 * on a rising edge in the "on powered" mode, shows when it is running and a failed operation for a moment, and opens
 * the machine's GUI.
 * Subclasses only add their block entity and, optionally, their own sounds.
 *
 * <p>Its item can also go into a tab of a {@link TabHost} (the Better Auto Hub), which then runs the machine itself
 * (see {@link #createHosted}); a machine that mustn't can say so with {@link #canBeHosted()}.</p>
 */
public abstract class AutoMachineBlock extends BaseEntityBlock {
    /** Direction the front of the block points to. Side configuration is stored relative to it. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /**
     * True while the machine runs: during the time each operation takes (see {@link AutoMachineBlockEntity#getBaseSpeed()}),
     * whatever the redstone mode and signal. Purely visual; kept in sync by the block entity.
     */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** True while the block receives a redstone signal. In the "on powered" mode it runs when this turns true. Not shown. */
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    /**
     * True for a short time after an operation failed (missing items, no room for the output, ...).
     * Purely visual; cleared by the block entity after {@link AutoMachineBlockEntity#FAILED_DISPLAY_TICKS}.
     */
    public static final BooleanProperty FAILED = BooleanProperty.create("failed");
    /** Ticks between the redstone rising edge and the operation (the vanilla Crafter uses 4). */
    public static final int OPERATION_DELAY = 1;

    protected AutoMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false).setValue(TRIGGERED, false).setValue(FAILED, false));
    }

    /** The block entity type of this machine; the ticker only runs for it. */
    protected abstract BlockEntityType<? extends AutoMachineBlockEntity> getBlockEntityType();

    /** Level event played after a successful operation in the "on powered" mode. */
    public abstract int getSuccessSound();

    /** Level event played after a failed operation in the "on powered" mode. */
    public abstract int getFailSound();

    /** Whether the machine can go into a tab of a {@link TabHost}. Every machine can, by default. */
    public boolean canBeHosted() {
        return true;
    }

    /**
     * A machine of this kind for a {@link TabHost} to run in one of its tabs: its block entity, not placed in the world,
     * at the host's position. The host gives it its {@link MachineHost}.
     */
    public AutoMachineBlockEntity createHosted(BlockPos hostPos) {
        return getBlockEntityType().create(hostPos, defaultBlockState());
    }

    /** The machine whose item {@code stack} is, if it can go into a host's tab; null for anything else. */
    @Nullable
    public static AutoMachineBlock hostable(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof AutoMachineBlock machine
                && machine.canBeHosted() ? machine : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, TRIGGERED, FAILED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Front faces the player who placed it. Placing it next to a powered source doesn't run it (no rising edge).
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(TRIGGERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    /**
     * Keeps {@link #TRIGGERED} in sync with the redstone signal (used by every redstone mode), and in the
     * "on powered" mode detects the rising edge, like the vanilla Crafter / Dispenser. The operation itself runs in
     * {@link #tick}, never in the middle of a neighbour update: it touches other blocks' inventories, which can cause
     * more block updates, so doing it here could re-enter this method.
     */
    @Override
    protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block neighborBlock,
                                   @NotNull BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && updateTriggered(state, level, pos) && getRedstoneMode(level, pos) == RedstoneMode.ON_POWERED) {
            level.scheduleTick(pos, this, OPERATION_DELAY);
        }
    }

    /**
     * Server only: keeps {@link #TRIGGERED} of a block that has it (a machine's, or a host's) in sync with its redstone
     * signal.
     *
     * @return true if the signal just turned on (a rising edge): the "on powered" mode runs a tick later, see
     * {@link #OPERATION_DELAY}
     */
    public static boolean updateTriggered(BlockState state, Level level, BlockPos pos) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(TRIGGERED)) {
            level.setBlock(pos, state.setValue(TRIGGERED, powered), Block.UPDATE_CLIENTS);
        }
        return powered && !state.getValue(TRIGGERED);
    }

    @Override
    protected void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        // The mode may have been changed in the tick between the rising edge and now. A pulse during the cooldown is
        // ignored, silently: the machine isn't ready for it.
        if (level.getBlockEntity(pos) instanceof AutoMachineBlockEntity blockEntity
                && blockEntity.getSettings().getRedstoneMode() == RedstoneMode.ON_POWERED
                && !blockEntity.isCoolingDown()) {
            boolean success = blockEntity.attemptOperation();
            // Sounds only in this mode: the continuous modes would play them every 2 ticks.
            level.levelEvent(success ? getSuccessSound() : getFailSound(), pos, 0);
        }
    }

    @Override
    protected @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        // BaseEntityBlock defaults to INVISIBLE; we want the normal block model.
        return RenderShape.MODEL;
    }

    private static RedstoneMode getRedstoneMode(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AutoMachineBlockEntity blockEntity
                ? blockEntity.getSettings().getRedstoneMode()
                : RedstoneMode.OFF;
    }

    /** Server only: drives the continuous redstone modes and keeps the running and failed states up to date. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state,
                                                                  @NotNull BlockEntityType<T> type) {
        return level.isClientSide || type != getBlockEntityType() ? null
                : (tickLevel, pos, tickState, blockEntity) ->
                AutoMachineBlockEntity.serverTick(tickLevel, tickState, (AutoMachineBlockEntity) blockEntity);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AutoMachineBlockEntity blockEntity) {
            player.openMenu(blockEntity, blockEntity::writeMenuData);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AutoMachineBlockEntity blockEntity) {
            blockEntity.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
