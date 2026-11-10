package laseta.bac.network;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.machine.HostTabs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Client -> server: show another of the host's tabs (see {@link HostTabs}) instead of the open menu's. The server opens
 * that tab's menu.
 */
public record SelectHostTabPayload(int containerId, int tab) implements CustomPacketPayload {
    public static final Type<SelectHostTabPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "select_host_tab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectHostTabPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectHostTabPayload::containerId,
            ByteBufCodecs.VAR_INT, SelectHostTabPayload::tab,
            SelectHostTabPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server thread. Ignored if that menu isn't open any more, or isn't in a host's tab. */
    public static void handle(SelectHostTabPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu.containerId == payload.containerId()
                && player.containerMenu instanceof HostTabs.Holder holder && holder.getHostTabs() != null
                && holder.getHostTabs().host() != null && player.containerMenu.stillValid(player)) {
            holder.getHostTabs().host().openTab(player, payload.tab());
        }
    }
}
