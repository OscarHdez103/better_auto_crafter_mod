package laseta.bac.upgrade;

import java.util.ArrayList;
import java.util.List;

import laseta.bac.crafting.InventoryFace;
import laseta.bac.item.MultiTranslocationUpgradeItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Resolves an installed Multi Translocation Upgrade to the inventories of its linked blocks. Server only.
 *
 * <p>Unlike a single Translocator, one unusable block doesn't stop the crafter: it keeps working with the linked blocks
 * that are there (like a pipe network skips a missing machine), and reports {@link UpgradeStatus#PARTIALLY_ACTIVE}.
 * Only when none of them can be used does the upgrade stop crafting, with the reason of the first one.</p>
 *
 * <p>Links beyond {@link MultiTranslocationUpgradeItem#getMaxTargets()} (made before the limit was lowered in the
 * config) are not used either, which also makes the upgrade PARTIALLY_ACTIVE.</p>
 */
public final class MultiTranslocation {
    /** @param faces the usable linked inventories, in link order; empty unless the status lets the crafter work */
    public record Result(UpgradeStatus status, List<InventoryFace> faces) {
        private static Result of(UpgradeStatus status) {
            return new Result(status, List.of());
        }
    }

    private MultiTranslocation() {
    }

    /** Never loads chunks: blocks in unloaded chunks (or dimensions) are skipped until they are loaded again. */
    public static Result resolve(ItemStack upgrade, ServerLevel crafterLevel) {
        if (upgrade.isEmpty() || !(upgrade.getItem() instanceof MultiTranslocationUpgradeItem item)) {
            return Result.of(UpgradeStatus.EMPTY);
        }
        List<LinkedFace> targets = MultiTranslocationUpgradeItem.getTargets(upgrade);
        if (targets.isEmpty()) {
            return Result.of(UpgradeStatus.NOT_LINKED);
        }
        int usable = Math.min(targets.size(), MultiTranslocationUpgradeItem.getMaxTargets());
        List<InventoryFace> faces = new ArrayList<>(usable);
        UpgradeStatus firstProblem = null;
        for (LinkedFace target : targets.subList(0, usable)) {
            UpgradeStatus problem = null;
            IItemHandler handler = null;
            ServerLevel targetLevel = crafterLevel.getServer().getLevel(target.pos().dimension());
            if (!item.isDimensional() && !target.pos().dimension().equals(crafterLevel.dimension())) {
                problem = UpgradeStatus.WRONG_DIMENSION;
            } else if (targetLevel == null) {
                problem = UpgradeStatus.DIMENSION_UNAVAILABLE;
            } else if (!targetLevel.isLoaded(target.pos().pos())) {
                problem = UpgradeStatus.UNLOADED;
            } else {
                handler = targetLevel.getCapability(Capabilities.ItemHandler.BLOCK, target.pos().pos(), target.side());
                if (handler == null) {
                    problem = UpgradeStatus.INVENTORY_MISSING;
                }
            }
            if (handler != null) {
                faces.add(new InventoryFace(null, handler));
            } else if (firstProblem == null) {
                firstProblem = problem;
            }
        }
        if (faces.isEmpty()) {
            return Result.of(firstProblem);
        }
        boolean allUsed = firstProblem == null && usable == targets.size();
        return new Result(allUsed ? UpgradeStatus.ACTIVE : UpgradeStatus.PARTIALLY_ACTIVE, List.copyOf(faces));
    }
}
