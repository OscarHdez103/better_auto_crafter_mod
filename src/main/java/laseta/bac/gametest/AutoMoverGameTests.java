package laseta.bac.gametest;

import java.util.List;

import io.netty.buffer.Unpooled;
import laseta.bac.BetterAutoCrafter;
import laseta.bac.CommonConfig;
import laseta.bac.block.entity.BetterAutoMoverBlockEntity;
import laseta.bac.machine.AutoMachineBlock;
import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.machine.RedstoneMode;
import laseta.bac.machine.TransferPriority;
import laseta.bac.menu.BetterAutoMoverMenu;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModDataComponents;
import laseta.bac.registry.ModItems;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import laseta.bac.upgrade.LinkedFace;
import laseta.bac.upgrade.UpgradeSlotType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world tests for the Better Auto Mover, which shares everything but the operation with the crafter (whose tests
 * cover the shared redstone handling, priorities and upgrades in depth).
 *
 * <p>Layout (relative to the test structure): the mover faces north, so
 * TOP = above, BOTTOM = below, FRONT = north, BACK = south (used for the redstone block).</p>
 */
@GameTestHolder(BetterAutoCrafter.MODID)
@PrefixGameTestTemplate(false)
public final class AutoMoverGameTests {
    private static final String TEMPLATE = "empty";
    private static final BlockPos MOVER = new BlockPos(2, 2, 2);
    private static final BlockPos TOP = MOVER.above();
    private static final BlockPos BOTTOM = MOVER.below();
    private static final BlockPos FRONT = MOVER.north();
    private static final BlockPos REDSTONE = MOVER.south();
    private static final BlockPos LINKED = new BlockPos(6, 2, 2);
    private static final int SETTLE = 5;

    private AutoMoverGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void movesTheTemplatesItemsOnPower(GameTestHelper helper) {
        BetterAutoMoverBlockEntity mover = placeMover(helper,
                new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.DIRT, 4));
        Container input = placeChest(helper, TOP, new ItemStack(Items.COBBLESTONE, 20), new ItemStack(Items.DIRT, 10));
        Container output = placeChest(helper, BOTTOM);

        helper.setBlock(REDSTONE, Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(SETTLE, () -> {
            assertCount(helper, output, Items.COBBLESTONE, 16, "cobblestone moved");
            assertCount(helper, output, Items.DIRT, 4, "dirt moved");
            assertCount(helper, input, Items.COBBLESTONE, 4, "cobblestone left");
            assertCount(helper, input, Items.DIRT, 6, "dirt left");
            helper.assertTrue(mover.getTemplate().getStackInSlot(0).getCount() == 16
                    && mover.getTemplate().getStackInSlot(1).getCount() == 4, "the template must never be consumed");
            helper.assertBlockProperty(MOVER, AutoMachineBlock.FAILED, false);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void movesNothingUnlessEverythingIsThere(GameTestHelper helper) {
        BetterAutoMoverBlockEntity mover = placeMover(helper,
                new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.DIRT, 4));
        Container input = placeChest(helper, TOP, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIRT, 3));
        Container output = placeChest(helper, BOTTOM);

        helper.assertTrue(!mover.attemptOperation(), "one dirt short: nothing moves");
        assertCount(helper, input, Items.COBBLESTONE, 64, "cobblestone stays");
        assertCount(helper, input, Items.DIRT, 3, "dirt stays");
        helper.assertTrue(output.isEmpty(), "output stays empty");
        helper.assertBlockProperty(MOVER, AutoMachineBlock.FAILED, true);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void eachSlotModeMovesWhatIsThere(GameTestHelper helper) {
        BetterAutoMoverBlockEntity mover = placeMover(helper,
                new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.DIRT, 4), new ItemStack(Items.SAND, 8));
        mover.getOptions().set(BetterAutoMoverBlockEntity.GRID_MODE, BetterAutoMoverBlockEntity.GridMode.EACH_SLOT);
        Container input = placeChest(helper, TOP, new ItemStack(Items.COBBLESTONE, 20), new ItemStack(Items.DIRT, 3),
                new ItemStack(Items.SAND, 8));
        Container output = placeChest(helper, BOTTOM);

        helper.assertTrue(mover.attemptOperation(), "moves the slots whose items are there");
        assertCount(helper, output, Items.COBBLESTONE, 16, "cobblestone moved");
        assertCount(helper, output, Items.SAND, 8, "sand moved");
        assertCount(helper, input, Items.DIRT, 3, "one dirt short: dirt stays");
        assertCount(helper, input, Items.COBBLESTONE, 4, "cobblestone left");

        helper.assertTrue(!mover.attemptOperation(), "no slot has all its items any more");
        assertCount(helper, input, Items.COBBLESTONE, 4, "nothing more taken");
        helper.assertBlockProperty(MOVER, AutoMachineBlock.FAILED, true);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void eachSlotModeSkipsSlotsWithoutRoom(GameTestHelper helper) {
        BetterAutoMoverBlockEntity mover = placeMover(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.DIRT, 4));
        mover.getOptions().set(BetterAutoMoverBlockEntity.GRID_MODE, BetterAutoMoverBlockEntity.GridMode.EACH_SLOT);
        Container input = placeChest(helper, TOP, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.DIRT, 4));
        // Room for 4 more dirt only (the chest is full of dirt stacks, one of them 60).
        Container output = fullChestExceptOneSlot(helper, BOTTOM, new ItemStack(Items.DIRT, 60));

        helper.assertTrue(mover.attemptOperation(), "the dirt fits");
        assertCount(helper, input, Items.DIRT, 0, "dirt moved");
        assertCount(helper, input, Items.COBBLESTONE, 16, "cobblestone has no room: stays");
        assertCount(helper, output, Items.COBBLESTONE, 0, "no cobblestone pushed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void gridModeIsSetFromTheMenuAndSaved(GameTestHelper helper) {
        BetterAutoMoverBlockEntity mover = placeMover(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BetterAutoMoverMenu menu = new BetterAutoMoverMenu(0, player.getInventory(), mover);
        helper.assertTrue(mover.getOptions().get(BetterAutoMoverBlockEntity.GRID_MODE) == BetterAutoMoverBlockEntity.GridMode.ALL_TOGETHER,
                "all together by default");

        helper.assertTrue(menu.clickMenuButton(player, AutoMachineMenu.selectOptionButton(0, BetterAutoMoverBlockEntity.GridMode.EACH_SLOT)),
                "the button is accepted");
        helper.assertTrue(mover.getOptions().get(BetterAutoMoverBlockEntity.GRID_MODE) == BetterAutoMoverBlockEntity.GridMode.EACH_SLOT,
                "the menu button selects each slot");
        helper.assertTrue(!menu.clickMenuButton(player, AutoMachineMenu.selectOptionButton(0, BetterAutoMoverBlockEntity.GridMode.EACH_SLOT) + 2),
                "a value the option doesn't have is refused");

        CompoundTag saved = mover.saveWithoutMetadata(helper.getLevel().registryAccess());
        mover.getOptions().set(BetterAutoMoverBlockEntity.GRID_MODE, BetterAutoMoverBlockEntity.GridMode.ALL_TOGETHER);
        mover.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(mover.getOptions().get(BetterAutoMoverBlockEntity.GRID_MODE) == BetterAutoMoverBlockEntity.GridMode.EACH_SLOT,
                "saved with the block");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void clientMenuGetsTheGridSizeFromTheServer(GameTestHelper helper) {
        BetterAutoMoverBlockEntity mover = placeMover(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(),
                ConnectionType.NEOFORGE);
        mover.writeMenuData(buffer);
        BetterAutoMoverMenu clientMenu = new BetterAutoMoverMenu(0, player.getInventory(), buffer);
        BetterAutoMoverMenu serverMenu = new BetterAutoMoverMenu(0, player.getInventory(), mover);

        helper.assertTrue(mover.getGridWidth() == CommonConfig.moverGridWidth() && mover.getGridHeight() == CommonConfig.moverGridHeight(),
                "the grid has the config's size");
        helper.assertTrue(clientMenu.getLayout().equals(serverMenu.getLayout()), "both sides lay the grid out the same");
        helper.assertTrue(clientMenu.slots.size() == serverMenu.slots.size(), "both sides have the same slots");
        helper.assertTrue(clientMenu.getBlockPos().equals(mover.getBlockPos()), "the client knows the block's position");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void gathersFromSeveralSlotsAndInventories(GameTestHelper helper) {
        // Two template slots of the same item add up; the input chests only have it in partial stacks.
        BetterAutoMoverBlockEntity mover = placeMover(helper,
                new ItemStack(Items.COBBLESTONE, 40), new ItemStack(Items.COBBLESTONE, 30));
        configure(mover, RelativeSide.FRONT, SideMode.INPUT);
        mover.getSettings().setInputPriority(TransferPriority.TOP);
        Container top = placeChest(helper, TOP, new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.COBBLESTONE, 20));
        Container front = placeChest(helper, FRONT, new ItemStack(Items.COBBLESTONE, 30));
        Container output = placeChest(helper, BOTTOM);

        helper.assertTrue(mover.attemptOperation(), "moves");
        assertCount(helper, output, Items.COBBLESTONE, 70, "all 70 cobblestone moved");
        assertCount(helper, top, Items.COBBLESTONE, 0, "first input emptied");
        assertCount(helper, front, Items.COBBLESTONE, 5, "the rest from the second input");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void splitsOverOutputsWhenNoneHasRoomForAll(GameTestHelper helper) {
        BetterAutoMoverBlockEntity mover = placeMover(helper, new ItemStack(Items.COBBLESTONE, 64));
        configure(mover, RelativeSide.FRONT, SideMode.OUTPUT);
        placeChest(helper, TOP, new ItemStack(Items.COBBLESTONE, 64));
        Container bottom = fullChestExceptOneSlot(helper, BOTTOM, new ItemStack(Items.COBBLESTONE, 32));
        Container front = fullChestExceptOneSlot(helper, FRONT, new ItemStack(Items.COBBLESTONE, 32));

        helper.assertTrue(mover.attemptOperation(), "moves");
        assertCount(helper, bottom, Items.COBBLESTONE, 64, "first output filled up");
        assertCount(helper, front, Items.COBBLESTONE, 64, "the rest in the second output");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fullOutputMovesNothing(GameTestHelper helper) {
        BetterAutoMoverBlockEntity mover = placeMover(helper, new ItemStack(Items.COBBLESTONE, 16));
        Container input = placeChest(helper, TOP, new ItemStack(Items.COBBLESTONE, 64));
        fullChestExceptOneSlot(helper, BOTTOM, new ItemStack(Items.COBBLESTONE, 60));

        helper.assertTrue(!mover.attemptOperation(), "room for 4 only: nothing moves");
        assertCount(helper, input, Items.COBBLESTONE, 64, "cobblestone stays");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stockingLeavesStockedItemsOut(GameTestHelper helper) {
        BetterAutoMoverBlockEntity mover = placeMover(helper,
                new ItemStack(Items.COBBLESTONE, 8), new ItemStack(Items.DIRT, 4));
        mover.getUpgrades().setStackInSlot(UpgradeSlotType.STOCKING.ordinal(), new ItemStack(ModItems.STOCKING_UPGRADE.get(), 16));
        Container input = placeChest(helper, TOP, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIRT, 64));
        Container output = placeChest(helper, BOTTOM, new ItemStack(Items.COBBLESTONE, 16));

        helper.assertTrue(mover.attemptOperation(), "moves the dirt");
        assertCount(helper, output, Items.COBBLESTONE, 16, "cobblestone is stocked: none added");
        assertCount(helper, input, Items.COBBLESTONE, 64, "cobblestone not taken");
        assertCount(helper, output, Items.DIRT, 4, "dirt moved");

        output.setItem(1, new ItemStack(Items.DIRT, 16));
        helper.assertTrue(!mover.attemptOperation(), "everything stocked: nothing to move");
        assertCount(helper, input, Items.DIRT, 60, "no more dirt taken");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void matchNbtOffMovesVariantsAsTheyAre(GameTestHelper helper) {
        BetterAutoMoverBlockEntity mover = placeMover(helper, new ItemStack(Items.OAK_PLANKS, 4));
        Container input = placeChest(helper, TOP, renamed(new ItemStack(Items.OAK_PLANKS, 2)), new ItemStack(Items.OAK_PLANKS, 2));
        Container output = placeChest(helper, BOTTOM);

        helper.assertTrue(!mover.attemptOperation(), "NBT matching on: only 2 exact planks");
        mover.getSettings().setMatchNbt(false);
        helper.assertTrue(mover.attemptOperation(), "NBT matching off: renamed planks count too");
        helper.assertTrue(input.isEmpty(), "all planks taken");
        int renamed = 0;
        int plain = 0;
        for (int i = 0; i < output.getContainerSize(); i++) {
            ItemStack stack = output.getItem(i);
            if (stack.has(DataComponents.CUSTOM_NAME)) {
                renamed += stack.getCount();
            } else {
                plain += stack.getCount();
            }
        }
        helper.assertTrue(renamed == 2 && plain == 2, "moved as they were: expected 2 renamed and 2 plain, found " + renamed + " and " + plain);
        helper.succeed();
    }

    @GameTest(template = "empty_wide")
    public static void multiPullUpgradeWorksOnTheMover(GameTestHelper helper) {
        BetterAutoMoverBlockEntity mover = placeMover(helper, new ItemStack(Items.COBBLESTONE, 10));
        Container linked = placeChest(helper, LINKED, new ItemStack(Items.COBBLESTONE, 10));
        Container local = placeChest(helper, TOP, new ItemStack(Items.COBBLESTONE, 10));
        Container output = placeChest(helper, BOTTOM);
        ItemStack upgrade = ModItems.MULTI_TRANSLOCATION_UPGRADE_PULL.get().getDefaultInstance();
        upgrade.set(ModDataComponents.TRANSLOCATION_TARGETS.get(), List.of(
                new LinkedFace(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(LINKED)), Direction.UP)));
        mover.getUpgrades().setStackInSlot(UpgradeSlotType.TRANSLOCATION_PULL.ordinal(), upgrade);

        helper.assertTrue(mover.attemptOperation(), "moves");
        assertCount(helper, linked, Items.COBBLESTONE, 0, "pulled from the linked chest");
        assertCount(helper, local, Items.COBBLESTONE, 10, "the input face is replaced by the link");
        assertCount(helper, output, Items.COBBLESTONE, 10, "pushed to the output face");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void blockHasCraftingRecipe(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack hopper = new ItemStack(Items.HOPPER);
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                iron, new ItemStack(Items.REDSTONE_TORCH), iron,
                hopper, new ItemStack(Items.DROPPER), hopper,
                iron, new ItemStack(Items.COMPARATOR), iron));
        ItemStack result = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(holder -> holder.value().assemble(input, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(ModItems.BETTER_AUTO_MOVER.get()), "recipe should craft the Better Auto Mover, got " + result);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------------

    /** Mover facing north in the "on powered" mode, pulling from the top and pushing to the bottom. */
    private static BetterAutoMoverBlockEntity placeMover(GameTestHelper helper, ItemStack... template) {
        helper.setBlock(MOVER, ModBlocks.BETTER_AUTO_MOVER.get().defaultBlockState().setValue(AutoMachineBlock.FACING, Direction.NORTH));
        BetterAutoMoverBlockEntity mover = helper.getBlockEntity(MOVER);
        mover.getSettings().setRedstoneMode(RedstoneMode.ON_POWERED);
        for (int i = 0; i < template.length; i++) {
            mover.getTemplate().setStackInSlot(i, template[i]);
        }
        configure(mover, RelativeSide.TOP, SideMode.INPUT);
        configure(mover, RelativeSide.BOTTOM, SideMode.OUTPUT);
        return mover;
    }

    private static void configure(BetterAutoMoverBlockEntity mover, RelativeSide side, SideMode mode) {
        mover.getSideConfig().setMode(side, mode);
    }

    private static Container placeChest(GameTestHelper helper, BlockPos pos, ItemStack... contents) {
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(pos);
        for (int i = 0; i < contents.length; i++) {
            chest.setItem(i, contents[i]);
        }
        return chest;
    }

    /** A chest full of dirt except its first slot, which holds {@code first}. */
    private static Container fullChestExceptOneSlot(GameTestHelper helper, BlockPos pos, ItemStack first) {
        Container chest = placeChest(helper, pos, first);
        for (int i = 1; i < chest.getContainerSize(); i++) {
            chest.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        return chest;
    }

    private static ItemStack renamed(ItemStack stack) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Renamed"));
        return stack;
    }

    private static void assertCount(GameTestHelper helper, Container container, Item item, int expected, String what) {
        int actual = container.countItem(item);
        helper.assertTrue(actual == expected, what + ": expected " + expected + " but found " + actual);
    }
}
