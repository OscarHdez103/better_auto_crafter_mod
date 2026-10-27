package laseta.bac.registry;

import java.util.function.Supplier;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.block.entity.BetterAutoBrewerBlockEntity;
import laseta.bac.block.entity.BetterAutoMoverBlockEntity;
import laseta.bac.block.entity.BetterAutoSmelterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BetterAutoCrafter.MODID);

    public static final Supplier<BlockEntityType<BetterAutoCrafterBlockEntity>> BETTER_AUTO_CRAFTER =
            BLOCK_ENTITIES.register("better_auto_crafter", () -> BlockEntityType.Builder
                    .of(BetterAutoCrafterBlockEntity::new, ModBlocks.BETTER_AUTO_CRAFTER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<BetterAutoMoverBlockEntity>> BETTER_AUTO_MOVER =
            BLOCK_ENTITIES.register("better_auto_mover", () -> BlockEntityType.Builder
                    .of(BetterAutoMoverBlockEntity::new, ModBlocks.BETTER_AUTO_MOVER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<BetterAutoSmelterBlockEntity>> BETTER_AUTO_SMELTER =
            BLOCK_ENTITIES.register("better_auto_smelter", () -> BlockEntityType.Builder
                    .of(BetterAutoSmelterBlockEntity::new, ModBlocks.BETTER_AUTO_SMELTER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<BetterAutoBrewerBlockEntity>> BETTER_AUTO_BREWER =
            BLOCK_ENTITIES.register("better_auto_brewer", () -> BlockEntityType.Builder
                    .of(BetterAutoBrewerBlockEntity::new, ModBlocks.BETTER_AUTO_BREWER.get())
                    .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
