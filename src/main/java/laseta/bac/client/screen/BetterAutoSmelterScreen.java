package laseta.bac.client.screen;

import java.util.ArrayList;
import java.util.List;

import laseta.bac.block.entity.BetterAutoSmelterBlockEntity;
import laseta.bac.client.screen.widget.FuelGauge;
import laseta.bac.client.screen.widget.MachineStatusIcon;
import laseta.bac.menu.BetterAutoSmelterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The smelter's screen: the input slot, a furnace's arrow (which fills up during the cooldown, as a furnace's does
 * while cooking) and the result, with the block that would cook such a recipe below the arrow, and the stored fuel as a
 * black gauge on the left. The input slot shows how many items each operation takes when it is more than one.
 */
public class BetterAutoSmelterScreen extends AutoMachineScreen<BetterAutoSmelterMenu> {
    /** Vanilla's furnace background: only its empty arrow is used. */
    private static final ResourceLocation FURNACE = ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final ResourceLocation ARROW_PROGRESS = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");

    /** The arrow between the input and the result, relative to the GUI's top-left corner (used by JEI's click area). */
    public static final int ARROW_X = 79;
    public static final int ARROW_Y = 34;
    public static final int ARROW_WIDTH = 24;
    public static final int ARROW_HEIGHT = 17;
    private static final int WORKSTATION_X = ARROW_X + (ARROW_WIDTH - 16) / 2;
    private static final int WORKSTATION_Y = 54;
    private static final int GAUGE_X = 34;
    private static final int GAUGE_Y = 17;
    private static final int GAUGE_WIDTH = 10;
    private static final int GAUGE_HEIGHT = 52;

    public BetterAutoSmelterScreen(BetterAutoSmelterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderMachineBackground(GuiGraphics graphics) {
        super.renderMachineBackground(graphics);
        graphics.blit(FURNACE, leftPos + ARROW_X, topPos + ARROW_Y, ARROW_X, ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT);
        int capacity = menu.getFuelCapacity();
        FuelGauge.COAL.render(graphics, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT,
                capacity <= 0 ? 0 : (float) menu.getStoredFuel() / capacity);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        int filled = Math.round(MachineStatusIcon.cooldownProgress(menu, partialTick) * ARROW_WIDTH);
        if (filled > 0) {
            graphics.blitSprite(ARROW_PROGRESS, ARROW_WIDTH, 16, 0, 0, leftPos + ARROW_X, topPos + ARROW_Y, filled, 16);
        }
        BetterAutoSmelterBlockEntity.Kind kind = menu.getKind();
        if (kind != null) {
            graphics.renderFakeItem(kind.getWorkstation(), leftPos + WORKSTATION_X, topPos + WORKSTATION_Y);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.getAmount() > 1) {
            drawSlotAmount(graphics, menu.slots.getFirst(), menu.getAmount());
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        List<Component> tooltip = null;
        if (isInside(mouseX, mouseY, GAUGE_X, GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT)) {
            tooltip = fuelTooltip();
        } else if (isInside(mouseX, mouseY, ARROW_X, ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT)
                || isInside(mouseX, mouseY, WORKSTATION_X, WORKSTATION_Y, 16, 16)) {
            tooltip = recipeTooltip();
        }
        if (tooltip != null && !tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return FuelGauge.isMouseOver(leftPos + x, topPos + y, width, height, mouseX, mouseY);
    }

    private List<Component> fuelTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(text().get("fuel", menu.getStoredFuel()));
        int cost = menu.getFuelCost();
        if (menu.getKind() != null) {
            lines.add((cost == 0 ? text().get("fuel.not_needed") : text().get("fuel.operations_left", menu.getStoredFuel() / cost))
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(text().get("fuel.refill").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** What kind of recipe the input is cooked with, and the fuel it uses. */
    private List<Component> recipeTooltip() {
        BetterAutoSmelterBlockEntity.Kind kind = menu.getKind();
        if (kind == null) {
            return List.of();
        }
        int cost = menu.getFuelCost();
        return List.of(text().get("recipe_kind." + kind.getSerializedName()),
                (cost == 0 ? text().get("fuel.not_needed") : text().get("fuel.cost", cost)).withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected @NotNull List<Component> getTooltipFromContainerItem(@NotNull ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        Slot input = menu.slots.getFirst();
        if (hoveredSlot == input && menu.getAmount() > 1) {
            tooltip.add(text().get("amount.tooltip", menu.getAmount()).withStyle(ChatFormatting.GOLD));
        }
        return tooltip;
    }
}
