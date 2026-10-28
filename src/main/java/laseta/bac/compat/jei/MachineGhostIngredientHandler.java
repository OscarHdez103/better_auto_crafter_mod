package laseta.bac.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import laseta.bac.client.screen.AutoMachineScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Lets items dragged from JEI's ingredient list be dropped on a machine's {@link AutoMachineScreen#getGhostTargets
 * ghost targets} (the setting boxes of its upgrades), which JEI highlights while dragging.
 */
public class MachineGhostIngredientHandler<S extends AutoMachineScreen<?>> implements IGhostIngredientHandler<S> {
    @Override
    public <I> @NotNull List<Target<I>> getTargetsTyped(@NotNull S screen, ITypedIngredient<I> ingredient, boolean doStart) {
        Optional<ItemStack> stack = ingredient.getItemStack();
        if (stack.isEmpty()) {
            return List.of();
        }
        List<Target<I>> targets = new ArrayList<>();
        for (AutoMachineScreen.GhostTarget target : screen.getGhostTargets(stack.get())) {
            targets.add(new Target<>() {
                @Override
                public @NotNull Rect2i getArea() {
                    return target.area();
                }

                @Override
                public void accept(@NotNull I dropped) {
                    target.drop().accept(stack.get());
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {
        // Nothing to clean up: each drop is sent right away.
    }
}
