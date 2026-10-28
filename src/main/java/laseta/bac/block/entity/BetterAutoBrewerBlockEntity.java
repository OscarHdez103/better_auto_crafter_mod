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
import laseta.bac.machine.RecipeChoices;
import laseta.bac.menu.BetterAutoBrewerMenu;
import laseta.bac.recipe.AutoBrewingRecipe;
import laseta.bac.recipe.AutoCraftingRecipe;
import laseta.bac.recipe.BrewingChain;
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
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Better Auto Brewer: its row of ingredient slots is a whole brew, in order, left to right (empty slots are
 * skipped). Each operation does what a brewing stand does with them, one ingredient after the other, all at once:
 * it pulls up to {@value #BREW_SIZE} starters from the starter faces (water bottles; one is enough), one of each
 * ingredient from the input faces and pushes a potion per starter (and any ingredient's leftovers). Like in a brewing
 * stand, a brew uses the same ingredients and fuel however many bottles it has. Any brewing stand recipe works (vanilla
 * and modded), and so do the brewer's own ({@link AutoBrewingRecipe}), which are tried first; they chain in any order,
 * see {@link BrewingChain}.
 *
 * <p>Like a brewing stand, every ingredient uses one fuel and a fuel item gives {@value #FUEL_PER_ITEM}. Fuel is any
 * item tagged {@code better_auto_crafter:brewer_fuels} (blaze powder), taken from the fuel faces only when the stored
 * fuel isn't enough. The starter upgrade slot changes what brews start from: an Infinite Water Upgrade makes the brewer
 * fill empty glass bottles itself (with no ingredients set, it only fills them, using no fuel), a Potion Starter Upgrade
 * starts them from the item it is set to.</p>
 *
 * <p>When the row brews into different potions (through different recipes), the first one found is brewed unless the
 * player picks another ({@link #chooseRecipe}).</p>
 */
public class BetterAutoBrewerBlockEntity extends AutoMachineBlockEntity {
    public static final int GRID_SIZE = AutoBrewingRecipe.MAX_INGREDIENTS;
    /** Most bottles per brew, as in a brewing stand. */
    public static final int BREW_SIZE = 3;
    public static final int FUEL_PER_ITEM = BrewingStandBlockEntity.FUEL_USES;
    public static final TagKey<Item> FUELS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "brewer_fuels"));

    /** The fuel {@code stack} gives, in brews; 0 if it isn't a fuel. */
    public static int fuelValue(ItemStack stack) {
        return stack.is(FUELS) ? FUEL_PER_ITEM : 0;
    }

    public static final FaceChannel STARTERS = new FaceChannel("starters", TranslocationMode.PULL, 0x22D3EE,
            () -> PotionContents.createItemStack(Items.POTION, Potions.WATER));
    public static final FaceChannel FUEL = new FaceChannel("fuel", TranslocationMode.PULL, 0xFACC15,
            () -> new ItemStack(Items.BLAZE_POWDER));
    /** Infinite Water and Potion Starter Upgrades exclude each other: one slot for both. */
    public static final MachineUpgradeSlotType STARTER_UPGRADE = new MachineUpgradeSlotType("starter",
            ModItems.INFINITE_WATER_UPGRADE, 1, true,
            stack -> stack.getItem() instanceof InfiniteWaterUpgradeItem || stack.getItem() instanceof PotionStarterUpgradeItem);
    public static final MachineFeatures FEATURES = new MachineFeatures(List.of(), List.of(STARTERS, FUEL), List.of(STARTER_UPGRADE));

    private final FuelTank fuel = new FuelTank(this::setChanged);
    /** Every brew the slots make, in the order they are found. */
    private List<BrewingChain.Brew> cachedBrews = List.of();
    private boolean brewDirty = true;
    /** The potion the player picked, brewed whenever the slots make it. */
    private ItemStack chosenResult = ItemStack.EMPTY;

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
        return fillsBottles() ? new ItemStack(Items.GLASS_BOTTLE) : getStarter();
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

    /** The brew the brewer makes: the player's choice if the slots make it, otherwise the first one found. */
    @Nullable
    private BrewingChain.Brew brew() {
        updateBrews();
        return chosen(cachedBrews);
    }

    private void updateBrews() {
        if (brewDirty && level != null && !level.isClientSide) {
            brewDirty = false;
            cachedBrews = findBrews(getStarter(), ingredients());
        }
    }

    @Nullable
    private BrewingChain.Brew chosen(List<BrewingChain.Brew> brews) {
        int index = chosenIndex(brews);
        return index < 0 ? null : brews.get(index);
    }

    private int chosenIndex(List<BrewingChain.Brew> brews) {
        for (int i = 0; i < brews.size(); i++) {
            if (ItemStack.matches(brews.get(i).result(), chosenResult)) {
                return i;
            }
        }
        return brews.isEmpty() ? -1 : 0;
    }

    @Override
    public RecipeChoices getRecipeChoices() {
        updateBrews();
        return new RecipeChoices(cachedBrews.stream().map(brew -> new RecipeChoices.Choice(brew.result(), 0)).toList(),
                chosenIndex(cachedBrews));
    }

    @Override
    public boolean chooseRecipe(int index) {
        updateBrews();
        if (index < 0 || index >= cachedBrews.size()) {
            return false;
        }
        chosenResult = cachedBrews.get(index).result().copy();
        onRecipeChosen();
        return true;
    }

    /** True with an Infinite Water Upgrade: the starters are filled from empty bottles, which is a job on its own. */
    private boolean fillsBottles() {
        return starterUpgrade().getItem() instanceof InfiniteWaterUpgradeItem;
    }

    private List<BrewingChain.Brew> findBrews(ItemStack starter, List<ItemStack> ingredients) {
        if (level == null) {
            return List.of();
        }
        if (ingredients.isEmpty()) {
            // Nothing to brew: just the water bottles, like filling them at a water source.
            return fillsBottles() && !starter.isEmpty() ? List.of(new BrewingChain.Brew(starter.copyWithCount(1), 0)) : List.of();
        }
        List<AutoBrewingRecipe> own = level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.AUTO_BREWING.get()).stream()
                .map(RecipeHolder::value)
                .toList();
        return BrewingChain.findAll(level, own, starter, ingredients);
    }

    /** Server only: one of the potions a brew makes; EMPTY if the slots aren't a brew. */
    public ItemStack getResult() {
        BrewingChain.Brew brew = brew();
        return brew == null ? ItemStack.EMPTY : brew.result();
    }

    /** Server only: fuel each operation uses; 0 without a brew. */
    public int getFuelCost() {
        BrewingChain.Brew brew = brew();
        return brew == null ? 0 : brew.fuel();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Running
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable MachineProblem templateProblem() {
        MachineProblem empty = super.templateProblem();
        if (empty != null && !fillsBottles()) {
            return empty;
        }
        if (getStarter().isEmpty()) {
            return MachineProblem.UNSET_UPGRADE;
        }
        return brew() == null ? MachineProblem.NO_RECIPE : null;
    }

    @Override
    protected boolean operate(ServerLevel level, ItemTransfer transfer) {
        BrewingChain.Brew brew = brew();
        ItemStack starter = getStarter();
        if (brew == null || starter.isEmpty()) {
            return transfer.fail(MachineProblem.NO_RECIPE);
        }
        // As many starters as there are, up to a whole brew's: a brewing stand brews a single bottle too.
        ItemTransfer.Pull starters = ItemTransfer.Pull.upTo(getChannelInventories(level, STARTERS), getPulledStarter(),
                1, BREW_SIZE, MachineProblem.MISSING_STARTER);
        transfer.pull(starters);
        int missing = fuel.missingFor(brew.fuel());
        if (missing > 0) {
            List<IItemHandler> fuelInventories = getChannelInventories(level, FUEL);
            // A fuel that leaves something (none in vanilla) leaves it in the fuel inventories, as a brewing stand keeps it.
            transfer.pull(new ItemTransfer.Pull(fuelInventories, stack -> fuelValue(stack) > 0,
                    BetterAutoBrewerBlockEntity::fuelValue, missing,
                    MachineProblem.NO_FUEL, fuelInventories, fuel::add));
        }
        List<ItemStack> wanted = getTemplateStacks().stream()
                .map(stack -> stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1))
                .toList();
        return transfer.run(wanted, pulled -> brew(brew, starter, transfer.planned(starters), pulled));
    }

    /**
     * Brews with the ingredients that would actually be pulled, which only differ from the slots' when NBT matching is
     * off: they must still make a brew with the fuel taken for it, and the potions are made from them, one per starter.
     */
    @Nullable
    private ItemTransfer.Output brew(BrewingChain.Brew brew, ItemStack starter, int bottles, List<List<ItemStack>> pulled) {
        List<ItemStack> ingredients = new ArrayList<>();
        for (List<ItemStack> slot : pulled) {
            if (!slot.isEmpty()) {
                ingredients.add(slot.getFirst().copyWithCount(1));
            }
        }
        BrewingChain.Brew actual = chosen(findBrews(starter, ingredients));
        if (actual == null || actual.fuel() != brew.fuel() || bottles <= 0) {
            return null;
        }
        ItemStack result = actual.result();
        List<ItemStack> potions = IntStream.range(0, bottles).mapToObj(i -> result.copy()).toList();
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
        if (!chosenResult.isEmpty()) {
            tag.put("chosen_result", chosenResult.save(registries));
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        fuel.load(tag.getCompound("fuel"));
        chosenResult = ItemStack.parseOptional(registries, tag.getCompound("chosen_result"));
        brewDirty = true;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        return new BetterAutoBrewerMenu(containerId, playerInventory, this);
    }
}
