package laseta.bac.compat.emi;

import java.util.List;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * Shows its stacks one after the other, each with its own amount (EMI's lists show a single amount for all of them).
 * It changes every second on EMI's own clock, so slots with as many stacks change in step.
 */
public class CyclingEmiIngredient implements EmiIngredient {
    private final List<EmiStack> stacks;

    public CyclingEmiIngredient(List<EmiStack> stacks) {
        if (stacks.isEmpty()) {
            throw new IllegalArgumentException("Nothing to cycle through");
        }
        this.stacks = List.copyOf(stacks);
    }

    private EmiStack current() {
        return stacks.get((int) (System.currentTimeMillis() / 500 % stacks.size()));
    }

    @Override
    public List<EmiStack> getEmiStacks() {
        return stacks;
    }

    @Override
    public EmiIngredient copy() {
        return new CyclingEmiIngredient(stacks.stream().map(EmiStack::copy).toList());
    }

    /** The amount shown right now. */
    @Override
    public long getAmount() {
        return current().getAmount();
    }

    /** Every stack keeps its own amount: that is what this shows. */
    @Override
    public EmiIngredient setAmount(long amount) {
        return this;
    }

    @Override
    public float getChance() {
        return 1;
    }

    @Override
    public EmiIngredient setChance(float chance) {
        return this;
    }

    @Override
    public void render(GuiGraphics draw, int x, int y, float delta, int flags) {
        current().render(draw, x, y, delta, flags);
    }

    @Override
    public List<ClientTooltipComponent> getTooltip() {
        return current().getTooltip();
    }
}
