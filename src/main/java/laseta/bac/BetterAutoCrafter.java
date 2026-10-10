package laseta.bac;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import laseta.bac.registry.ModBlockEntities;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModDataComponents;
import laseta.bac.registry.ModRecipeSerializers;
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
        ModDataComponents.register(modEventBus);
        ModRecipeSerializers.register(modEventBus);

        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.BETTER_AUTO_CRAFTER);
            event.accept(ModItems.TRANSLOCATOR);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.TRANSLOCATION_UPGRADE_PULL);
            event.accept(ModItems.TRANSLOCATION_UPGRADE_PUSH);
            event.accept(ModItems.DIMENSIONAL_TRANSLOCATION_UPGRADE_PULL);
            event.accept(ModItems.DIMENSIONAL_TRANSLOCATION_UPGRADE_PUSH);
            event.accept(ModItems.STOCKING_UPGRADE);
            event.accept(ModItems.STOCKING_UPGRADE_TIER_2);
            event.accept(ModItems.STOCKING_UPGRADE_TIER_3);
        }
    }
}
