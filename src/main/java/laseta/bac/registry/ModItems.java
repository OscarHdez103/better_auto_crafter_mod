package laseta.bac.registry;

import laseta.bac.BetterAutoCrafter;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BetterAutoCrafter.MODID);

    public static final DeferredItem<BlockItem> BETTER_AUTO_CRAFTER = ITEMS.registerSimpleBlockItem(ModBlocks.BETTER_AUTO_CRAFTER);

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
