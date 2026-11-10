package laseta.bac.client.screen;

import java.util.List;
import java.util.Objects;

import laseta.bac.client.screen.widget.HostTabStrip;
import laseta.bac.machine.HostTabs;
import laseta.bac.machine.MachineLayout;
import laseta.bac.menu.BetterAutoHubMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The hub's screen for an empty tab ({@link BetterAutoHubMenu}): like a browser's new tab, a blank page under its
 * tabs, with only the player inventory and, faded into the page, the machines that can go into a tab (hints, not
 * slots). Putting machines into the tab shows their own screen there instead.
 */
public class BetterAutoHubScreen extends AbstractContainerScreen<BetterAutoHubMenu> {
    /** Top of the machines' icons: in the middle of the space above the inventory. */
    private static final int MACHINES_Y = 36;
    private static final int ICON_SIZE = 16;
    private static final int MACHINE_GAP = 8;
    /** The panel's colour, mostly hiding the machines' icons: they are only hints. */
    private static final int PAGE_FADE = 0xA8C6C6C6;
    /** Above items rendered in the GUI, so the fade isn't hidden behind them. */
    private static final float FADE_Z = 200.0F;

    /** Every machine a tab can hold, in registration order. */
    private final List<ItemStack> machines = BuiltInRegistries.ITEM.stream()
            .map(Item::getDefaultInstance)
            .filter(HostTabs::canHold)
            .toList();
    private HostTabStrip tabStrip;
    /** Set when the screen handled a mouse press itself, so the matching release isn't handled as a slot click. */
    private boolean ignoreNextRelease;

    public BetterAutoHubScreen(BetterAutoHubMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        MachineLayout layout = BetterAutoHubMenu.LAYOUT;
        this.imageWidth = layout.width();
        this.imageHeight = layout.height();
        this.inventoryLabelX = layout.inventoryLeft();
        this.inventoryLabelY = layout.inventoryLabelY();
    }

    @Override
    protected void init() {
        super.init();
        tabStrip = new HostTabStrip(Objects.requireNonNull(menu.getHostTabs()), menu.containerId);
        topPos += tabStrip.getGuiOffset();
        tabStrip.setGuiOrigin(leftPos, topPos);
    }

    /** Areas outside the main GUI that this screen draws on, for the JEI / EMI compat: the tabs. */
    public List<Rect2i> getExtraAreas() {
        return tabStrip == null ? List.of() : List.of(tabStrip.getArea());
    }

    private int machineLeft(int index) {
        int width = machines.size() * ICON_SIZE + (machines.size() - 1) * MACHINE_GAP;
        return (imageWidth - width) / 2 + index * (ICON_SIZE + MACHINE_GAP);
    }

    /** Index in {@link #machines} of the one under the mouse, or -1. */
    private int machineAt(double mouseX, double mouseY) {
        for (int i = 0; i < machines.size(); i++) {
            int x = leftPos + machineLeft(i);
            int y = topPos + MACHINES_Y;
            if (mouseX >= x && mouseX < x + ICON_SIZE && mouseY >= y && mouseY < y + ICON_SIZE) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        tabStrip.renderBehind(graphics);
        AutoMachineScreen.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            if (slot.y >= 0) {
                AutoMachineScreen.drawSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1);
            }
        }
        tabStrip.renderFront(graphics);
        for (int i = 0; i < machines.size(); i++) {
            graphics.renderFakeItem(machines.get(i), leftPos + machineLeft(i), topPos + MACHINES_Y);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, FADE_Z);
        for (int i = 0; i < machines.size(); i++) {
            int x = leftPos + machineLeft(i);
            graphics.fill(x, topPos + MACHINES_Y, x + ICON_SIZE, topPos + MACHINES_Y + ICON_SIZE, PAGE_FADE);
        }
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        tabStrip.renderLamps(graphics);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        List<Component> stripTooltip = tabStrip.getTooltip(mouseX, mouseY, menu.getCarried());
        int machine = machineAt(mouseX, mouseY);
        if (stripTooltip != null) {
            graphics.renderComponentTooltip(font, stripTooltip, mouseX, mouseY);
        } else if (!menu.getCarried().isEmpty()) {
            renderTooltip(graphics, mouseX, mouseY);
        } else if (machine >= 0) {
            graphics.renderComponentTooltip(font, List.of(machines.get(machine).getHoverName(),
                    Component.translatable("gui.better_auto_crafter.better_auto_hub.machine.desc").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tabStrip.mouseClicked(mouseX, mouseY, menu.getCarried())) {
            ignoreNextRelease = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ignoreNextRelease) {
            ignoreNextRelease = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button) {
        // Clicking a tab must never count as "outside", which would drop the carried item.
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, button) && !tabStrip.isMouseOver(mouseX, mouseY);
    }
}
