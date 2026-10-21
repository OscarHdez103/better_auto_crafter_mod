package laseta.bac.registry;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.BetterAutoCrafterBlock;
import laseta.bac.block.BetterAutoMoverBlock;
import laseta.bac.block.TranslocatorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BetterAutoCrafter.MODID);

    public static final DeferredBlock<BetterAutoCrafterBlock> BETTER_AUTO_CRAFTER = BLOCKS.registerBlock(
            "better_auto_crafter",
            BetterAutoCrafterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.5F)
                    .sound(SoundType.WOOD));

    public static final DeferredBlock<BetterAutoMoverBlock> BETTER_AUTO_MOVER = BLOCKS.registerBlock(
            "better_auto_mover",
            BetterAutoMoverBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.5F)
                    .sound(SoundType.WOOD));

    public static final DeferredBlock<TranslocatorBlock> TRANSLOCATOR = BLOCKS.registerBlock(
            "translocator",
            TranslocatorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F)
                    .sound(SoundType.LODESTONE));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
