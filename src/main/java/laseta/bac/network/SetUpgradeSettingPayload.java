package laseta.bac.network;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.machine.AutoMachineMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Client -> server: "set the upgrade in this machine upgrade slot of my open machine to this item". Sent when an item
 * is dragged from JEI / EMI onto an upgrade's setting box, like a filter slot of other mods. The item only describes
 * the setting: nothing is given or taken.
 *
 * @param slotIndex index of the machine upgrade slot, in the machine's features
 */
public record SetUpgradeSettingPayload(int containerId, int slotIndex, ItemStack setting) implements CustomPacketPayload {
    public static final Type<SetUpgradeSettingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "set_upgrade_setting"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetUpgradeSettingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetUpgradeSettingPayload::containerId,
            ByteBufCodecs.VAR_INT, SetUpgradeSettingPayload::slotIndex,
            ItemStack.STREAM_CODEC, SetUpgradeSettingPayload::setting,
            SetUpgradeSettingPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server thread. Ignores the request unless the player really has this menu open. */
    public static void handle(SetUpgradeSettingPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!player.isSpectator()
                && player.containerMenu instanceof AutoMachineMenu menu
                && menu.containerId == payload.containerId()
                && menu.stillValid(player)) {
            menu.setUpgradeSetting(payload.slotIndex(), payload.setting());
        }
    }
}
