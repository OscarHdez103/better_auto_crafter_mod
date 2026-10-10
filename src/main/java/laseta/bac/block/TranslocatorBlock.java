package laseta.bac.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * Remote anchor for translocation upgrades. A Better Auto Crafter with a linked upgrade pulls from / pushes to the
 * inventories around this block as if it stood here, facing the same way this block faces. It needs no block
 * entity: the link is stored in the upgrade item, and the crafter checks that this block is still in place.
 */
public class TranslocatorBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<TranslocatorBlock> CODEC = simpleCodec(TranslocatorBlock::new);

    public TranslocatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Front faces the player who placed it, like the Better Auto Crafter.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
