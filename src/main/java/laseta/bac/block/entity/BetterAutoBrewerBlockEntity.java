package laseta.bac.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.CommonConfig;
import laseta.bac.item.InfiniteWaterUpgradeItem;
import laseta.bac.item.ItemSettingUpgradeItem;
import laseta.bac.item.PotionStarterUpgradeItem;
import laseta.bac.machine.AutoMachineBlockEntity;
import laseta.bac.machine.FaceChannel;
import laseta.bac.machine.FuelTank;
import laseta.bac.machine.ItemTransfer;
import laseta.bac.machine.MachineFeatures;
import laseta.bac.machine.MachineProblem;
import laseta.bac.machine.MachineUpgradeSlotType;
import laseta.bac.menu.BetterAutoBrewerMenu;
import laseta.bac.recipe.AutoBrewingRecipe;
import laseta.bac.recipe.AutoCraftingRecipe;
import laseta.bac.recipe.BrewingInput;
import laseta.bac.registry.ModBlockEntities;
import laseta.bac.registry.ModItems;
import laseta.bac.registry.ModRecipeTypes;
import laseta.bac.upgrade.TranslocationMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Better Auto Brewer: its row of ingredient slots is a whole brew, in order, left to right (empty slots are
 * skipped). Each operation does what a brewing stand does with them, one ingredient after the other, all at once:
 * it pulls {@value #BREW_SIZE} starters from the starter faces (water bottles), one of each ingredient from the input
 * faces and pushes the {@value #BREW_SIZE} potions it makes (and any ingredient's leftovers). Any brewing stand recipe
 * works (vanilla and modded), and so do the brewer's own ({@link AutoBrewingRecipe}), which are tried first.
 *
 * <p>Like a brewing stand, every ingredient uses one fuel and a fuel item gives {@value #FUEL_PER_ITEM}. Fuel is any
 * item tagged {@code better_auto_crafter:brewer_fuels} (blaze powder), taken from the fuel faces only when the stored
 * fuel isn't enough. The starter upgrade slot changes what brews start from: an Infinite Water Upgrade makes the brewer
 * fill empty glass bottles itself, a Potion Starter Upgrade starts them from the item it is set to.</p>
 */
public class BetterAutoBrewerBlockEntity extends AutoMachineBlockEntity {
    public static final int GRID_SIZE = AutoBrewingRecipe.MAX_INGREDIENTS;
    /** Bottles per brew, as in a brewing stand. */
    public static final int BREW_SIZE = 3;
    public static final int FUEL_PER_ITEM = BrewingStandBlockEntity.FUEL_USES;
    public static final TagKey<Item> FUELS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "brewer_fuels"));

    public static final FaceChannel STARTERS = new FaceChannel("starters", TranslocationMode.PULL, 0x22D3EE,
            () -> PotionContents.createItemStack(Items.POTION, Potions.WATER));
    public static final FaceChannel FUEL = new FaceChannel("fuel", TranslocationMode.PULL, 0xFACC15,
            () -> new ItemStack(Items.BLAZE_POWDER));
    /** Infinite Water and Potion Starter Upgrades exclude each other: one slot for both. */
    public static final MachineUpgradeSlotType STARTER_UPGRADE = new MachineUpgradeSlotType("starter",
            ModItems.INFINITE_WATER_UPGRADE, 1, true,
            stack -> stack.getItem() instanceof InfiniteWaterUpgradeItem || stack.getItem() instanceof PotionStarterUpgradeItem);
    public static final MachineFeatures FEATURES = new MachineFeatures(List.of(), List.of(STARTERS, FUEL), List.of(STARTER_UPGRADE));

    /**
     * The brew the ingredient slots make.
     *
     * @param recipe the brewer's own recipe, or null for brewing stand recipes
     * @param fuel   fuel each operation uses
     */
    private record Brew(@Nullable AutoBrewingRecipe recipe, int fuel, ItemStack result) {
    }

    private final FuelTank fuel = new FuelTank(this::setChanged);
    @Nullable
    private Brew cachedBrew;
    private boolean brewDirty = true;

    public BetterAutoBrewerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BETTER_AUTO_BREWER.get(), pos, state, GRID_SIZE, 1, FEATURES);
    }

    @Override
    protected int getBaseSpeed() {
        return CommonConfig.brewerBaseSpeed();
    }

    /** Each slot is one ingredient of the brew: one item is all it means. */
    @Override
    protected int getTemplateSlotLimit() {
        return 1;
    }

    @Override
    protected void onTemplateChanged() {
        brewDirty = true;
    }

    public FuelTank getFuel() {
        return fuel;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Starters
    // ---------------------------------------------------------------------------------------------------------------

    private static ItemStack waterBottle() {
        return PotionContents.createItemStack(Items.POTION, Potions.WATER);
    }

    private ItemStack starterUpgrade() {
        return getMachineUpgrades().get(STARTER_UPGRADE);
    }

    /** What brews start from: water bottles, or the Potion Starter Upgrade's item (EMPTY while it isn't set). */
    public ItemStack getStarter() {
        ItemStack upgrade = starterUpgrade();
        return upgrade.getItem() instanceof PotionStarterUpgradeItem ? ItemSettingUpgradeItem.getSetting(upgrade) : waterBottle();
    }

    /** What the starter faces supply: the starter itself, or empty glass bottles with an Infinite Water Upgrade. */
    public ItemStack getPulledStarter() {
        return starterUpgrade().getItem() instanceof InfiniteWaterUpgradeItem ? new ItemStack(Items.GLASS_BOTTLE) : getStarter();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Recipe
    // ---------------------------------------------------------------------------------------------------------------

    /** The ingredients in brewing order: the slots' items, left to right, skipping empty ones. */
    private List<ItemStack> ingredients() {
        List<ItemStack> ingredients = new ArrayList<>();
        for (ItemStack stack : getTemplateStacks()) {
            if (!stack.isEmpty()) {
                ingredients.add(stack.copyWithCount(1));
            }
        }
        return ingredients;
    }

    @Nullable
    private Brew brew() {
        if (brewDirty && level != null && !level.isClientSide) {
            brewDirty = false;
            cachedBrew = findBrew(getStarter(), ingredients());
        }
        return cachedBrew;
    }

    @Nullable
    private Brew findBrew(ItemStack starter, List<ItemStack> ingredients) {
        if (starter.isEmpty() || ingredients.isEmpty() || level == null) {
            return null;
        }
        AutoBrewingRecipe own = level.getRecipeManager()
                .getRecipeFor(ModRecipeTypes.AUTO_BREWING.get(), new BrewingInput(starter, ingredients), level)
                .map(RecipeHolder::value).orElse(null);
        if (own != null) {
            ItemStack result = own.assemble(new BrewingInput(starter, ingredients), level.registryAccess());
            return result.isEmpty() ? null : new Brew(own, own.getFuel(), result);
        }
        ItemStack result = brewInStand(level, starter, ingredients);
        return result.isEmpty() ? null : new Brew(null, ingredients.size(), result);
    }

    /**
     * What a brewing stand makes of {@code starter} with each ingredient in turn, through the level's brewing recipes
     * (vanilla's and every mod's); EMPTY if an ingredient doesn't brew with what the previous ones made.
     */
    private static ItemStack brewInStand(Level level, ItemStack starter, List<ItemStack> ingredients) {
        PotionBrewing brewing = level.potionBrewing();
        ItemStack brewed = starter.copyWithCount(1);
        for (ItemStack ingredient : ingredients) {
            if (!brewing.hasMix(brewed, ingredient)) {
                return ItemStack.EMPTY;
            }
            brewed = brewing.mix(ingredient, brewed);
            if (brewed.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return brewed;
    }

    /** Server only: one of the potions a brew makes; EMPTY if the slots aren't a brew. */
    public ItemStack getResult() {
        Brew brew = brew();
        return brew == null ? ItemStack.EMPTY : brew.result();
    }

    /** Server only: fuel each operation uses; 0 without a brew. */
    public int getFuelCost() {
        Brew brew = brew();
        return brew == null ? 0 : brew.fuel();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Running
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable MachineProblem templateProblem() {
        MachineProblem empty = super.templateProblem();
        if (empty != null) {
            return empty;
        }
        if (getStarter().isEmpty()) {
            return MachineProblem.UNSET_UPGRADE;
        }
        return brew() == null ? MachineProblem.NO_RECIPE : null;
    }

    @Override
    protected boolean operate(ServerLevel level, ItemTransfer transfer) {
        Brew brew = brew();
        ItemStack starter = getStarter();
        if (brew == null || starter.isEmpty()) {
            return transfer.fail(MachineProblem.NO_RECIPE);
        }
        transfer.pull(ItemTransfer.Pull.exactly(getChannelInventories(level, STARTERS), getPulledStarter(), BREW_SIZE,
                MachineProblem.MISSING_STARTER));
        int missing = fuel.missingFor(brew.fuel());
        if (missing > 0) {
            List<IItemHandler> fuelInventories = getChannelInventories(level, FUEL);
            // A fuel that leaves something (none in vanilla) leaves it in the fuel inventories, as a brewing stand keeps it.
            transfer.pull(new ItemTransfer.Pull(fuelInventories, stack -> stack.is(FUELS), stack -> FUEL_PER_ITEM, missing,
                    MachineProblem.NO_FUEL, fuelInventories, fuel::add));
        }
        List<ItemStack> wanted = getTemplateStacks().stream()
                .map(stack -> stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1))
                .toList();
        return transfer.run(wanted, pulled -> brew(level, brew, starter, pulled));
    }

    /**
     * Brews with the ingredients that would actually be pulled, which only differ from the slots' when NBT matching is
     * off: they must still make a brew, and the potions are made from them.
     */
    @Nullable
    private ItemTransfer.Output brew(ServerLevel level, Brew brew, ItemStack starter, List<List<ItemStack>> pulled) {
        List<ItemStack> ingredients = new ArrayList<>();
        for (List<ItemStack> slot : pulled) {
            if (!slot.isEmpty()) {
                ingredients.add(slot.getFirst().copyWithCount(1));
            }
        }
        ItemStack result;
        if (brew.recipe() != null) {
            BrewingInput input = new BrewingInput(starter, ingredients);
            result = brew.recipe().matches(input, level) ? brew.recipe().assemble(input, level.registryAccess()) : ItemStack.EMPTY;
        } else {
            result = brewInStand(level, starter, ingredients);
        }
        if (result.isEmpty()) {
            return null;
        }
        List<ItemStack> potions = IntStream.range(0, BREW_SIZE).mapToObj(i -> result.copy()).toList();
        List<ItemStack> leftovers = ItemTransfer.merge(List.of(AutoCraftingRecipe.remainders(pulled)));
        return new ItemTransfer.Output(potions, leftovers, () -> fuel.consume(brew.fuel()));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------------------------

    /** The starter upgrade decides what brews start from: the brew must be worked out again when it changes. */
    @Override
    protected void onMachineUpgradesChanged() {
        brewDirty = true;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.save());
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        fuel.load(tag.getCompound("fuel"));
        brewDirty = true;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        return new BetterAutoBrewerMenu(containerId, playerInventory, this);
    }
}
