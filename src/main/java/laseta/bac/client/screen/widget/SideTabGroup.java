package laseta.bac.client.screen.widget;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

/**
 * A column of {@link SideTab}s attached to the side of a screen. Tabs are stacked vertically and move down
 * smoothly when one above them opens. Only one tab is open at a time, like Thermal's tabs.
 */
public class SideTabGroup {
    private static final int GAP = 2;
    /** Index of the open tab, remembered for the whole session so it stays open between screens. -1 = none. */
    private static int rememberedOpenTab = -1;

    private final List<SideTab> tabs;
    private int x;
    private int y;

    public SideTabGroup(List<SideTab> tabs) {
        this.tabs = List.copyOf(tabs);
        if (rememberedOpenTab >= 0 && rememberedOpenTab < this.tabs.size()) {
            this.tabs.get(rememberedOpenTab).setOpen(true, true);
        }
    }

    /** Top-left corner of the first tab. */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        layout();
    }

    private void layout() {
        int currentY = y;
        for (SideTab tab : tabs) {
            tab.setPosition(x, currentY);
            currentY += tab.currentHeight() + GAP;
        }
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        for (SideTab tab : tabs) {
            tab.tickAnimation();
        }
        layout();
        for (SideTab tab : tabs) {
            tab.render(graphics, font, mouseX, mouseY);
        }
    }

    public boolean renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        for (SideTab tab : tabs) {
            if (tab.renderTooltip(graphics, font, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        layout();
        for (int i = 0; i < tabs.size(); i++) {
            SideTab tab = tabs.get(i);
            SideTab.ClickResult result = tab.mouseClicked(mouseX, mouseY, button);
            if (result == SideTab.ClickResult.TOGGLED) {
                if (tab.isOpen()) {
                    for (SideTab other : tabs) {
                        if (other != tab) {
                            other.setOpen(false, false);
                        }
                    }
                }
                rememberedOpenTab = tab.isOpen() ? i : -1;
                SideTab.playClickSound();
                return true;
            }
            if (result == SideTab.ClickResult.CONSUMED) {
                return true;
            }
        }
        return false;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        for (SideTab tab : tabs) {
            if (tab.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Areas other mods (JEI, EMI, ...) must keep clear, one per tab. Each covers both the tab's current and final
     * size (and position), so their overlays move out of the way once when a tab opens instead of every frame.
     */
    public List<Rect2i> getExclusionAreas() {
        List<Rect2i> areas = new ArrayList<>(tabs.size());
        int areaY = y;
        for (SideTab tab : tabs) {
            int width = Math.max(tab.currentWidth(), tab.targetWidth());
            int height = Math.max(tab.currentHeight(), tab.targetHeight());
            areas.add(new Rect2i(x, areaY, width, height));
            areaY += height + GAP;
        }
        return areas;
    }
}
