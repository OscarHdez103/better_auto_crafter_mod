package laseta.bac.gametest;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.entity.BetterAutoBrewerBlockEntity;
import laseta.bac.block.entity.BetterAutoSmelterBlockEntity;
import laseta.bac.item.ItemSettingUpgradeItem;
import laseta.bac.machine.AutoMachineBlock;
import laseta.bac.machine.AutoMachineBlockEntity;
import laseta.bac.machine.FaceChannel;
import laseta.bac.machine.MachineProblem;
import laseta.bac.machine.RedstoneMode;
import laseta.bac.menu.BetterAutoBrewerMenu;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModItems;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The smelter and the brewer: their recipes, fuel, face channels and machine upgrades. The machine faces north, so its
 * front is north, its left east and its right west; inputs come from the top and outputs go to the bottom.
 */
@GameTestHolder(BetterAutoCrafter.MODID)
@PrefixGameTestTemplate(false)
public final class SmelterBrewerGameTests {
    private static final String TEMPLATE = "empty";
    private static final BlockPos MACHINE = new BlockPos(2, 2, 2);
    private static final BlockPos TOP = MACHINE.above();
    private static final BlockPos BOTTOM = MACHINE.below();
    private static final BlockPos FRONT = MACHINE.north();
    private static final BlockPos LEFT = MACHINE.east();

    private SmelterBrewerGameTests() {
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Smelter
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void smelterBlastsWithHalfTheFuel(GameTestHelper helper) {
        BetterAutoSmelterBlockEntity smelter = placeSmelter(helper, new ItemStack(Items.RAW_IRON));
        Container input = placeChest(helper, TOP, new ItemStack(Items.RAW_IRON, 2));
        Container output = placeChest(helper, BOTTOM);
        Container fuel = placeChest(helper, FRONT, new ItemStack(Items.COAL, 2));
        smelter.getChannelFaces().set(BetterAutoSmelterBlockEntity.FUEL, RelativeSide.FRONT, true);

        helper.assertTrue(smelter.getKind() == BetterAutoSmelterBlockEntity.Kind.BLASTING, "raw iron is blasted");
        helper.assertTrue(smelter.getFuelCost() == 100, "blasting uses half a furnace's fuel, got " + smelter.getFuelCost());
        helper.assertTrue(smelter.attemptOperation(), "smelts");
        assertCount(helper, output, Items.IRON_INGOT, 1, "an ingot made");
        assertCount(helper, input, Items.RAW_IRON, 1, "one raw iron taken");
        assertCount(helper, fuel, Items.COAL, 1, "one coal taken");
        helper.assertTrue(smelter.getFuel().getStored() == 1500, "1600 - 100 fuel left, got " + smelter.getFuel().getStored());

        smelter.getStatus().endCooldown();
        helper.assertTrue(smelter.attemptOperation(), "smelts again");
        assertCount(helper, fuel, Items.COAL, 1, "the stored fuel is used first: no coal taken");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void smelterPrefersCampfireRecipesWithoutFuel(GameTestHelper helper) {
        BetterAutoSmelterBlockEntity smelter = placeSmelter(helper, new ItemStack(Items.BEEF));
        placeChest(helper, TOP, new ItemStack(Items.BEEF, 1));
        Container output = placeChest(helper, BOTTOM);

        helper.assertTrue(smelter.getKind() == BetterAutoSmelterBlockEntity.Kind.CAMPFIRE, "beef is cooked like on a campfire");
        helper.assertTrue(smelter.attemptOperation(), "cooks without any fuel face");
        assertCount(helper, output, Items.COOKED_BEEF, 1, "steak made");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void smelterWithoutFuelTakesNothing(GameTestHelper helper) {
        BetterAutoSmelterBlockEntity smelter = placeSmelter(helper, new ItemStack(Items.SAND));
        Container input = placeChest(helper, TOP, new ItemStack(Items.SAND, 4));
        Container output = placeChest(helper, BOTTOM);

        helper.assertTrue(!smelter.attemptOperation(), "no fuel");
        helper.assertTrue(smelter.getStatus().getLastProblem() == MachineProblem.NO_FUEL, "says it has no fuel");
        assertCount(helper, input, Items.SAND, 4, "nothing taken");
        helper.assertTrue(output.isEmpty(), "nothing made");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void smelterPushesFuelLeftoversToTheirFaces(GameTestHelper helper) {
        BetterAutoSmelterBlockEntity smelter = placeSmelter(helper, new ItemStack(Items.SAND));
        Container input = placeChest(helper, TOP, new ItemStack(Items.SAND, 4));
        placeChest(helper, BOTTOM);
        Container fuel = placeChest(helper, FRONT, new ItemStack(Items.LAVA_BUCKET));
        Container leftovers = placeChest(helper, LEFT);
        smelter.getChannelFaces().set(BetterAutoSmelterBlockEntity.FUEL, RelativeSide.FRONT, true);

        helper.assertTrue(!smelter.attemptOperation(), "no leftover face for the bucket");
        helper.assertTrue(smelter.getStatus().getLastProblem() == MachineProblem.LEFTOVER_FULL, "says the bucket has nowhere to go");
        assertCount(helper, fuel, Items.LAVA_BUCKET, 1, "the lava bucket stays");
        assertCount(helper, input, Items.SAND, 4, "nothing taken");

        smelter.getChannelFaces().set(BetterAutoSmelterBlockEntity.FUEL_LEFTOVERS, RelativeSide.LEFT, true);
        helper.assertTrue(smelter.attemptOperation(), "smelts");
        assertCount(helper, leftovers, Items.BUCKET, 1, "the empty bucket goes to the leftover face");
        helper.assertTrue(fuel.isEmpty(), "the lava bucket is used");
        helper.assertTrue(smelter.getFuel().getStored() == 20000 - 200, "a lava bucket's fuel stored, got " + smelter.getFuel().getStored());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void smelterFuelFilterOnlyBurnsItsFuel(GameTestHelper helper) {
        BetterAutoSmelterBlockEntity smelter = placeSmelter(helper, new ItemStack(Items.SAND));
        placeChest(helper, TOP, new ItemStack(Items.SAND, 4));
        placeChest(helper, BOTTOM);
        Container fuel = placeChest(helper, FRONT, new ItemStack(Items.COAL, 2));
        smelter.getChannelFaces().set(BetterAutoSmelterBlockEntity.FUEL, RelativeSide.FRONT, true);
        ItemStack filter = ModItems.FUEL_FILTER_UPGRADE.get().getDefaultInstance();
        smelter.getMachineUpgrades().setStackInSlot(0, filter);

        helper.assertTrue(smelter.findProblem() == MachineProblem.UNSET_UPGRADE, "an unset filter stops it");
        helper.assertTrue(!ModItems.FUEL_FILTER_UPGRADE.get().canBeSetTo(new ItemStack(Items.DIRT)), "only fuels can be set");
        ItemStack set = filter.copy();
        ItemSettingUpgradeItem.setSetting(set, new ItemStack(Items.CHARCOAL));
        smelter.getMachineUpgrades().setStackInSlot(0, set);
        helper.assertTrue(!smelter.attemptOperation(), "coal isn't charcoal");
        helper.assertTrue(smelter.getStatus().getLastProblem() == MachineProblem.NO_FUEL, "no allowed fuel");

        fuel.setItem(1, new ItemStack(Items.CHARCOAL));
        helper.assertTrue(smelter.attemptOperation(), "burns the charcoal");
        assertCount(helper, fuel, Items.COAL, 2, "the coal stays");
        assertCount(helper, fuel, Items.CHARCOAL, 0, "the charcoal is used");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void smelterKeepsItsFuelAndFacesWhenSaved(GameTestHelper helper) {
        BetterAutoSmelterBlockEntity smelter = placeSmelter(helper, new ItemStack(Items.SAND));
        smelter.getFuel().add(1234);
        smelter.getChannelFaces().set(BetterAutoSmelterBlockEntity.FUEL_LEFTOVERS, RelativeSide.BACK, true);
        CompoundTag saved = smelter.saveWithoutMetadata(helper.getLevel().registryAccess());
        smelter.getFuel().consume(1234);
        smelter.getChannelFaces().set(BetterAutoSmelterBlockEntity.FUEL_LEFTOVERS, RelativeSide.BACK, false);
        smelter.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(smelter.getFuel().getStored() == 1234, "fuel kept");
        helper.assertTrue(smelter.getChannelFaces().isOn(BetterAutoSmelterBlockEntity.FUEL_LEFTOVERS, RelativeSide.BACK), "faces kept");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Brewer
    // ---------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void brewerBrewsInOrder(GameTestHelper helper) {
        BetterAutoBrewerBlockEntity brewer = placeBrewer(helper, Items.NETHER_WART, Items.SUGAR);
        Container input = placeChest(helper, TOP, new ItemStack(Items.NETHER_WART, 2), new ItemStack(Items.SUGAR, 2));
        Container output = placeChest(helper, BOTTOM);
        Container starters = placeChest(helper, FRONT, waterBottles(4));
        Container fuel = placeChest(helper, LEFT, new ItemStack(Items.BLAZE_POWDER, 2));

        helper.assertTrue(isPotion(brewer.getResult(), Potions.SWIFTNESS), "wart then sugar is swiftness, got " + brewer.getResult());
        helper.assertTrue(brewer.attemptOperation(), "brews");
        int swiftness = 0;
        for (int i = 0; i < output.getContainerSize(); i++) {
            swiftness += isPotion(output.getItem(i), Potions.SWIFTNESS) ? output.getItem(i).getCount() : 0;
        }
        helper.assertTrue(swiftness == 3, "3 potions of swiftness, got " + swiftness);
        assertCount(helper, input, Items.NETHER_WART, 1, "one wart taken");
        assertCount(helper, input, Items.SUGAR, 1, "one sugar taken");
        assertCount(helper, starters, Items.POTION, 1, "3 water bottles taken");
        assertCount(helper, fuel, Items.BLAZE_POWDER, 1, "one blaze powder taken");
        helper.assertTrue(brewer.getFuel().getStored() == 18, "2 of 20 fuel used, got " + brewer.getFuel().getStored());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void brewerNeedsAWholeBrewOfStarters(GameTestHelper helper) {
        BetterAutoBrewerBlockEntity brewer = placeBrewer(helper, Items.NETHER_WART);
        Container input = placeChest(helper, TOP, new ItemStack(Items.NETHER_WART));
        placeChest(helper, BOTTOM);
        Container starters = placeChest(helper, FRONT, waterBottles(2));
        Container fuel = placeChest(helper, LEFT, new ItemStack(Items.BLAZE_POWDER));

        helper.assertTrue(!brewer.attemptOperation(), "only 2 bottles");
        helper.assertTrue(brewer.getStatus().getLastProblem() == MachineProblem.MISSING_STARTER, "says starters are missing");
        assertCount(helper, starters, Items.POTION, 2, "bottles stay");
        assertCount(helper, input, Items.NETHER_WART, 1, "wart stays");
        assertCount(helper, fuel, Items.BLAZE_POWDER, 1, "fuel stays");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void brewerFillsGlassBottlesWithInfiniteWater(GameTestHelper helper) {
        BetterAutoBrewerBlockEntity brewer = placeBrewer(helper, Items.NETHER_WART);
        placeChest(helper, TOP, new ItemStack(Items.NETHER_WART));
        Container output = placeChest(helper, BOTTOM);
        Container starters = placeChest(helper, FRONT, new ItemStack(Items.GLASS_BOTTLE, 3));
        placeChest(helper, LEFT, new ItemStack(Items.BLAZE_POWDER));
        brewer.getMachineUpgrades().setStackInSlot(0, ModItems.INFINITE_WATER_UPGRADE.get().getDefaultInstance());

        helper.assertTrue(brewer.getPulledStarter().is(Items.GLASS_BOTTLE), "pulls glass bottles");
        helper.assertTrue(brewer.attemptOperation(), "brews");
        helper.assertTrue(starters.isEmpty(), "the glass bottles are used");
        helper.assertTrue(isPotion(output.getItem(0), Potions.AWKWARD), "awkward potions, got " + output.getItem(0));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void brewerStartsFromThePotionStartersItem(GameTestHelper helper) {
        BetterAutoBrewerBlockEntity brewer = placeBrewer(helper, Items.SUGAR);
        placeChest(helper, TOP, new ItemStack(Items.SUGAR));
        Container output = placeChest(helper, BOTTOM);
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        placeChest(helper, FRONT, awkward.copy(), awkward.copy(), awkward.copy());
        placeChest(helper, LEFT, new ItemStack(Items.BLAZE_POWDER));
        brewer.getMachineUpgrades().setStackInSlot(0, ModItems.POTION_STARTER_UPGRADE.get().getDefaultInstance());
        helper.assertTrue(brewer.findProblem() == MachineProblem.UNSET_UPGRADE, "an unset starter stops it");

        // Set it like a player does: clicking the setting box with the item carried.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BetterAutoBrewerMenu menu = new BetterAutoBrewerMenu(0, player.getInventory(), brewer);
        menu.setCarried(awkward.copy());
        helper.assertTrue(menu.clickMenuButton(player, menu.setUpgradeSettingButton(0)), "setting accepted");
        helper.assertTrue(menu.getCarried().is(Items.POTION), "the carried item isn't used up");
        helper.assertTrue(brewer.findProblem() == null, "set: nothing stops it");
        helper.assertTrue(brewer.attemptOperation(), "brews");
        helper.assertTrue(isPotion(output.getItem(0), Potions.SWIFTNESS), "awkward + sugar is swiftness, got " + output.getItem(0));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void brewerSaysWhenItIsNoBrew(GameTestHelper helper) {
        BetterAutoBrewerBlockEntity brewer = placeBrewer(helper);
        helper.assertTrue(brewer.findProblem() == MachineProblem.EMPTY_TEMPLATE, "nothing set");
        brewer.getTemplate().setStackInSlot(0, new ItemStack(Items.DIRT));
        helper.assertTrue(brewer.findProblem() == MachineProblem.NO_RECIPE, "dirt doesn't brew");
        helper.assertTrue(brewer.getTemplate().insertItem(1, new ItemStack(Items.SUGAR, 5), false).getCount() == 4,
                "ingredient slots hold one item");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void channelFacesAreSetFromTheMenu(GameTestHelper helper) {
        BetterAutoBrewerBlockEntity brewer = placeBrewer(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BetterAutoBrewerMenu menu = new BetterAutoBrewerMenu(0, player.getInventory(), brewer);
        FaceChannel fuel = BetterAutoBrewerBlockEntity.FUEL;
        int index = BetterAutoBrewerBlockEntity.FEATURES.channels().indexOf(fuel);
        helper.assertTrue(menu.clickMenuButton(player, menu.toggleChannelFaceButton(index, RelativeSide.TOP)), "button accepted");
        helper.assertTrue(brewer.getChannelFaces().isOn(fuel, RelativeSide.TOP), "top is a fuel face");
        helper.assertTrue(!brewer.getChannelFaces().isOn(BetterAutoBrewerBlockEntity.STARTERS, RelativeSide.TOP), "only for fuel");
        menu.clickMenuButton(player, menu.toggleChannelFaceButton(index, RelativeSide.TOP));
        helper.assertTrue(!brewer.getChannelFaces().isOn(fuel, RelativeSide.TOP), "toggled off again");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------------

    private static BetterAutoSmelterBlockEntity placeSmelter(GameTestHelper helper, ItemStack input) {
        BetterAutoSmelterBlockEntity smelter = place(helper, ModBlocks.BETTER_AUTO_SMELTER.get().defaultBlockState());
        smelter.getTemplate().setStackInSlot(0, input);
        return smelter;
    }

    /** Brewer with those ingredients in order, starters from the front and fuel from the left. */
    private static BetterAutoBrewerBlockEntity placeBrewer(GameTestHelper helper, Item... ingredients) {
        BetterAutoBrewerBlockEntity brewer = place(helper, ModBlocks.BETTER_AUTO_BREWER.get().defaultBlockState());
        for (int i = 0; i < ingredients.length; i++) {
            brewer.getTemplate().setStackInSlot(i, new ItemStack(ingredients[i]));
        }
        brewer.getChannelFaces().set(BetterAutoBrewerBlockEntity.STARTERS, RelativeSide.FRONT, true);
        brewer.getChannelFaces().set(BetterAutoBrewerBlockEntity.FUEL, RelativeSide.LEFT, true);
        return brewer;
    }

    private static <T extends AutoMachineBlockEntity> T place(GameTestHelper helper, BlockState state) {
        helper.setBlock(MACHINE, state.setValue(AutoMachineBlock.FACING, Direction.NORTH));
        T machine = helper.getBlockEntity(MACHINE);
        machine.getSettings().setRedstoneMode(RedstoneMode.ON_POWERED);
        machine.getSideConfig().setMode(RelativeSide.TOP, SideMode.INPUT);
        machine.getSideConfig().setMode(RelativeSide.BOTTOM, SideMode.OUTPUT);
        return machine;
    }

    private static ItemStack[] waterBottles(int count) {
        ItemStack[] bottles = new ItemStack[count];
        for (int i = 0; i < count; i++) {
            bottles[i] = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        }
        return bottles;
    }

    private static boolean isPotion(ItemStack stack, Holder<Potion> potion) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return stack.is(Items.POTION) && contents != null && contents.is(potion);
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
