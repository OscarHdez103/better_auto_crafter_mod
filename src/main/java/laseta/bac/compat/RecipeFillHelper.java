package laseta.bac.compat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.menu.transfer.GridFillPlanner;
import laseta.bac.network.FillTemplatePayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-side glue shared by the JEI and EMI recipe transfer handlers. */
public final class RecipeFillHelper {
    private RecipeFillHelper() {
    }

    /**
     * Turns the items a recipe viewer shows for one recipe slot into the candidate list the planner / packet use. Their
     * count is how many the slot takes (more than one for auto crafting recipes).
     */
    public static List<ItemStack> candidates(Stream<ItemStack> displayed) {
        return displayed
                .filter(stack -> !stack.isEmpty())
                .limit(GridFillPlanner.MAX_CANDIDATES)
                .map(stack -> stack.copyWithCount(Math.max(1, stack.getCount())))
                .toList();
    }

    /**
     * Asks the server to fill the machine's template. The server re-plans with its own (authoritative) inventory state.
     *
     * @param recipe the recipe's id, null if it isn't a real recipe
     */
    public static void requestFill(AutoMachineMenu menu, List<List<ItemStack>> wanted, boolean fillMaximum,
                                   ResourceLocation recipe) {
        PacketDistributor.sendToServer(new FillTemplatePayload(menu.containerId, wanted, fillMaximum, Optional.ofNullable(recipe)));
    }
}
