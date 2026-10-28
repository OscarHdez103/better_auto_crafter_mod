package laseta.bac.network;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.machine.RecipeChoices;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Server -> client: the recipes the open machine's template could make, and which one it makes. Sent whenever they
 * change, as menu data slots can't hold item stacks. Picking one goes back as a menu button click.
 */
public record RecipeChoicesPayload(int containerId, RecipeChoices choices) implements CustomPacketPayload {
    public static final Type<RecipeChoicesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "recipe_choices"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeChoicesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RecipeChoicesPayload::containerId,
            RecipeChoices.STREAM_CODEC, RecipeChoicesPayload::choices,
            RecipeChoicesPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client thread. Ignored if that menu isn't open any more. */
    public static void handle(RecipeChoicesPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof AutoMachineMenu menu && menu.containerId == payload.containerId()) {
            menu.setRecipeChoices(payload.choices());
        }
    }
}
