package laseta.bac.gametest;

import java.util.ArrayList;
import java.util.List;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.compat.StorageLinkIntegrations;
import laseta.bac.machine.AutoMachineBlock;
import laseta.bac.machine.RedstoneMode;
import laseta.bac.item.LinkingUpgradeItem;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModCreativeTabs;
import laseta.bac.registry.ModDataComponents;
import laseta.bac.registry.ModItems;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.stock.StockReading;
import laseta.bac.upgrade.UpgradeSlotType;
import laseta.bac.upgrade.UpgradeStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * In-world tests for the Storage Link Upgrades. Run with {@code ./gradlew runGameTestServer}.
 * The mod-specific tests only check something when that storage mod is installed in the test run.
 *
 * <p>Layout: the crafter faces north at {@link #CRAFTER}, powered from its back (south), with an input chest on top
 * and an output chest below; the linked storage stands a few blocks east at {@link #STORAGE}.</p>
 */
@GameTestHolder(BetterAutoCrafter.MODID)
@PrefixGameTestTemplate(false)
public final class StorageLinkGameTests {
    private static final String TEMPLATE = "empty_wide";
    private static final BlockPos CRAFTER = new BlockPos(2, 2, 2);
    private static final BlockPos REDSTONE = CRAFTER.south();
    private static final BlockPos STORAGE = new BlockPos(7, 2, 2);
    private static final int SETTLE = 5;

    private StorageLinkGameTests() {
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Counting in the linked storage
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void stockedLinkedStorageStopsCrafting(GameTestHelper helper) {
        // Limit 8; the empty output doesn't matter, the linked storage already holds 8 sticks.
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container input = placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container output = placeChest(helper, CRAFTER.below());
        placeChest(helper, STORAGE, new ItemStack(Items.STICK, 8));
        install(crafter, UpgradeSlotType.STOCKING, new ItemStack(ModItems.STOCKING_UPGRADE_TIER_2.get()));
        install(crafter, UpgradeSlotType.STORAGE_LINK, linked(helper, ModItems.STORAGE_LINK_UPGRADE.get().getDefaultInstance(), STORAGE));

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, input, Items.OAK_PLANKS, 10, "nothing pulled");
            assertCount(helper, output, Items.STICK, 0, "nothing crafted");
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.FAILED, true);
            helper.assertTrue(crafter.getUpgradeStatus(UpgradeSlotType.STORAGE_LINK) == UpgradeStatus.ACTIVE, "link works");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void understockedLinkedStorageCraftsIntoTheOutputs(GameTestHelper helper) {
        // The output already holds 64 sticks, but only the linked storage (7 of 8) counts.
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container output = placeChest(helper, CRAFTER.below(), new ItemStack(Items.STICK, 64));
        Container storage = placeChest(helper, STORAGE, new ItemStack(Items.STICK, 7));
        install(crafter, UpgradeSlotType.STOCKING, new ItemStack(ModItems.STOCKING_UPGRADE_TIER_2.get()));
        install(crafter, UpgradeSlotType.STORAGE_LINK, linked(helper, ModItems.STORAGE_LINK_UPGRADE.get().getDefaultInstance(), STORAGE));

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(output.countItem(Items.STICK) >= 68, "crafted into the output, found " + output.countItem(Items.STICK));
            assertCount(helper, storage, Items.STICK, 7, "the linked storage is only read");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void brokenStorageLinkStopsCrafting(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container input = placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        placeChest(helper, CRAFTER.below());
        install(crafter, UpgradeSlotType.STORAGE_LINK, ModItems.STORAGE_LINK_UPGRADE.get().getDefaultInstance());
        helper.assertTrue(crafter.getUpgradeStatus(UpgradeSlotType.STORAGE_LINK) == UpgradeStatus.NOT_LINKED, "unlinked");

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, input, Items.OAK_PLANKS, 10, "an unlinked upgrade stops crafting");
            // Linked to a block without an inventory.
            install(crafter, UpgradeSlotType.STORAGE_LINK, linked(helper, ModItems.STORAGE_LINK_UPGRADE.get().getDefaultInstance(), STORAGE));
            helper.assertTrue(crafter.getUpgradeStatus(UpgradeSlotType.STORAGE_LINK) == UpgradeStatus.STORAGE_MISSING,
                    "nothing to read at the linked position");
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Linking, slot, recipes
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void storageLinkLinksTheWholeStackToInventoriesOnly(GameTestHelper helper) {
        placeChest(helper, STORAGE);
        helper.setBlock(STORAGE.east(), Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STORAGE_LINK_UPGRADE.get(), 5));

        useOn(helper, player, STORAGE.east());
        helper.assertTrue(LinkingUpgradeItem.getLink(player.getMainHandItem()) == null, "stone has no inventory");
        useOn(helper, player, STORAGE);
        ItemStack held = player.getMainHandItem();
        helper.assertTrue(held.getCount() == 5
                        && GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(STORAGE)).equals(LinkingUpgradeItem.getLink(held)),
                "all 5 linked to the chest, got " + held);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void storageLinkSlotAndRecipes(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        int slot = UpgradeSlotType.STORAGE_LINK.ordinal();
        helper.assertTrue(crafter.getUpgrades().insertItem(slot, new ItemStack(ModItems.STORAGE_LINK_UPGRADE.get(), 3), false).getCount() == 2,
                "the slot holds one Storage Link Upgrade");
        helper.assertTrue(crafter.getUpgrades().insertItem(slot, ModItems.STOCKING_UPGRADE.get().getDefaultInstance(), false).getCount() == 1,
                "and nothing else");

        ItemStack paper = new ItemStack(Items.PAPER);
        ItemStack comparator = new ItemStack(Items.COMPARATOR);
        ItemStack crafted = craft(helper, paper, paper, paper, comparator, new ItemStack(Items.ENDER_PEARL), comparator,
                paper, paper, paper);
        helper.assertTrue(crafted.is(ModItems.STORAGE_LINK_UPGRADE.get()), "crafted from paper, an ender pearl and comparators, got " + crafted);
        ItemStack reset = craft(helper, linked(helper, ModItems.STORAGE_LINK_UPGRADE.get().getDefaultInstance(), STORAGE));
        helper.assertTrue(reset.is(ModItems.STORAGE_LINK_UPGRADE.get()) && LinkingUpgradeItem.getLink(reset) == null,
                "crafting it alone clears the link, got " + reset);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Storage mods
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void modStorageLinksOnlyExistWithTheirMod(GameTestHelper helper) {
        int available = 0;
        for (StorageLinkIntegrations.Integration integration : StorageLinkIntegrations.ALL) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, integration.itemName());
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id) == integration.isAvailable(),
                    id + " must exist exactly when " + integration.modId() + " is loaded");
            if (integration.isAvailable()) {
                available++;
                Item item = BuiltInRegistries.ITEM.get(id);
                helper.assertTrue(UpgradeSlotType.forStack(item.getDefaultInstance()) == UpgradeSlotType.STORAGE_LINK,
                        id + " goes in the storage link slot");
            }
        }
        helper.assertTrue(ModItems.MOD_STORAGE_LINK_UPGRADES.size() == available, "one item per installed storage mod");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void everyItemIsInTheModsCreativeTab(GameTestHelper helper) {
        CreativeModeTab tab = ModCreativeTabs.MAIN.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(helper.getLevel().enabledFeatures(), false,
                helper.getLevel().registryAccess()));
        for (DeferredHolder<Item, ? extends Item> item : ModItems.ITEMS.getEntries()) {
            helper.assertTrue(tab.contains(item.get().getDefaultInstance()), item.getId() + " must be in the mod's tab");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void ae2StorageLinkReadsTheNetwork(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ae2")) {
            helper.succeed();
            return;
        }
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Item upgrade = item("me_storage_link_upgrade");
        helper.setBlock(STORAGE, block("ae2", "controller"));
        placeChest(helper, STORAGE.south());
        assertLinksOnlyTo(helper, upgrade, STORAGE, STORAGE.south());
        install(crafter, UpgradeSlotType.STORAGE_LINK, linked(helper, upgrade.getDefaultInstance(), STORAGE));

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(crafter.getUpgradeStatus(UpgradeSlotType.STORAGE_LINK) == UpgradeStatus.NETWORK_OFFLINE,
                    "an unpowered ME network is offline, got " + crafter.getUpgradeStatus(UpgradeSlotType.STORAGE_LINK));
            // Power it and give it storage: a drive with a 1k cell, filled through an ME interface.
            helper.setBlock(STORAGE.above(), block("ae2", "creative_energy_cell"));
            helper.setBlock(STORAGE.east(), block("ae2", "drive"));
            helper.setBlock(STORAGE.north(), block("ae2", "interface"));
        });
        helper.runAfterDelay(60, () -> {
            IItemHandler drive = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(STORAGE.east()), null);
            helper.assertTrue(drive != null, "drive accepts cells");
            drive.insertItem(0, item("ae2", "item_storage_cell_1k").getDefaultInstance(), false);
        });
        helper.runAfterDelay(80, () -> {
            IItemHandler network = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(STORAGE.north()), Direction.SOUTH);
            helper.assertTrue(network != null, "interface accepts items");
            ItemHandlerHelper.insertItemStacked(network, new ItemStack(Items.STICK, 40), false);
        });
        helper.runAfterDelay(100, () -> helper.succeedWhen(() -> {
            StockReading reading = crafter.resolveStorageLink();
            helper.assertTrue(reading.status() == UpgradeStatus.ACTIVE && reading.counter() != null,
                    "a powered ME network works, got " + reading.status());
            long sticks = reading.counter().count(new ItemStack(Items.STICK), 1000);
            helper.assertTrue(sticks == 40, "counts the 40 sticks stored in the network, got " + sticks);
        }));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void refinedStorageLinkReadsTheNetwork(GameTestHelper helper) {
        if (!ModList.get().isLoaded("refinedstorage")) {
            helper.succeed();
            return;
        }
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Item upgrade = item("refined_storage_link_upgrade");
        helper.setBlock(STORAGE, block("refinedstorage", "controller"));
        BlockPos creative = STORAGE.north(2); // not touching the other controller, so a network of its own
        helper.setBlock(creative, block("refinedstorage", "creative_controller"));
        placeChest(helper, STORAGE.above());
        assertLinksOnlyTo(helper, upgrade, STORAGE, STORAGE.above());

        install(crafter, UpgradeSlotType.STORAGE_LINK, linked(helper, upgrade.getDefaultInstance(), STORAGE));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(crafter.getUpgradeStatus(UpgradeSlotType.STORAGE_LINK) == UpgradeStatus.NETWORK_OFFLINE,
                    "a controller without energy is offline, got " + crafter.getUpgradeStatus(UpgradeSlotType.STORAGE_LINK));
            install(crafter, UpgradeSlotType.STORAGE_LINK, linked(helper, upgrade.getDefaultInstance(), creative));
            StockReading reading = crafter.resolveStorageLink();
            helper.assertTrue(reading.status() == UpgradeStatus.ACTIVE && reading.counter() != null,
                    "a creative controller's network works, got " + reading.status());
            helper.assertTrue(reading.counter().count(new ItemStack(Items.STICK), 64) == 0, "an empty network holds nothing");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void refinedStorageExternalStorageCountIsNeverOutdated(GameTestHelper helper) {
        // Taking items out through the network updates Refined Storage's copy of an external inventory right away,
        // but a craft put straight into that inventory is only seen when Refined Storage rescans it (which can take
        // up to 2 seconds). The count must include it immediately, or the crafter crafts one too many.
        if (!ModList.get().isLoaded("refinedstorage")) {
            helper.succeed();
            return;
        }
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        helper.setBlock(STORAGE, block("refinedstorage", "creative_controller"));
        helper.setBlock(STORAGE.north(), withProperty(block("refinedstorage", "external_storage").defaultBlockState(), "direction", "north"));
        Container chest = placeChest(helper, STORAGE.north(2), new ItemStack(Items.STICK, 9));
        install(crafter, UpgradeSlotType.STORAGE_LINK, linked(helper, item("refined_storage_link_upgrade").getDefaultInstance(), STORAGE));
        ItemStack stick = new ItemStack(Items.STICK);

        helper.runAfterDelay(40, () -> {
            StockReading reading = crafter.resolveStorageLink();
            helper.assertTrue(reading.status() == UpgradeStatus.ACTIVE && reading.counter() != null, "network works, got " + reading.status());
            helper.assertTrue(reading.counter().count(stick, 8) == 9, "the external chest's 9 sticks are counted");

            long taken = RefinedStorageTestHelper.extractThroughNetwork(helper.getLevel(), helper.absolutePos(STORAGE), stick, 2);
            helper.assertTrue(taken == 2 && chest.countItem(Items.STICK) == 7, "2 taken out through the network");
            chest.setItem(1, new ItemStack(Items.STICK)); // a craft delivered straight into the chest: 8 now
            long counted = crafter.resolveStorageLink().counter().count(stick, 8);
            helper.assertTrue(counted == 8, "the delivered stick counts right away (8), got " + counted);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void tomsStorageLinkCountsTheConnectorsInventories(GameTestHelper helper) {
        if (!ModList.get().isLoaded("toms_storage")) {
            helper.succeed();
            return;
        }
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Item upgrade = item("toms_storage_link_upgrade");
        helper.setBlock(STORAGE, block("toms_storage", "inventory_connector"));
        placeChest(helper, STORAGE.east(), new ItemStack(Items.STICK, 20));
        placeChest(helper, STORAGE.above(), new ItemStack(Items.STICK, 30));
        assertLinksOnlyTo(helper, upgrade, STORAGE, STORAGE.east());

        install(crafter, UpgradeSlotType.STORAGE_LINK, linked(helper, upgrade.getDefaultInstance(), STORAGE));
        // The connector finds its inventories over its first ticks.
        helper.succeedWhen(() -> {
            StockReading reading = crafter.resolveStorageLink();
            helper.assertTrue(reading.status() == UpgradeStatus.ACTIVE && reading.counter() != null, "connector works, got " + reading.status());
            long sticks = reading.counter().count(new ItemStack(Items.STICK), 1000);
            helper.assertTrue(sticks == 50, "counts both chests (50 sticks), got " + sticks);
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------------

    private static BetterAutoCrafterBlockEntity placeStickCrafter(GameTestHelper helper) {
        helper.setBlock(CRAFTER, ModBlocks.BETTER_AUTO_CRAFTER.get().defaultBlockState()
                .setValue(AutoMachineBlock.FACING, Direction.NORTH));
        BetterAutoCrafterBlockEntity crafter = helper.getBlockEntity(CRAFTER);
        crafter.getSettings().setRedstoneMode(RedstoneMode.ON_POWERED);
        ItemStack plank = new ItemStack(Items.OAK_PLANKS);
        crafter.getTemplate().setStackInSlot(1, plank.copy());
        crafter.getTemplate().setStackInSlot(4, plank.copy());
        crafter.getSideConfig().setMode(RelativeSide.TOP, SideMode.INPUT);
        crafter.getSideConfig().setMode(RelativeSide.BOTTOM, SideMode.OUTPUT);
        return crafter;
    }

    /** The upgrade links to {@code valid} but not to {@code invalid}. */
    private static void assertLinksOnlyTo(GameTestHelper helper, Item upgrade, BlockPos valid, BlockPos invalid) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, upgrade.getDefaultInstance());
        useOn(helper, player, invalid);
        helper.assertTrue(LinkingUpgradeItem.getLink(player.getMainHandItem()) == null, upgrade + " must not link to " + invalid);
        useOn(helper, player, valid);
        helper.assertTrue(LinkingUpgradeItem.getLink(player.getMainHandItem()) != null, upgrade + " must link to " + valid);
    }

    private static void useOn(GameTestHelper helper, Player player, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    private static ItemStack linked(GameTestHelper helper, ItemStack upgrade, BlockPos relativePos) {
        upgrade.set(ModDataComponents.STORAGE_LINK.get(), GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relativePos)));
        return upgrade;
    }

    private static void install(BetterAutoCrafterBlockEntity crafter, UpgradeSlotType type, ItemStack upgrade) {
        crafter.getUpgrades().setStackInSlot(type.ordinal(), upgrade);
    }

    private static Item item(String name) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, name));
    }

    private static Item item(String namespace, String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState withProperty(BlockState state, String name, String value) {
        Property property = state.getBlock().getStateDefinition().getProperty(name);
        return state.setValue(property, (Comparable) property.getValue(value).orElseThrow());
    }

    private static Block block(String namespace, String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private static Container placeChest(GameTestHelper helper, BlockPos pos, ItemStack... contents) {
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(pos);
        for (int i = 0; i < contents.length; i++) {
            chest.setItem(i, contents[i]);
        }
        return chest;
    }

    private static void power(GameTestHelper helper) {
        helper.setBlock(REDSTONE, Blocks.REDSTONE_BLOCK);
    }

    private static void assertCount(GameTestHelper helper, Container container, Item item, int expected, String what) {
        int actual = container.countItem(item);
        helper.assertTrue(actual == expected, what + ": expected " + expected + " but found " + actual);
    }

    private static ItemStack craft(GameTestHelper helper, ItemStack... grid) {
        List<ItemStack> items = new ArrayList<>(List.of(grid));
        while (items.size() < 9) {
            items.add(ItemStack.EMPTY);
        }
        CraftingInput input = CraftingInput.of(3, 3, items);
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(holder -> holder.value().assemble(input, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
    }
}
