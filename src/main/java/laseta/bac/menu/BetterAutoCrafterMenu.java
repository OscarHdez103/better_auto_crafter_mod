package laseta.bac.menu;

import java.util.List;

import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.machine.MachineLayout;
import laseta.bac.menu.slot.ResultPreviewSlot;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

/**
 * The crafter's menu: the machine's slots plus a display-only result preview, and how many items each grid slot takes
 * per craft as data slots. Slot indices (recipe viewers rely on them): grid 0-8, result 9, player inventory 10-45,
 * upgrades after that.
 */
public class BetterAutoCrafterMenu extends AutoMachineMenu {
    /** The vanilla crafting table's layout, so its background texture fits. */
    public static final MachineLayout LAYOUT = new MachineLayout(176, 166, BetterAutoCrafterBlockEntity.GRID_WIDTH,
            BetterAutoCrafterBlockEntity.GRID_HEIGHT, 30, 17, 8, 84);

    private final SimpleContainer resultContainer = new SimpleContainer(1);
    /** Items each grid slot takes per craft: more than 1 for auto crafting recipes. Computed on the server. */
    private final ContainerData amounts;

    /** Client-side constructor (called by the MenuType with the data written in {@code openMenu}). */
    public BetterAutoCrafterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, Source.client(extraData, List.of()));
    }

    /** Server-side constructor (called by the block entity). */
    public BetterAutoCrafterMenu(int containerId, Inventory playerInventory, BetterAutoCrafterBlockEntity blockEntity) {
        this(containerId, playerInventory, Source.of(blockEntity));
    }

    private BetterAutoCrafterMenu(int containerId, Inventory playerInventory, Source source) {
        super(ModMenuTypes.BETTER_AUTO_CRAFTER.get(), containerId, ModBlocks.BETTER_AUTO_CRAFTER.get(), LAYOUT, source);
        addTemplateSlots(source);
        addSlot(new ResultPreviewSlot(resultContainer, 0, 124, 35));
        addPlayerInventory(playerInventory);
        addUpgradeSlots(source);
        amounts = getBlockEntity() instanceof BetterAutoCrafterBlockEntity crafter
                ? amountsOf(crafter)
                : new SimpleContainerData(BetterAutoCrafterBlockEntity.GRID_SIZE);
        addDataSlots(amounts);
    }

    private static ContainerData amountsOf(BetterAutoCrafterBlockEntity crafter) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return crafter.getAmount(index);
            }

            @Override
            public void set(int index, int value) {
                // Read-only: derived from the grid's recipe.
            }

            @Override
            public int getCount() {
                return BetterAutoCrafterBlockEntity.GRID_SIZE;
            }
        };
    }

    /** How many items the grid slot takes per craft; 0 for any other slot, or without a recipe. */
    public int getAmount(Slot slot) {
        int gridSlot = slot.index - templateStart();
        return gridSlot >= 0 && gridSlot < BetterAutoCrafterBlockEntity.GRID_SIZE ? amounts.get(gridSlot) : 0;
    }

    @Override
    public void broadcastChanges() {
        // Server only: refresh the preview before syncing slots to the client.
        if (getBlockEntity() instanceof BetterAutoCrafterBlockEntity crafter) {
            resultContainer.setItem(0, crafter.getCraftingResult().copy());
        }
        super.broadcastChanges();
    }
}
