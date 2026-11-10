package laseta.bac.block;

import com.mojang.serialization.MapCodec;

import laseta.bac.block.entity.BetterAutoHubBlockEntity;
import laseta.bac.machine.AutoMachineBlock;
import laseta.bac.registry.ModBlockEntities;
import laseta.bac.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the machines in its tabs from one block (see {@link BetterAutoHubBlockEntity}). Like a machine's block, it faces
 * a direction (the machines' sides are relative to it) and follows its redstone signal: a pulse runs the machines of
 * every tab in the "on powered" mode, the other modes are each machine's own. Using a Hub Expansion on it unlocks its
 * next tab.
 */
public class BetterAutoHubBlock extends BaseEntityBlock {
    public static final MapCodec<BetterAutoHubBlock> CODEC = simpleCodec(BetterAutoHubBlock::new);

    public BetterAutoHubBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AutoMachineBlock.FACING, Direction.NORTH)
                .setValue(AutoMachineBlock.TRIGGERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(AutoMachineBlock.FACING, AutoMachineBlock.TRIGGERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Front faces the player who placed it. Placing it next to a powered source doesn't run it (no rising edge).
        return defaultBlockState()
                .setValue(AutoMachineBlock.FACING, context.getHorizontalDirection().getOpposite())
                .setValue(AutoMachineBlock.TRIGGERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block neighborBlock,
                                   @NotNull BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && AutoMachineBlock.updateTriggered(state, level, pos)
                && level.getBlockEntity(pos) instanceof BetterAutoHubBlockEntity hub && hub.runsOnPulse()) {
            level.scheduleTick(pos, this, AutoMachineBlock.OPERATION_DELAY);
        }
    }

    @Override
    protected void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (level.getBlockEntity(pos) instanceof BetterAutoHubBlockEntity hub) {
            hub.runPulse(level);
        }
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level,
                                                       @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
                                                       @NotNull BlockHitResult hitResult) {
        // A Hub Expansion unlocks the next tab; once they all are, it opens the GUI like any other item does.
        if (!stack.is(ModItems.HUB_EXPANSION.get()) || !(level.getBlockEntity(pos) instanceof BetterAutoHubBlockEntity hub)
                || !hub.canExpand()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && hub.expand()) {
            stack.consume(1, player);
            hub.playExpandSound();
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof BetterAutoHubBlockEntity hub) {
            hub.openTab(serverPlayer, hub.getSelectedTab());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BetterAutoHubBlockEntity hub) {
            hub.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(AutoMachineBlock.FACING, rotation.rotate(state.getValue(AutoMachineBlock.FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(AutoMachineBlock.FACING, mirror.mirror(state.getValue(AutoMachineBlock.FACING)));
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        // BaseEntityBlock defaults to INVISIBLE; the lamps are drawn over the normal block model.
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state,
                                                                  @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.BETTER_AUTO_HUB.get(),
                BetterAutoHubBlockEntity::serverTick);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BetterAutoHubBlockEntity(pos, state);
    }
}
