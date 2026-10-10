package laseta.bac.client.screen.widget;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/**
 * Expandable tab attached to the side of a container screen (Thermal-style). Collapsed it only shows its icon;
 * clicking it slides open to show its content. Subclasses draw the icon and the content.
 * Tabs are positioned and opened/closed by a {@link SideTabGroup}.
 */
public abstract class SideTab {
    protected static final int COLLAPSED_SIZE = 24;
    protected static final int HEADER_HEIGHT = 24;
    protected static final int ICON_OFFSET = 4;
    protected static final int PADDING = 8;
    private static final long ANIMATION_MS = 160;
    private static final int TITLE_COLOR = 0xFFFFFFFF;

    /** Colours of the tab's background panel (ARGB). */
    public record Palette(int outline, int fill, int highlight, int shadow) {
    }

    /**
     * A tooltip and the element it belongs to. The tooltip is drawn below (or above) that element, never on top of
     * it: the tabs sit near the screen edge, where a normal tooltip would flip sideways over the hovered button.
     */
    public record Hover(List<Component> lines, ScreenRectangle anchor) {
    }

    /** Which edge of the GUI the tab hangs off. Left tabs grow to the left, right tabs to the right. */
    public enum Side {
        LEFT,
        RIGHT
    }

    public enum ClickResult {
        /** The click wasn't on this tab. */
        IGNORED,
        /** The click was on this tab, but the screen should handle it (e.g. it's on a slot shown in the tab). */
        PASS,
        /** The click was on this tab and has been handled (even if it did nothing). */
        CONSUMED,
        /** The header was clicked: the tab opened or closed. */
        TOGGLED
    }

    private final Component title;
    private final Component description;
    private final Palette palette;
    private final int expandedWidth;
    private final int expandedHeight;

    private boolean open;
    private float progress;
    private long lastFrameMillis = Util.getMillis();
    private Side side = Side.RIGHT;
    private int anchorX;
    protected int x;
    protected int y;

    protected SideTab(Component title, Component description, Palette palette, Font font, int contentWidth, int contentHeight) {
        this.title = title;
        this.description = description;
        this.palette = palette;
        this.expandedWidth = Math.max(contentWidth + PADDING * 2, COLLAPSED_SIZE + font.width(title) + PADDING);
        this.expandedHeight = HEADER_HEIGHT + contentHeight + PADDING;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // State and geometry
    // ---------------------------------------------------------------------------------------------------------------

    public boolean isOpen() {
        return open;
    }

    /** @param instant skip the animation (used when restoring the remembered state on screen open) */
    public void setOpen(boolean open, boolean instant) {
        this.open = open;
        if (instant) {
            progress = open ? 1.0F : 0.0F;
        }
    }

    /**
     * @param anchorX the GUI edge the tab is attached to; the tab's left edge for right tabs, its right edge for
     *                left tabs. Call again whenever the width changes (the group does this every frame).
     */
    public void setPosition(Side side, int anchorX, int y) {
        this.side = side;
        this.anchorX = anchorX;
        this.x = side == Side.RIGHT ? anchorX : anchorX - currentWidth();
        this.y = y;
    }

    /** The GUI edge the tab is attached to (see {@link #setPosition}). */
    protected int anchorX() {
        return anchorX;
    }

    public void tickAnimation() {
        long now = Util.getMillis();
        float step = (now - lastFrameMillis) / (float) ANIMATION_MS;
        lastFrameMillis = now;
        progress = Mth.clamp(progress + (open ? step : -step), 0.0F, 1.0F);
    }

    private float easedProgress() {
        float inverse = 1.0F - progress;
        return 1.0F - inverse * inverse * inverse; // ease-out cubic
    }

    public int currentWidth() {
        return Mth.lerpInt(easedProgress(), COLLAPSED_SIZE, expandedWidth);
    }

    public int currentHeight() {
        return Mth.lerpInt(easedProgress(), COLLAPSED_SIZE, expandedHeight);
    }

    /** Size the tab is animating towards. */
    public int targetWidth() {
        return open ? expandedWidth : COLLAPSED_SIZE;
    }

    public int targetHeight() {
        return open ? expandedHeight : COLLAPSED_SIZE;
    }

    protected int expandedWidth() {
        return expandedWidth;
    }

    public boolean isFullyOpen() {
        return open && progress >= 1.0F;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + currentWidth() && mouseY >= y && mouseY < y + currentHeight();
    }

    /** Left edge of the content area (below the header). */
    protected int contentLeft() {
        return x + PADDING;
    }

    protected int contentTop() {
        return y + HEADER_HEIGHT;
    }

    protected int contentWidth() {
        return expandedWidth - PADDING * 2;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------------------------

    public final void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        drawPanel(graphics, x, y, currentWidth(), currentHeight());
        renderIcon(graphics, x + ICON_OFFSET, y + ICON_OFFSET);
        if (isFullyOpen()) {
            graphics.drawString(font, title, x + COLLAPSED_SIZE, y + 8, TITLE_COLOR, true);
            renderContent(graphics, font, mouseX, mouseY);
        }
    }

    /** Draws the 16x16 header icon. */
    protected abstract void renderIcon(GuiGraphics graphics, int x, int y);

    /** Draws the content below the header. Only called while fully open. */
    protected abstract void renderContent(GuiGraphics graphics, Font font, int mouseX, int mouseY);

    /** Tooltip for the content under the mouse, or null. Only called while fully open. */
    @Nullable
    protected abstract Hover getContentTooltip(int mouseX, int mouseY);

    /**
     * Handles a click in the content area. Only called while fully open.
     *
     * @return false to let the screen handle the click instead (e.g. a slot shown in the tab)
     */
    protected abstract boolean contentClicked(double mouseX, double mouseY, int button);

    /** Bevelled panel with rounded corners, same construction as vanilla's GUI panels. */
    private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x + 1, y, right - 1, y + 1, palette.outline());
        graphics.fill(x + 1, bottom - 1, right - 1, bottom, palette.outline());
        graphics.fill(x, y + 1, x + 1, bottom - 1, palette.outline());
        graphics.fill(right - 1, y + 1, right, bottom - 1, palette.outline());

        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, palette.fill());

        graphics.fill(x + 1, y + 1, right - 2, y + 2, palette.highlight());
        graphics.fill(x + 1, y + 2, x + 2, bottom - 2, palette.highlight());
        graphics.fill(x + 2, bottom - 2, right - 1, bottom - 1, palette.shadow());
        graphics.fill(right - 2, y + 2, right - 1, bottom - 2, palette.shadow());
    }

    /** Draws the tooltip for whatever part of the tab is hovered. Returns true if one was drawn. */
    public boolean renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        Hover hover = null;
        if (isFullyOpen()) {
            hover = getContentTooltip(mouseX, mouseY);
        } else if (!open) {
            hover = new Hover(List.of(title, description.copy().withStyle(ChatFormatting.GRAY)),
                    new ScreenRectangle(x, y, currentWidth(), currentHeight()));
        }
        if (hover == null) {
            return false;
        }
        graphics.renderTooltip(font, hover.lines().stream().map(Component::getVisualOrderText).toList(),
                new BelowOrAboveWidgetTooltipPositioner(hover.anchor()), mouseX, mouseY);
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------------------------

    public ClickResult mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) {
            return ClickResult.IGNORED;
        }
        if (isFullyOpen() && mouseY >= y + HEADER_HEIGHT) {
            return contentClicked(mouseX, mouseY, button) ? ClickResult.CONSUMED : ClickResult.PASS;
        }
        if (button == 0) {
            open = !open;
            return ClickResult.TOGGLED;
        }
        return ClickResult.CONSUMED;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers for subclasses
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Applies a menu button locally right away (no visible delay), then lets the server do the same through vanilla's
     * container button packet. The server's value is synced back through the menu's data slots.
     */
    protected static void sendMenuButton(AbstractContainerMenu menu, int buttonId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        if (menu.clickMenuButton(minecraft.player, buttonId)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
            playClickSound();
        }
    }

    public static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    protected static boolean isInside(double mouseX, double mouseY, int left, int top, int width, int height) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }
}
