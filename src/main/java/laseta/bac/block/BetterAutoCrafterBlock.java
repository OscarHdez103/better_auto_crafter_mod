package laseta.bac.block;

import com.mojang.serialization.MapCodec;

import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.machine.AutoMachineBlock;
import laseta.bac.machine.AutoMachineBlockEntity;
import laseta.bac.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/** Crafts the recipe laid out in its grid from the items in the neighbouring inventories. */
public class BetterAutoCrafterBlock extends AutoMachineBlock {
    public static final MapCodec<BetterAutoCrafterBlock> CODEC = simpleCodec(BetterAutoCrafterBlock::new);

    public BetterAutoCrafterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockEntityType<? extends AutoMachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BETTER_AUTO_CRAFTER.get();
    }

    @Override
    protected int getSuccessSound() {
        return LevelEvent.SOUND_CRAFTER_CRAFT;
    }

    @Override
    protected int getFailSound() {
        return LevelEvent.SOUND_CRAFTER_FAIL;
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BetterAutoCrafterBlockEntity(pos, state);
    }
}
