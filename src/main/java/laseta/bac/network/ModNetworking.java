package laseta.bac.network;

import laseta.bac.BetterAutoCrafter;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = BetterAutoCrafter.MODID)
public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "3";

    private ModNetworking() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToServer(FillTemplatePayload.TYPE, FillTemplatePayload.STREAM_CODEC, FillTemplatePayload::handle)
                .playToServer(SetUpgradeSettingPayload.TYPE, SetUpgradeSettingPayload.STREAM_CODEC, SetUpgradeSettingPayload::handle)
                .playToClient(RecipeChoicesPayload.TYPE, RecipeChoicesPayload.STREAM_CODEC, RecipeChoicesPayload::handle);
    }
}
