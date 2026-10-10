package laseta.bac.registry;

import java.util.function.Supplier;

import laseta.bac.BetterAutoCrafter;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BetterAutoCrafter.MODID);

    /** Dimension + position of the Translocator a translocation upgrade is linked to. Saved and synced to clients. */
    public static final Supplier<DataComponentType<GlobalPos>> TRANSLOCATOR_LINK = DATA_COMPONENTS.registerComponentType(
            "translocator_link",
            builder -> builder.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
