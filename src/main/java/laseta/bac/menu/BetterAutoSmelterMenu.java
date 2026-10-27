package laseta.bac.menu;

import laseta.bac.block.entity.BetterAutoSmelterBlockEntity;
import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.machine.FuelTank;
import laseta.bac.machine.MachineLayout;
import laseta.bac.menu.slot.ResultPreviewSlot;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import org.jetbrains.annotations.Nullable;

/**
 * The smelter's menu: its input slot and a display-only result preview, laid out like a furnace's, plus the stored
 * fuel and the recipe's details as data slots. Slot indices: input 0, result 1, player inventory 2-37, upgrades after
 * that.
 */
public class BetterAutoSmelterMenu extends AutoMachineMenu {
    /** A furnace's layout, with the input slot level with the result (the fuel goes into the gauge on the left). */
    public static final MachineLayout LAYOUT = new MachineLayout(176, 166, 1, 1, 56, 35, 8, 84);
    public static final int RESULT_X = 116;
    public static final int RESULT_Y = 35;

    // Recipe data slot indices: the fuel cost is split in two, as data slots are synced as 16-bit values.
    private static final int DATA_AMOUNT = 0;
    private static final int DATA_KIND = 1;
    private static final int DATA_FUEL_COST = 2;
    private static final int DATA_COUNT = 4;

    private final SimpleContainer resultContainer = new SimpleContainer(1);
    private final ContainerData fuel;
    private final ContainerData recipe;

    /** Client-side constructor (called by the MenuType with the data written in {@code openMenu}). */
    public BetterAutoSmelterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, Source.client(extraData, BetterAutoSmelterBlockEntity.FEATURES));
    }

    /** Server-side constructor (called by the block entity). */
    public BetterAutoSmelterMenu(int containerId, Inventory playerInventory, BetterAutoSmelterBlockEntity blockEntity) {
        this(containerId, playerInventory, Source.of(blockEntity));
    }

    private BetterAutoSmelterMenu(int containerId, Inventory playerInventory, Source source) {
        super(ModMenuTypes.BETTER_AUTO_SMELTER.get(), containerId, ModBlocks.BETTER_AUTO_SMELTER.get(), LAYOUT, source);
        addTemplateSlots(source);
        addSlot(new ResultPreviewSlot(resultContainer, 0, RESULT_X, RESULT_Y));
        addPlayerInventory(playerInventory);
        addUpgradeSlots(source);
        if (getBlockEntity() instanceof BetterAutoSmelterBlockEntity smelter) {
            fuel = smelter.getFuel().asContainerData();
            recipe = recipeOf(smelter);
        } else {
            fuel = new SimpleContainerData(FuelTank.DATA_COUNT);
            recipe = new SimpleContainerData(DATA_COUNT);
        }
        addDataSlots(fuel);
        addDataSlots(recipe);
    }

    private static ContainerData recipeOf(BetterAutoSmelterBlockEntity smelter) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_AMOUNT -> smelter.getAmount();
                    case DATA_KIND -> BetterAutoSmelterBlockEntity.Kind.toId(smelter.getKind());
                    case DATA_FUEL_COST -> smelter.getFuelCost() & 0xFFFF;
                    case DATA_FUEL_COST + 1 -> smelter.getFuelCost() >>> 16;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Read-only: derived from the input slot's recipe.
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    public int getStoredFuel() {
        return FuelTank.stored(fuel);
    }

    public int getFuelCapacity() {
        return FuelTank.capacity(fuel);
    }

    /** Items each operation takes (more than 1 only for the smelter's own recipes); 0 without a recipe. */
    public int getAmount() {
        return recipe.get(DATA_AMOUNT);
    }

    /** The kind of recipe the input is cooked with, or null without one. */
    @Nullable
    public BetterAutoSmelterBlockEntity.Kind getKind() {
        return BetterAutoSmelterBlockEntity.Kind.byId(recipe.get(DATA_KIND));
    }

    /** Fuel each operation uses, in furnace burn ticks. */
    public int getFuelCost() {
        return (recipe.get(DATA_FUEL_COST + 1) & 0xFFFF) << 16 | recipe.get(DATA_FUEL_COST) & 0xFFFF;
    }

    @Override
    public void broadcastChanges() {
        // Server only: refresh the preview before syncing slots to the client.
        if (getBlockEntity() instanceof BetterAutoSmelterBlockEntity smelter) {
            resultContainer.setItem(0, smelter.getResult().copy());
        }
        super.broadcastChanges();
    }
}
