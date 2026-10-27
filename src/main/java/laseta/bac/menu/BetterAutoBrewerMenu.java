package laseta.bac.menu;

import laseta.bac.block.entity.BetterAutoBrewerBlockEntity;
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
import net.minecraft.world.item.ItemStack;

/**
 * The brewer's menu: its row of ingredient slots on top and, below it, display-only previews of what each brew starts
 * from (as pulled from the starter faces) and the potions it makes, plus the stored fuel and the brew's fuel cost as
 * data slots. Slot indices: ingredients 0-8, starter 9, result 10, player inventory 11-46, upgrades after that.
 */
public class BetterAutoBrewerMenu extends AutoMachineMenu {
    public static final MachineLayout LAYOUT = new MachineLayout(176, 166, BetterAutoBrewerBlockEntity.GRID_SIZE, 1, 8, 17, 8, 84);
    public static final int STARTER_X = 56;
    public static final int RESULT_X = 116;
    public static final int PREVIEW_Y = 48;

    private final SimpleContainer previews = new SimpleContainer(2);
    private final ContainerData fuel;
    /** Fuel each operation uses (at most the 9 ingredients' worth for brewing stand recipes). */
    private final ContainerData fuelCost;

    /** Client-side constructor (called by the MenuType with the data written in {@code openMenu}). */
    public BetterAutoBrewerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, Source.client(extraData, BetterAutoBrewerBlockEntity.FEATURES));
    }

    /** Server-side constructor (called by the block entity). */
    public BetterAutoBrewerMenu(int containerId, Inventory playerInventory, BetterAutoBrewerBlockEntity blockEntity) {
        this(containerId, playerInventory, Source.of(blockEntity));
    }

    private BetterAutoBrewerMenu(int containerId, Inventory playerInventory, Source source) {
        super(ModMenuTypes.BETTER_AUTO_BREWER.get(), containerId, ModBlocks.BETTER_AUTO_BREWER.get(), LAYOUT, source);
        addTemplateSlots(source);
        addSlot(new ResultPreviewSlot(previews, 0, STARTER_X, PREVIEW_Y));
        addSlot(new ResultPreviewSlot(previews, 1, RESULT_X, PREVIEW_Y));
        addPlayerInventory(playerInventory);
        addUpgradeSlots(source);
        if (getBlockEntity() instanceof BetterAutoBrewerBlockEntity brewer) {
            fuel = brewer.getFuel().asContainerData();
            fuelCost = new ContainerData() {
                @Override
                public int get(int index) {
                    return Math.min(Short.MAX_VALUE, brewer.getFuelCost());
                }

                @Override
                public void set(int index, int value) {
                    // Read-only: derived from the brew.
                }

                @Override
                public int getCount() {
                    return 1;
                }
            };
        } else {
            fuel = new SimpleContainerData(FuelTank.DATA_COUNT);
            fuelCost = new SimpleContainerData(1);
        }
        addDataSlots(fuel);
        addDataSlots(fuelCost);
    }

    public int getStoredFuel() {
        return FuelTank.stored(fuel);
    }

    public int getFuelCapacity() {
        return FuelTank.capacity(fuel);
    }

    /** Fuel each operation uses; 0 without a brew. */
    public int getFuelCost() {
        return fuelCost.get(0);
    }

    @Override
    public void broadcastChanges() {
        // Server only: refresh the previews before syncing slots to the client. They show a whole brew's worth.
        if (getBlockEntity() instanceof BetterAutoBrewerBlockEntity brewer) {
            ItemStack result = brewer.getResult();
            previews.setItem(0, brewer.getPulledStarter().copyWithCount(BetterAutoBrewerBlockEntity.BREW_SIZE));
            previews.setItem(1, result.copyWithCount(result.getCount() * BetterAutoBrewerBlockEntity.BREW_SIZE));
        }
        super.broadcastChanges();
    }
}
