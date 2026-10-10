package laseta.bac.registry;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.item.StockingUpgradeItem;
import laseta.bac.item.TranslocationUpgradeItem;
import laseta.bac.upgrade.TranslocationMode;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BetterAutoCrafter.MODID);

    public static final DeferredItem<BlockItem> BETTER_AUTO_CRAFTER = ITEMS.registerSimpleBlockItem(ModBlocks.BETTER_AUTO_CRAFTER);
    public static final DeferredItem<BlockItem> TRANSLOCATOR = ITEMS.registerSimpleBlockItem(ModBlocks.TRANSLOCATOR);

    public static final DeferredItem<TranslocationUpgradeItem> TRANSLOCATION_UPGRADE_PULL =
            registerTranslocationUpgrade("translocation_upgrade_pull", TranslocationMode.PULL, false);
    public static final DeferredItem<TranslocationUpgradeItem> TRANSLOCATION_UPGRADE_PUSH =
            registerTranslocationUpgrade("translocation_upgrade_push", TranslocationMode.PUSH, false);
    public static final DeferredItem<TranslocationUpgradeItem> DIMENSIONAL_TRANSLOCATION_UPGRADE_PULL =
            registerTranslocationUpgrade("dimensional_translocation_upgrade_pull", TranslocationMode.PULL, true);
    public static final DeferredItem<TranslocationUpgradeItem> DIMENSIONAL_TRANSLOCATION_UPGRADE_PUSH =
            registerTranslocationUpgrade("dimensional_translocation_upgrade_push", TranslocationMode.PUSH, true);

    /** Each tier is crafted from 8 of the previous one and counts 8 times as much stock. */
    public static final DeferredItem<StockingUpgradeItem> STOCKING_UPGRADE =
            registerStockingUpgrade("stocking_upgrade", 1, Rarity.COMMON);
    public static final DeferredItem<StockingUpgradeItem> STOCKING_UPGRADE_TIER_2 =
            registerStockingUpgrade("stocking_upgrade_tier_2", 8, Rarity.UNCOMMON);
    public static final DeferredItem<StockingUpgradeItem> STOCKING_UPGRADE_TIER_3 =
            registerStockingUpgrade("stocking_upgrade_tier_3", 64, Rarity.RARE);

    private ModItems() {
    }

    private static DeferredItem<TranslocationUpgradeItem> registerTranslocationUpgrade(String name, TranslocationMode mode,
                                                                                    boolean dimensional) {
        return ITEMS.registerItem(name, properties -> new TranslocationUpgradeItem(mode, dimensional, properties),
                new Item.Properties().rarity(dimensional ? Rarity.RARE : Rarity.UNCOMMON));
    }

    private static DeferredItem<StockingUpgradeItem> registerStockingUpgrade(String name, int stockPerItem, Rarity rarity) {
        return ITEMS.registerItem(name, properties -> new StockingUpgradeItem(stockPerItem, properties),
                new Item.Properties().rarity(rarity));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
