package laseta.bac.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.BetterAutoCrafterBlock;
import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.crafting.RedstoneMode;
import laseta.bac.crafting.TransferPriority;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModItems;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world tests for crafting from neighbouring inventories. Run with {@code ./gradlew runGameTestServer}.
 * Only registered when the mod's game test namespace is enabled (the dev run configs do that), never in normal play.
 *
 * <p>Layout (relative to the test structure): the crafter faces north, so
 * TOP = above, BOTTOM = below, FRONT = north, BACK = south (used for the redstone block), LEFT = east, RIGHT = west.</p>
 */
@GameTestHolder(BetterAutoCrafter.MODID)
@PrefixGameTestTemplate(false)
public final class AutoCrafterGameTests {
    private static final String TEMPLATE = "empty";
    private static final BlockPos CRAFTER = new BlockPos(2, 2, 2);
    private static final BlockPos TOP = CRAFTER.above();
    private static final BlockPos BOTTOM = CRAFTER.below();
    private static final BlockPos FRONT = CRAFTER.north();
    private static final BlockPos REDSTONE = CRAFTER.south();
    /** Ticks to wait after a redstone change before checking (the craft runs one tick after the rising edge). */
    private static final int SETTLE = 5;

    private AutoCrafterGameTests() {
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void craftsFromInputIntoOutput(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container input = placeChest(helper, TOP, new ItemStack(Items.OAK_PLANKS, 10));
        Container output = placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, output, Items.STICK, 4, "sticks in output");
            assertCount(helper, input, Items.OAK_PLANKS, 8, "planks left in input");
            assertGridUntouched(helper, crafter);
            helper.assertBlockProperty(CRAFTER, BetterAutoCrafterBlock.POWERED, true);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void craftsOnlyOnRisingEdge(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container input = placeChest(helper, TOP, new ItemStack(Items.OAK_PLANKS, 10));
        Container output = placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> assertCount(helper, output, Items.STICK, 4, "sticks after first pulse"));
        // Staying powered must not craft again.
        helper.runAfterDelay(SETTLE * 3, () -> assertCount(helper, output, Items.STICK, 4, "sticks while still powered"));
        helper.runAfterDelay(SETTLE * 3 + 1, () -> unpower(helper));
        helper.runAfterDelay(SETTLE * 4, () -> {
            helper.assertBlockProperty(CRAFTER, BetterAutoCrafterBlock.POWERED, false);
            power(helper);
        });
        helper.runAfterDelay(SETTLE * 5, () -> {
            assertCount(helper, output, Items.STICK, 8, "sticks after second pulse");
            assertCount(helper, input, Items.OAK_PLANKS, 6, "planks after two crafts");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void missingIngredientsTakeNothing(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container input = placeChest(helper, TOP, new ItemStack(Items.OAK_PLANKS, 1));
        Container output = placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, input, Items.OAK_PLANKS, 1, "the single plank must not be taken");
            assertCount(helper, output, Items.STICK, 0, "nothing crafted");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void fullOutputTakesNothing(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container input = placeChest(helper, TOP, new ItemStack(Items.OAK_PLANKS, 10));
        Container output = placeChest(helper, BOTTOM);
        for (int i = 0; i < output.getContainerSize(); i++) {
            output.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, input, Items.OAK_PLANKS, 10, "planks must stay when there is no room for the result");
            assertCount(helper, output, Items.STICK, 0, "nothing crafted");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void noOutputFaceTakesNothing(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container input = placeChest(helper, TOP, new ItemStack(Items.OAK_PLANKS, 10));
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, input, Items.OAK_PLANKS, 10, "planks must stay without an output face");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void craftingRemaindersGoToOutput(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeCrafter(helper);
        ItemStack milk = new ItemStack(Items.MILK_BUCKET);
        ItemStack sugar = new ItemStack(Items.SUGAR);
        ItemStack wheat = new ItemStack(Items.WHEAT);
        setGrid(crafter, milk, milk, milk, sugar, new ItemStack(Items.EGG), sugar, wheat, wheat, wheat);
        Container input = placeChest(helper, TOP,
                new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET),
                new ItemStack(Items.SUGAR, 2), new ItemStack(Items.EGG), new ItemStack(Items.WHEAT, 3));
        Container output = placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, output, Items.CAKE, 1, "cake");
            assertCount(helper, output, Items.BUCKET, 3, "empty buckets (crafting remainders)");
            helper.assertTrue(input.isEmpty(), "all ingredients should be used");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void matchNbtOnRejectsDifferentData(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container input = placeChest(helper, TOP, renamed(new ItemStack(Items.OAK_PLANKS, 10)));
        Container output = placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, input, Items.OAK_PLANKS, 10, "renamed planks must not match with NBT matching on");
            assertCount(helper, output, Items.STICK, 0, "nothing crafted");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void matchNbtOffAcceptsDataButPrefersExact(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        crafter.getCraftingSettings().setMatchNbt(false);
        Container input = placeChest(helper, TOP,
                renamed(new ItemStack(Items.OAK_PLANKS, 3)), new ItemStack(Items.OAK_PLANKS, 1));
        Container output = placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, output, Items.STICK, 4, "sticks");
            // One exact plank exists: it is used first, the second plank comes from the renamed stack.
            helper.assertTrue(input.getItem(1).isEmpty(), "the exact-match plank should be used first");
            helper.assertTrue(input.getItem(0).getCount() == 2, "one renamed plank should be used");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void pullsFromBothHalvesOfDoubleChest(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        // Double chest above the crafter; the items are only in the half that does NOT touch it.
        BlockPos otherHalf = TOP.east();
        helper.setBlock(TOP, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
        helper.setBlock(otherHalf, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        ChestBlockEntity far = helper.getBlockEntity(otherHalf);
        far.setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
        Container output = placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, output, Items.STICK, 4, "sticks crafted from the far half of the double chest");
            helper.assertTrue(far.isEmpty(), "far half should be emptied");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void inputOutputFaceUsesSameInventory(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container chest = placeChest(helper, TOP, new ItemStack(Items.OAK_PLANKS, 2));
        configure(crafter, RelativeSide.TOP, SideMode.INPUT_OUTPUT);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, chest, Items.OAK_PLANKS, 0, "planks used");
            assertCount(helper, chest, Items.STICK, 4, "sticks put back into the same chest");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void roundRobinAlternatesOutputs(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        placeChest(helper, TOP, new ItemStack(Items.OAK_PLANKS, 10));
        Container front = placeChest(helper, FRONT);
        Container bottom = placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.FRONT, SideMode.OUTPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        crafter.getCraftingSettings().setOutputPriority(TransferPriority.ROUND_ROBIN);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> unpower(helper));
        helper.runAfterDelay(SETTLE * 2, () -> power(helper));
        helper.runAfterDelay(SETTLE * 3, () -> {
            assertCount(helper, front, Items.STICK, 4, "first craft goes to the first output face");
            assertCount(helper, bottom, Items.STICK, 4, "second craft goes to the next output face");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void facePriorityPrefersThatFace(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container topInput = placeChest(helper, TOP, new ItemStack(Items.OAK_PLANKS, 10));
        Container frontInput = placeChest(helper, FRONT, new ItemStack(Items.OAK_PLANKS, 10));
        Container leftOutput = placeChest(helper, CRAFTER.east());
        Container bottomOutput = placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.FRONT, SideMode.INPUT);
        configure(crafter, RelativeSide.LEFT, SideMode.OUTPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        // Without a preference, face order would pick FRONT for input and LEFT for output.
        crafter.getCraftingSettings().setInputPriority(TransferPriority.TOP);
        crafter.getCraftingSettings().setOutputPriority(TransferPriority.BOTTOM);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> unpower(helper));
        helper.runAfterDelay(SETTLE * 2, () -> power(helper));
        helper.runAfterDelay(SETTLE * 3, () -> {
            assertCount(helper, topInput, Items.OAK_PLANKS, 6, "both crafts pull from the preferred top face");
            assertCount(helper, frontInput, Items.OAK_PLANKS, 10, "front input untouched");
            assertCount(helper, bottomOutput, Items.STICK, 8, "both crafts go to the preferred bottom face");
            assertCount(helper, leftOutput, Items.STICK, 0, "left output untouched");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void newBlockDefaultsToOff(GameTestHelper helper) {
        helper.setBlock(CRAFTER, ModBlocks.BETTER_AUTO_CRAFTER.get().defaultBlockState());
        BetterAutoCrafterBlockEntity crafter = helper.getBlockEntity(CRAFTER);
        helper.assertTrue(crafter.getCraftingSettings().getRedstoneMode() == RedstoneMode.OFF, "default redstone mode should be off");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void offModeNeverCrafts(GameTestHelper helper) {
        placeStickSetup(helper, RedstoneMode.OFF);
        power(helper);
        helper.runAfterDelay(SETTLE, () -> unpower(helper));
        helper.runAfterDelay(SETTLE * 2, () -> power(helper));
        helper.runAfterDelay(SETTLE * 3, () -> {
            helper.assertTrue(sticks(helper) == 0, "off mode must never craft, crafted " + sticks(helper) / 4);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void constantModeCraftsWhilePowered(GameTestHelper helper) {
        placeStickSetup(helper, RedstoneMode.CONSTANT);
        AtomicInteger beforePower = new AtomicInteger();
        AtomicInteger afterUnpower = new AtomicInteger();
        helper.runAfterDelay(4, () -> {
            beforePower.set(sticks(helper));
            power(helper);
        });
        helper.runAfterDelay(14, () -> unpower(helper));
        helper.runAfterDelay(16, () -> afterUnpower.set(sticks(helper)));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(beforePower.get() == 0, "constant mode must not craft while unpowered");
            int crafts = afterUnpower.get() / 4;
            // 10 powered ticks with one attempt every 2 ticks.
            helper.assertTrue(crafts >= 4 && crafts <= 6, "expected about 5 crafts while powered, got " + crafts);
            helper.assertTrue(sticks(helper) == afterUnpower.get(), "constant mode must stop when unpowered");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void invertedModeCraftsWhileUnpowered(GameTestHelper helper) {
        placeStickSetup(helper, RedstoneMode.INVERTED);
        AtomicInteger afterPower = new AtomicInteger();
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(sticks(helper) >= 16, "inverted mode should craft while unpowered, got " + sticks(helper));
            power(helper);
        });
        helper.runAfterDelay(12, () -> afterPower.set(sticks(helper)));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(sticks(helper) == afterPower.get(), "inverted mode must stop while powered");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void failedStateClearsAfterTenTicks(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container input = placeChest(helper, TOP, new ItemStack(Items.OAK_PLANKS, 1));
        placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);

        // Rising edge at tick 0, failed attempt at tick 1, cleared at tick 11.
        power(helper);
        helper.runAfterDelay(6, () -> {
            helper.assertBlockProperty(CRAFTER, BetterAutoCrafterBlock.FAILED, true);
            helper.assertBlockProperty(CRAFTER, BetterAutoCrafterBlock.POWERED, true);
        });
        helper.runAfterDelay(14, () -> {
            helper.assertBlockProperty(CRAFTER, BetterAutoCrafterBlock.FAILED, false);
            input.setItem(1, new ItemStack(Items.OAK_PLANKS, 1));
            unpower(helper);
        });
        helper.runAfterDelay(16, () -> power(helper));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(sticks(helper) == 4, "second attempt should craft");
            helper.assertBlockProperty(CRAFTER, BetterAutoCrafterBlock.FAILED, false);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void failedStateStaysWhileConstantlyFailing(GameTestHelper helper) {
        // Constant mode with nothing to craft from: fails every 2 ticks, so it must stay failed.
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        crafter.getCraftingSettings().setRedstoneMode(RedstoneMode.CONSTANT);
        placeChest(helper, TOP);
        placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);

        power(helper);
        for (int tick = 4; tick <= 30; tick += 2) {
            helper.runAfterDelay(tick, () -> helper.assertBlockProperty(CRAFTER, BetterAutoCrafterBlock.FAILED, true));
        }
        helper.runAfterDelay(31, () -> unpower(helper));
        // No more attempts once unpowered: cleared within 10 ticks of the last failure.
        helper.runAfterDelay(45, () -> {
            helper.assertBlockProperty(CRAFTER, BetterAutoCrafterBlock.FAILED, false);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void blockHasCraftingRecipe(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack hopper = new ItemStack(Items.HOPPER);
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                iron, new ItemStack(Items.DIAMOND), iron,
                hopper, new ItemStack(Items.CRAFTER), hopper,
                iron, new ItemStack(Items.COMPARATOR), iron));
        ItemStack result = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(holder -> holder.value().assemble(input, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(ModItems.BETTER_AUTO_CRAFTER.get()), "recipe should craft the Better Auto Crafter, got " + result);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void exposesNoItemCapability(GameTestHelper helper) {
        placeCrafter(helper);
        BlockPos pos = helper.absolutePos(CRAFTER);
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, null) == null,
                "crafter must not expose an item handler");
        for (Direction direction : Direction.values()) {
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, direction) == null,
                    "crafter must not expose an item handler on " + direction);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------------

    /** Crafter facing north in the "on powered" (rising edge) redstone mode, which most tests use. */
    private static BetterAutoCrafterBlockEntity placeCrafter(GameTestHelper helper) {
        helper.setBlock(CRAFTER, ModBlocks.BETTER_AUTO_CRAFTER.get().defaultBlockState()
                .setValue(BetterAutoCrafterBlock.FACING, Direction.NORTH));
        BetterAutoCrafterBlockEntity crafter = helper.getBlockEntity(CRAFTER);
        crafter.getCraftingSettings().setRedstoneMode(RedstoneMode.ON_POWERED);
        return crafter;
    }

    /** Stick crafter with a 40-plank input chest on top and an empty output chest below. */
    private static BetterAutoCrafterBlockEntity placeStickSetup(GameTestHelper helper, RedstoneMode mode) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        crafter.getCraftingSettings().setRedstoneMode(mode);
        placeChest(helper, TOP, new ItemStack(Items.OAK_PLANKS, 40));
        placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        return crafter;
    }

    private static int sticks(GameTestHelper helper) {
        return ((Container) helper.getBlockEntity(BOTTOM)).countItem(Items.STICK);
    }

    /** Crafter with the stick recipe (two planks in the middle column) as template. */
    private static BetterAutoCrafterBlockEntity placeStickCrafter(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeCrafter(helper);
        ItemStack plank = new ItemStack(Items.OAK_PLANKS);
        setGrid(crafter, ItemStack.EMPTY, plank, ItemStack.EMPTY, ItemStack.EMPTY, plank, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
        return crafter;
    }

    private static void setGrid(BetterAutoCrafterBlockEntity crafter, ItemStack... stacks) {
        for (int i = 0; i < stacks.length; i++) {
            crafter.getInventory().setStackInSlot(i, stacks[i].copy());
        }
    }

    private static void assertGridUntouched(GameTestHelper helper, BetterAutoCrafterBlockEntity crafter) {
        helper.assertTrue(crafter.getInventory().getStackInSlot(1).is(Items.OAK_PLANKS)
                        && crafter.getInventory().getStackInSlot(4).is(Items.OAK_PLANKS),
                "the template grid must never be consumed");
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

    private static ItemStack renamed(ItemStack stack) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Renamed"));
        return stack;
    }

    private static void power(GameTestHelper helper) {
        helper.setBlock(REDSTONE, Blocks.REDSTONE_BLOCK);
    }

    private static void unpower(GameTestHelper helper) {
        helper.setBlock(REDSTONE, Blocks.AIR);
    }

    private static void assertCount(GameTestHelper helper, Container container, Item item, int expected, String what) {
        int actual = container.countItem(item);
        helper.assertTrue(actual == expected, what + ": expected " + expected + " but found " + actual);
    }
}
