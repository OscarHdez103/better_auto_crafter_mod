package laseta.bac.client.screen;

import java.util.ArrayList;
import java.util.List;

import laseta.bac.block.entity.BetterAutoBrewerBlockEntity;
import laseta.bac.client.screen.widget.FuelGauge;
import laseta.bac.client.screen.widget.MachineStatusIcon;
import laseta.bac.menu.BetterAutoBrewerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The brewer's screen: the row of ingredient slots on top, in brewing order, and below it what each brew starts from,
 * a furnace's arrow (which fills up during the cooldown) and the potions it makes, with the stored fuel as an
 * orange gauge on the left.
 */
public class BetterAutoBrewerScreen extends AutoMachineScreen<BetterAutoBrewerMenu> {
    /** Vanilla's furnace background: only its empty arrow is used. */
    private static final ResourceLocation FURNACE = ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final ResourceLocation ARROW_PROGRESS = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private static final int FURNACE_ARROW_X = 79;
    private static final int FURNACE_ARROW_Y = 34;

    /** The arrow between the starter and the result, relative to the GUI's top-left corner (used by JEI's click area). */
    public static final int ARROW_X = 79;
    public static final int ARROW_Y = BetterAutoBrewerMenu.PREVIEW_Y - 1;
    public static final int ARROW_WIDTH = 24;
    public static final int ARROW_HEIGHT = 17;
    private static final int GAUGE_X = 34;
    private static final int GAUGE_Y = 40;
    private static final int GAUGE_WIDTH = 10;
    private static final int GAUGE_HEIGHT = 29;

    public BetterAutoBrewerScreen(BetterAutoBrewerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderMachineBackground(GuiGraphics graphics) {
        super.renderMachineBackground(graphics);
        graphics.blit(FURNACE, leftPos + ARROW_X, topPos + ARROW_Y, FURNACE_ARROW_X, FURNACE_ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT);
        int capacity = menu.getFuelCapacity();
        FuelGauge.BLAZE.render(graphics, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT,
                capacity <= 0 ? 0 : (float) menu.getStoredFuel() / capacity);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        int filled = Math.round(MachineStatusIcon.cooldownProgress(menu, partialTick) * ARROW_WIDTH);
        if (filled > 0) {
            graphics.blitSprite(ARROW_PROGRESS, ARROW_WIDTH, 16, 0, 0, leftPos + ARROW_X, topPos + ARROW_Y, filled, 16);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (menu.getCarried().isEmpty()
                && FuelGauge.isMouseOver(leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(text().get("fuel", menu.getStoredFuel()));
            if (menu.getFuelCost() > 0) {
                lines.add(text().get("fuel.operations_left", menu.getStoredFuel() / menu.getFuelCost()).withStyle(ChatFormatting.GRAY));
            }
            lines.add(text().get("fuel.refill").withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** Ingredients say where they are in the brew; the previews say how many each brew takes and makes. */
    @Override
    protected @NotNull List<Component> getTooltipFromContainerItem(@NotNull ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot == null) {
            return tooltip;
        }
        int index = hoveredSlot.index;
        if (index < BetterAutoBrewerBlockEntity.GRID_SIZE) {
            int step = 0;
            for (int i = 0; i <= index; i++) {
                step += menu.slots.get(i).hasItem() ? 1 : 0;
            }
            tooltip.add(text().get("ingredient_step", step).withStyle(ChatFormatting.GOLD));
        } else if (index == BetterAutoBrewerBlockEntity.GRID_SIZE) {
            tooltip.add(text().get("starter.tooltip", BetterAutoBrewerBlockEntity.BREW_SIZE).withStyle(ChatFormatting.GOLD));
        } else if (index == BetterAutoBrewerBlockEntity.GRID_SIZE + 1) {
            tooltip.add(text().get("result.tooltip", BetterAutoBrewerBlockEntity.BREW_SIZE).withStyle(ChatFormatting.GOLD));
        }
        return tooltip;
    }
}
