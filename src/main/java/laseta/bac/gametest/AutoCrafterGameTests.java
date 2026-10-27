package laseta.bac.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.CommonConfig;
import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.machine.AutoMachineBlock;
import laseta.bac.machine.AutoMachineBlockEntity;
import laseta.bac.machine.MachineProblem;
import laseta.bac.machine.RedstoneMode;
import laseta.bac.machine.TransferPriority;
import laseta.bac.recipe.AutoCraftingRecipe;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModItems;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.upgrade.UpgradeSlotType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
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
    private static final int COOLDOWN = CommonConfig.crafterBaseSpeed();

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
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.POWERED, true);
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
        helper.runAfterDelay(COOLDOWN + SETTLE, () -> {
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.TRIGGERED, false);
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.POWERED, false);
            power(helper);
        });
        helper.runAfterDelay(COOLDOWN + SETTLE * 2, () -> {
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
        crafter.getSettings().setMatchNbt(false);
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
        crafter.getSettings().setOutputPriority(TransferPriority.ROUND_ROBIN);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> unpower(helper));
        helper.runAfterDelay(COOLDOWN + SETTLE, () -> power(helper));
        helper.runAfterDelay(COOLDOWN + SETTLE * 2, () -> {
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
        crafter.getSettings().setInputPriority(TransferPriority.TOP);
        crafter.getSettings().setOutputPriority(TransferPriority.BOTTOM);

        power(helper);
        helper.runAfterDelay(SETTLE, () -> unpower(helper));
        helper.runAfterDelay(COOLDOWN + SETTLE, () -> power(helper));
        helper.runAfterDelay(COOLDOWN + SETTLE * 2, () -> {
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
        helper.assertTrue(crafter.getSettings().getRedstoneMode() == RedstoneMode.OFF, "default redstone mode should be off");
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
        helper.runAfterDelay(4 + COOLDOWN * 2 + SETTLE, () -> unpower(helper));
        helper.runAfterDelay(4 + COOLDOWN * 2 + SETTLE * 2, () -> afterUnpower.set(sticks(helper)));
        helper.runAfterDelay(4 + COOLDOWN * 4, () -> {
            helper.assertTrue(beforePower.get() == 0, "constant mode must not craft while unpowered");
            int crafts = afterUnpower.get() / 4;
            // Powered for two cooldowns and a bit: a craft right away, then one after each cooldown.
            helper.assertTrue(crafts == 3, "expected 3 crafts while powered, got " + crafts);
            helper.assertTrue(sticks(helper) == afterUnpower.get(), "constant mode must stop when unpowered");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void invertedModeCraftsWhileUnpowered(GameTestHelper helper) {
        placeStickSetup(helper, RedstoneMode.INVERTED);
        AtomicInteger afterPower = new AtomicInteger();
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(sticks(helper) == 4, "inverted mode should craft while unpowered, got " + sticks(helper));
            power(helper);
        });
        helper.runAfterDelay(SETTLE + 2, () -> afterPower.set(sticks(helper)));
        // Past the cooldown: it would have crafted again by now if it still ran.
        helper.runAfterDelay(COOLDOWN * 2, () -> {
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
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.FAILED, true);
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.TRIGGERED, true);
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.POWERED, false);
        });
        helper.runAfterDelay(14, () -> {
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.FAILED, false);
            input.setItem(1, new ItemStack(Items.OAK_PLANKS, 1));
            unpower(helper);
        });
        helper.runAfterDelay(16, () -> power(helper));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(sticks(helper) == 4, "second attempt should craft");
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.FAILED, false);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void failedStateStaysWhileConstantlyFailing(GameTestHelper helper) {
        // Constant mode with nothing to craft from: fails every 2 ticks, so it must stay failed.
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        crafter.getSettings().setRedstoneMode(RedstoneMode.CONSTANT);
        placeChest(helper, TOP);
        placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);

        power(helper);
        for (int tick = 4; tick <= 30; tick += 2) {
            helper.runAfterDelay(tick, () -> helper.assertBlockProperty(CRAFTER, AutoMachineBlock.FAILED, true));
        }
        helper.runAfterDelay(31, () -> unpower(helper));
        // No more attempts once unpowered: cleared within 10 ticks of the last failure.
        helper.runAfterDelay(45, () -> {
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.FAILED, false);
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
    // Cooldown and status
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void pulseDuringCooldownIsIgnored(GameTestHelper helper) {
        placeStickSetup(helper, RedstoneMode.ON_POWERED);
        power(helper);
        helper.runAfterDelay(2, () -> unpower(helper));
        helper.runAfterDelay(SETTLE, () -> power(helper));
        helper.runAfterDelay(SETTLE + 2, () -> unpower(helper));
        helper.runAfterDelay(SETTLE * 2, () -> {
            helper.assertTrue(sticks(helper) == 4, "a pulse during the cooldown doesn't craft, got " + sticks(helper));
            helper.assertTrue(helper.<AutoMachineBlockEntity>getBlockEntity(CRAFTER).isCoolingDown(), "still cooling down");
        });
        helper.runAfterDelay(COOLDOWN + SETTLE, () -> power(helper));
        helper.runAfterDelay(COOLDOWN + SETTLE * 2, () -> {
            helper.assertTrue(sticks(helper) == 8, "a pulse after the cooldown crafts, got " + sticks(helper));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void showsRunningOnlyDuringTheCooldown(GameTestHelper helper) {
        placeStickSetup(helper, RedstoneMode.ON_POWERED);
        power(helper);
        helper.runAfterDelay(2, () -> unpower(helper));
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.TRIGGERED, false);
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.POWERED, true);
        });
        helper.runAfterDelay(COOLDOWN + SETTLE, () -> {
            helper.assertBlockProperty(CRAFTER, AutoMachineBlock.POWERED, false);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void togglingTheSignalDoesNotSkipTheCooldown(GameTestHelper helper) {
        placeStickSetup(helper, RedstoneMode.CONSTANT);
        power(helper);
        helper.runAfterDelay(SETTLE, () -> unpower(helper));
        helper.runAfterDelay(SETTLE + 2, () -> power(helper));
        helper.runAfterDelay(COOLDOWN - 2, () ->
                helper.assertTrue(sticks(helper) == 4, "no craft before the cooldown ends, got " + sticks(helper)));
        helper.runAfterDelay(COOLDOWN + SETTLE, () -> {
            helper.assertTrue(sticks(helper) == 8, "crafts again once it ends, got " + sticks(helper));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void cooldownIsSavedWithTheBlock(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickSetup(helper, RedstoneMode.ON_POWERED);
        helper.assertTrue(crafter.attemptOperation(), "crafts");
        CompoundTag saved = crafter.saveWithoutMetadata(helper.getLevel().registryAccess());
        crafter.getStatus().endCooldown();
        helper.assertTrue(!crafter.isCoolingDown(), "cooldown ended");
        crafter.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(crafter.isCoolingDown(), "loading brings the cooldown back");
        helper.assertTrue(!crafter.attemptOperation(), "and it still stops the machine");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void statusSaysWhyItFails(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeCrafter(helper);
        helper.assertTrue(crafter.findProblem() == MachineProblem.EMPTY_TEMPLATE, "empty grid");
        setGrid(crafter, new ItemStack(Items.DIRT), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.STICK));
        helper.assertTrue(crafter.findProblem() == MachineProblem.NO_RECIPE, "not a recipe");

        crafter = placeStickCrafter(helper);
        helper.assertTrue(crafter.findProblem() == null, "a valid recipe can run");
        placeChest(helper, TOP, new ItemStack(Items.OAK_PLANKS, 1));
        placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        helper.assertTrue(!crafter.attemptOperation(), "no output face");
        helper.assertTrue(crafter.getStatus().getLastProblem() == MachineProblem.NO_OUTPUTS, "says there is no output");
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        helper.assertTrue(!crafter.attemptOperation(), "one plank short");
        helper.assertTrue(crafter.getStatus().getLastProblem() == MachineProblem.MISSING_ITEMS, "says items are missing");
        helper.assertTrue(!crafter.isCoolingDown(), "failed attempts don't cool down");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Speed and co-processor upgrades
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void speedUpgradesShortenTheCooldown(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickSetup(helper, RedstoneMode.ON_POWERED);
        int slot = UpgradeSlotType.SPEED.ordinal();
        crafter.getUpgrades().setStackInSlot(slot, new ItemStack(ModItems.SPEED_UPGRADE.get(), 3));
        helper.assertTrue(crafter.attemptOperation(), "crafts");
        helper.assertTrue(crafter.getStatus().getCooldownTotal() == COOLDOWN - 6, "3 upgrades: 6 ticks shorter, got "
                + crafter.getStatus().getCooldownTotal());

        // Only as many as take it down to 2 ticks fit.
        int useful = (COOLDOWN - 2 + 1) / 2;
        helper.assertTrue(crafter.getUpgrades().getSlotLimit(slot) == useful, "slot holds " + useful);
        crafter.getUpgrades().setStackInSlot(slot, new ItemStack(ModItems.SPEED_UPGRADE.get(), useful));
        helper.assertTrue(crafter.getUpgrades().insertItem(slot, ModItems.SPEED_UPGRADE.get().getDefaultInstance(), true).getCount() == 1,
                "a full slot refuses more");
        crafter.getStatus().endCooldown();
        helper.assertTrue(crafter.attemptOperation(), "crafts again");
        helper.assertTrue(crafter.getStatus().getCooldownTotal() == 2, "never below 2 ticks, got "
                + crafter.getStatus().getCooldownTotal());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void coProcessorsRepeatTheOperation(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickSetup(helper, RedstoneMode.ON_POWERED);
        crafter.getUpgrades().setStackInSlot(UpgradeSlotType.CO_PROCESSOR.ordinal(), new ItemStack(ModItems.CO_PROCESSOR_UPGRADE.get(), 2));
        helper.assertTrue(crafter.attemptOperation(), "crafts");
        helper.assertTrue(sticks(helper) == 12, "1 + 2 co-processors = 3 crafts, got " + sticks(helper));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void coProcessorsStopWhenItemsRunOut(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        Container input = placeChest(helper, TOP, new ItemStack(Items.OAK_PLANKS, 5));
        placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);
        crafter.getUpgrades().setStackInSlot(UpgradeSlotType.CO_PROCESSOR.ordinal(), new ItemStack(ModItems.CO_PROCESSOR_UPGRADE.get(), 64));
        helper.assertTrue(crafter.attemptOperation(), "some crafts happened: a success");
        helper.assertTrue(sticks(helper) == 8, "only 2 crafts' worth of planks, got " + sticks(helper));
        assertCount(helper, input, Items.OAK_PLANKS, 1, "the odd plank is left");
        helper.assertTrue(crafter.getStatus().getLastProblem() == null, "no problem shown");
        helper.assertTrue(crafter.isCoolingDown(), "cools down like any success");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Auto crafting recipes
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void craftsAutoCraftingRecipeWithItsCounts(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeBetterMaterialCrafter(helper);
        Container input = placeChest(helper, TOP, new ItemStack(Items.COPPER_INGOT, 25), new ItemStack(Items.IRON_INGOT, 9),
                new ItemStack(Items.REDSTONE_TORCH, 6));
        Container output = placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);

        helper.assertTrue(crafter.getCraftingResult().is(ModItems.BETTER_MATERIAL.get()), "the grid makes a Better Material");
        helper.assertTrue(crafter.getAmount(0) == 6 && crafter.getAmount(1) == 2 && crafter.getAmount(4) == 5,
                "6 copper per corner, 2 iron per side, 5 redstone torches in the middle");
        helper.assertTrue(crafter.attemptOperation(), "crafts");
        assertCount(helper, output, ModItems.BETTER_MATERIAL.get(), 3, "three made");
        assertCount(helper, input, Items.COPPER_INGOT, 1, "24 copper taken");
        assertCount(helper, input, Items.IRON_INGOT, 1, "8 iron taken");
        assertCount(helper, input, Items.REDSTONE_TORCH, 1, "5 redstone torches taken");
        helper.assertTrue(crafter.getTemplate().getStackInSlot(0).getCount() == 1, "the grid is never consumed");

        crafter.getStatus().endCooldown();
        helper.assertTrue(!crafter.attemptOperation(), "not enough left for a second one");
        assertCount(helper, input, Items.COPPER_INGOT, 1, "nothing taken by the failed craft");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void autoCraftingRecipeNeedsItsWholeCount(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeBetterMaterialCrafter(helper);
        // One copper short: every slot has at least one item, but not the recipe's count.
        Container input = placeChest(helper, TOP, new ItemStack(Items.COPPER_INGOT, 23), new ItemStack(Items.IRON_INGOT, 8),
                new ItemStack(Items.REDSTONE_TORCH, 5));
        Container output = placeChest(helper, BOTTOM);
        configure(crafter, RelativeSide.TOP, SideMode.INPUT);
        configure(crafter, RelativeSide.BOTTOM, SideMode.OUTPUT);

        helper.assertTrue(!crafter.attemptOperation(), "doesn't craft");
        helper.assertTrue(output.isEmpty(), "nothing made");
        assertCount(helper, input, Items.COPPER_INGOT, 23, "copper stays");
        assertCount(helper, input, Items.REDSTONE_TORCH, 5, "redstone torches stay");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void autoCraftingRecipeMatchesMovedAndMirroredLayouts(GameTestHelper helper) {
        AutoCraftingRecipe recipe = parseAutoCraftingRecipe(helper, """
                {"pattern": ["AB"], "key": {"A": {"item": "minecraft:stone", "count": 2}, "B": {"item": "minecraft:dirt", "count": 5}},
                 "result": {"id": "minecraft:diamond"}}""").getOrThrow();
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemStack none = ItemStack.EMPTY;

        int[] moved = recipe.amounts(CraftingInput.of(3, 3, List.of(none, none, none, none, stone, dirt, none, none, none)));
        helper.assertTrue(moved != null && moved[0] == 2 && moved[1] == 5, "matches anywhere in the grid");
        int[] mirrored = recipe.amounts(CraftingInput.of(3, 3, List.of(dirt, stone, none, none, none, none, none, none, none)));
        helper.assertTrue(mirrored != null && mirrored[0] == 5 && mirrored[1] == 2, "matches mirrored, with the counts following the items");
        helper.assertTrue(recipe.amounts(CraftingInput.of(3, 3, List.of(stone, none, dirt, none, none, none, none, none, none))) == null,
                "doesn't match with a gap");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void invalidAutoCraftingRecipesAreRejected(GameTestHelper helper) {
        helper.assertTrue(parseAutoCraftingRecipe(helper, """
                {"pattern": ["AB"], "key": {"A": {"item": "minecraft:stone"}}, "result": {"id": "minecraft:diamond"}}""").isError(),
                "symbol missing from the key");
        helper.assertTrue(parseAutoCraftingRecipe(helper, """
                {"pattern": ["A"], "key": {"A": {"item": "minecraft:stone"}, "B": {"item": "minecraft:dirt"}},
                 "result": {"id": "minecraft:diamond"}}""").isError(), "unused key");
        helper.assertTrue(parseAutoCraftingRecipe(helper, """
                {"pattern": ["AAAA"], "key": {"A": {"item": "minecraft:stone"}}, "result": {"id": "minecraft:diamond"}}""").isError(),
                "wider than the crafter's grid");
        helper.assertTrue(parseAutoCraftingRecipe(helper, """
                {"pattern": ["A"], "key": {"A": {"item": "minecraft:stone", "count": 0}}, "result": {"id": "minecraft:diamond"}}""").isError(),
                "count below 1");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void craftingRecipesStillComeFirst(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        helper.assertTrue(crafter.getCraftingResult().is(Items.STICK), "a crafting recipe's layout makes its result");
        helper.assertTrue(crafter.getAmount(1) == 1 && crafter.getAmount(4) == 1 && crafter.getAmount(0) == 0,
                "one item per used slot");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------------

    /** Crafter facing north in the "on powered" (rising edge) redstone mode, which most tests use. */
    private static BetterAutoCrafterBlockEntity placeCrafter(GameTestHelper helper) {
        helper.setBlock(CRAFTER, ModBlocks.BETTER_AUTO_CRAFTER.get().defaultBlockState()
                .setValue(AutoMachineBlock.FACING, Direction.NORTH));
        BetterAutoCrafterBlockEntity crafter = helper.getBlockEntity(CRAFTER);
        crafter.getSettings().setRedstoneMode(RedstoneMode.ON_POWERED);
        return crafter;
    }

    /** Stick crafter with a 40-plank input chest on top and an empty output chest below. */
    private static BetterAutoCrafterBlockEntity placeStickSetup(GameTestHelper helper, RedstoneMode mode) {
        BetterAutoCrafterBlockEntity crafter = placeStickCrafter(helper);
        crafter.getSettings().setRedstoneMode(mode);
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

    /** Crafter with the Better Material's auto crafting layout, one item per slot. */
    private static BetterAutoCrafterBlockEntity placeBetterMaterialCrafter(GameTestHelper helper) {
        BetterAutoCrafterBlockEntity crafter = placeCrafter(helper);
        ItemStack copper = new ItemStack(Items.COPPER_INGOT);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        setGrid(crafter, copper, iron, copper, iron, new ItemStack(Items.REDSTONE_TORCH), iron, copper, iron, copper);
        return crafter;
    }

    private static DataResult<AutoCraftingRecipe> parseAutoCraftingRecipe(GameTestHelper helper, String json) {
        return new AutoCraftingRecipe.Serializer().codec().codec()
                .parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), JsonParser.parseString(json));
    }

    private static void setGrid(BetterAutoCrafterBlockEntity crafter, ItemStack... stacks) {
        for (int i = 0; i < stacks.length; i++) {
            crafter.getTemplate().setStackInSlot(i, stacks[i].copy());
        }
    }

    private static void assertGridUntouched(GameTestHelper helper, BetterAutoCrafterBlockEntity crafter) {
        helper.assertTrue(crafter.getTemplate().getStackInSlot(1).is(Items.OAK_PLANKS)
                        && crafter.getTemplate().getStackInSlot(4).is(Items.OAK_PLANKS),
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
