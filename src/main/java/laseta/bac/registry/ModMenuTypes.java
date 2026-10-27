package laseta.bac.registry;

import java.util.function.Supplier;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.menu.BetterAutoBrewerMenu;
import laseta.bac.menu.BetterAutoMoverMenu;
import laseta.bac.menu.BetterAutoSmelterMenu;
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

    public static final Supplier<MenuType<BetterAutoMoverMenu>> BETTER_AUTO_MOVER =
            MENU_TYPES.register("better_auto_mover",
                    () -> IMenuTypeExtension.create(BetterAutoMoverMenu::new));

    public static final Supplier<MenuType<BetterAutoSmelterMenu>> BETTER_AUTO_SMELTER =
            MENU_TYPES.register("better_auto_smelter",
                    () -> IMenuTypeExtension.create(BetterAutoSmelterMenu::new));

    public static final Supplier<MenuType<BetterAutoBrewerMenu>> BETTER_AUTO_BREWER =
            MENU_TYPES.register("better_auto_brewer",
                    () -> IMenuTypeExtension.create(BetterAutoBrewerMenu::new));

    private ModMenuTypes() {
    }

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
