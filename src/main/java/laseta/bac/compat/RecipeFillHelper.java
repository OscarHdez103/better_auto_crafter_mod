package laseta.bac.compat;

import java.util.List;
import java.util.stream.Stream;

import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.menu.transfer.GridFillPlanner;
import laseta.bac.network.FillCraftingGridPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-side glue shared by the JEI and EMI recipe transfer handlers. */
public final class RecipeFillHelper {
    private RecipeFillHelper() {
    }

    /** Turns the items a recipe viewer shows for one recipe slot into the candidate list the planner / packet use. */
    public static List<ItemStack> candidates(Stream<ItemStack> displayed) {
        return displayed
                .filter(stack -> !stack.isEmpty())
                .limit(GridFillPlanner.MAX_CANDIDATES)
                .map(stack -> stack.copyWithCount(1))
                .toList();
    }

    /** Asks the server to fill the grid. The server re-plans with its own (authoritative) inventory state. */
    public static void requestFill(BetterAutoCrafterMenu menu, List<List<ItemStack>> wanted, boolean fillMaximum) {
        PacketDistributor.sendToServer(new FillCraftingGridPayload(menu.containerId, wanted, fillMaximum));
    }
}
