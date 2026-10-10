package laseta.bac.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Where the faces of a crafting operation are: the crafter itself, or a Translocator it is linked to with an
 * upgrade (possibly in another dimension). Relative faces (front, left, ...) are resolved with {@code facing}.
 */
public record CraftingAnchor(ServerLevel level, BlockPos pos, Direction facing) {
}
