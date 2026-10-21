package laseta.bac;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import laseta.bac.registry.ModBlockEntities;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModCreativeTabs;
import laseta.bac.registry.ModDataComponents;
import laseta.bac.registry.ModRecipeSerializers;
import laseta.bac.registry.ModRecipeTypes;
import laseta.bac.registry.ModItems;
import laseta.bac.registry.ModMenuTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

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
        ModRecipeTypes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
    }
}
