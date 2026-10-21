package laseta.bac.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * on a rising edge in the "on powered" mode, shows a failed operation for a moment, and opens the machine's GUI.
 * Subclasses only add their block entity and, optionally, their own sounds.
 */
public abstract class AutoMachineBlock extends BaseEntityBlock {
    /** Direction the front of the block points to. Side configuration is stored relative to it. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** True while the block receives a redstone signal. In the "on powered" mode it runs when this turns true. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /**
     * True for a short time after an operation failed (missing items, no room for the output, ...).
     * Purely visual; cleared by the block entity after {@link AutoMachineBlockEntity#FAILED_DISPLAY_TICKS}.
     */
    public static final BooleanProperty FAILED = BooleanProperty.create("failed");
    /** Ticks between the redstone rising edge and the operation (the vanilla Crafter uses 4). */
    private static final int OPERATION_DELAY = 1;

    protected AutoMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false).setValue(FAILED, false));
    }

    /** The block entity type of this machine; the ticker only runs for it. */
    protected abstract BlockEntityType<? extends AutoMachineBlockEntity> getBlockEntityType();

    /** Level event played after a successful operation in the "on powered" mode. */
    protected abstract int getSuccessSound();

    /** Level event played after a failed operation in the "on powered" mode. */
    protected abstract int getFailSound();

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, FAILED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Front faces the player who placed it. Placing it next to a powered source doesn't run it (no rising edge).
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    /**
     * Keeps {@link #POWERED} in sync with the redstone signal (used by every redstone mode), and in the
     * "on powered" mode detects the rising edge, like the vanilla Crafter / Dispenser. The operation itself runs in
     * {@link #tick}, never in the middle of a neighbour update: it touches other blocks' inventories, which can cause
     * more block updates, so doing it here could re-enter this method.
     */
    @Override
    protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block neighborBlock,
                                   @NotNull BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        boolean wasPowered = state.getValue(POWERED);
        if (powered && !wasPowered) {
            if (getRedstoneMode(level, pos) == RedstoneMode.ON_POWERED) {
                level.scheduleTick(pos, this, OPERATION_DELAY);
            }
            level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_CLIENTS);
        } else if (!powered && wasPowered) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        // The mode may have been changed in the tick between the rising edge and now.
        if (level.getBlockEntity(pos) instanceof AutoMachineBlockEntity blockEntity
                && blockEntity.getSettings().getRedstoneMode() == RedstoneMode.ON_POWERED) {
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

    /** Server only: drives the continuous redstone modes and clears the failed state after its time. */
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
