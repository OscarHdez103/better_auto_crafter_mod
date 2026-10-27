package laseta.bac.client;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.client.screen.BetterAutoCrafterScreen;
import laseta.bac.client.screen.BetterAutoBrewerScreen;
import laseta.bac.client.screen.BetterAutoMoverScreen;
import laseta.bac.client.screen.BetterAutoSmelterScreen;
import laseta.bac.registry.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = BetterAutoCrafter.MODID, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.BETTER_AUTO_CRAFTER.get(), BetterAutoCrafterScreen::new);
        event.register(ModMenuTypes.BETTER_AUTO_MOVER.get(), BetterAutoMoverScreen::new);
        event.register(ModMenuTypes.BETTER_AUTO_SMELTER.get(), BetterAutoSmelterScreen::new);
        event.register(ModMenuTypes.BETTER_AUTO_BREWER.get(), BetterAutoBrewerScreen::new);
    }
}
