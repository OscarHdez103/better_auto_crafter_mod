package laseta.bac.gametest;

import java.util.ArrayList;
import java.util.List;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.TranslocatorBlock;
import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.machine.AutoMachineBlock;
import laseta.bac.machine.MachineProblem;
import laseta.bac.machine.RedstoneMode;
import laseta.bac.item.TranslocationUpgradeItem;
import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModDataComponents;
import laseta.bac.registry.ModItems;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.upgrade.UpgradeInventory;
import laseta.bac.upgrade.UpgradeSlotType;
import laseta.bac.upgrade.UpgradeStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * In-world tests for the upgrade slots, translocation and stocking upgrades. Run with {@code ./gradlew runGameTestServer}.
 *
 * <p>Layout: the crafter faces north at {@link #CRAFTER}, powered from its back (south); a Translocator stands
 * 6 blocks east at {@link #TRANSLOCATOR}.</p>
 */
@GameTestHolder(BetterAutoCrafter.MODID)
@PrefixGameTestTemplate(false)
public final class TranslocationGameTests {
    private static final String TEMPLATE = "empty_wide";
    private static final BlockPos CRAFTER = new BlockPos(2, 2, 2);
    private static final BlockPos REDSTONE = CRAFTER.south();
    private static final BlockPos TRANSLOCATOR = new BlockPos(8, 2, 2);
    private static final int SETTLE = 5;

    private TranslocationGameTests() {
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Crafting through a Translocator
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void pullUpgradePullsAroundTranslocator(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        placeTranslocator(helper, TRANSLOCATOR, Direction.NORTH);
        Container remote = placeChest(helper, TRANSLOCATOR.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container local = placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container output = placeChest(helper, CRAFTER.below());
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL, linked(helper, ModItems.TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance(), TRANSLOCATOR));

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, remote, Items.OAK_PLANKS, 8, "planks pulled from above the Translocator");
            assertCount(helper, local, Items.OAK_PLANKS, 10, "planks above the crafter itself are not used");
            assertCount(helper, output, Items.STICK, 4, "sticks pushed locally (no push upgrade)");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void pushUpgradePushesAroundTranslocator(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        placeTranslocator(helper, TRANSLOCATOR, Direction.NORTH);
        placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container remote = placeChest(helper, TRANSLOCATOR.below());
        Container local = placeChest(helper, CRAFTER.below());
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PUSH, linked(helper, ModItems.TRANSLOCATION_UPGRADE_PUSH.get().getDefaultInstance(), TRANSLOCATOR));

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, remote, Items.STICK, 4, "sticks pushed below the Translocator");
            assertCount(helper, local, Items.STICK, 0, "nothing pushed below the crafter itself");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void translocatorFacingDefinesItsFaces(GameTestHelper helper) {
        // The crafter faces north, the Translocator east: the crafter's FRONT input becomes the Translocator's east side.
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        placeTranslocator(helper, TRANSLOCATOR, Direction.EAST);
        Container translocatorFront = placeChest(helper, TRANSLOCATOR.east(), new ItemStack(Items.OAK_PLANKS, 10));
        Container translocatorNorth = placeChest(helper, TRANSLOCATOR.north(), new ItemStack(Items.OAK_PLANKS, 10));
        Container output = placeChest(helper, CRAFTER.below());
        configure(crafter, RelativeSide.FRONT, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL, linked(helper, ModItems.TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance(), TRANSLOCATOR));

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, translocatorFront, Items.OAK_PLANKS, 8, "pulled from the Translocator's front");
            assertCount(helper, translocatorNorth, Items.OAK_PLANKS, 10, "the Translocator's north side is not its front");
            assertCount(helper, output, Items.STICK, 4, "sticks");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void missingTranslocatorStopsCraftingWithoutFallback(GameTestHelper helper) {
        // Linked to a position without a Translocator: must NOT fall back to the crafter's own faces.
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container local = placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container output = placeChest(helper, CRAFTER.below());
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL, linked(helper, ModItems.TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance(), TRANSLOCATOR));

        helper.assertTrue(crafter.resolveUpgrade(UpgradeSlotType.TRANSLOCATION_PULL).status() == UpgradeStatus.TRANSLOCATOR_MISSING,
                "status should report the missing Translocator");
        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, local, Items.OAK_PLANKS, 10, "local planks untouched");
            assertCount(helper, output, Items.STICK, 0, "nothing crafted");
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.FAILED, true);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void unlinkedUpgradeStopsCrafting(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container local = placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        placeChest(helper, CRAFTER.below());
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL, ModItems.TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance());

        helper.assertTrue(crafter.resolveUpgrade(UpgradeSlotType.TRANSLOCATION_PULL).status() == UpgradeStatus.NOT_LINKED,
                "status should report the missing link");
        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, local, Items.OAK_PLANKS, 10, "local planks untouched");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void dimensionRulesAndUnloadedChunks(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        // Far away in the Nether: in another dimension, and certainly not loaded.
        GlobalPos farNether = GlobalPos.of(Level.NETHER, new BlockPos(1_000_000, 64, 1_000_000));
        ItemStack basic = ModItems.TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance();
        basic.set(ModDataComponents.TRANSLOCATOR_LINK.get(), farNether);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL, basic);
        helper.assertTrue(crafter.resolveUpgrade(UpgradeSlotType.TRANSLOCATION_PULL).status() == UpgradeStatus.WRONG_DIMENSION,
                "basic upgrade must not work across dimensions");

        ItemStack dimensional = ModItems.DIMENSIONAL_TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance();
        dimensional.set(ModDataComponents.TRANSLOCATOR_LINK.get(), farNether);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL, dimensional);
        helper.assertTrue(crafter.resolveUpgrade(UpgradeSlotType.TRANSLOCATION_PULL).status() == UpgradeStatus.UNLOADED,
                "dimensional upgrade accepts the Nether, but must not load the chunk");
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null && !nether.isLoaded(farNether.pos()), "resolving must never load the chunk");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void dimensionalUpgradeWorksAcrossDimensions(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "the Nether should exist");
        BlockPos remotePos = new BlockPos(8, 100, 8);
        ChunkPos chunk = new ChunkPos(remotePos);
        // Keep the remote chunk loaded for the test, like a chunk loader would.
        nether.setChunkForced(chunk.x, chunk.z, true);
        nether.setBlockAndUpdate(remotePos, ModBlocks.TRANSLOCATOR.get().defaultBlockState());
        nether.setBlockAndUpdate(remotePos.above(), Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) nether.getBlockEntity(remotePos.above())).setItem(0, new ItemStack(Items.OAK_PLANKS, 10));

        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container output = placeChest(helper, CRAFTER.below());
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        ItemStack upgrade = ModItems.DIMENSIONAL_TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance();
        upgrade.set(ModDataComponents.TRANSLOCATOR_LINK.get(), GlobalPos.of(Level.NETHER, remotePos));
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL, upgrade);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            Container remote = (Container) nether.getBlockEntity(remotePos.above());
            int planks = remote == null ? -1 : remote.countItem(Items.OAK_PLANKS);
            // Clean up the Nether before asserting, so a failure doesn't leave blocks or a forced chunk behind.
            nether.removeBlock(remotePos.above(), false);
            nether.removeBlock(remotePos, false);
            nether.setChunkForced(chunk.x, chunk.z, false);
            helper.assertTrue(planks == 8, "should pull 2 planks from the Nether, " + planks + " left");
            assertCount(helper, output, Items.STICK, 4, "sticks crafted in the Overworld");
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Slots, items, recipes
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void upgradeSlotsOnlyAcceptTheirTypeOneEach(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        UpgradeInventory upgrades = crafter.getUpgrades();
        int pull = UpgradeSlotType.TRANSLOCATION_PULL.ordinal();
        int push = UpgradeSlotType.TRANSLOCATION_PUSH.ordinal();

        ItemStack pushStack = new ItemStack(ModItems.TRANSLOCATION_UPGRADE_PUSH.get(), 5);
        helper.assertTrue(upgrades.insertItem(pull, pushStack, false).getCount() == 5, "pull slot must reject push upgrades");
        helper.assertTrue(upgrades.insertItem(pull, new ItemStack(Items.DIAMOND), false).getCount() == 1, "must reject other items");

        ItemStack rest = upgrades.insertItem(pull, new ItemStack(ModItems.DIMENSIONAL_TRANSLOCATION_UPGRADE_PULL.get(), 5), false);
        helper.assertTrue(rest.getCount() == 4 && upgrades.getStackInSlot(pull).getCount() == 1,
                "pull slot takes one dimensional pull upgrade");
        helper.assertTrue(upgrades.insertItem(push, pushStack, false).getCount() == 4, "push slot takes one push upgrade");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void upgradeInventoryKeepsSlotsWhenLoadingOlderSaves(GameTestHelper helper) {
        // A save written when there was only one upgrade slot.
        ItemStackHandler old = new ItemStackHandler(1);
        old.setStackInSlot(0, ModItems.TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance());
        CompoundTag saved = old.serializeNBT(helper.getLevel().registryAccess());

        UpgradeInventory upgrades = new UpgradeInventory(() -> {
        }, UpgradeSlotType::getMaxStackSize);
        upgrades.deserializeNBT(helper.getLevel().registryAccess(), saved);
        helper.assertTrue(upgrades.getSlots() == UpgradeSlotType.COUNT, "must keep all current slots, has " + upgrades.getSlots());
        helper.assertTrue(upgrades.getStackInSlot(0).is(ModItems.TRANSLOCATION_UPGRADE_PULL.get()), "saved upgrade kept");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void upgradesDropWhenBroken(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PUSH, ModItems.TRANSLOCATION_UPGRADE_PUSH.get().getDefaultInstance());
        helper.getLevel().destroyBlock(helper.absolutePos(CRAFTER), false);
        helper.runAfterDelay(1, () -> {
            helper.assertItemEntityPresent(ModItems.TRANSLOCATION_UPGRADE_PUSH.get(), CRAFTER, 2.0);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void craftingAloneClearsTheLink(GameTestHelper helper) {
        ItemStack linked = linked(helper, ModItems.TRANSLOCATION_UPGRADE_PUSH.get().getDefaultInstance(), TRANSLOCATOR);
        ItemStack result = craft(helper, linked);
        helper.assertTrue(result.is(ModItems.TRANSLOCATION_UPGRADE_PUSH.get()), "reset gives the same upgrade back, got " + result);
        helper.assertTrue(!result.has(ModDataComponents.TRANSLOCATOR_LINK.get()), "reset upgrade must not be linked");
        helper.assertTrue(craft(helper, ModItems.TRANSLOCATION_UPGRADE_PUSH.get().getDefaultInstance()).isEmpty(),
                "an unlinked upgrade alone is not a recipe");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void upgradesAndTranslocatorHaveRecipes(GameTestHelper helper) {
        ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
        ItemStack obsidian = new ItemStack(Items.OBSIDIAN);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack eye = new ItemStack(Items.ENDER_EYE);
        ItemStack crying = new ItemStack(Items.CRYING_OBSIDIAN);
        ItemStack none = ItemStack.EMPTY;
        assertCrafts(helper, ModItems.TRANSLOCATOR.get().getDefaultInstance(),
                obsidian, pearl, obsidian, pearl, new ItemStack(Items.HOPPER), pearl, obsidian, pearl, obsidian);
        assertCrafts(helper, ModItems.TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance(),
                none, pearl, none, iron, new ItemStack(Items.HOPPER), iron, none, new ItemStack(Items.REDSTONE), none);
        assertCrafts(helper, ModItems.TRANSLOCATION_UPGRADE_PUSH.get().getDefaultInstance(),
                none, pearl, none, iron, new ItemStack(Items.DROPPER), iron, none, new ItemStack(Items.REDSTONE), none);
        assertCrafts(helper, ModItems.DIMENSIONAL_TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance(),
                none, eye, none, crying, ModItems.TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance(), crying, none, eye, none);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Linking
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void linkingLinksTheWholeHeldStack(GameTestHelper helper) {
        placeTranslocator(helper, TRANSLOCATOR, Direction.NORTH);
        BlockPos clicked = helper.absolutePos(TRANSLOCATOR);
        GlobalPos expected = GlobalPos.of(helper.getLevel().dimension(), clicked);
        for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            Player player = helper.makeMockPlayer(mode);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TRANSLOCATION_UPGRADE_PULL.get(), 16));
            player.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));
            ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
            helper.assertTrue(held.getCount() == 16 && expected.equals(TranslocationUpgradeItem.getLink(held)),
                    mode + ": the whole stack of 16 must be linked, got " + held);
            helper.assertTrue(player.getInventory().countItem(ModItems.TRANSLOCATION_UPGRADE_PULL.get()) == 16,
                    mode + ": no extra upgrades handed out");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true) // no barrier ceiling, so looking up hits nothing
    public static void sneakUseAtNothingClearsTheWholeStack(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 standAt = helper.absoluteVec(new Vec3(2.5, 3, 2.5));
        player.moveTo(standAt.x, standAt.y, standAt.z, 0.0F, -90.0F); // looking straight up, at the sky
        ItemStack stack = linked(helper, new ItemStack(ModItems.TRANSLOCATION_UPGRADE_PUSH.get(), 12), TRANSLOCATOR);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(TranslocationUpgradeItem.getLink(player.getMainHandItem()) != null, "not sneaking: link kept");

        player.setShiftKeyDown(true);
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        ItemStack held = player.getMainHandItem();
        helper.assertTrue(held.getCount() == 12 && TranslocationUpgradeItem.getLink(held) == null,
                "sneak-use at nothing clears all 12, got " + held);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Installing
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void sneakUseInstallsOneUpgradeAndSwapsOthers(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        int pull = UpgradeSlotType.TRANSLOCATION_PULL.ordinal();
        int stocking = UpgradeSlotType.STOCKING.ordinal();

        useOnCrafter(helper, player, new ItemStack(ModItems.TRANSLOCATION_UPGRADE_PULL.get(), 3));
        helper.assertTrue(crafter.getUpgrades().getStackInSlot(pull).is(ModItems.TRANSLOCATION_UPGRADE_PULL.get()), "pull upgrade installed");
        helper.assertTrue(player.getMainHandItem().getCount() == 2, "one taken from the hand");

        useOnCrafter(helper, player, new ItemStack(ModItems.DIMENSIONAL_TRANSLOCATION_UPGRADE_PULL.get(), 1));
        helper.assertTrue(crafter.getUpgrades().getStackInSlot(pull).is(ModItems.DIMENSIONAL_TRANSLOCATION_UPGRADE_PULL.get()),
                "dimensional upgrade replaces the basic one");
        helper.assertTrue(player.getInventory().countItem(ModItems.TRANSLOCATION_UPGRADE_PULL.get()) == 1, "basic upgrade given back");

        useOnCrafter(helper, player, new ItemStack(ModItems.STOCKING_UPGRADE.get(), 5));
        useOnCrafter(helper, player, player.getMainHandItem());
        helper.assertTrue(crafter.getUpgrades().getStackInSlot(stocking).getCount() == 2, "stocking upgrades added one per use");
        helper.assertTrue(player.getMainHandItem().getCount() == 3, "rest stays in the hand");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void shiftClickFillsTheUpgradeSlot(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BetterAutoCrafterMenu menu = new BetterAutoCrafterMenu(0, player.getInventory(), crafter);
        int firstInventorySlot = BetterAutoCrafterBlockEntity.GRID_SIZE + 1; // player inventory slot 9
        List<ItemStack> grid = crafter.getTemplateStacks().stream().map(ItemStack::copy).toList();

        player.getInventory().setItem(9, new ItemStack(ModItems.STOCKING_UPGRADE.get(), 10));
        menu.clicked(firstInventorySlot, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(crafter.getUpgrades().get(UpgradeSlotType.STOCKING).getCount() == 10, "the whole stack fits");
        helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "nothing left behind");

        player.getInventory().setItem(9, new ItemStack(ModItems.TRANSLOCATION_UPGRADE_PUSH.get(), 4));
        menu.clicked(firstInventorySlot, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(crafter.getUpgrades().get(UpgradeSlotType.TRANSLOCATION_PUSH).getCount() == 1, "as many as the slot holds");
        helper.assertTrue(player.getInventory().getItem(9).getCount() == 3, "the rest stays in the inventory");

        // The slot is full: the rest stays put instead of going into the grid.
        menu.clicked(firstInventorySlot, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(player.getInventory().getItem(9).getCount() == 3, "a full slot takes nothing");
        List<ItemStack> after = crafter.getTemplateStacks();
        for (int i = 0; i < grid.size(); i++) {
            helper.assertTrue(ItemStack.matches(grid.get(i), after.get(i)), "grid untouched");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Stocking
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void stockedOutputStopsCrafting(GameTestHelper helper) {
        // 2 basic stocking upgrades = limit 2, and the output already holds 2 sticks.
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container input = placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container output = placeChest(helper, CRAFTER.below(), new ItemStack(Items.STICK, 2));
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.STOCKING, new ItemStack(ModItems.STOCKING_UPGRADE.get(), 2));

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, input, Items.OAK_PLANKS, 10, "nothing pulled");
            assertCount(helper, output, Items.STICK, 2, "no sticks added");
            // Having nothing to do isn't a failure.
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.FAILED, false);
            helper.assertTrue(crafter.getStatus().getLastProblem() == MachineProblem.STOCKED, "says it is stocked");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void understockedOutputStillCrafts(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container output = placeChest(helper, CRAFTER.below(), new ItemStack(Items.STICK, 7));
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.STOCKING, new ItemStack(ModItems.STOCKING_UPGRADE_TIER_2.get()));

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, output, Items.STICK, 11, "7 of 8 in stock, so one more craft");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void stockIsCheckedPerOutputFace(GameTestHelper helper) {
        // Two output faces: the stocked one is skipped and the result goes to the other one.
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container east = placeChest(helper, CRAFTER.east(), new ItemStack(Items.STICK, 8));
        Container west = placeChest(helper, CRAFTER.west());
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.LEFT, SideMode.OUTPUT);
        configure(crafter, RelativeSide.RIGHT, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.STOCKING, new ItemStack(ModItems.STOCKING_UPGRADE_TIER_2.get()));

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, east, Items.STICK, 8, "stocked face gets nothing");
            assertCount(helper, west, Items.STICK, 4, "sticks go to the face that is not stocked");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void stockIsCheckedAtTranslocatorOutputs(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        placeTranslocator(helper, TRANSLOCATOR, Direction.NORTH);
        Container input = placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container remote = placeChest(helper, TRANSLOCATOR.below(), new ItemStack(Items.STICK, 64));
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PUSH, linked(helper, ModItems.TRANSLOCATION_UPGRADE_PUSH.get().getDefaultInstance(), TRANSLOCATOR));
        install(crafter, UpgradeSlotType.STOCKING, new ItemStack(ModItems.STOCKING_UPGRADE_TIER_3.get()));

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, input, Items.OAK_PLANKS, 10, "nothing pulled");
            assertCount(helper, remote, Items.STICK, 64, "remote output already holds 64");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void stockingSlotHoldsAStackAndAddsUp(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        UpgradeInventory upgrades = crafter.getUpgrades();
        int stocking = UpgradeSlotType.STOCKING.ordinal();
        helper.assertTrue(upgrades.insertItem(stocking, ModItems.TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance(), false).getCount() == 1,
                "stocking slot rejects other upgrades");
        ItemStack rest = upgrades.insertItem(stocking, new ItemStack(ModItems.STOCKING_UPGRADE_TIER_2.get(), 64), false);
        helper.assertTrue(rest.isEmpty() && upgrades.getStackInSlot(stocking).getCount() == 64, "takes a full stack of 64");
        helper.assertTrue(crafter.getStockLimit() == 64 * 8, "64 tier 2 = 512, got " + crafter.getStockLimit());
        upgrades.setStackInSlot(stocking, new ItemStack(ModItems.STOCKING_UPGRADE_TIER_3.get(), 3));
        helper.assertTrue(crafter.getStockLimit() == 3 * 64, "3 tier 3 = 192, got " + crafter.getStockLimit());
        helper.assertTrue(crafter.getUpgradeStatus(UpgradeSlotType.STOCKING) == UpgradeStatus.ACTIVE, "installed = active");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stockingUpgradeTiersHaveRecipes(GameTestHelper helper) {
        ItemStack paper = new ItemStack(Items.PAPER);
        ItemStack tier1 = ModItems.STOCKING_UPGRADE.get().getDefaultInstance();
        ItemStack tier2 = ModItems.STOCKING_UPGRADE_TIER_2.get().getDefaultInstance();
        ItemStack table = new ItemStack(Items.CRAFTING_TABLE);
        ItemStack first = craft(helper, paper, paper, paper, paper, new ItemStack(Items.COMPARATOR), paper, paper, paper, paper);
        helper.assertTrue(first.is(ModItems.STOCKING_UPGRADE.get()), "basic from paper and a comparator, got " + first);
        assertCrafts(helper, tier2, tier1, tier1, tier1, tier1, table, tier1, tier1, tier1, tier1);
        assertCrafts(helper, ModItems.STOCKING_UPGRADE_TIER_3.get().getDefaultInstance(),
                tier2, tier2, tier2, tier2, table, tier2, tier2, tier2, tier2);
        helper.succeed();
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
        return crafter;
    }

    private static void placeTranslocator(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.TRANSLOCATOR.get().defaultBlockState().setValue(TranslocatorBlock.FACING, facing));
    }

    private static ItemStack linked(GameTestHelper helper, ItemStack upgrade, BlockPos relativePos) {
        upgrade.set(ModDataComponents.TRANSLOCATOR_LINK.get(), GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relativePos)));
        return upgrade;
    }

    private static void install(BetterAutoCrafterBlockEntity crafter, UpgradeSlotType type, ItemStack upgrade) {
        crafter.getUpgrades().setStackInSlot(type.ordinal(), upgrade);
    }

    private static void useOnCrafter(GameTestHelper helper, Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = helper.absolutePos(CRAFTER);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    private static void configure(BetterAutoCrafterBlockEntity crafter, RelativeSide side, SideMode mode) {
        crafter.getSideConfig().setMode(side, mode);
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

    private static void assertCrafts(GameTestHelper helper, ItemStack expected, ItemStack... grid) {
        ItemStack result = craft(helper, grid);
        helper.assertTrue(ItemStack.isSameItem(result, expected), "expected " + expected + " but crafted " + result);
    }
}
