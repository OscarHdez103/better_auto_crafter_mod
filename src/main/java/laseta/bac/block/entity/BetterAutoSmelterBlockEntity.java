package laseta.bac.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import laseta.bac.CommonConfig;
import laseta.bac.item.FuelFilterUpgradeItem;
import laseta.bac.item.ItemSettingUpgradeItem;
import laseta.bac.machine.AutoMachineBlockEntity;
import laseta.bac.machine.FaceChannel;
import laseta.bac.machine.FuelTank;
import laseta.bac.machine.ItemTransfer;
import laseta.bac.machine.MachineFeatures;
import laseta.bac.machine.MachineProblem;
import laseta.bac.machine.MachineUpgradeSlotType;
import laseta.bac.machine.RecipeChoices;
import laseta.bac.menu.BetterAutoSmelterMenu;
import laseta.bac.recipe.AutoSmeltingRecipe;
import laseta.bac.registry.ModBlockEntities;
import laseta.bac.registry.ModItems;
import laseta.bac.registry.ModRecipeTypes;
import laseta.bac.upgrade.TranslocationMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Better Auto Smelter: its single input slot says what to cook. Each operation pulls it, cooks it and pushes the
 * result, with any recipe a furnace, blast furnace, smoker or campfire has, or one of its own
 * ({@link AutoSmeltingRecipe}, which can take several items at once). Every recipe takes the machine's base speed.
 *
 * <p>Recipes are tried in this order: its own (once the input slot holds as many items as they take); campfire
 * recipes, which need no fuel; blasting and smoking recipes, which use half a furnace's fuel; and furnace recipes. The
 * player can pick any other recipe that matches instead, of another kind or the same ({@link #chooseRecipe}). Fuel is counted in furnace burn ticks, and a recipe uses its
 * cooking time's worth, as a furnace burns while cooking (so 200 for most furnace recipes, 100 for blasting and
 * smoking ones). Fuel comes from the fuel faces, only when the stored fuel isn't enough; what is left of a fuel (the
 * bucket of a lava bucket) goes to the leftover faces. A Fuel Filter Upgrade limits it to one fuel.</p>
 */
public class BetterAutoSmelterBlockEntity extends AutoMachineBlockEntity {
    public static final FaceChannel FUEL = new FaceChannel("fuel", TranslocationMode.PULL, 0xEF4444,
            () -> new ItemStack(Items.COAL));
    public static final FaceChannel FUEL_LEFTOVERS = new FaceChannel("fuel_leftovers", TranslocationMode.PUSH, 0xA855F7,
            () -> new ItemStack(Items.BUCKET));
    public static final MachineUpgradeSlotType FUEL_FILTER = new MachineUpgradeSlotType("fuel_filter",
            ModItems.FUEL_FILTER_UPGRADE, 1, true, stack -> stack.getItem() instanceof FuelFilterUpgradeItem);
    public static final MachineFeatures FEATURES = new MachineFeatures(List.of(), List.of(FUEL, FUEL_LEFTOVERS), List.of(FUEL_FILTER));

    /** What kind of recipe the input slot's item is cooked with, in the order they are tried. */
    public enum Kind {
        AUTO_SMELTING,
        CAMPFIRE,
        BLASTING,
        SMOKING,
        SMELTING;

        private static final Kind[] VALUES = values();

        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The block that cooks this kind of recipe, shown in the GUI. */
        public ItemStack getWorkstation() {
            return new ItemStack(switch (this) {
                case AUTO_SMELTING -> ModItems.BETTER_AUTO_SMELTER.get();
                case CAMPFIRE -> Items.CAMPFIRE;
                case BLASTING -> Items.BLAST_FURNACE;
                case SMOKING -> Items.SMOKER;
                case SMELTING -> Items.FURNACE;
            });
        }

        /** Menu data value: 0 for none, otherwise the ordinal + 1. */
        public static int toId(@Nullable Kind kind) {
            return kind == null ? 0 : kind.ordinal() + 1;
        }

        @Nullable
        public static Kind byId(int id) {
            return id > 0 && id <= VALUES.length ? VALUES[id - 1] : null;
        }
    }

    /**
     * A recipe for the input slot's items.
     *
     * @param amount items each operation takes
     * @param fuel   fuel each operation uses, in furnace burn ticks
     */
    private record Cooking(Kind kind, ResourceLocation id, Recipe<SingleRecipeInput> recipe, int amount, int fuel,
                           ItemStack result) {
    }

    private final FuelTank fuel = new FuelTank(this::setChanged);
    /** Every recipe for the input slot's items, in the order they are tried. */
    private List<Cooking> cachedCookings = List.of();
    /** Index in {@link #cachedCookings} of the one used, -1 for none. */
    private int cachedChosen = -1;
    private boolean cookingDirty = true;
    /** The recipe the player picked, used whenever it matches the input slot's items. */
    @Nullable
    private ResourceLocation chosenRecipe;

    public BetterAutoSmelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BETTER_AUTO_SMELTER.get(), pos, state, 1, 1, FEATURES);
    }

    @Override
    protected int getBaseSpeed() {
        return CommonConfig.smelterBaseSpeed();
    }

    @Override
    protected void onTemplateChanged() {
        cookingDirty = true;
    }

    public FuelTank getFuel() {
        return fuel;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Recipe
    // ---------------------------------------------------------------------------------------------------------------

    private void updateCooking() {
        if (!cookingDirty || level == null || level.isClientSide) {
            return;
        }
        cookingDirty = false;
        cachedCookings = findCookings(getTemplate().getStackInSlot(0));
        cachedChosen = cachedCookings.isEmpty() ? -1 : 0;
        for (int i = 0; i < cachedCookings.size(); i++) {
            if (cachedCookings.get(i).id().equals(chosenRecipe)) {
                cachedChosen = i;
            }
        }
    }

    /** The recipe the smelter cooks with: the player's choice if it matches, otherwise the first one tried. */
    @Nullable
    private Cooking cooking() {
        updateCooking();
        return cachedChosen < 0 ? null : cachedCookings.get(cachedChosen);
    }

    private List<Cooking> findCookings(ItemStack stack) {
        List<Cooking> found = new ArrayList<>();
        if (stack.isEmpty() || level == null) {
            return found;
        }
        // Its own recipes see the whole stack, as they need several items; the others cook one at a time.
        SingleRecipeInput all = new SingleRecipeInput(stack);
        for (RecipeHolder<AutoSmeltingRecipe> holder : matchingRecipes(ModRecipeTypes.AUTO_SMELTING.get(), all)) {
            AutoSmeltingRecipe own = holder.value();
            addCooking(found, Kind.AUTO_SMELTING, holder.id(), own, stack, own.getIngredient().count(), own.getFuel());
        }
        SingleRecipeInput one = new SingleRecipeInput(stack.copyWithCount(1));
        addVanilla(found, Kind.CAMPFIRE, RecipeType.CAMPFIRE_COOKING, one);
        addVanilla(found, Kind.BLASTING, RecipeType.BLASTING, one);
        addVanilla(found, Kind.SMOKING, RecipeType.SMOKING, one);
        addVanilla(found, Kind.SMELTING, RecipeType.SMELTING, one);
        return found;
    }

    private <R extends AbstractCookingRecipe> void addVanilla(List<Cooking> found, Kind kind, RecipeType<R> type,
                                                              SingleRecipeInput input) {
        for (RecipeHolder<R> holder : matchingRecipes(type, input)) {
            // A furnace burns fuel all the while it cooks; a campfire needs none.
            int fuelCost = kind == Kind.CAMPFIRE ? 0 : Math.max(0, holder.value().getCookingTime());
            addCooking(found, kind, holder.id(), holder.value(), input.item(), 1, fuelCost);
        }
    }

    private void addCooking(List<Cooking> found, Kind kind, ResourceLocation id, Recipe<SingleRecipeInput> recipe,
                            ItemStack stack, int amount, int fuelCost) {
        int taken = Math.max(1, amount);
        ItemStack result = recipe.assemble(new SingleRecipeInput(stack.copyWithCount(taken)), level.registryAccess());
        if (!result.isEmpty()) {
            found.add(new Cooking(kind, id, recipe, taken, fuelCost, result));
        }
    }

    @Override
    public RecipeChoices getRecipeChoices() {
        updateCooking();
        return new RecipeChoices(cachedCookings.stream()
                .map(cooking -> new RecipeChoices.Choice(cooking.result(), Kind.toId(cooking.kind())))
                .toList(), cachedChosen);
    }

    @Override
    public boolean chooseRecipe(ResourceLocation id) {
        updateCooking();
        for (int i = 0; i < cachedCookings.size(); i++) {
            if (cachedCookings.get(i).id().equals(id)) {
                return chooseRecipe(i);
            }
        }
        return false;
    }

    @Override
    public boolean chooseRecipe(int index) {
        updateCooking();
        if (index < 0 || index >= cachedCookings.size()) {
            return false;
        }
        chosenRecipe = cachedCookings.get(index).id();
        onRecipeChosen();
        return true;
    }

    /** Server only: what the input slot's item cooks into; EMPTY if nothing. */
    public ItemStack getResult() {
        Cooking cooking = cooking();
        return cooking == null ? ItemStack.EMPTY : cooking.result();
    }

    /** Server only: items each operation takes; 0 without a recipe. */
    public int getAmount() {
        Cooking cooking = cooking();
        return cooking == null ? 0 : cooking.amount();
    }

    /** Server only: fuel each operation uses; 0 without a recipe. */
    public int getFuelCost() {
        Cooking cooking = cooking();
        return cooking == null ? 0 : cooking.fuel();
    }

    /** Server only: the kind of recipe the input is cooked with, or null. */
    @Nullable
    public Kind getKind() {
        Cooking cooking = cooking();
        return cooking == null ? null : cooking.kind();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Fuel
    // ---------------------------------------------------------------------------------------------------------------

    /** The fuel the installed Fuel Filter Upgrade allows, EMPTY without one (any fuel) or while it isn't set. */
    private ItemStack fuelFilter() {
        return ItemSettingUpgradeItem.getSetting(getMachineUpgrades().get(FUEL_FILTER));
    }

    private boolean hasUnsetFilter() {
        ItemStack upgrade = getMachineUpgrades().get(FUEL_FILTER);
        return !upgrade.isEmpty() && ItemSettingUpgradeItem.getSetting(upgrade).isEmpty();
    }

    /** Whether the smelter may burn {@code stack}: any fuel, or only the filter's. */
    public boolean isAllowedFuel(ItemStack stack) {
        ItemStack filter = fuelFilter();
        return (filter.isEmpty() || ItemStack.isSameItemSameComponents(filter, stack)) && fuelValue(stack) > 0;
    }

    /** The fuel {@code stack} gives, in furnace burn ticks; 0 if it isn't a fuel. */
    public static int fuelValue(ItemStack stack) {
        return stack.getBurnTime(RecipeType.SMELTING);
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
        if (cooking() == null) {
            return MachineProblem.NO_RECIPE;
        }
        return hasUnsetFilter() ? MachineProblem.UNSET_UPGRADE : null;
    }

    @Override
    protected boolean operate(ServerLevel level, ItemTransfer transfer) {
        Cooking cooking = cooking();
        if (cooking == null) {
            return transfer.fail(MachineProblem.NO_RECIPE);
        }
        int missing = fuel.missingFor(cooking.fuel());
        if (missing > 0) {
            List<IItemHandler> fuelInventories = getChannelInventories(level, FUEL);
            List<IItemHandler> leftovers = getChannelInventories(level, FUEL_LEFTOVERS);
            transfer.pull(new ItemTransfer.Pull(fuelInventories, this::isAllowedFuel, BetterAutoSmelterBlockEntity::fuelValue,
                    missing, MachineProblem.NO_FUEL, leftovers, fuel::add));
        }
        ItemStack wanted = getTemplate().getStackInSlot(0).copyWithCount(cooking.amount());
        return transfer.run(List.of(wanted), pulled -> cook(level, cooking, pulled));
    }

    /**
     * Cooks the items that would actually be pulled, which only differ from the input slot's when NBT matching is off:
     * the recipe must still accept them, and the result is made from them.
     */
    @Nullable
    private ItemTransfer.Output cook(ServerLevel level, Cooking cooking, List<List<ItemStack>> pulled) {
        if (pulled.getFirst().isEmpty()) {
            return null;
        }
        SingleRecipeInput input = new SingleRecipeInput(pulled.getFirst().getFirst().copyWithCount(cooking.amount()));
        if (!cooking.recipe().matches(input, level)) {
            return null;
        }
        ItemStack result = cooking.recipe().assemble(input, level.registryAccess());
        if (result.isEmpty()) {
            return null;
        }
        return new ItemTransfer.Output(List.of(result), List.of(), () -> fuel.consume(cooking.fuel()));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.save());
        if (chosenRecipe != null) {
            tag.putString("chosen_recipe", chosenRecipe.toString());
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        fuel.load(tag.getCompound("fuel"));
        chosenRecipe = tag.contains("chosen_recipe") ? ResourceLocation.tryParse(tag.getString("chosen_recipe")) : null;
        cookingDirty = true;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        return new BetterAutoSmelterMenu(containerId, playerInventory, this);
    }
}
