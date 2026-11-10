package laseta.bac.client.screen.widget;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import laseta.bac.machine.AutoMachineBlock;
import laseta.bac.machine.HostTabs;
import laseta.bac.machine.MachineProblem;
import laseta.bac.machine.MachineState;
import laseta.bac.network.SelectHostTabPayload;
import laseta.bac.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The tabs of a host (the Better Auto Hub) above a screen, see {@link HostTabs}. They are drawn like the creative
 * inventory's tabs, widened to share the GUI's whole width, the menu's own one joined to the GUI. Each tab's icon is its
 * slot, drawn by the screen like any slot: the menu's own one sits in a slot frame, as it is the only one that can be
 * used, and an empty one shows a machine's outline. A small lamp in the corner of each icon shows what that tab's
 * machines are doing, like the status icon does. A locked tab shows, faded, the Hub Expansion that unlocks it.
 *
 * <p>Clicking another tab shows that one instead: the server opens its menu. Clicking a locked tab while carrying a
 * Hub Expansion unlocks it.</p>
 *
 * <p>A host that needs energy shows it in a bar above the tabs; one that doesn't shows nothing of it.</p>
 */
public class HostTabStrip {
    /** Vanilla's tab sprites are taller than the tabs stick out: their bottom goes behind (or into) the GUI. */
    private static final int SPRITE_HEIGHT = 32;
    private static final int SPRITE_WIDTH = 26;
    /** The sprites' left and right halves are drawn as they are, the column between them repeated to widen the tab. */
    private static final int SPRITE_HALF = SPRITE_WIDTH / 2;
    private static final int LAMP_SIZE = 4;
    /** Above the slots' items and their counts. */
    private static final float LAMP_Z = 300.0F;
    /** Above the items rendered in the GUI, so the fade isn't hidden behind them. */
    private static final float FADE_Z = 200.0F;
    /** An unselected tab's colour, mostly hiding the Hub Expansion on a locked tab: it is only a hint. */
    private static final int LOCKED_FADE = 0xA88B8B8B;
    private static final int ENERGY_HEIGHT = 6;
    /** Between the energy bar and the tabs. */
    private static final int ENERGY_GAP = 2;

    private final HostTabs tabs;
    private final int containerId;
    private final ItemStack expansion = new ItemStack(ModItems.HUB_EXPANSION.get());
    private int leftPos;
    private int topPos;

    public HostTabStrip(HostTabs tabs, int containerId) {
        this.tabs = tabs;
        this.containerId = containerId;
    }

    /** How far the GUI moves down so what is above it stays on screen, as much as it would grow. */
    public int getGuiOffset() {
        return (HostTabs.TAB_HEIGHT + (tabs.usesEnergy() ? ENERGY_HEIGHT + ENERGY_GAP : 0)) / 2;
    }

    public void setGuiOrigin(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;
    }

    /** The first tab's sprite joins the GUI's left border, the last one's its right border, like vanilla's columns 1 and 7. */
    private ResourceLocation sprite(boolean selected, int tab) {
        int column = tab == 0 ? 1 : tab == tabs.count() - 1 ? 7 : 2;
        return ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_"
                + (selected ? "selected_" : "unselected_") + column);
    }

    private int tabLeft(int tab) {
        return leftPos + tabs.tabLeft(tab);
    }

    private int tabTop() {
        return topPos - HostTabs.TAB_HEIGHT;
    }

    private int stripWidth() {
        return tabs.tabLeft(tabs.count()) - HostTabs.TAB_GAP;
    }

    private int energyTop() {
        return tabTop() - ENERGY_GAP - ENERGY_HEIGHT;
    }

    private void drawTab(GuiGraphics graphics, boolean selected, int tab) {
        ResourceLocation sprite = sprite(selected, tab);
        int x = tabLeft(tab);
        int width = tabs.tabWidth(tab);
        graphics.blitSprite(sprite, SPRITE_WIDTH, SPRITE_HEIGHT, 0, 0, x, tabTop(), SPRITE_HALF, SPRITE_HEIGHT);
        for (int column = SPRITE_HALF; column < width - SPRITE_HALF; column++) {
            graphics.blitSprite(sprite, SPRITE_WIDTH, SPRITE_HEIGHT, SPRITE_HALF - 1, 0, x + column, tabTop(), 1, SPRITE_HEIGHT);
        }
        graphics.blitSprite(sprite, SPRITE_WIDTH, SPRITE_HEIGHT, SPRITE_WIDTH - SPRITE_HALF, 0, x + width - SPRITE_HALF,
                tabTop(), SPRITE_HALF, SPRITE_HEIGHT);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------------------------

    /** The other tabs and the energy bar, before the GUI's background: it covers the tabs' bottom. */
    public void renderBehind(GuiGraphics graphics) {
        for (int tab = 0; tab < tabs.count(); tab++) {
            if (tab != tabs.current()) {
                drawTab(graphics, false, tab);
                if (tabs.isLocked(tab)) {
                    drawLockedIcon(graphics, tab);
                }
            }
        }
        if (tabs.usesEnergy()) {
            FuelGauge.ENERGY.renderHorizontal(graphics, leftPos, energyTop(), stripWidth(), ENERGY_HEIGHT,
                    (float) tabs.getEnergy() / tabs.getEnergyCapacity());
        }
    }

    private void drawLockedIcon(GuiGraphics graphics, int tab) {
        int x = leftPos + tabs.slotX(tab);
        int y = topPos + HostTabs.SLOT_Y;
        graphics.renderFakeItem(expansion, x, y);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, FADE_Z);
        graphics.fill(x, y, x + 16, y + 16, LOCKED_FADE);
        graphics.pose().popPose();
    }

    /** The menu's own tab, after the GUI's background: it joins it, over its border. */
    public void renderFront(GuiGraphics graphics) {
        int tab = tabs.current();
        drawTab(graphics, true, tab);
        SlotWidgets.drawSlot(graphics, leftPos + tabs.slotX(tab) - 1, topPos + HostTabs.SLOT_Y - 1);
    }

    /**
     * Each tab's lamp, in the top right corner of its icon (where a block's icon leaves room for it). Call from the
     * screen's {@code renderLabels}, where positions are relative to the GUI.
     */
    public void renderLamps(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, LAMP_Z);
        for (int tab = 0; tab < tabs.count(); tab++) {
            MachineState state = tabs.getState(tab);
            if (state != null) {
                MachineStatusIcon.renderLamp(graphics, state, tabs.slotX(tab) + 16 - LAMP_SIZE, HostTabs.SLOT_Y);
            }
        }
        graphics.pose().popPose();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Mouse
    // ---------------------------------------------------------------------------------------------------------------

    /** The tab under the mouse, or -1. */
    public int tabAt(double mouseX, double mouseY) {
        if (mouseY < tabTop() || mouseY >= topPos) {
            return -1;
        }
        for (int tab = 0; tab < tabs.count(); tab++) {
            if (mouseX >= tabLeft(tab) && mouseX < tabLeft(tab) + tabs.tabWidth(tab)) {
                return tab;
            }
        }
        return -1;
    }

    private boolean isOverEnergy(double mouseX, double mouseY) {
        return tabs.usesEnergy() && FuelGauge.isMouseOver(leftPos, energyTop(), stripWidth(), ENERGY_HEIGHT, mouseX, mouseY);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return tabAt(mouseX, mouseY) >= 0 || isOverEnergy(mouseX, mouseY);
    }

    /** Where the tabs (and the energy bar) are, for the screen's areas outside the GUI (other mods' overlays keep clear of them). */
    public Rect2i getArea() {
        int top = tabs.usesEnergy() ? energyTop() : tabTop();
        return new Rect2i(leftPos, top, stripWidth(), topPos - top);
    }

    /**
     * Clicking another tab asks the server to show it, and clicking a locked one with a Hub Expansion to unlock it. The
     * menu's own tab is left to the screen: its icon is a slot.
     *
     * @return true if the click was handled
     */
    public boolean mouseClicked(double mouseX, double mouseY, ItemStack carried) {
        int tab = tabAt(mouseX, mouseY);
        if (tab < 0 || tab == tabs.current()) {
            return isOverEnergy(mouseX, mouseY);
        }
        if (tabs.isLocked(tab) && !carried.is(expansion.getItem())) {
            return true;
        }
        PacketDistributor.sendToServer(new SelectHostTabPayload(containerId, tab));
        SideTab.playClickSound();
        return true;
    }

    /**
     * What is under the mouse: a tab (its machines, how many machines they run as and what they are doing) or the
     * energy bar. While carrying something, only the hint that a Hub Expansion unlocks a locked tab.
     *
     * @return null if there is nothing to show
     */
    @Nullable
    public List<Component> getTooltip(double mouseX, double mouseY, ItemStack carried) {
        int tab = tabAt(mouseX, mouseY);
        if (!carried.isEmpty()) {
            return tab >= 0 && tabs.isLocked(tab) && carried.is(expansion.getItem())
                    ? List.of(Component.translatable("gui.better_auto_crafter.host_tab.unlock")) : null;
        }
        if (tab >= 0) {
            return tabTooltip(tab);
        }
        if (isOverEnergy(mouseX, mouseY)) {
            NumberFormat format = NumberFormat.getIntegerInstance(Locale.ROOT);
            return List.of(
                    Component.translatable("gui.better_auto_crafter.host_energy", format.format(tabs.getEnergy()),
                            format.format(tabs.getEnergyCapacity())),
                    Component.translatable("gui.better_auto_crafter.host_energy.per_operation",
                            format.format(tabs.getEnergyPerOperation())).withStyle(ChatFormatting.GRAY));
        }
        return null;
    }

    private List<Component> tabTooltip(int tab) {
        List<Component> lines = new ArrayList<>();
        if (tabs.isLocked(tab)) {
            lines.add(Component.translatable("gui.better_auto_crafter.host_tab.locked"));
            lines.add(Component.translatable("gui.better_auto_crafter.host_tab.locked.desc").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        ItemStack machines = tabs.getMachines(tab);
        AutoMachineBlock machine = AutoMachineBlock.hostable(machines);
        if (machine == null) {
            lines.add(Component.translatable("gui.better_auto_crafter.host_tab.empty"));
            lines.add(Component.translatable("gui.better_auto_crafter.host_tab.empty.desc").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(machines.getHoverName());
            if (machines.getCount() > 1) {
                lines.add(Component.translatable("gui.better_auto_crafter.host_tab.copies", machines.getCount())
                        .withStyle(ChatFormatting.GRAY));
            }
            MachineState state = tabs.getState(tab);
            if (state != null) {
                MachineText text = new MachineText(machine);
                lines.add(text.get("status." + state.getSerializedName()).withStyle(MachineStatusIcon.color(state)));
                MachineProblem problem = tabs.getProblem(tab);
                if (problem != null && (state == MachineState.WAITING || state == MachineState.FAILED)) {
                    lines.add(text.get("problem." + problem.getSerializedName()).withStyle(ChatFormatting.GRAY));
                }
            }
        }
        if (tab != tabs.current()) {
            lines.add(Component.translatable("gui.better_auto_crafter.host_tab.open").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }
}
