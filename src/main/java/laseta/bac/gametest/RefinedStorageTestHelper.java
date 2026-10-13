package laseta.bac.gametest;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.controller.ControllerBlockEntity;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Refined Storage calls for the game tests. Only loaded by tests that first check that Refined Storage is installed. */
final class RefinedStorageTestHelper {
    private RefinedStorageTestHelper() {
    }

    /** Takes items out of the network like a grid / terminal does. @return how many were taken */
    static long extractThroughNetwork(ServerLevel level, BlockPos controllerPos, ItemStack stack, long amount) {
        ControllerBlockEntity controller = (ControllerBlockEntity) level.getBlockEntity(controllerPos);
        StorageNetworkComponent storage = controller.getNetworkForItem().getComponent(StorageNetworkComponent.class);
        return storage.extract(ItemResource.ofItemStack(stack), amount, Action.EXECUTE, Actor.EMPTY);
    }
}
