package laseta.bac.menu.slot;

import com.mojang.datafixers.util.Pair;

import java.util.function.BooleanSupplier;
import laseta.bac.BetterAutoCrafter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The slot of one of a host's tabs (see {@link laseta.bac.machine.HostTabs}), holding its machines. Only the slot of
 * the menu's own tab can be used: the others are only there to be seen, and clicking one shows that tab instead. An
 * empty one shows the outline of a machine, like an empty armor slot shows a piece of armor. A locked tab's slot isn't
 * active at all.
 */
public class HostTabSlot extends SlotItemHandler {
    public static final ResourceLocation EMPTY_ICON =
            ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "item/empty_slot_machine");

    private final int tab;
    private final boolean current;
    private final BooleanSupplier locked;

    public HostTabSlot(IItemHandler tabs, int tab, boolean current, BooleanSupplier locked, int x, int y) {
        super(tabs, tab, x, y);
        this.tab = tab;
        this.current = current;
        this.locked = locked;
    }

    public int getTab() {
        return tab;
    }

    /** Whether this is the menu's own tab, the only one whose slot can be used. */
    public boolean isCurrent() {
        return current;
    }

    @Override
    public boolean isActive() {
        return !locked.getAsBoolean();
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return current && super.mayPlace(stack);
    }

    @Override
    public boolean mayPickup(@NotNull Player player) {
        return current && super.mayPickup(player);
    }

    /**
     * Shift-clicking machines onto the ones already in the tab grows their stack in place, which an item handler doesn't
     * notice: set it again, so the host knows how many there are now (and saves it).
     */
    @Override
    public void setChanged() {
        if (getItemHandler() instanceof IItemHandlerModifiable handler) {
            handler.setStackInSlot(getContainerSlot(), getItem());
        }
    }

    @Override
    public @Nullable Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        return Pair.of(InventoryMenu.BLOCK_ATLAS, EMPTY_ICON);
    }
}
