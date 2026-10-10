package laseta.bac;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import laseta.bac.registry.ModBlockEntities;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModItems;
import laseta.bac.registry.ModMenuTypes;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(BetterAutoCrafter.MODID)
public class BetterAutoCrafter {
    public static final String MODID = "better_auto_crafter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BetterAutoCrafter(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);

        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.BETTER_AUTO_CRAFTER);
        }
    }
}
