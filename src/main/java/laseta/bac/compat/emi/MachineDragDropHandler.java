package laseta.bac.compat.emi;

import java.util.List;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import laseta.bac.client.screen.AutoMachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/**
 * Lets items dragged from EMI be dropped on a machine's {@link AutoMachineScreen#getGhostTargets ghost targets} (the
 * setting boxes of its upgrades), highlighted while dragging like EMI's own drop targets. Only single items: a tag or
 * any other list of several stacks says nothing about which one to set.
 */
public class MachineDragDropHandler<S extends AutoMachineScreen<?>> implements EmiDragDropHandler<S> {
    /** EMI's own highlight colour for drop targets. */
    private static final int HIGHLIGHT = 0x8822BB33;

    @Override
    public boolean dropStack(S screen, EmiIngredient ingredient, int x, int y) {
        ItemStack stack = singleItem(ingredient);
        for (AutoMachineScreen.GhostTarget target : screen.getGhostTargets(stack)) {
            if (target.area().contains(x, y)) {
                target.drop().accept(stack);
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(S screen, EmiIngredient dragged, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        for (AutoMachineScreen.GhostTarget target : screen.getGhostTargets(singleItem(dragged))) {
            Rect2i area = target.area();
            graphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(), HIGHLIGHT);
        }
    }

    private static ItemStack singleItem(EmiIngredient ingredient) {
        List<EmiStack> stacks = ingredient.getEmiStacks();
        return stacks.size() == 1 ? stacks.getFirst().getItemStack() : ItemStack.EMPTY;
    }
}
