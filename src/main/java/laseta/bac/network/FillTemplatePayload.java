package laseta.bac.network;

import java.util.List;
import java.util.Optional;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.menu.transfer.GridFillPlanner;
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
 * Client -> server: "fill the template of my open machine with this recipe" (the crafter's grid, the smelter's input).
 * Sent by the JEI / EMI recipe transfer ("+") buttons.
 *
 * @param slots       per template slot, the items accepted there, with the count one operation takes. These only
 *                    describe the recipe; the items actually placed always come from the player's inventory (or the
 *                    template itself).
 * @param fillMaximum fill as many operations' worth as possible instead of one (shift-click)
 * @param recipe      the recipe's id, which the machine then uses if the template makes several
 */
public record FillTemplatePayload(int containerId, List<List<ItemStack>> slots, boolean fillMaximum,
                                  Optional<ResourceLocation> recipe) implements CustomPacketPayload {

    public static final Type<FillTemplatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "fill_template"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FillTemplatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FillTemplatePayload::containerId,
            ItemStack.STREAM_CODEC
                    .apply(ByteBufCodecs.list(GridFillPlanner.MAX_CANDIDATES))
                    .apply(ByteBufCodecs.list(BetterAutoCrafterBlockEntity.GRID_SIZE)),
            FillTemplatePayload::slots,
            ByteBufCodecs.BOOL, FillTemplatePayload::fillMaximum,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), FillTemplatePayload::recipe,
            FillTemplatePayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server thread. Ignores the request unless the player really has this menu open. */
    public static void handle(FillTemplatePayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!player.isSpectator()
                && player.containerMenu instanceof AutoMachineMenu menu
                && menu.containerId == payload.containerId()
                && menu.stillValid(player)) {
            menu.fillTemplate(player, payload.slots(), payload.fillMaximum(), payload.recipe().orElse(null));
        }
    }
}
