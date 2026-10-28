package laseta.bac.client.screen.widget;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A small panel of items to pick one from, opened over the GUI like the recipe book's panel of alternative recipes (and
 * drawn with its background): the machines' other recipes, or the smelter's other kinds of recipe. The current one has
 * a gold frame. Clicking an item picks it; clicking anywhere else closes the panel.
 *
 * <p>Also draws the small button that opens it, a vanilla button with an arrow pointing to where the panel opens.</p>
 */
public class ChoicePopup {
    /**
     * One item to pick.
     *
     * @param tooltip shown while hovered
     * @param pick    what picking it does
     */
    public record Option(ItemStack icon, boolean current, List<Component> tooltip, Runnable pick) {
    }

    public static final int BUTTON_SIZE = 11;

    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("recipe_book/overlay_recipe");
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final int MAX_COLUMNS = 6;
    private static final int CELL = 20;
    private static final int PADDING = 4;
    /** Above the GUI's items and their counts. */
    private static final float Z = 400.0F;
    // Vanilla slot colours
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int HOVER = 0x80FFFFFF;
    private static final int CURRENT = 0xFFFFC94A;
    private static final int ARROW = 0xFFFFFFFF;
    private static final int ARROW_SHADOW = 0xFF3F3F3F;

    private List<Option> options = List.of();
    private int x;
    private int y;
    private int width;
    private int height;

    /**
     * Opens the panel above an area of the screen (below it if there is no room above), centered on it.
     *
     * @param screenWidth the panel is kept on screen
     */
    public void open(List<Option> options, int anchorX, int anchorY, int anchorWidth, int anchorHeight, int screenWidth) {
        this.options = List.copyOf(options);
        int columns = Math.min(MAX_COLUMNS, options.size());
        int rows = Mth.positiveCeilDiv(options.size(), MAX_COLUMNS);
        width = columns * CELL + 2 * PADDING - 2;
        height = rows * CELL + 2 * PADDING - 2;
        x = Mth.clamp(anchorX + (anchorWidth - width) / 2, 0, Math.max(0, screenWidth - width));
        y = anchorY - height - 2;
        if (y < 0) {
            y = anchorY + anchorHeight + 2;
        }
    }

    public void close() {
        options = List.of();
    }

    public boolean isOpen() {
        return !options.isEmpty();
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return isOpen() && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isOpen()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, Z);
        graphics.blitSprite(BACKGROUND, x, y, width, height);
        Option hovered = hovered(mouseX, mouseY);
        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            int slotX = slotX(i);
            int slotY = slotY(i);
            if (option.current()) {
                graphics.fill(slotX - 1, slotY - 1, slotX + 19, slotY + 19, CURRENT);
            }
            drawSlot(graphics, slotX, slotY);
            graphics.renderItem(option.icon(), slotX + 1, slotY + 1);
            if (option == hovered) {
                graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, HOVER);
            }
        }
        graphics.pose().popPose();
    }

    /** The hovered option's tooltip; false if none is hovered. */
    public boolean renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        Option hovered = hovered(mouseX, mouseY);
        if (hovered == null || hovered.tooltip().isEmpty()) {
            return false;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, Z);
        graphics.renderComponentTooltip(font, hovered.tooltip(), mouseX, mouseY);
        graphics.pose().popPose();
        return true;
    }

    /**
     * Picks the clicked option, or closes the panel when clicked anywhere else.
     *
     * @return true if the panel was open: then the click was its, nothing under it may react
     */
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!isOpen()) {
            return false;
        }
        Option clicked = hovered(mouseX, mouseY);
        close();
        if (clicked != null && !clicked.current()) {
            clicked.pick().run();
        }
        return true;
    }

    @Nullable
    private Option hovered(double mouseX, double mouseY) {
        for (int i = 0; i < options.size(); i++) {
            int slotX = slotX(i);
            int slotY = slotY(i);
            if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                return options.get(i);
            }
        }
        return null;
    }

    private int slotX(int index) {
        return x + PADDING - 1 + index % MAX_COLUMNS * CELL;
    }

    private int slotY(int index) {
        return y + PADDING - 1 + index / MAX_COLUMNS * CELL;
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 17, y + 1, SLOT_DARK);
        graphics.fill(x, y + 1, x + 1, y + 17, SLOT_DARK);
        graphics.fill(x + 1, y + 17, x + 18, y + 18, SLOT_LIGHT);
        graphics.fill(x + 17, y + 1, x + 18, y + 17, SLOT_LIGHT);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
    }

    /** The button that opens a panel, {@value #BUTTON_SIZE} pixels square, with an arrow pointing up. */
    public static void renderButton(GuiGraphics graphics, int x, int y, boolean hovered) {
        graphics.blitSprite(hovered ? BUTTON_HIGHLIGHTED : BUTTON, x, y, BUTTON_SIZE, BUTTON_SIZE);
        // White with a shadow, like a button's text.
        int centerX = x + BUTTON_SIZE / 2;
        int top = y + 4;
        for (int row = 0; row < 3; row++) {
            graphics.fill(centerX - row + 1, top + row + 1, centerX + row + 2, top + row + 2, ARROW_SHADOW);
        }
        for (int row = 0; row < 3; row++) {
            graphics.fill(centerX - row, top + row, centerX + row + 1, top + row + 1, ARROW);
        }
    }

    public static boolean isMouseOverButton(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
    }
}
