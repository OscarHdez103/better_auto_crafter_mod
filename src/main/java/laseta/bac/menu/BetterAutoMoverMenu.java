package laseta.bac.menu;

import laseta.bac.block.entity.BetterAutoMoverBlockEntity;
import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.machine.MachineLayout;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/**
 * The mover's menu: just the machine's slots, with the template grid centered above the inventory. The grid's size
 * comes from the server (see the config), so the layout is made for each menu.
 */
public class BetterAutoMoverMenu extends AutoMachineMenu {
    /** Free space on both sides of the grid for the screen's arrows. */
    public static final int ARROW_SPACE = 24;

    /** Client-side constructor (called by the MenuType with the data written in {@code openMenu}). */
    public BetterAutoMoverMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, Source.client(extraData, BetterAutoMoverBlockEntity.OPTIONS));
    }

    /** Server-side constructor (called by the block entity). */
    public BetterAutoMoverMenu(int containerId, Inventory playerInventory, BetterAutoMoverBlockEntity blockEntity) {
        this(containerId, playerInventory, Source.of(blockEntity));
    }

    private BetterAutoMoverMenu(int containerId, Inventory playerInventory, Source source) {
        super(ModMenuTypes.BETTER_AUTO_MOVER.get(), containerId, ModBlocks.BETTER_AUTO_MOVER.get(),
                MachineLayout.centered(source.gridWidth(), source.gridHeight(), ARROW_SPACE), source);
        addTemplateSlots(source);
        addPlayerInventory(playerInventory);
        addUpgradeSlots(source);
    }
}
