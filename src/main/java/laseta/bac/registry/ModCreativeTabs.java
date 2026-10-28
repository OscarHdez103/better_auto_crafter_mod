package laseta.bac.registry;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.material.Materials;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BetterAutoCrafter.MODID);

    /**
     * Every item of the mod, in registration order (so new items, and the storage mods' upgrades, appear by themselves),
     * except its items of materials another mod provides instead.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + BetterAutoCrafter.MODID))
                    .icon(() -> ModItems.BETTER_AUTO_CRAFTER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> ModItems.ITEMS.getEntries().stream()
                            .map(DeferredHolder::get)
                            .filter(item -> !Materials.isHidden(item, parameters.holders()))
                            .forEach(output::accept))
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
