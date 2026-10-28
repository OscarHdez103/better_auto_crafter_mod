package laseta.bac.registry;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.material.NoOtherModInTagCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Data conditions ({@code "neoforge:conditions"} in recipes, advancements, ...). */
public final class ModConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, BetterAutoCrafter.MODID);

    public static final Supplier<MapCodec<NoOtherModInTagCondition>> NO_OTHER_MOD_IN_TAG =
            CONDITION_CODECS.register("no_other_mod_in_tag", () -> NoOtherModInTagCondition.CODEC);

    private ModConditions() {
    }

    public static void register(IEventBus modEventBus) {
        CONDITION_CODECS.register(modEventBus);
    }
}
