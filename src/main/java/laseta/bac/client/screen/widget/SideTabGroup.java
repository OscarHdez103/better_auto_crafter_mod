package laseta.bac.client.screen.widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

/**
 * A column of {@link SideTab}s attached to one side of a screen. Tabs are stacked vertically and move down
 * smoothly when one above them opens. Only one tab per column is open at a time, like Thermal's tabs; columns
 * {@linkplain #linkExclusive linked} together share that rule, so only one tab on the whole screen is open.
 */
public class SideTabGroup {
    private static final int GAP = 2;
    /** Index of the open tab per column, remembered for the whole session so it stays open between screens. */
    private static final Map<String, Integer> REMEMBERED_OPEN_TAB = new HashMap<>();

    private final String id;
    private final SideTab.Side side;
    private final List<SideTab> tabs;
    /** Other columns whose tabs close when one of this column's tabs opens. */
    private final List<SideTabGroup> linked = new ArrayList<>();
    private int anchorX;
    private int y;

    /** @param id identifies the column for remembering which tab was open */
    public SideTabGroup(String id, SideTab.Side side, List<SideTab> tabs) {
        this.id = id;
        this.side = side;
        this.tabs = List.copyOf(tabs);
        int remembered = REMEMBERED_OPEN_TAB.getOrDefault(id, -1);
        if (remembered >= 0 && remembered < this.tabs.size()) {
            this.tabs.get(remembered).setOpen(true, true);
        }
    }

    /**
     * Makes opening a tab in any of {@code groups} close the tabs in all the others. If several of them start with a
     * tab open, only the first one keeps it.
     */
    public static void linkExclusive(SideTabGroup... groups) {
        boolean anyOpen = false;
        for (SideTabGroup group : groups) {
            for (SideTabGroup other : groups) {
                if (other != group) {
                    group.linked.add(other);
                }
            }
            if (anyOpen) {
                group.closeAll(true);
            }
            anyOpen |= group.hasOpenTab();
        }
    }

    private boolean hasOpenTab() {
        return tabs.stream().anyMatch(SideTab::isOpen);
    }

    private void closeAll(boolean instant) {
        for (SideTab tab : tabs) {
            tab.setOpen(false, instant);
        }
        REMEMBERED_OPEN_TAB.put(id, -1);
    }

    /**
     * @param anchorX the GUI edge the column is attached to (the GUI's right edge for right tabs, its left edge for
     *                left tabs)
     * @param y       top of the first tab
     */
    public void setPosition(int anchorX, int y) {
        this.anchorX = anchorX;
        this.y = y;
        layout();
    }

    private void layout() {
        int currentY = y;
        for (SideTab tab : tabs) {
            tab.setPosition(side, anchorX, currentY);
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

    /** @return true if the click was handled by a tab; false if it wasn't on a tab or should reach the screen */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        layout();
        for (int i = 0; i < tabs.size(); i++) {
            SideTab tab = tabs.get(i);
            SideTab.ClickResult result = tab.mouseClicked(mouseX, mouseY, button);
            switch (result) {
                case TOGGLED -> {
                    if (tab.isOpen()) {
                        for (SideTab other : tabs) {
                            if (other != tab) {
                                other.setOpen(false, false);
                            }
                        }
                        for (SideTabGroup group : linked) {
                            group.closeAll(false);
                        }
                    }
                    REMEMBERED_OPEN_TAB.put(id, tab.isOpen() ? i : -1);
                    SideTab.playClickSound();
                    return true;
                }
                case CONSUMED -> {
                    return true;
                }
                case PASS -> {
                    return false;
                }
                case IGNORED -> {
                }
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
            int left = side == SideTab.Side.RIGHT ? anchorX : anchorX - width;
            areas.add(new Rect2i(left, areaY, width, height));
            areaY += height + GAP;
        }
        return areas;
    }
}
