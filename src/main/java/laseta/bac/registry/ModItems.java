package laseta.bac.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.compat.StorageLinkIntegrations;
import laseta.bac.item.CraftingComponentItem;
import laseta.bac.item.MultiTranslocationUpgradeItem;
import laseta.bac.item.StockingUpgradeItem;
import laseta.bac.item.StorageLinkUpgradeItem;
import laseta.bac.item.TranslocationUpgradeItem;
import laseta.bac.stock.InventoryStockReader;
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
    public static final DeferredItem<BlockItem> BETTER_AUTO_MOVER = ITEMS.registerSimpleBlockItem(ModBlocks.BETTER_AUTO_MOVER);
    public static final DeferredItem<BlockItem> TRANSLOCATOR = ITEMS.registerSimpleBlockItem(ModBlocks.TRANSLOCATOR);

    /** Only made by the Better Auto Crafter (an auto crafting recipe); goes into the recipes of the mod's machines. */
    public static final DeferredItem<CraftingComponentItem> BETTER_MATERIAL = ITEMS.registerItem("better_material",
            CraftingComponentItem::new, new Item.Properties());

    public static final DeferredItem<TranslocationUpgradeItem> TRANSLOCATION_UPGRADE_PULL =
            registerTranslocationUpgrade("translocation_upgrade_pull", TranslocationMode.PULL, false);
    public static final DeferredItem<TranslocationUpgradeItem> TRANSLOCATION_UPGRADE_PUSH =
            registerTranslocationUpgrade("translocation_upgrade_push", TranslocationMode.PUSH, false);
    public static final DeferredItem<TranslocationUpgradeItem> DIMENSIONAL_TRANSLOCATION_UPGRADE_PULL =
            registerTranslocationUpgrade("dimensional_translocation_upgrade_pull", TranslocationMode.PULL, true);
    public static final DeferredItem<TranslocationUpgradeItem> DIMENSIONAL_TRANSLOCATION_UPGRADE_PUSH =
            registerTranslocationUpgrade("dimensional_translocation_upgrade_push", TranslocationMode.PUSH, true);

    /** Go into the same slots as the upgrades above, but link up to 8 blocks directly instead of a Translocator. */
    public static final DeferredItem<MultiTranslocationUpgradeItem> MULTI_TRANSLOCATION_UPGRADE_PULL =
            registerMultiTranslocationUpgrade("multi_translocation_upgrade_pull", TranslocationMode.PULL, false);
    public static final DeferredItem<MultiTranslocationUpgradeItem> MULTI_TRANSLOCATION_UPGRADE_PUSH =
            registerMultiTranslocationUpgrade("multi_translocation_upgrade_push", TranslocationMode.PUSH, false);
    public static final DeferredItem<MultiTranslocationUpgradeItem> DIMENSIONAL_MULTI_TRANSLOCATION_UPGRADE_PULL =
            registerMultiTranslocationUpgrade("dimensional_multi_translocation_upgrade_pull", TranslocationMode.PULL, true);
    public static final DeferredItem<MultiTranslocationUpgradeItem> DIMENSIONAL_MULTI_TRANSLOCATION_UPGRADE_PUSH =
            registerMultiTranslocationUpgrade("dimensional_multi_translocation_upgrade_push", TranslocationMode.PUSH, true);

    /** Each tier is crafted from 8 of the previous one and counts 8 times as much stock. */
    public static final DeferredItem<StockingUpgradeItem> STOCKING_UPGRADE =
            registerStockingUpgrade("stocking_upgrade", 1, Rarity.COMMON);
    public static final DeferredItem<StockingUpgradeItem> STOCKING_UPGRADE_TIER_2 =
            registerStockingUpgrade("stocking_upgrade_tier_2", 8, Rarity.UNCOMMON);
    public static final DeferredItem<StockingUpgradeItem> STOCKING_UPGRADE_TIER_3 =
            registerStockingUpgrade("stocking_upgrade_tier_3", 64, Rarity.RARE);

    /** Makes the stocking upgrades count in any linked inventory. */
    public static final DeferredItem<StorageLinkUpgradeItem> STORAGE_LINK_UPGRADE = ITEMS.registerItem("storage_link_upgrade",
            properties -> new StorageLinkUpgradeItem(new InventoryStockReader(), 0x40D0B0, properties),
            new Item.Properties().rarity(Rarity.UNCOMMON));
    /** The Storage Link Upgrades for the storage mods that are installed (see {@link StorageLinkIntegrations}). */
    public static final List<DeferredItem<StorageLinkUpgradeItem>> MOD_STORAGE_LINK_UPGRADES = registerModStorageLinkUpgrades();

    private ModItems() {
    }

    private static List<DeferredItem<StorageLinkUpgradeItem>> registerModStorageLinkUpgrades() {
        List<DeferredItem<StorageLinkUpgradeItem>> items = new ArrayList<>();
        for (StorageLinkIntegrations.Integration integration : StorageLinkIntegrations.ALL) {
            if (integration.isAvailable()) {
                items.add(ITEMS.registerItem(integration.itemName(),
                        properties -> new StorageLinkUpgradeItem(integration.reader().get(), integration.color(), properties),
                        new Item.Properties().rarity(integration.rarity())));
            }
        }
        return Collections.unmodifiableList(items);
    }

    private static DeferredItem<TranslocationUpgradeItem> registerTranslocationUpgrade(String name, TranslocationMode mode,
                                                                                    boolean dimensional) {
        return ITEMS.registerItem(name, properties -> new TranslocationUpgradeItem(mode, dimensional, properties),
                new Item.Properties().rarity(dimensional ? Rarity.RARE : Rarity.UNCOMMON));
    }

    private static DeferredItem<MultiTranslocationUpgradeItem> registerMultiTranslocationUpgrade(String name, TranslocationMode mode,
                                                                                              boolean dimensional) {
        return ITEMS.registerItem(name, properties -> new MultiTranslocationUpgradeItem(mode, dimensional, properties),
                new Item.Properties().rarity(dimensional ? Rarity.EPIC : Rarity.RARE));
    }

    private static DeferredItem<StockingUpgradeItem> registerStockingUpgrade(String name, int stockPerItem, Rarity rarity) {
        return ITEMS.registerItem(name, properties -> new StockingUpgradeItem(stockPerItem, properties),
                new Item.Properties().rarity(rarity));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
