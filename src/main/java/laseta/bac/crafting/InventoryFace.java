package laseta.bac.crafting;

import laseta.bac.sideconfig.RelativeSide;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * An inventory a craft pulls from or pushes into.
 *
 * @param side the crafter face it belongs to, used by the face priorities; null for a block linked by a Multi
 *             Translocation Upgrade (those keep their link order)
 */
public record InventoryFace(@Nullable RelativeSide side, IItemHandler handler) {
}
