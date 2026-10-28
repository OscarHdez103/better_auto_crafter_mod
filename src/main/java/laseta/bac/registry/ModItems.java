package laseta.bac.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.compat.StorageLinkIntegrations;
import laseta.bac.item.CoprocessingUpgradeItem;
import laseta.bac.item.CraftingComponentItem;
import laseta.bac.item.FuelFilterUpgradeItem;
import laseta.bac.item.InfiniteWaterUpgradeItem;
import laseta.bac.item.MultiTranslocationUpgradeItem;
import laseta.bac.item.PotionStarterUpgradeItem;
import laseta.bac.item.SpeedUpgradeItem;
import laseta.bac.item.StockingUpgradeItem;
import laseta.bac.item.StorageLinkUpgradeItem;
import laseta.bac.item.TranslocationUpgradeItem;
import laseta.bac.stock.InventoryStockReader;
import laseta.bac.upgrade.TranslocationMode;
import net.minecraft.resources.ResourceLocation;
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
    public static final DeferredItem<BlockItem> BETTER_AUTO_SMELTER = ITEMS.registerSimpleBlockItem(ModBlocks.BETTER_AUTO_SMELTER);
    public static final DeferredItem<BlockItem> BETTER_AUTO_BREWER = ITEMS.registerSimpleBlockItem(ModBlocks.BETTER_AUTO_BREWER);
    public static final DeferredItem<BlockItem> TRANSLOCATOR = ITEMS.registerSimpleBlockItem(ModBlocks.TRANSLOCATOR);

    /** Only made by the Better Auto Crafter (an auto crafting recipe); goes into the recipes of the mod's machines. */
    public static final DeferredItem<CraftingComponentItem> BETTER_MATERIAL = ITEMS.registerItem("better_material",
            CraftingComponentItem::new, new Item.Properties());
    /** Better Material infused with steel: only brewed in the Better Auto Brewer (an auto brewing recipe). */
    public static final DeferredItem<CraftingComponentItem> INFUSED_BETTER_MATERIAL = ITEMS.registerItem("infused_better_material",
            CraftingComponentItem::new, new Item.Properties());

    /**
     * Steel, made in the Better Auto Smelter. Only used while no other mod adds steel: see
     * {@link laseta.bac.material.Materials#STEEL}.
     */
    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem("steel_ingot");
    public static final DeferredItem<Item> STEEL_NUGGET = ITEMS.registerSimpleItem("steel_nugget");
    public static final DeferredItem<BlockItem> STEEL_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.STEEL_BLOCK);

    public static final DeferredItem<TranslocationUpgradeItem> TRANSLOCATION_UPGRADE_PULL =
            registerTranslocationUpgrade("translocation_upgrade_pull", TranslocationMode.PULL, false);
    public static final DeferredItem<TranslocationUpgradeItem> TRANSLOCATION_UPGRADE_PUSH =
            registerTranslocationUpgrade("translocation_upgrade_push", TranslocationMode.PUSH, false);
    public static final DeferredItem<TranslocationUpgradeItem> DIMENSIONAL_TRANSLOCATION_UPGRADE_PULL =
            registerTranslocationUpgrade("dimensional_translocation_upgrade_pull", TranslocationMode.PULL, true);
    public static final DeferredItem<TranslocationUpgradeItem> DIMENSIONAL_TRANSLOCATION_UPGRADE_PUSH =
            registerTranslocationUpgrade("dimensional_translocation_upgrade_push", TranslocationMode.PUSH, true);

    /** Go into the same slots as the upgrades above, but link several blocks directly (up to the config's limit) instead of a Translocator. */
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
    /** Make every operation shorter, and repeat it several times per run. */
    public static final DeferredItem<SpeedUpgradeItem> SPEED_UPGRADE = ITEMS.registerItem("speed_upgrade",
            SpeedUpgradeItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<CoprocessingUpgradeItem> COPROCESSING_UPGRADE = ITEMS.registerItem("coprocessing_upgrade",
            CoprocessingUpgradeItem::new, new Item.Properties().rarity(Rarity.RARE));

    /** Upgrades only some machines take, in their own tab: the brewer's starter slot and the smelter's fuel filter slot. */
    public static final DeferredItem<InfiniteWaterUpgradeItem> INFINITE_WATER_UPGRADE = ITEMS.registerItem("infinite_water_upgrade",
            InfiniteWaterUpgradeItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<PotionStarterUpgradeItem> POTION_STARTER_UPGRADE = ITEMS.registerItem("potion_starter_upgrade",
            PotionStarterUpgradeItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<FuelFilterUpgradeItem> FUEL_FILTER_UPGRADE = ITEMS.registerItem("fuel_filter_upgrade",
            FuelFilterUpgradeItem::new, new Item.Properties());

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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, path);
    }

    public static void register(IEventBus modEventBus) {
        // Renamed items: worlds saved with the old id load them as the new one.
        ITEMS.addAlias(id("co_processor_upgrade"), COPROCESSING_UPGRADE.getId());
        ITEMS.register(modEventBus);
    }
}
