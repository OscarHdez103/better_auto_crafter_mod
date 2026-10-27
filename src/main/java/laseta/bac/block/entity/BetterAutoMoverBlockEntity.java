package laseta.bac.block.entity;

import java.util.List;

import laseta.bac.CommonConfig;
import laseta.bac.machine.AutoMachineBlockEntity;
import laseta.bac.machine.ItemTransfer;
import laseta.bac.machine.MachineOption;
import laseta.bac.machine.MachineProblem;
import laseta.bac.menu.BetterAutoMoverMenu;
import laseta.bac.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Better Auto Mover: its template grid (its size is set in the config) is a list of items and counts. Each
 * operation moves those items from the input inventories into the output inventories: all together or not at all, or
 * each slot on its own (see {@link GridMode}). With stocking upgrades, the kinds of items that are already stocked are
 * left out and the rest is moved.
 */
public class BetterAutoMoverBlockEntity extends AutoMachineBlockEntity {
    /** How the grid's slots are moved. */
    public enum GridMode implements StringRepresentable {
        /** Only when every slot's items are there, all in one go (like a recipe). */
        ALL_TOGETHER("all_together"),
        /** Each slot whose items are there, whatever the other slots. */
        EACH_SLOT("each_slot");

        private final String name;

        GridMode(String name) {
            this.name = name;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }
    }

    public static final MachineOption<GridMode> GRID_MODE = MachineOption.of("grid_mode", GridMode.class, GridMode.ALL_TOGETHER);
    public static final List<MachineOption<?>> OPTIONS = List.of(GRID_MODE);

    public BetterAutoMoverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BETTER_AUTO_MOVER.get(), pos, state, CommonConfig.moverGridWidth(), CommonConfig.moverGridHeight(),
                OPTIONS);
    }

    @Override
    protected int getBaseSpeed() {
        return CommonConfig.moverBaseSpeed();
    }

    @Override
    protected boolean operate(ServerLevel level, ItemTransfer transfer) {
        List<ItemStack> wanted = getTemplateStacks().stream()
                .map(stack -> transfer.isStocked(stack) ? ItemStack.EMPTY : stack)
                .toList();
        if (wanted.stream().allMatch(ItemStack::isEmpty)) {
            // The template isn't empty (checked before): everything in it is stocked already.
            return transfer.fail(MachineProblem.STOCKED);
        }
        // Nothing is made: what was pulled is pushed, one stack per kind of item.
        ItemTransfer.Process process = pulled -> ItemTransfer.Output.of(ItemTransfer.merge(pulled));
        return getOptions().get(GRID_MODE) == GridMode.EACH_SLOT
                ? transfer.runEach(wanted, process)
                : transfer.run(wanted, process);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        return new BetterAutoMoverMenu(containerId, playerInventory, this);
    }
}
