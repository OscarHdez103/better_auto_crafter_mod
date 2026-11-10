package laseta.bac.gametest;

import java.util.List;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.CommonConfig;
import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.block.entity.BetterAutoHubBlockEntity;
import laseta.bac.block.entity.BetterAutoSmelterBlockEntity;
import laseta.bac.item.SpeedUpgradeItem;
import laseta.bac.machine.AutoMachineBlock;
import laseta.bac.machine.AutoMachineBlockEntity;
import laseta.bac.machine.HostTabs;
import laseta.bac.machine.MachineState;
import laseta.bac.machine.RedstoneMode;
import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.menu.BetterAutoHubMenu;
import laseta.bac.menu.slot.HostTabSlot;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModDataComponents;
import laseta.bac.registry.ModItems;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.upgrade.UpgradeSlotType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Better Auto Hub: machines in its tabs run as that many machines, through the hub's block, each tab with its own
 * template, upgrades and settings; what is in them is never lost. Only its starting tabs are unlocked, Hub Expansions
 * unlock the others. The hub faces north; inputs come from the top and outputs go to the bottom. Expected speeds come
 * from the config, whatever it says.
 */
@GameTestHolder(BetterAutoCrafter.MODID)
@PrefixGameTestTemplate(false)
public final class HubGameTests {
    private static final String TEMPLATE = "empty";
    private static final BlockPos HUB = new BlockPos(2, 2, 2);
    private static final BlockPos TOP = HUB.above();
    private static final BlockPos BOTTOM = HUB.below();

    private HubGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void aTabRunsAsManyMachinesAsItHolds(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeHub(helper);
        Container input = placeChest(helper, TOP, new ItemStack(Items.OAK_LOG, 10));
        Container output = placeChest(helper, BOTTOM);
        AutoMachineBlockEntity crafter = putMachines(hub, 0, ModItems.BETTER_AUTO_CRAFTER.get(), 3);
        crafter.getTemplate().setStackInSlot(0, new ItemStack(Items.OAK_LOG));

        helper.assertTrue(crafter instanceof BetterAutoCrafterBlockEntity, "a crafter runs the tab");
        helper.assertTrue(crafter.getFacing() == Direction.NORTH, "it faces where the hub faces");
        helper.assertTrue(crafter.attemptOperation(), "crafts");
        assertCount(helper, output, Items.OAK_PLANKS, 12, "3 crafters' planks");
        assertCount(helper, input, Items.OAK_LOG, 7, "3 logs taken");
        helper.assertTrue(hub.getLamp(0) == BetterAutoHubBlockEntity.LAMP_RUNNING, "its lamp is lit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void tabsRunTogetherWithTheirOwnUpgrades(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeHub(helper);
        placeChest(helper, TOP, new ItemStack(Items.OAK_LOG, 10), new ItemStack(Items.BEEF, 10));
        Container output = placeChest(helper, BOTTOM);
        AutoMachineBlockEntity crafter = putMachines(hub, 0, ModItems.BETTER_AUTO_CRAFTER.get(), 2);
        crafter.getTemplate().setStackInSlot(0, new ItemStack(Items.OAK_LOG));
        AutoMachineBlockEntity smelter = putMachines(hub, 1, ModItems.BETTER_AUTO_SMELTER.get(), 3);
        smelter.getTemplate().setStackInSlot(0, new ItemStack(Items.BEEF));
        smelter.getUpgrades().setStackInSlot(UpgradeSlotType.SPEED.ordinal(), new ItemStack(ModItems.SPEED_UPGRADE.get(), 4));

        helper.assertTrue(smelter instanceof BetterAutoSmelterBlockEntity, "a smelter runs the second tab");
        helper.assertTrue(crafter.attemptOperation() && smelter.attemptOperation(), "both run");
        assertCount(helper, output, Items.OAK_PLANKS, 8, "2 crafters' planks");
        assertCount(helper, output, Items.COOKED_BEEF, 3, "3 smelters' steaks");
        int crafterTicks = hubTicks(CommonConfig.crafterBaseSpeed(), 0);
        int smelterTicks = hubTicks(CommonConfig.smelterBaseSpeed(), 4);
        helper.assertTrue(crafter.getStatus().getCooldownTotal() == crafterTicks, "the crafters have no upgrade: expected "
                + crafterTicks + ", got " + crafter.getStatus().getCooldownTotal());
        helper.assertTrue(smelter.getStatus().getCooldownTotal() == smelterTicks, "the smelters' 4 speed upgrades: expected "
                + smelterTicks + ", got " + smelter.getStatus().getCooldownTotal());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void changingHowManyMachinesKeepsTheTab(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeHub(helper);
        AutoMachineBlockEntity crafter = putMachines(hub, 0, ModItems.BETTER_AUTO_CRAFTER.get(), 2);
        crafter.getTemplate().setStackInSlot(0, new ItemStack(Items.OAK_LOG));

        putMachines(hub, 0, ModItems.BETTER_AUTO_CRAFTER.get(), 8);
        helper.assertTrue(hub.getMachine(0) == crafter, "the same machine");
        helper.assertTrue(crafter.getCopies() == 8, "now as 8 crafters");
        helper.assertTrue(crafter.getTemplate().getStackInSlot(0).is(Items.OAK_LOG), "its grid kept");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void onlyMachinesGoIntoTabs(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeHub(helper);
        helper.assertTrue(hub.getTabItems().isItemValid(0, new ItemStack(ModItems.BETTER_AUTO_MOVER.get())), "a mover");
        helper.assertTrue(hub.getTabItems().isItemValid(0, new ItemStack(ModItems.BETTER_AUTO_BREWER.get())), "a brewer");
        helper.assertTrue(!hub.getTabItems().isItemValid(0, new ItemStack(ModItems.BETTER_AUTO_HUB.get())), "not a hub");
        helper.assertTrue(!hub.getTabItems().isItemValid(0, new ItemStack(Items.CRAFTER)), "not a vanilla crafter");
        ItemStack rest = hub.getTabItems().insertItem(0, new ItemStack(ModItems.BETTER_AUTO_CRAFTER.get(), 10), false);
        int perTab = CommonConfig.hubMachinesPerTab();
        helper.assertTrue(rest.getCount() == Math.max(0, 10 - perTab), perTab + " per tab, got " + (10 - rest.getCount()));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void takingTheMachinesOutGivesBackWhatWasInThem(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeHub(helper);
        AutoMachineBlockEntity crafter = putMachines(hub, 0, ModItems.BETTER_AUTO_CRAFTER.get(), 2);
        crafter.getTemplate().setStackInSlot(0, new ItemStack(Items.OAK_LOG, 5));
        crafter.getUpgrades().setStackInSlot(UpgradeSlotType.SPEED.ordinal(), new ItemStack(ModItems.SPEED_UPGRADE.get(), 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AbstractContainerMenu menu = crafter.createMenu(0, player.getInventory(), player);

        helper.assertTrue(menu instanceof BetterAutoCrafterMenu crafterMenu && crafterMenu.getHostTabs() != null,
                "the crafter's own menu, with the hub's tabs");
        menu.clicked(tabSlot(menu, 0), 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(hub.getMachine(0) == null && hub.getTabItems().getStackInSlot(0).isEmpty(), "the tab is empty");
        helper.assertTrue(player.getInventory().countItem(ModItems.BETTER_AUTO_CRAFTER.get()) == 2, "the crafters taken");
        helper.assertTrue(player.getInventory().countItem(Items.OAK_LOG) == 5, "the grid's logs given back");
        helper.assertTrue(player.getInventory().countItem(ModItems.SPEED_UPGRADE.get()) == 3, "the upgrades given back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anotherKindOfMachineGivesBackTheOldOne(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeHub(helper);
        AutoMachineBlockEntity crafter = putMachines(hub, 0, ModItems.BETTER_AUTO_CRAFTER.get(), 1);
        crafter.getTemplate().setStackInSlot(4, new ItemStack(Items.STICK));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AbstractContainerMenu menu = crafter.createMenu(0, player.getInventory(), player);

        menu.setCarried(new ItemStack(ModItems.BETTER_AUTO_SMELTER.get(), 2));
        menu.clicked(tabSlot(menu, 0), 0, ClickType.PICKUP, player);
        helper.assertTrue(hub.getMachine(0) instanceof BetterAutoSmelterBlockEntity, "smelters run the tab now");
        helper.assertTrue(menu.getCarried().is(ModItems.BETTER_AUTO_CRAFTER.get()), "the crafter is picked up");
        helper.assertTrue(player.getInventory().countItem(Items.STICK) == 1, "its grid given back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void otherTabsCantBeChangedFromATabsMenu(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeHub(helper);
        putMachines(hub, 1, ModItems.BETTER_AUTO_MOVER.get(), 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BetterAutoHubMenu menu = new BetterAutoHubMenu(0, player.getInventory(), hub, 0);

        menu.setCarried(new ItemStack(ModItems.BETTER_AUTO_CRAFTER.get()));
        menu.clicked(tabSlot(menu, 1), 0, ClickType.PICKUP, player);
        menu.clicked(tabSlot(menu, 2), 0, ClickType.PICKUP, player);
        helper.assertTrue(hub.getTabItems().getStackInSlot(1).is(ModItems.BETTER_AUTO_MOVER.get()), "the mover stays");
        helper.assertTrue(hub.getTabItems().getStackInSlot(2).isEmpty(), "nothing goes into another tab");

        // Shift-clicking machines puts them into the menu's own tab.
        player.getInventory().setItem(0, new ItemStack(ModItems.BETTER_AUTO_BREWER.get(), 3));
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(inventorySlot(menu, 0), 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(hub.getTabItems().getStackInSlot(0).getCount() == 3, "the brewers go into the tab");
        helper.assertTrue(hub.getMachine(0) != null, "and run in it");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void hubSavesItsMachines(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeHub(helper);
        AutoMachineBlockEntity smelter = putMachines(hub, 2, ModItems.BETTER_AUTO_SMELTER.get(), 4);
        smelter.getTemplate().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 7));
        smelter.getSettings().setRedstoneMode(RedstoneMode.CONSTANT);
        smelter.getSideConfig().setMode(RelativeSide.LEFT, SideMode.OUTPUT);

        CompoundTag saved = hub.saveWithoutMetadata(helper.getLevel().registryAccess());
        BetterAutoHubBlockEntity loaded = new BetterAutoHubBlockEntity(hub.getBlockPos(), hub.getBlockState());
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        AutoMachineBlockEntity copy = loaded.getMachine(2);
        helper.assertTrue(copy instanceof BetterAutoSmelterBlockEntity, "the smelters");
        helper.assertTrue(loaded.getTabItems().getStackInSlot(2).getCount() == 4, "all 4 of them");
        helper.assertTrue(copy.getTemplate().getStackInSlot(0).getCount() == 7, "their input");
        helper.assertTrue(copy.getSettings().getRedstoneMode() == RedstoneMode.CONSTANT, "their settings");
        helper.assertTrue(copy.getSideConfig().getMode(RelativeSide.LEFT) == SideMode.OUTPUT, "their sides");
        helper.assertTrue(loaded.getMachine(0) == null, "the empty tabs stay empty");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPulseRunsTheTabsThatRunOnPulses(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeHub(helper);
        placeChest(helper, TOP, new ItemStack(Items.OAK_LOG, 10), new ItemStack(Items.STICK, 10));
        Container output = placeChest(helper, BOTTOM);
        putMachines(hub, 0, ModItems.BETTER_AUTO_CRAFTER.get(), 1).getTemplate().setStackInSlot(0, new ItemStack(Items.OAK_LOG));
        AutoMachineBlockEntity off = putMachines(hub, 1, ModItems.BETTER_AUTO_CRAFTER.get(), 1);
        off.getTemplate().setStackInSlot(0, new ItemStack(Items.STICK));
        off.getSettings().setRedstoneMode(RedstoneMode.OFF);

        helper.setBlock(HUB.east(), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            assertCount(helper, output, Items.OAK_PLANKS, 4, "the pulsed tab crafted");
            helper.assertTrue(hub.getMachine(0).hasSignal(), "it sees the hub's signal");
            helper.assertTrue(hub.getMachine(1).getMachineState() == MachineState.OFF, "the other one is off");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void breakingTheHubDropsEverything(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeHub(helper);
        AutoMachineBlockEntity crafter = putMachines(hub, 3, ModItems.BETTER_AUTO_CRAFTER.get(), 5);
        crafter.getTemplate().setStackInSlot(0, new ItemStack(Items.DIAMOND, 2));

        helper.setBlock(HUB, Blocks.AIR);
        helper.assertItemEntityCountIs(ModItems.BETTER_AUTO_CRAFTER.get(), HUB, 2.0, 5);
        helper.assertItemEntityCountIs(Items.DIAMOND, HUB, 2.0, 2);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void hubIsCraftedFromEveryMachine(GameTestHelper helper) {
        helper.setBlock(HUB, ModBlocks.BETTER_AUTO_CRAFTER.get().defaultBlockState());
        BetterAutoCrafterBlockEntity crafter = helper.getBlockEntity(HUB);
        Item[] grid = {ModItems.INFUSED_BETTER_MATERIAL.get(), ModItems.BETTER_AUTO_CRAFTER.get(), ModItems.INFUSED_BETTER_MATERIAL.get(),
                ModItems.BETTER_AUTO_SMELTER.get(), Items.NETHER_STAR, ModItems.BETTER_AUTO_BREWER.get(),
                ModItems.INFUSED_BETTER_MATERIAL.get(), ModItems.BETTER_AUTO_MOVER.get(), ModItems.INFUSED_BETTER_MATERIAL.get()};
        for (int i = 0; i < grid.length; i++) {
            crafter.getTemplate().setStackInSlot(i, new ItemStack(grid[i], grid[i] == ModItems.INFUSED_BETTER_MATERIAL.get() ? 3 : 1));
        }
        helper.assertTrue(crafter.getCraftingResult().isEmpty(), "3 infused better material per corner aren't enough");
        for (int corner : new int[] {0, 2, 6, 8}) {
            crafter.getTemplate().setStackInSlot(corner, new ItemStack(ModItems.INFUSED_BETTER_MATERIAL.get(), 4));
        }
        helper.assertTrue(crafter.getCraftingResult().is(ModItems.BETTER_AUTO_HUB.get()), "4 per corner make the hub, got "
                + crafter.getCraftingResult());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aNewHubOnlyHasItsStartingTabs(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeLockedHub(helper);
        int starting = CommonConfig.hubStartingTabs();
        helper.assertTrue(hub.getUnlockedTabs() == starting, "the config's " + starting + " tabs");
        helper.assertTrue(hub.getEnergyStorage() == null || CommonConfig.hubUsesEnergy(), "no energy unless the config says so");
        if (starting >= BetterAutoHubBlockEntity.TABS) {
            helper.succeed();
            return;
        }
        ItemStack crafters = new ItemStack(ModItems.BETTER_AUTO_CRAFTER.get(), 2);
        helper.assertTrue(!hub.getTabItems().isItemValid(starting, crafters), "a locked tab takes no machines");
        helper.assertTrue(hub.getTabItems().insertItem(starting, crafters, false).getCount() == 2, "none go in");
        helper.assertTrue(hub.getTabStates().get(starting) == HostTabs.LOCKED, "its menus show it locked");
        helper.assertTrue(hub.getTabItems().isItemValid(starting - 1, crafters), "an unlocked one does");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aHubExpansionUnlocksTheNextTab(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeLockedHub(helper);
        int starting = hub.getUnlockedTabs();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HUB_EXPANSION.get(), 10));

        for (int unlocked = starting + 1; unlocked <= BetterAutoHubBlockEntity.TABS; unlocked++) {
            helper.assertTrue(useOnHub(helper, player).consumesAction(), "used on the hub");
            helper.assertTrue(hub.getUnlockedTabs() == unlocked, "unlocks one more tab, " + unlocked);
            helper.assertTrue(hub.getLamp(unlocked - 1) != BetterAutoHubBlockEntity.LAMP_LOCKED, "its lamp is uncovered");
        }
        int left = 10 - (BetterAutoHubBlockEntity.TABS - starting);
        helper.assertTrue(player.getMainHandItem().getCount() == left, "one used per tab");
        helper.assertTrue(useOnHub(helper, player) == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                "with every tab unlocked, it opens the hub instead");
        helper.assertTrue(player.getMainHandItem().getCount() == left, "and isn't used");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theHubKeepsItsExpansionsWhenBroken(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeLockedHub(helper);
        int starting = hub.getUnlockedTabs();
        hub.expand();
        hub.expand();
        List<ItemStack> drops = Block.getDrops(hub.getBlockState(), helper.getLevel(), helper.absolutePos(HUB), hub);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModItems.BETTER_AUTO_HUB.get()), "drops itself");
        helper.assertTrue(drops.getFirst().getOrDefault(ModDataComponents.HUB_EXPANSIONS.get(), 0) == 2,
                "with the 2 expansions");

        helper.setBlock(HUB, Blocks.AIR);
        BetterAutoHubBlockEntity placed = placeLockedHub(helper);
        placed.applyComponentsFromItemStack(drops.getFirst());
        helper.assertTrue(placed.getExpansions() == 2, "placed again, it has them");
        helper.assertTrue(placed.getUnlockedTabs() == Math.min(BetterAutoHubBlockEntity.TABS, starting + 2), "and their tabs");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void machinesInTheHubRunAtTheConfigsSpeed(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeHub(helper);
        AutoMachineBlockEntity hosted = putMachines(hub, 0, ModItems.BETTER_AUTO_SMELTER.get(), 1);
        int base = CommonConfig.smelterBaseSpeed();
        int percent = CommonConfig.hubSpeedPercent();
        boolean extra = CommonConfig.hubExtraSpeedUpgrades();
        helper.assertTrue(percent >= 100 || hubTicks(base, 0) > base, "slower than on its own");
        int limit = hosted.getUpgradeLimit(UpgradeSlotType.SPEED);
        helper.assertTrue(limit == SpeedUpgradeItem.getUsefulCount(base, percent, extra), "speed upgrades it takes");
        helper.assertTrue(!extra || hubTicks(base, limit) == SpeedUpgradeItem.MIN_TICKS,
                "with extra upgrades, as fast as a machine on its own gets");
        helper.assertTrue(extra || limit == SpeedUpgradeItem.getUsefulCount(base), "without, as many as on its own");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aHubExpansionIsCrafted(GameTestHelper helper) {
        helper.setBlock(HUB, ModBlocks.BETTER_AUTO_CRAFTER.get().defaultBlockState());
        BetterAutoCrafterBlockEntity crafter = helper.getBlockEntity(HUB);
        Item[] grid = {ModItems.INFUSED_BETTER_MATERIAL.get(), Items.REDSTONE_BLOCK, ModItems.INFUSED_BETTER_MATERIAL.get(),
                Items.REDSTONE_BLOCK, Items.DIAMOND_BLOCK, Items.REDSTONE_BLOCK,
                ModItems.INFUSED_BETTER_MATERIAL.get(), Items.REDSTONE_BLOCK, ModItems.INFUSED_BETTER_MATERIAL.get()};
        for (int i = 0; i < grid.length; i++) {
            crafter.getTemplate().setStackInSlot(i, new ItemStack(grid[i]));
        }
        helper.assertTrue(crafter.getCraftingResult().is(ModItems.HUB_EXPANSION.get()), "makes a Hub Expansion, got "
                + crafter.getCraftingResult());
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------------

    /** A hub with every tab unlocked, and full of energy if the config has it use some. */
    private static BetterAutoHubBlockEntity placeHub(GameTestHelper helper) {
        BetterAutoHubBlockEntity hub = placeLockedHub(helper);
        while (hub.expand()) {
            // One more tab
        }
        if (hub.getEnergyStorage() != null) {
            hub.getEnergyStorage().receiveEnergy(Integer.MAX_VALUE, false);
        }
        return hub;
    }

    /** A new hub: only the config's starting tabs are unlocked. */
    private static BetterAutoHubBlockEntity placeLockedHub(GameTestHelper helper) {
        helper.setBlock(HUB, ModBlocks.BETTER_AUTO_HUB.get().defaultBlockState().setValue(AutoMachineBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(HUB);
    }

    /** Ticks an operation of a machine in the hub takes, by the config. */
    private static int hubTicks(int baseSpeed, int speedUpgrades) {
        return SpeedUpgradeItem.getOperationTicks(baseSpeed, speedUpgrades, CommonConfig.hubSpeedPercent(),
                CommonConfig.hubExtraSpeedUpgrades());
    }

    private static ItemInteractionResult useOnHub(GameTestHelper helper, Player player) {
        BlockPos pos = helper.absolutePos(HUB);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
        return helper.getLevel().getBlockState(pos).useItemOn(player.getMainHandItem(), helper.getLevel(), player,
                InteractionHand.MAIN_HAND, hit);
    }

    /** Puts machines into a tab, set up like the other tests' machines: on pulses, from the top to the bottom. */
    private static AutoMachineBlockEntity putMachines(BetterAutoHubBlockEntity hub, int tab, Item machine, int count) {
        boolean sameKind = hub.getTabItems().getStackInSlot(tab).is(machine);
        ((HostTabs.TabItems) hub.getTabItems()).setStackInSlot(tab, new ItemStack(machine, count));
        AutoMachineBlockEntity hosted = hub.getMachine(tab);
        if (!sameKind) {
            hosted.getSettings().setRedstoneMode(RedstoneMode.ON_POWERED);
            hosted.getSideConfig().setMode(RelativeSide.TOP, SideMode.INPUT);
            hosted.getSideConfig().setMode(RelativeSide.BOTTOM, SideMode.OUTPUT);
        }
        return hosted;
    }

    private static int tabSlot(AbstractContainerMenu menu, int tab) {
        return menu.slots.stream().filter(slot -> slot instanceof HostTabSlot tabSlot && tabSlot.getTab() == tab)
                .findFirst().orElseThrow().index;
    }

    private static int inventorySlot(AbstractContainerMenu menu, int inventoryIndex) {
        return menu.slots.stream().filter(slot -> slot.container instanceof Inventory
                && slot.getContainerSlot() == inventoryIndex).findFirst().orElseThrow().index;
    }

    private static Container placeChest(GameTestHelper helper, BlockPos pos, ItemStack... contents) {
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(pos);
        for (int i = 0; i < contents.length; i++) {
            chest.setItem(i, contents[i]);
        }
        return chest;
    }

    private static void assertCount(GameTestHelper helper, Container container, Item item, int expected, String message) {
        int count = container.countItem(item);
        helper.assertTrue(count == expected, message + ": expected " + expected + " but found " + count);
    }
}
