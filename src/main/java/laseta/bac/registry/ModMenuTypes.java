package laseta.bac.registry;

import java.util.function.Supplier;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.menu.BetterAutoCrafterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, BetterAutoCrafter.MODID);

    public static final Supplier<MenuType<BetterAutoCrafterMenu>> BETTER_AUTO_CRAFTER =
            MENU_TYPES.register("better_auto_crafter",
                    () -> IMenuTypeExtension.create(BetterAutoCrafterMenu::new));

    private ModMenuTypes() {
    }

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
