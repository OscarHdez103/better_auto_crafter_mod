package laseta.bac.gametest;

import java.util.ArrayList;
import java.util.List;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.BetterAutoCrafterBlock;
import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.crafting.RedstoneMode;
import laseta.bac.crafting.TransferPriority;
import laseta.bac.item.MultiTranslocationUpgradeItem;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModDataComponents;
import laseta.bac.registry.ModItems;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.upgrade.LinkedFace;
import laseta.bac.upgrade.UpgradeInventory;
import laseta.bac.upgrade.UpgradeSlotType;
import laseta.bac.upgrade.UpgradeStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
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

/**
 * In-world tests for the Multi Translocation Upgrades: linking faces, crafting through every linked block instead of
 * the configured faces, partly reachable links, slots and recipes. Run with {@code ./gradlew runGameTestServer}.
 *
 * <p>Layout: the crafter faces north at {@link #CRAFTER}, powered from its back (south); the linked inventories stand
 * a few blocks east at {@link #A}, {@link #B} and {@link #C}.</p>
 */
@GameTestHolder(BetterAutoCrafter.MODID)
@PrefixGameTestTemplate(false)
public final class MultiTranslocationGameTests {
    private static final String TEMPLATE = "empty_wide";
    private static final BlockPos CRAFTER = new BlockPos(2, 2, 2);
    private static final BlockPos REDSTONE = CRAFTER.south();
    private static final BlockPos A = new BlockPos(6, 2, 2);
    private static final BlockPos B = new BlockPos(8, 2, 2);
    private static final BlockPos C = new BlockPos(6, 2, 4);
    private static final int SETTLE = 5;

    private MultiTranslocationGameTests() {
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Crafting through linked blocks
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void multiPullPullsFromEveryLinkedBlockInsteadOfInputFaces(GameTestHelper helper) {
        // One plank in each linked chest: a stick recipe needs both, so it only works if both are used.
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container a = placeChest(helper, A, new ItemStack(Items.OAK_PLANKS, 1));
        Container b = placeChest(helper, B, new ItemStack(Items.OAK_PLANKS, 1));
        Container local = placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container output = placeChest(helper, CRAFTER.below());
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL,
                linked(helper, ModItems.MULTI_TRANSLOCATION_UPGRADE_PULL.get(), face(A, Direction.UP), face(B, Direction.UP)));

        helper.assertTrue(crafter.getUpgradeStatus(UpgradeSlotType.TRANSLOCATION_PULL) == UpgradeStatus.ACTIVE, "both links work");
        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, a, Items.OAK_PLANKS, 0, "plank pulled from the first linked chest");
            assertCount(helper, b, Items.OAK_PLANKS, 0, "plank pulled from the second linked chest");
            assertCount(helper, local, Items.OAK_PLANKS, 10, "the configured input face is not used");
            assertCount(helper, output, Items.STICK, 4, "sticks pushed to the local output (no push upgrade)");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void multiPushPushesIntoLinkedBlocksInsteadOfOutputFaces(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container local = placeChest(helper, CRAFTER.below());
        Container a = placeChest(helper, A);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PUSH,
                linked(helper, ModItems.MULTI_TRANSLOCATION_UPGRADE_PUSH.get(), face(A, Direction.WEST)));

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, a, Items.STICK, 4, "sticks pushed into the linked chest");
            assertCount(helper, local, Items.STICK, 0, "the configured output face is not used");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void multiPushRoundRobinsOverLinkedBlocks(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container a = placeChest(helper, A);
        Container b = placeChest(helper, B);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PUSH,
                linked(helper, ModItems.MULTI_TRANSLOCATION_UPGRADE_PUSH.get(), face(A, Direction.UP), face(B, Direction.UP)));

        helper.assertTrue(crafter.attemptCraft() && crafter.attemptCraft(), "two crafts");
        assertCount(helper, a, Items.STICK, 4, "first craft into the first linked chest");
        assertCount(helper, b, Items.STICK, 4, "round robin: second craft into the second one");

        // A face priority has no face to prefer among linked blocks: they are used in link order.
        crafter.getCraftingSettings().setOutputPriority(TransferPriority.FRONT);
        helper.assertTrue(crafter.attemptCraft() && crafter.attemptCraft(), "two more crafts");
        assertCount(helper, a, Items.STICK, 12, "link order: both into the first linked chest");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void linkedFaceDecidesWhichSlotsAreUsed(GameTestHelper helper) {
        // A furnace seen from below only gives its result slot, like a hopper under it would.
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        helper.setBlock(A, Blocks.FURNACE);
        Container furnace = helper.getBlockEntity(A);
        furnace.setItem(0, new ItemStack(Items.OAK_PLANKS, 10));
        furnace.setItem(2, new ItemStack(Items.OAK_PLANKS, 2));
        Container output = placeChest(helper, CRAFTER.below());
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL,
                linked(helper, ModItems.MULTI_TRANSLOCATION_UPGRADE_PULL.get(), face(A, Direction.DOWN)));

        helper.assertTrue(crafter.attemptCraft(), "crafts from the furnace's result slot");
        helper.assertTrue(furnace.getItem(2).isEmpty(), "result slot emptied");
        helper.assertTrue(furnace.getItem(0).getCount() == 10, "input slot untouched");
        helper.assertTrue(!crafter.attemptCraft(), "nothing left on the linked face");
        assertCount(helper, output, Items.STICK, 4, "one craft");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stockIsCheckedPerLinkedOutput(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container a = placeChest(helper, A, new ItemStack(Items.STICK, 8));
        Container b = placeChest(helper, B);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PUSH,
                linked(helper, ModItems.MULTI_TRANSLOCATION_UPGRADE_PUSH.get(), face(A, Direction.UP), face(B, Direction.UP)));
        install(crafter, UpgradeSlotType.STOCKING, new ItemStack(ModItems.STOCKING_UPGRADE_TIER_2.get()));

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, a, Items.STICK, 8, "stocked linked chest gets nothing");
            assertCount(helper, b, Items.STICK, 4, "sticks go to the linked chest that is not stocked");
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Unusable links
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void missingLinkedBlockLeavesTheOthersWorking(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container a = placeChest(helper, A, new ItemStack(Items.OAK_PLANKS, 10));
        Container output = placeChest(helper, CRAFTER.below());
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        // B is empty air: nothing to pull from there.
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL,
                linked(helper, ModItems.MULTI_TRANSLOCATION_UPGRADE_PULL.get(), face(B, Direction.UP), face(A, Direction.UP)));

        helper.assertTrue(crafter.getUpgradeStatus(UpgradeSlotType.TRANSLOCATION_PULL) == UpgradeStatus.PARTIALLY_ACTIVE,
                "status warns about the missing block");
        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, a, Items.OAK_PLANKS, 8, "still pulls from the block that is there");
            assertCount(helper, output, Items.STICK, 4, "sticks");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void noUsableLinkStopsCraftingWithoutFallback(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container local = placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        Container output = placeChest(helper, CRAFTER.below());
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        helper.setBlock(A, Blocks.STONE);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL,
                linked(helper, ModItems.MULTI_TRANSLOCATION_UPGRADE_PULL.get(), face(A, Direction.UP), face(B, Direction.UP)));

        helper.assertTrue(crafter.getUpgradeStatus(UpgradeSlotType.TRANSLOCATION_PULL) == UpgradeStatus.INVENTORY_MISSING,
                "status reports the missing inventories");
        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, local, Items.OAK_PLANKS, 10, "the configured input face is not used as a fallback");
            assertCount(helper, output, Items.STICK, 0, "nothing crafted");
            helper.assertBlockProperty(CRAFTER, BetterAutoCrafterBlock.FAILED, true);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void unlinkedMultiUpgradeStopsCrafting(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container local = placeChest(helper, CRAFTER.above(), new ItemStack(Items.OAK_PLANKS, 10));
        placeChest(helper, CRAFTER.below());
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PUSH, ModItems.MULTI_TRANSLOCATION_UPGRADE_PUSH.get().getDefaultInstance());

        helper.assertTrue(crafter.getUpgradeStatus(UpgradeSlotType.TRANSLOCATION_PUSH) == UpgradeStatus.NOT_LINKED, "not linked");
        helper.assertTrue(!crafter.attemptCraft(), "does not craft");
        assertCount(helper, local, Items.OAK_PLANKS, 10, "nothing pulled");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void dimensionRulesAndUnloadedChunks(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        LinkedFace farNether = new LinkedFace(GlobalPos.of(Level.NETHER, new BlockPos(1_000_000, 64, 1_000_000)), Direction.UP);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL, withTargets(ModItems.MULTI_TRANSLOCATION_UPGRADE_PULL.get(), farNether));
        helper.assertTrue(crafter.getUpgradeStatus(UpgradeSlotType.TRANSLOCATION_PULL) == UpgradeStatus.WRONG_DIMENSION,
                "basic upgrade must not work across dimensions");

        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL,
                withTargets(ModItems.DIMENSIONAL_MULTI_TRANSLOCATION_UPGRADE_PULL.get(), farNether));
        helper.assertTrue(crafter.getUpgradeStatus(UpgradeSlotType.TRANSLOCATION_PULL) == UpgradeStatus.UNLOADED,
                "dimensional upgrade accepts the Nether, but must not load the chunk");
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null && !nether.isLoaded(farNether.pos().pos()), "resolving must never load the chunk");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void dimensionalMultiUpgradeMixesDimensions(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "the Nether should exist");
        BlockPos remotePos = new BlockPos(24, 100, 24);
        ChunkPos chunk = new ChunkPos(remotePos);
        nether.setChunkForced(chunk.x, chunk.z, true);
        nether.setBlockAndUpdate(remotePos, Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) nether.getBlockEntity(remotePos)).setItem(0, new ItemStack(Items.OAK_PLANKS, 1));

        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container a = placeChest(helper, A, new ItemStack(Items.OAK_PLANKS, 1));
        Container output = placeChest(helper, CRAFTER.below());
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL, withTargets(ModItems.DIMENSIONAL_MULTI_TRANSLOCATION_UPGRADE_PULL.get(),
                new LinkedFace(GlobalPos.of(Level.NETHER, remotePos), Direction.UP), face(helper, A, Direction.UP)));

        boolean crafted = crafter.attemptCraft();
        Container remote = (Container) nether.getBlockEntity(remotePos);
        int planks = remote == null ? -1 : remote.countItem(Items.OAK_PLANKS);
        // Clean up the Nether before asserting, so a failure doesn't leave blocks or a forced chunk behind.
        nether.removeBlock(remotePos, false);
        nether.setChunkForced(chunk.x, chunk.z, false);
        helper.assertTrue(crafted, "crafts with one plank from each dimension");
        helper.assertTrue(planks == 0, "plank pulled from the Nether, " + planks + " left");
        assertCount(helper, a, Items.OAK_PLANKS, 0, "plank pulled from the Overworld");
        assertCount(helper, output, Items.STICK, 4, "sticks");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Linking
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void useTogglesLinkedFacesOfTheWholeStack(GameTestHelper helper) {
        placeChest(helper, A);
        placeChest(helper, B);
        helper.setBlock(C, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MULTI_TRANSLOCATION_UPGRADE_PULL.get(), 3));

        useOn(helper, player, A, Direction.UP);
        assertTargets(helper, player, List.of(face(helper, A, Direction.UP)), "first face linked");
        useOn(helper, player, A, Direction.NORTH);
        useOn(helper, player, B, Direction.UP);
        assertTargets(helper, player, List.of(face(helper, A, Direction.UP), face(helper, A, Direction.NORTH), face(helper, B, Direction.UP)),
                "faces linked in order; another face of the same block is its own link");
        useOn(helper, player, A, Direction.NORTH);
        assertTargets(helper, player, List.of(face(helper, A, Direction.UP), face(helper, B, Direction.UP)), "using a linked face unlinks it");
        useOn(helper, player, C, Direction.UP);
        assertTargets(helper, player, List.of(face(helper, A, Direction.UP), face(helper, B, Direction.UP)), "stone has no inventory");
        helper.assertTrue(player.getMainHandItem().getCount() == 3, "the whole stack stays together");

        useOn(helper, player, A, Direction.UP);
        useOn(helper, player, B, Direction.UP);
        helper.assertTrue(!player.getMainHandItem().has(ModDataComponents.TRANSLOCATION_TARGETS.get()),
                "unlinking the last face removes the component, so the stack stacks with fresh ones again");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void linkingStopsAtTheConfiguredLimit(GameTestHelper helper) {
        int max = MultiTranslocationUpgradeItem.getMaxTargets();
        List<LinkedFace> faces = chestFaces(helper, max + 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.MULTI_TRANSLOCATION_UPGRADE_PUSH.get().getDefaultInstance());
        for (LinkedFace face : faces) {
            BlockPos pos = face.pos().pos();
            player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), face.side(), pos, false)));
        }
        assertTargets(helper, player, faces.subList(0, max), "links the first " + max + " faces, not the one over the limit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void linksOverTheLimitAreNotUsed(GameTestHelper helper) {
        // Links made before the limit was lowered: the crafter only uses the first ones, up to the limit.
        int max = MultiTranslocationUpgradeItem.getMaxTargets();
        List<LinkedFace> faces = new ArrayList<>(chestFaces(helper, max));
        Container last = placeChest(helper, B, new ItemStack(Items.OAK_PLANKS, 10));
        faces.add(face(helper, B, Direction.UP));
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        placeChest(helper, CRAFTER.below());
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL,
                withTargets(ModItems.MULTI_TRANSLOCATION_UPGRADE_PULL.get(), faces.toArray(LinkedFace[]::new)));

        helper.assertTrue(crafter.getUpgradeStatus(UpgradeSlotType.TRANSLOCATION_PULL) == UpgradeStatus.PARTIALLY_ACTIVE,
                "status warns that not every link is used");
        helper.assertTrue(!crafter.attemptCraft(), "the planks are only behind the link over the limit");
        assertCount(helper, last, Items.OAK_PLANKS, 10, "nothing pulled through the link over the limit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void basicUpgradeLinksOneDimensionOnly(GameTestHelper helper) {
        placeChest(helper, A);
        LinkedFace nether = new LinkedFace(GlobalPos.of(Level.NETHER, BlockPos.ZERO), Direction.UP);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        player.setItemInHand(InteractionHand.MAIN_HAND, withTargets(ModItems.MULTI_TRANSLOCATION_UPGRADE_PULL.get(), nether));
        useOn(helper, player, A, Direction.UP);
        assertTargets(helper, player, List.of(nether), "basic: an Overworld block can't join a Nether link");

        player.setItemInHand(InteractionHand.MAIN_HAND, withTargets(ModItems.DIMENSIONAL_MULTI_TRANSLOCATION_UPGRADE_PULL.get(), nether));
        useOn(helper, player, A, Direction.UP);
        assertTargets(helper, player, List.of(nether, face(helper, A, Direction.UP)), "dimensional: links any dimension");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void craftingAloneClearsAllLinks(GameTestHelper helper) {
        ItemStack linked = linked(helper, ModItems.DIMENSIONAL_MULTI_TRANSLOCATION_UPGRADE_PUSH.get(), face(A, Direction.UP), face(B, Direction.UP));
        ItemStack result = craft(helper, linked);
        helper.assertTrue(result.is(ModItems.DIMENSIONAL_MULTI_TRANSLOCATION_UPGRADE_PUSH.get()), "same upgrade back, got " + result);
        helper.assertTrue(!result.has(ModDataComponents.TRANSLOCATION_TARGETS.get()), "without links");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Slots and recipes
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void multiUpgradesGoIntoTheTranslocationSlots(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        UpgradeInventory upgrades = crafter.getUpgrades();
        int pull = UpgradeSlotType.TRANSLOCATION_PULL.ordinal();
        int push = UpgradeSlotType.TRANSLOCATION_PUSH.ordinal();
        helper.assertTrue(upgrades.isItemValid(pull, ModItems.MULTI_TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance())
                && upgrades.isItemValid(pull, ModItems.DIMENSIONAL_MULTI_TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance()),
                "pull slot takes the multi pull upgrades");
        helper.assertTrue(!upgrades.isItemValid(pull, ModItems.MULTI_TRANSLOCATION_UPGRADE_PUSH.get().getDefaultInstance()),
                "pull slot rejects multi push upgrades");
        helper.assertTrue(upgrades.isItemValid(push, ModItems.DIMENSIONAL_MULTI_TRANSLOCATION_UPGRADE_PUSH.get().getDefaultInstance()),
                "push slot takes the multi push upgrades");

        // Sneak-use swaps a Translocation Upgrade for a multi one, giving the old one back.
        install(crafter, UpgradeSlotType.TRANSLOCATION_PULL, ModItems.TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.MULTI_TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance());
        useOn(helper, player, CRAFTER, Direction.UP);
        helper.assertTrue(upgrades.get(UpgradeSlotType.TRANSLOCATION_PULL).is(ModItems.MULTI_TRANSLOCATION_UPGRADE_PULL.get()),
                "multi upgrade installed");
        helper.assertTrue(player.getInventory().countItem(ModItems.TRANSLOCATION_UPGRADE_PULL.get()) == 1, "old upgrade given back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void multiUpgradesHaveRecipes(GameTestHelper helper) {
        ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        ItemStack translocator = ModItems.TRANSLOCATOR.get().getDefaultInstance();
        Item[][] recipes = {
                {ModItems.TRANSLOCATION_UPGRADE_PULL.get(), ModItems.MULTI_TRANSLOCATION_UPGRADE_PULL.get()},
                {ModItems.TRANSLOCATION_UPGRADE_PUSH.get(), ModItems.MULTI_TRANSLOCATION_UPGRADE_PUSH.get()},
                {ModItems.DIMENSIONAL_TRANSLOCATION_UPGRADE_PULL.get(), ModItems.DIMENSIONAL_MULTI_TRANSLOCATION_UPGRADE_PULL.get()},
                {ModItems.DIMENSIONAL_TRANSLOCATION_UPGRADE_PUSH.get(), ModItems.DIMENSIONAL_MULTI_TRANSLOCATION_UPGRADE_PUSH.get()},
        };
        for (Item[] recipe : recipes) {
            ItemStack base = recipe[0].getDefaultInstance();
            ItemStack result = craft(helper, base, pearl, base, redstone, translocator, redstone, base, pearl, base);
            helper.assertTrue(result.is(recipe[1]), "4 " + base + " + Translocator should make " + recipe[1] + ", got " + result);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------------

    private static BetterAutoCrafterBlockEntity placeStickCrafter(GameTestHelper helper) {
        helper.setBlock(CRAFTER, ModBlocks.BETTER_AUTO_CRAFTER.get().defaultBlockState()
                .setValue(BetterAutoCrafterBlock.FACING, Direction.NORTH));
        BetterAutoCrafterBlockEntity crafter = helper.getBlockEntity(CRAFTER);
        crafter.getCraftingSettings().setRedstoneMode(RedstoneMode.ON_POWERED);
        ItemStack plank = new ItemStack(Items.OAK_PLANKS);
        crafter.getInventory().setStackInSlot(1, plank.copy());
        crafter.getInventory().setStackInSlot(4, plank.copy());
        return crafter;
    }

    /** A face of a block in the test structure; resolved to absolute coordinates by {@link #linked}. */
    private record RelativeFace(BlockPos pos, Direction side) {
    }

    private static RelativeFace face(BlockPos relativePos, Direction side) {
        return new RelativeFace(relativePos, side);
    }

    private static LinkedFace face(GameTestHelper helper, BlockPos relativePos, Direction side) {
        return new LinkedFace(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relativePos)), side);
    }

    private static ItemStack linked(GameTestHelper helper, Item upgrade, RelativeFace... faces) {
        List<LinkedFace> targets = new ArrayList<>();
        for (RelativeFace face : faces) {
            targets.add(face(helper, face.pos(), face.side()));
        }
        return withTargets(upgrade, targets.toArray(LinkedFace[]::new));
    }

    private static ItemStack withTargets(Item upgrade, LinkedFace... targets) {
        ItemStack stack = upgrade.getDefaultInstance();
        stack.set(ModDataComponents.TRANSLOCATION_TARGETS.get(), List.of(targets));
        return stack;
    }

    /** {@code count} distinct faces of empty chests placed in a row along the far side of the structure. */
    private static List<LinkedFace> chestFaces(GameTestHelper helper, int count) {
        List<LinkedFace> faces = new ArrayList<>();
        for (int x = 0; faces.size() < count; x += 2) {
            BlockPos chest = new BlockPos(x, 2, 4);
            placeChest(helper, chest);
            for (Direction side : Direction.values()) {
                if (faces.size() < count) {
                    faces.add(face(helper, chest, side));
                }
            }
        }
        return faces;
    }

    private static void assertTargets(GameTestHelper helper, Player player, List<LinkedFace> expected, String what) {
        List<LinkedFace> actual = MultiTranslocationUpgradeItem.getTargets(player.getMainHandItem());
        helper.assertTrue(actual.equals(expected), what + ": expected " + expected + " but was " + actual);
    }

    private static void install(BetterAutoCrafterBlockEntity crafter, UpgradeSlotType type, ItemStack upgrade) {
        crafter.getUpgrades().setStackInSlot(type.ordinal(), upgrade);
    }

    private static void useOn(GameTestHelper helper, Player player, BlockPos relativePos, Direction side) {
        BlockPos pos = helper.absolutePos(relativePos);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), side, pos, false)));
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
}
