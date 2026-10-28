package laseta.bac.gametest;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.entity.BetterAutoBrewerBlockEntity;
import laseta.bac.block.entity.BetterAutoSmelterBlockEntity;
import laseta.bac.compat.MachineFuel;
import laseta.bac.item.ItemSettingUpgradeItem;
import laseta.bac.machine.AutoMachineBlock;
import laseta.bac.machine.AutoMachineBlockEntity;
import laseta.bac.machine.FaceChannel;
import laseta.bac.machine.MachineProblem;
import laseta.bac.machine.RecipeChoices;
import laseta.bac.machine.RedstoneMode;
import laseta.bac.material.Materials;
import laseta.bac.material.NoOtherModInTagCondition;
import laseta.bac.material.SharedMaterial;
import laseta.bac.menu.BetterAutoBrewerMenu;
import laseta.bac.menu.BetterAutoSmelterMenu;
import laseta.bac.recipe.AutoBrewingRecipe;
import laseta.bac.recipe.BrewingChain;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModItems;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.conditions.ICondition;
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
    public static void smelterFuelFilterIsSetLikeAGhostItem(GameTestHelper helper) {
        BetterAutoSmelterBlockEntity smelter = placeSmelter(helper, new ItemStack(Items.SAND));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BetterAutoSmelterMenu menu = new BetterAutoSmelterMenu(0, player.getInventory(), smelter);
        helper.assertTrue(!menu.setUpgradeSetting(0, new ItemStack(Items.CHARCOAL)), "no upgrade installed: nothing to set");
        smelter.getMachineUpgrades().setStackInSlot(0, ModItems.FUEL_FILTER_UPGRADE.get().getDefaultInstance());
        helper.assertTrue(!menu.setUpgradeSetting(0, new ItemStack(Items.DIRT)), "dirt isn't a fuel");
        helper.assertTrue(!menu.setUpgradeSetting(1, new ItemStack(Items.CHARCOAL)), "no such slot");
        helper.assertTrue(menu.setUpgradeSetting(0, new ItemStack(Items.CHARCOAL, 5)), "charcoal accepted");
        ItemStack setting = ItemSettingUpgradeItem.getSetting(smelter.getMachineUpgrades().get(BetterAutoSmelterBlockEntity.FUEL_FILTER));
        helper.assertTrue(setting.is(Items.CHARCOAL) && setting.getCount() == 1, "set to one charcoal, got " + setting);
        helper.assertTrue(menu.setUpgradeSetting(0, ItemStack.EMPTY), "cleared");
        helper.assertTrue(smelter.findProblem() == MachineProblem.UNSET_UPGRADE, "unset again");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void itemSettingUpgradesAreClearedByCraftingThemAlone(GameTestHelper helper) {
        for (Item upgrade : List.of(ModItems.FUEL_FILTER_UPGRADE.get(), ModItems.POTION_STARTER_UPGRADE.get())) {
            ItemStack set = new ItemStack(upgrade);
            ItemSettingUpgradeItem.setSetting(set, new ItemStack(Items.CHARCOAL));
            ItemStack cleared = craftAlone(helper, set);
            helper.assertTrue(cleared.is(upgrade) && cleared.getCount() == 1 && ItemSettingUpgradeItem.getSetting(cleared).isEmpty(),
                    "crafting it alone clears the setting, got " + cleared);
            helper.assertTrue(craftAlone(helper, new ItemStack(upgrade)).isEmpty(), "nothing to clear: no recipe");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fuelPagesListEveryMachineFuel(GameTestHelper helper) {
        List<MachineFuel> smelter = MachineFuel.smelterFuels();
        helper.assertTrue(smelter.stream().anyMatch(fuel -> fuel.stack().is(Items.COAL) && fuel.fuel() == 1600), "coal smelts 8 items");
        helper.assertTrue(smelter.stream().anyMatch(fuel -> fuel.stack().is(Items.LAVA_BUCKET)), "lava buckets burn too");
        helper.assertTrue(smelter.stream().noneMatch(fuel -> fuel.stack().is(Items.DIRT)), "dirt doesn't burn");
        helper.assertTrue(smelter.getFirst().fuel() >= smelter.getLast().fuel(), "the longest burning first");
        List<MachineFuel> brewer = MachineFuel.brewerFuels();
        helper.assertTrue(brewer.size() == 1 && brewer.getFirst().stack().is(Items.BLAZE_POWDER)
                && brewer.getFirst().fuel() == BetterAutoBrewerBlockEntity.FUEL_PER_ITEM, "only blaze powder, got " + brewer);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void smelterMakesSteelFromThreeIron(GameTestHelper helper) {
        BetterAutoSmelterBlockEntity smelter = placeSmelter(helper, new ItemStack(Items.IRON_INGOT, 3));
        Container input = placeChest(helper, TOP, new ItemStack(Items.IRON_INGOT, 5));
        Container output = placeChest(helper, BOTTOM);
        Container fuel = placeChest(helper, FRONT, new ItemStack(Items.COAL));
        smelter.getChannelFaces().set(BetterAutoSmelterBlockEntity.FUEL, RelativeSide.FRONT, true);

        helper.assertTrue(smelter.getKind() == BetterAutoSmelterBlockEntity.Kind.AUTO_SMELTING, "a smelter recipe");
        helper.assertTrue(smelter.getAmount() == 3, "takes 3 iron, got " + smelter.getAmount());
        helper.assertTrue(smelter.getFuelCost() == 400, "twice a furnace's fuel, got " + smelter.getFuelCost());
        // This mod's steel, or another steel mod's when one is installed.
        Item steel = Materials.STEEL.mainItem(helper.getLevel().registryAccess(), 1).getItem();
        helper.assertTrue(smelter.getResult().is(steel), "the steel in use, got " + smelter.getResult());
        helper.assertTrue(smelter.attemptOperation(), "smelts");
        assertCount(helper, output, steel, 1, "a steel ingot made");
        assertCount(helper, input, Items.IRON_INGOT, 2, "3 iron taken");
        assertCount(helper, fuel, Items.COAL, 0, "a coal burnt");
        helper.assertTrue(smelter.getFuel().getStored() == 1600 - 400, "1600 - 400 fuel left, got " + smelter.getFuel().getStored());

        smelter.getStatus().endCooldown();
        helper.assertTrue(!smelter.attemptOperation(), "only 2 iron left");
        helper.assertTrue(smelter.getStatus().getLastProblem() == MachineProblem.MISSING_ITEMS, "says items are missing");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void smelterRecipeNeedsItsCountInTheInputSlot(GameTestHelper helper) {
        BetterAutoSmelterBlockEntity smelter = placeSmelter(helper, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(smelter.getKind() == null && smelter.getResult().isEmpty(), "2 iron are just iron: no recipe");
        helper.assertTrue(smelter.findProblem() == MachineProblem.NO_RECIPE, "says there is no recipe");
        smelter.getTemplate().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        helper.assertTrue(smelter.getTemplate().getStackInSlot(0).getCount() == 64, "the input slot holds whole stacks");
        helper.assertTrue(smelter.getKind() == BetterAutoSmelterBlockEntity.Kind.AUTO_SMELTING && smelter.getAmount() == 3,
                "steel, still taking 3, got " + smelter.getAmount());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void smelterIsFilledFromRecipeViewers(GameTestHelper helper) {
        BetterAutoSmelterBlockEntity smelter = placeSmelter(helper, ItemStack.EMPTY);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 5));
        player.getInventory().add(new ItemStack(Items.RAW_IRON, 64));
        BetterAutoSmelterMenu menu = new BetterAutoSmelterMenu(0, player.getInventory(), smelter);

        // A smelter recipe's "+": as many as it takes.
        menu.fillTemplate(player, List.of(List.of(new ItemStack(Items.IRON_INGOT, 3))), false, null);
        helper.assertTrue(smelter.getTemplate().getStackInSlot(0).is(Items.IRON_INGOT)
                && smelter.getTemplate().getStackInSlot(0).getCount() == 3, "3 iron placed");
        helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 2, "2 iron left");
        helper.assertTrue(smelter.getKind() == BetterAutoSmelterBlockEntity.Kind.AUTO_SMELTING, "makes steel");

        // A blasting recipe's "+": the iron goes back, the raw iron is blasted rather than smelted.
        menu.fillTemplate(player, List.of(List.of(new ItemStack(Items.RAW_IRON))), false,
                ResourceLocation.withDefaultNamespace("iron_ingot_from_blasting_raw_iron"));
        helper.assertTrue(smelter.getTemplate().getStackInSlot(0).is(Items.RAW_IRON), "raw iron placed");
        helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 5, "the iron is given back");
        helper.assertTrue(smelter.getKind() == BetterAutoSmelterBlockEntity.Kind.BLASTING, "the blasting recipe, got " + smelter.getKind());

        // Shift: a whole stack's worth.
        menu.fillTemplate(player, List.of(List.of(new ItemStack(Items.RAW_IRON))), true, null);
        helper.assertTrue(smelter.getTemplate().getStackInSlot(0).getCount() == 64, "a whole stack placed, got "
                + smelter.getTemplate().getStackInSlot(0).getCount());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void smelterCanUseAnotherKindOfRecipe(GameTestHelper helper) {
        BetterAutoSmelterBlockEntity smelter = placeSmelter(helper, new ItemStack(Items.BEEF));
        helper.assertTrue(smelter.getKind() == BetterAutoSmelterBlockEntity.Kind.CAMPFIRE, "a campfire first, as it needs no fuel");
        RecipeChoices choices = smelter.getRecipeChoices();
        List<Integer> groups = choices.choices().stream().map(RecipeChoices.Choice::group).toList();
        helper.assertTrue(groups.equals(List.of(BetterAutoSmelterBlockEntity.Kind.toId(BetterAutoSmelterBlockEntity.Kind.CAMPFIRE),
                        BetterAutoSmelterBlockEntity.Kind.toId(BetterAutoSmelterBlockEntity.Kind.SMOKING),
                        BetterAutoSmelterBlockEntity.Kind.toId(BetterAutoSmelterBlockEntity.Kind.SMELTING))),
                "campfire, smoker and furnace can all cook beef, got " + groups);
        helper.assertTrue(smelter.chooseRecipe(1), "the smoker's recipe chosen");
        helper.assertTrue(smelter.getKind() == BetterAutoSmelterBlockEntity.Kind.SMOKING && smelter.getFuelCost() == 100,
                "smokes it with half a furnace's fuel, got " + smelter.getKind() + " " + smelter.getFuelCost());
        helper.assertTrue(smelter.getResult().is(Items.COOKED_BEEF), "the same steak");

        // Kept while it matches, saved with the machine.
        CompoundTag saved = smelter.saveWithoutMetadata(helper.getLevel().registryAccess());
        smelter.getTemplate().setStackInSlot(0, new ItemStack(Items.SAND));
        helper.assertTrue(smelter.getKind() == BetterAutoSmelterBlockEntity.Kind.SMELTING, "sand only has a furnace recipe");
        smelter.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(smelter.getKind() == BetterAutoSmelterBlockEntity.Kind.SMOKING, "beef is smoked again after loading");
        helper.assertTrue(!smelter.chooseRecipe(3), "no fourth recipe");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void steelIsOnlyThisModsWithoutAnotherSteelMod(GameTestHelper helper) {
        // Whether a steel mod (e.g. Mekanism) is installed or not, the steel made and listed agree with it.
        boolean otherSteel = Materials.STEEL.isProvidedByOtherMod(helper.getLevel().registryAccess());
        helper.assertTrue(Materials.STEEL.mainItem(helper.getLevel().registryAccess(), 2).is(ModItems.STEEL_INGOT.get()) != otherSteel,
                "this mod's steel is made only without another steel mod");
        helper.assertTrue(Materials.isHidden(ModItems.STEEL_BLOCK.get(), helper.getLevel().registryAccess()) == otherSteel,
                "and only listed then");

        // With another mod's item in the tag (here a vanilla one), its own steel stands aside.
        Holder<Item> own = BuiltInRegistries.ITEM.wrapAsHolder(ModItems.STEEL_INGOT.get());
        Holder<Item> other = BuiltInRegistries.ITEM.wrapAsHolder(Items.IRON_INGOT);
        helper.assertTrue(SharedMaterial.otherModItem(List.of(own)).isEmpty(), "only this mod's");
        helper.assertTrue(SharedMaterial.otherModItem(List.of(own, other)).orElseThrow() == other, "the other mod's is found");
        NoOtherModInTagCondition condition = new NoOtherModInTagCondition(Materials.STEEL.mainTag());
        helper.assertTrue(condition.test(contextWithSteel(List.of(own))), "own steel recipes load without another steel");
        helper.assertTrue(!condition.test(contextWithSteel(List.of(own, other))), "and don't with one");
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
    public static void brewerBrewsFewerBottlesLikeABrewingStand(GameTestHelper helper) {
        BetterAutoBrewerBlockEntity brewer = placeBrewer(helper, Items.NETHER_WART);
        Container input = placeChest(helper, TOP, new ItemStack(Items.NETHER_WART, 2));
        Container output = placeChest(helper, BOTTOM);
        Container starters = placeChest(helper, FRONT, waterBottles(2));
        Container fuel = placeChest(helper, LEFT, new ItemStack(Items.BLAZE_POWDER));

        helper.assertTrue(brewer.attemptOperation(), "brews with only 2 bottles");
        assertPotions(helper, output, Potions.AWKWARD, 2, "one potion per bottle");
        helper.assertTrue(starters.isEmpty(), "both bottles taken");
        assertCount(helper, input, Items.NETHER_WART, 1, "one wart taken, as for 3 bottles");
        assertCount(helper, fuel, Items.BLAZE_POWDER, 0, "fuel taken");
        helper.assertTrue(brewer.getFuel().getStored() == 19, "one fuel used, as for 3 bottles, got " + brewer.getFuel().getStored());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void brewerNeedsAStarter(GameTestHelper helper) {
        BetterAutoBrewerBlockEntity brewer = placeBrewer(helper, Items.NETHER_WART);
        Container input = placeChest(helper, TOP, new ItemStack(Items.NETHER_WART));
        placeChest(helper, BOTTOM);
        placeChest(helper, FRONT);
        Container fuel = placeChest(helper, LEFT, new ItemStack(Items.BLAZE_POWDER));

        helper.assertTrue(!brewer.attemptOperation(), "no bottles");
        helper.assertTrue(brewer.getStatus().getLastProblem() == MachineProblem.MISSING_STARTER, "says starters are missing");
        assertCount(helper, input, Items.NETHER_WART, 1, "wart stays");
        assertCount(helper, fuel, Items.BLAZE_POWDER, 1, "fuel stays");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void brewerWaitsForRoomForEveryPotion(GameTestHelper helper) {
        BetterAutoBrewerBlockEntity brewer = placeBrewer(helper, Items.NETHER_WART);
        Container input = placeChest(helper, TOP, new ItemStack(Items.NETHER_WART));
        Container output = placeChest(helper, BOTTOM);
        Container starters = placeChest(helper, FRONT, waterBottles(3));
        Container fuel = placeChest(helper, LEFT, new ItemStack(Items.BLAZE_POWDER));
        // Room for one potion only: potions don't stack, so the 3 of a brew need 3 slots.
        for (int i = 1; i < output.getContainerSize(); i++) {
            output.setItem(i, new ItemStack(Items.DIRT, 64));
        }

        helper.assertTrue(!brewer.attemptOperation(), "no room for the 3 potions");
        helper.assertTrue(brewer.getStatus().getLastProblem() == MachineProblem.OUTPUT_FULL, "says the output is full");
        assertCount(helper, starters, Items.POTION, 3, "bottles stay");
        assertCount(helper, input, Items.NETHER_WART, 1, "wart stays");
        assertCount(helper, fuel, Items.BLAZE_POWDER, 1, "fuel stays");
        helper.assertTrue(output.getItem(0).isEmpty(), "nothing pushed");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty(), "nothing dropped");

        output.setItem(1, ItemStack.EMPTY);
        output.setItem(2, ItemStack.EMPTY);
        helper.assertTrue(brewer.attemptOperation(), "brews once there is room");
        assertPotions(helper, output, Potions.AWKWARD, 3, "all 3 potions pushed");
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
    public static void brewerOnlyFillsBottlesWithInfiniteWaterAndNoIngredients(GameTestHelper helper) {
        // No ingredients, no input inventory and no fuel: just glass bottles in, water bottles out.
        BetterAutoBrewerBlockEntity brewer = placeBrewer(helper);
        Container output = placeChest(helper, BOTTOM);
        Container starters = placeChest(helper, FRONT, new ItemStack(Items.GLASS_BOTTLE, 5));
        helper.assertTrue(brewer.findProblem() == MachineProblem.EMPTY_TEMPLATE, "without the upgrade there is nothing to do");

        brewer.getMachineUpgrades().setStackInSlot(0, ModItems.INFINITE_WATER_UPGRADE.get().getDefaultInstance());
        helper.assertTrue(brewer.findProblem() == null, "with it, filling bottles is the job");
        helper.assertTrue(brewer.getFuelCost() == 0, "filling bottles takes no fuel");
        helper.assertTrue(brewer.attemptOperation(), "fills");
        assertPotions(helper, output, Potions.WATER, BetterAutoBrewerBlockEntity.BREW_SIZE, "a whole brew of water bottles");
        assertCount(helper, starters, Items.GLASS_BOTTLE, 5 - BetterAutoBrewerBlockEntity.BREW_SIZE, "as many glass bottles used");
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
    public static void brewerInfusesBetterMaterialWithNineSteel(GameTestHelper helper) {
        Item steel = ModItems.STEEL_INGOT.get();
        Item[] eight = new Item[8];
        Arrays.fill(eight, steel);
        BetterAutoBrewerBlockEntity brewer = placeBrewer(helper, eight);
        Container input = placeChest(helper, TOP, new ItemStack(steel, 64));
        Container output = placeChest(helper, BOTTOM);
        Container starters = placeChest(helper, FRONT, new ItemStack(ModItems.BETTER_MATERIAL.get(), 2));
        placeChest(helper, LEFT, new ItemStack(Items.BLAZE_POWDER));
        brewer.getMachineUpgrades().setStackInSlot(0, ModItems.POTION_STARTER_UPGRADE.get().getDefaultInstance());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BetterAutoBrewerMenu menu = new BetterAutoBrewerMenu(0, player.getInventory(), brewer);
        helper.assertTrue(menu.setUpgradeSetting(0, new ItemStack(ModItems.BETTER_MATERIAL.get())), "starts from Better Material");
        helper.assertTrue(brewer.findProblem() == MachineProblem.NO_RECIPE, "eight steel isn't the recipe");

        brewer.getTemplate().setStackInSlot(8, new ItemStack(steel));
        helper.assertTrue(brewer.findProblem() == null, "nine steel is");
        helper.assertTrue(brewer.attemptOperation(), "brews");
        assertCount(helper, output, ModItems.INFUSED_BETTER_MATERIAL.get(), 2, "one per starter");
        assertCount(helper, starters, ModItems.BETTER_MATERIAL.get(), 0, "both starters used");
        assertCount(helper, input, steel, 64 - 9, "nine steel used");
        helper.assertTrue(brewer.getFuel().getStored() == BetterAutoBrewerBlockEntity.FUEL_PER_ITEM - 9,
                "nine brews' fuel used, got " + brewer.getFuel().getStored());
        helper.succeed();
    }

    // The brewer's own recipes chain with each other and with brewing stand recipes. These recipes only exist here.

    @GameTest(template = TEMPLATE)
    public static void brewerChainsItsOwnRecipes(GameTestHelper helper) {
        List<AutoBrewingRecipe> own = List.of(
                ownRecipe(Items.COPPER_INGOT, new ItemStack(Items.IRON_INGOT), Items.GOLD_INGOT),
                ownRecipe(Items.IRON_INGOT, new ItemStack(Items.DIAMOND), Items.COAL));
        BrewingChain.Brew brew = BrewingChain.find(helper.getLevel(), own, new ItemStack(Items.COPPER_INGOT),
                List.of(new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.COAL)));
        helper.assertTrue(brew != null && brew.result().is(Items.DIAMOND), "copper + gold is iron, iron + coal is diamond, got " + brew);
        helper.assertTrue(brew.fuel() == 2, "each step's fuel, got " + brew.fuel());
        helper.assertTrue(BrewingChain.find(helper.getLevel(), own, new ItemStack(Items.COPPER_INGOT),
                List.of(new ItemStack(Items.COAL), new ItemStack(Items.GOLD_INGOT))) == null, "in order only");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void brewerChainsItsOwnRecipesWithBrewingStandRecipes(GameTestHelper helper) {
        List<AutoBrewingRecipe> own = List.of(
                ownRecipe(Items.COPPER_INGOT, PotionContents.createItemStack(Items.POTION, Potions.WATER), Items.GOLD_INGOT));
        BrewingChain.Brew brew = BrewingChain.find(helper.getLevel(), own, new ItemStack(Items.COPPER_INGOT),
                List.of(new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.NETHER_WART), new ItemStack(Items.SUGAR)));
        helper.assertTrue(brew != null && isPotion(brew.result(), Potions.SWIFTNESS),
                "copper + gold is water, then awkward, then swiftness, got " + brew);
        helper.assertTrue(brew.fuel() == 3, "one per step, got " + brew.fuel());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void brewerFindsEveryDifferentBrew(GameTestHelper helper) {
        List<AutoBrewingRecipe> own = List.of(
                ownRecipe(Items.COPPER_INGOT, new ItemStack(Items.IRON_INGOT), Items.GOLD_INGOT),
                ownRecipe(Items.COPPER_INGOT, new ItemStack(Items.EMERALD), Items.GOLD_INGOT),
                ownRecipe(Items.COPPER_INGOT, new ItemStack(Items.IRON_INGOT), Items.GOLD_INGOT));
        List<BrewingChain.Brew> brews = BrewingChain.findAll(helper.getLevel(), own, new ItemStack(Items.COPPER_INGOT),
                List.of(new ItemStack(Items.GOLD_INGOT)));
        helper.assertTrue(brews.size() == 2 && brews.get(0).result().is(Items.IRON_INGOT) && brews.get(1).result().is(Items.EMERALD),
                "iron first, then emerald, once each, got " + brews);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void brewerChainTriesTheNextRecipeAtADeadEnd(GameTestHelper helper) {
        // Taking gold and coal at once leads nowhere with redstone left; gold alone, then coal and redstone, does.
        List<AutoBrewingRecipe> own = List.of(
                ownRecipe(Items.COPPER_INGOT, new ItemStack(Items.EMERALD), Items.GOLD_INGOT, Items.COAL),
                ownRecipe(Items.COPPER_INGOT, new ItemStack(Items.IRON_INGOT), Items.GOLD_INGOT),
                ownRecipe(Items.IRON_INGOT, new ItemStack(Items.DIAMOND, 2), Items.COAL, Items.REDSTONE));
        BrewingChain.Brew brew = BrewingChain.find(helper.getLevel(), own, new ItemStack(Items.COPPER_INGOT),
                List.of(new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.COAL), new ItemStack(Items.REDSTONE)));
        helper.assertTrue(brew != null && brew.result().is(Items.DIAMOND) && brew.result().getCount() == 2,
                "copper + gold is iron, iron + coal, redstone is 2 diamonds, got " + brew);
        helper.assertTrue(brew.fuel() == 3, "1 + 2 fuel, got " + brew.fuel());
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

    /** What a crafting grid holding only {@code stack} makes; EMPTY if nothing. */
    private static ItemStack craftAlone(GameTestHelper helper, ItemStack stack) {
        CraftingInput input = CraftingInput.of(1, 1, List.of(stack));
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(holder -> holder.value().assemble(input, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    /** One of the brewer's own recipes, with the default fuel (one per ingredient). */
    private static AutoBrewingRecipe ownRecipe(Item starter, ItemStack result, Item... ingredients) {
        return new AutoBrewingRecipe("", Ingredient.of(starter), Arrays.stream(ingredients).map(Ingredient::of).toList(), result,
                Optional.empty());
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

    private static void assertPotions(GameTestHelper helper, Container container, Holder<Potion> potion, int expected, String message) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            count += isPotion(container.getItem(i), potion) ? container.getItem(i).getCount() : 0;
        }
        helper.assertTrue(count == expected, message + ": expected " + expected + " but found " + count);
    }

    /** Tags as a data condition sees them while loading, with {@code steel} as the common steel ingot tag. */
    private static ICondition.IContext contextWithSteel(Collection<Holder<Item>> steel) {
        return new ICondition.IContext() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Map<ResourceLocation, Collection<Holder<T>>> getAllTags(ResourceKey<? extends Registry<T>> registry) {
                return Map.of(Materials.STEEL.mainTag().location(), (Collection<Holder<T>>) (Collection<?>) steel);
            }
        };
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
