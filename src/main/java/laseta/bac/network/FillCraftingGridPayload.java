package laseta.bac.network;

import java.util.List;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.menu.BetterAutoCrafterMenu;
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
 * Client -> server: "fill the crafting grid of my open Better Auto Crafter with this recipe".
 * Sent by the JEI / EMI recipe transfer ("+") buttons.
 *
 * @param slots per grid slot, the items accepted there. These only describe the recipe; the items
 *              actually placed always come from the player's inventory (or the grid itself).
 * @param fillMaximum fill as many crafts as possible instead of one (shift-click)
 */
public record FillCraftingGridPayload(int containerId, List<List<ItemStack>> slots, boolean fillMaximum)
        implements CustomPacketPayload {

    public static final Type<FillCraftingGridPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "fill_crafting_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FillCraftingGridPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FillCraftingGridPayload::containerId,
            ItemStack.STREAM_CODEC
                    .apply(ByteBufCodecs.list(GridFillPlanner.MAX_CANDIDATES))
                    .apply(ByteBufCodecs.list(BetterAutoCrafterBlockEntity.GRID_SIZE)),
            FillCraftingGridPayload::slots,
            ByteBufCodecs.BOOL, FillCraftingGridPayload::fillMaximum,
            FillCraftingGridPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server thread. Ignores the request unless the player really has this menu open. */
    public static void handle(FillCraftingGridPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!player.isSpectator()
                && player.containerMenu instanceof BetterAutoCrafterMenu menu
                && menu.containerId == payload.containerId()
                && menu.stillValid(player)) {
            menu.fillGrid(player, payload.slots(), payload.fillMaximum());
        }
    }
}
