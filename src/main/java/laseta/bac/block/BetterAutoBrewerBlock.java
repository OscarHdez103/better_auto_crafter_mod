package laseta.bac.block;

import com.mojang.serialization.MapCodec;

import laseta.bac.block.entity.BetterAutoBrewerBlockEntity;
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

/** Brews what its ingredient slots hold, from starters, ingredients and fuel on its faces, into the output inventories. */
public class BetterAutoBrewerBlock extends AutoMachineBlock {
    public static final MapCodec<BetterAutoBrewerBlock> CODEC = simpleCodec(BetterAutoBrewerBlock::new);

    public BetterAutoBrewerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockEntityType<? extends AutoMachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BETTER_AUTO_BREWER.get();
    }

    @Override
    protected int getSuccessSound() {
        return LevelEvent.SOUND_BREWING_STAND_BREW;
    }

    @Override
    protected int getFailSound() {
        return LevelEvent.SOUND_DISPENSER_FAIL;
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BetterAutoBrewerBlockEntity(pos, state);
    }
}
