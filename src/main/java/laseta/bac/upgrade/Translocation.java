package laseta.bac.upgrade;

import laseta.bac.block.TranslocatorBlock;
import laseta.bac.crafting.CraftingAnchor;
import laseta.bac.item.TranslocationUpgradeItem;
import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModDataComponents;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Resolves an installed translocation upgrade to the place the crafter should work from. Server only. */
public final class Translocation {
    /** @param anchor the Translocator to work from; only set when {@code status} is ACTIVE */
    public record Result(UpgradeStatus status, @Nullable CraftingAnchor anchor) {
        private static Result of(UpgradeStatus status) {
            return new Result(status, null);
        }
    }

    private Translocation() {
    }

    /**
     * Never loads chunks: an unloaded Translocator (or one in an unloaded dimension) makes the upgrade inactive until
     * it is loaded again, the same way hoppers and pipes don't reach into unloaded chunks.
     */
    public static Result resolve(ItemStack upgrade, ServerLevel crafterLevel) {
        if (upgrade.isEmpty() || !(upgrade.getItem() instanceof TranslocationUpgradeItem item)) {
            return Result.of(UpgradeStatus.EMPTY);
        }
        GlobalPos link = upgrade.get(ModDataComponents.TRANSLOCATOR_LINK.get());
        if (link == null) {
            return Result.of(UpgradeStatus.NOT_LINKED);
        }
        if (!item.isDimensional() && !link.dimension().equals(crafterLevel.dimension())) {
            return Result.of(UpgradeStatus.WRONG_DIMENSION);
        }
        ServerLevel targetLevel = crafterLevel.getServer().getLevel(link.dimension());
        if (targetLevel == null) {
            return Result.of(UpgradeStatus.DIMENSION_UNAVAILABLE);
        }
        if (!targetLevel.isLoaded(link.pos())) {
            return Result.of(UpgradeStatus.UNLOADED);
        }
        BlockState state = targetLevel.getBlockState(link.pos());
        if (!state.is(ModBlocks.TRANSLOCATOR.get())) {
            return Result.of(UpgradeStatus.TRANSLOCATOR_MISSING);
        }
        return new Result(UpgradeStatus.ACTIVE,
                new CraftingAnchor(targetLevel, link.pos(), state.getValue(TranslocatorBlock.FACING)));
    }
}
