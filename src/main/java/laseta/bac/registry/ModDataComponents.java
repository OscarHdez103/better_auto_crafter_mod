package laseta.bac.registry;

import java.util.List;
import java.util.function.Supplier;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.upgrade.LinkedFace;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BetterAutoCrafter.MODID);

    /** Dimension + position of the Translocator a translocation upgrade is linked to. Saved and synced to clients. */
    public static final Supplier<DataComponentType<GlobalPos>> TRANSLOCATOR_LINK = DATA_COMPONENTS.registerComponentType(
            "translocator_link",
            builder -> builder.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    /** Dimension + position of the storage a Storage Link Upgrade reads the stock from. Saved and synced to clients. */
    public static final Supplier<DataComponentType<GlobalPos>> STORAGE_LINK = DATA_COMPONENTS.registerComponentType(
            "storage_link",
            builder -> builder.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    /** The blocks (and faces) a Multi Translocation Upgrade is linked to, in link order. Saved and synced to clients. */
    public static final Supplier<DataComponentType<List<LinkedFace>>> TRANSLOCATION_TARGETS = DATA_COMPONENTS.registerComponentType(
            "translocation_targets",
            builder -> builder.persistent(LinkedFace.CODEC.listOf())
                    .networkSynchronized(LinkedFace.STREAM_CODEC.apply(ByteBufCodecs.list())));

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
