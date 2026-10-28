package laseta.bac.client.screen.widget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import laseta.bac.item.ItemSettingUpgradeItem;
import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.machine.FaceChannel;
import laseta.bac.machine.MachineOption;
import laseta.bac.machine.MachineTabLayout;
import laseta.bac.machine.MachineUpgradeSlotType;
import laseta.bac.sideconfig.RelativeSide;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Purple tab on the left of the GUI, below the upgrades, for what only this kind of machine has
 * ({@link laseta.bac.machine.MachineFeatures}). Only machines that have any get it, and it looks the same on all of
 * them; {@link MachineTabLayout} says where everything is.
 *
 * <ul>
 *     <li>Options: one icon button each showing the current value, like the settings tab. Left-click selects the next
 *     value, right-click the previous one.</li>
 *     <li>Face channels: the channel's icon above a cube net of the machine's faces. Clicking a face turns it on or off
 *     for the channel; faces that are on are tinted in the channel's colour.</li>
 *     <li>Upgrade slots: real menu slots the screen shows while the tab is open. A configurable one has a box next to it
 *     showing the item its upgrade is set to: clicking it with an item (or dropping one dragged from JEI / EMI on it)
 *     sets the upgrade to a copy of that item (nothing is used up), with an empty hand clears it. While no upgrade
 *     with a setting is installed, the box is a hatched plate instead, so it doesn't look like a slot.</li>
 * </ul>
 *
 * <p>Texts, from {@link MachineText} (so worded per machine): "option.&lt;option&gt;" for the option's name,
 * "option.&lt;option&gt;.&lt;value&gt;" for a value's name and "...&lt;value&gt;.desc" for its explanation,
 * "channel.&lt;channel&gt;" and "channel.&lt;channel&gt;.desc" for a channel; the tab itself uses "machine_tab" and
 * "machine_tab.description".</p>
 */
public class MachineTab extends SideTab {
    private static final Palette PALETTE = new Palette(0xFF1C0F2B, 0xFF6B4499, 0xFF9A78C6, 0xFF48296E);

    private static final int ICON_INSET = (MachineTabLayout.BUTTON_SIZE - 16) / 2;

    private static final int BUTTON_OUTLINE = 0xFF1C0F2B;
    private static final int BUTTON_FILL = 0xFF4E2F75;
    private static final int BUTTON_FILL_HOVER = 0xFF5E3A8A;
    private static final int BUTTON_HIGHLIGHT = 0xFF66449A;
    private static final int BUTTON_SHADOW = 0xFF3A2058;
    private static final int FACE_OFF_OVERLAY = 0x90201030;
    private static final int HOVER_OVERLAY = 0x80FFFFFF;
    private static final int SETTING_FRAME = 0xFFFFC94A;
    private static final int UNAVAILABLE_FRAME = 0xFF3A2058;
    private static final int UNAVAILABLE_FILL = 0xFF2A1840;
    private static final int UNAVAILABLE_STRIPE = 0xFF4A2C70;
    private static final int UNAVAILABLE_STRIPE_SPACING = 4;

    /** Draws the 16x16 icon of one value of one of the machine's options. */
    @FunctionalInterface
    public interface ValueIcons {
        void render(GuiGraphics graphics, MachineOption<?> option, Enum<?> value, int x, int y);
    }

    /** Something in the tab's content, for hovering and clicking. */
    private sealed interface Element {
        record Option(int index) implements Element {
        }

        record ChannelIcon(int index) implements Element {
        }

        record ChannelFace(int index, RelativeSide side) implements Element {
        }

        record UpgradeSlot(int index) implements Element {
        }

        record UpgradeSetting(int index) implements Element {
        }
    }

    private final AutoMachineMenu menu;
    private final MachineText text;
    private final ValueIcons icons;
    private final ItemStack machine;
    private final MachineTabLayout layout;
    private final Direction facing;
    private final Map<RelativeSide, TextureAtlasSprite> faceSprites;
    private int guiLeft;
    private int guiTop;

    /**
     * @param faces state whose model provides the face textures of the channels' nets
     */
    public MachineTab(AutoMachineMenu menu, MachineText text, Font font, ValueIcons icons, BlockState faces, Direction facing) {
        super(text.get("machine_tab"), text.get("machine_tab.description"), PALETTE, font,
                menu.getMachineTabLayout().contentWidth(), menu.getMachineTabLayout().contentHeight());
        this.menu = menu;
        this.text = text;
        this.icons = icons;
        this.machine = new ItemStack(menu.getMachineBlock());
        this.layout = menu.getMachineTabLayout();
        this.facing = facing;
        this.faceSprites = FaceSprites.of(faces, facing);
    }

    /** Top-left corner of the GUI; the layout's positions are relative to it. */
    public void setGuiOrigin(int guiLeft, int guiTop) {
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
    }

    private ScreenRectangle onScreen(MachineTabLayout.Box box) {
        return new ScreenRectangle(guiLeft + box.x(), guiTop + box.y(), box.width(), box.height());
    }

    private static String serializedName(Enum<?> value) {
        return ((StringRepresentable) value).getSerializedName();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Enum<?> selected(MachineOption<?> option) {
        return menu.getOption((MachineOption) option);
    }

    @Override
    protected void renderIcon(GuiGraphics graphics, int x, int y) {
        graphics.renderFakeItem(machine, x, y);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------------------------

    private List<FaceChannel> channels() {
        return menu.getFeatures().channels();
    }

    private List<MachineUpgradeSlotType> upgradeSlots() {
        return menu.getFeatures().upgradeSlots();
    }

    private ScreenRectangle rect(Element element) {
        return onScreen(switch (element) {
            case Element.Option option -> layout.optionButton(option.index());
            case Element.ChannelIcon icon -> layout.channelIcon(icon.index());
            case Element.ChannelFace face -> layout.channelFace(face.index(), face.side());
            case Element.UpgradeSlot slot -> layout.upgradeSlot(slot.index());
            case Element.UpgradeSetting setting -> layout.upgradeSetting(setting.index());
        });
    }

    private List<Element> elements() {
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < menu.getOptionTypes().size(); i++) {
            elements.add(new Element.Option(i));
        }
        for (int i = 0; i < channels().size(); i++) {
            elements.add(new Element.ChannelIcon(i));
            for (RelativeSide side : RelativeSide.values()) {
                elements.add(new Element.ChannelFace(i, side));
            }
        }
        for (int i = 0; i < upgradeSlots().size(); i++) {
            elements.add(new Element.UpgradeSlot(i));
            if (upgradeSlots().get(i).configurable()) {
                elements.add(new Element.UpgradeSetting(i));
            }
        }
        return elements;
    }

    @Nullable
    private Element elementAt(double mouseX, double mouseY) {
        for (Element element : elements()) {
            ScreenRectangle rect = rect(element);
            if (isInside(mouseX, mouseY, rect.left(), rect.top(), rect.width(), rect.height())) {
                return element;
            }
        }
        return null;
    }

    /** The upgrade in a machine upgrade slot, if it can be set to an item. */
    @Nullable
    private ItemStack settingUpgrade(int slotIndex) {
        ItemStack upgrade = menu.getMachineUpgradeSlot(slotIndex).getItem();
        return upgrade.getItem() instanceof ItemSettingUpgradeItem ? upgrade : null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        Element hovered = elementAt(mouseX, mouseY);
        for (Element element : elements()) {
            ScreenRectangle rect = rect(element);
            boolean isHovered = element.equals(hovered);
            switch (element) {
                case Element.Option option -> {
                    drawButtonBackground(graphics, rect, isHovered);
                    MachineOption<?> type = menu.getOptionTypes().get(option.index());
                    icons.render(graphics, type, selected(type), rect.left() + ICON_INSET, rect.top() + ICON_INSET);
                }
                case Element.ChannelIcon icon -> graphics.renderFakeItem(channels().get(icon.index()).icon().get(),
                        rect.left(), rect.top());
                case Element.ChannelFace face -> drawFace(graphics, rect, face, isHovered);
                case Element.UpgradeSlot slot -> {
                    SlotWidgets.drawSlot(graphics, rect.left(), rect.top());
                    if (!menu.getMachineUpgradeSlot(slot.index()).hasItem()) {
                        SlotWidgets.drawGhostItem(graphics, upgradeSlots().get(slot.index()).getIcon(), rect.left(), rect.top());
                    }
                }
                case Element.UpgradeSetting setting -> drawSetting(graphics, rect, setting.index(), isHovered);
            }
        }
    }

    private static void drawButtonBackground(GuiGraphics graphics, ScreenRectangle rect, boolean hovered) {
        int left = rect.left();
        int top = rect.top();
        int right = rect.right();
        int bottom = rect.bottom();
        graphics.fill(left, top, right, bottom, BUTTON_OUTLINE);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, hovered ? BUTTON_FILL_HOVER : BUTTON_FILL);
        graphics.fill(left + 1, top + 1, right - 1, top + 2, BUTTON_HIGHLIGHT);
        graphics.fill(left + 1, top + 2, left + 2, bottom - 1, BUTTON_HIGHLIGHT);
        graphics.fill(left + 2, bottom - 2, right - 1, bottom - 1, BUTTON_SHADOW);
        graphics.fill(right - 2, top + 2, right - 1, bottom - 2, BUTTON_SHADOW);
    }

    /** The block's texture on that face; faces that are off are darkened, faces that are on tinted and framed. */
    private void drawFace(GuiGraphics graphics, ScreenRectangle rect, Element.ChannelFace face, boolean hovered) {
        int left = rect.left();
        int top = rect.top();
        int right = rect.right();
        int bottom = rect.bottom();
        graphics.fill(left, top, right, bottom, BUTTON_OUTLINE);
        graphics.blit(left + 1, top + 1, 0, rect.width() - 2, rect.height() - 2, faceSprites.get(face.side()));
        if (menu.isChannelFaceOn(face.index(), face.side())) {
            int color = channels().get(face.index()).color();
            graphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0x70000000 | color);
            int solid = 0xFF000000 | color;
            graphics.fill(left + 1, top + 1, right - 1, top + 2, solid);
            graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, solid);
            graphics.fill(left + 1, top + 2, left + 2, bottom - 2, solid);
            graphics.fill(right - 2, top + 2, right - 1, bottom - 2, solid);
        } else {
            graphics.fill(left + 1, top + 1, right - 1, bottom - 1, FACE_OFF_OVERLAY);
        }
        if (hovered) {
            graphics.fill(left + 1, top + 1, right - 1, bottom - 1, HOVER_OVERLAY);
        }
    }

    /** The item the installed upgrade is set to, in a gold frame; a hatched plate while no upgrade that takes one is installed. */
    private void drawSetting(GuiGraphics graphics, ScreenRectangle rect, int slotIndex, boolean hovered) {
        ItemStack upgrade = settingUpgrade(slotIndex);
        if (upgrade == null) {
            drawUnavailable(graphics, rect);
            return;
        }
        SlotWidgets.drawColoredSlot(graphics, rect.left(), rect.top(), SETTING_FRAME);
        ItemStack setting = ItemSettingUpgradeItem.getSetting(upgrade);
        if (!setting.isEmpty()) {
            graphics.renderFakeItem(setting, rect.left() + 1, rect.top() + 1);
        }
        if (hovered) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200.0F);
            graphics.fill(rect.left() + 1, rect.top() + 1, rect.right() - 1, rect.bottom() - 1, HOVER_OVERLAY);
            graphics.pose().popPose();
        }
    }

    /** A flat plate with diagonal stripes, darker than the tab: clearly not a slot, nothing to put there. */
    private static void drawUnavailable(GuiGraphics graphics, ScreenRectangle rect) {
        int left = rect.left();
        int top = rect.top();
        graphics.fill(left, top, rect.right(), rect.bottom(), UNAVAILABLE_FRAME);
        graphics.fill(left + 1, top + 1, rect.right() - 1, rect.bottom() - 1, UNAVAILABLE_FILL);
        for (int y = 1; y < rect.height() - 1; y++) {
            for (int x = 1; x < rect.width() - 1; x++) {
                if ((x + y) % UNAVAILABLE_STRIPE_SPACING == 0) {
                    graphics.fill(left + x, top + y, left + x + 1, top + y + 1, UNAVAILABLE_STRIPE);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ghost ingredients (JEI / EMI)
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The setting boxes {@code stack} may be dropped on right now, by upgrade slot index, on screen: only while the tab
     * is fully open, and only those whose installed upgrade can be set to it.
     */
    public Map<Integer, ScreenRectangle> getSettingTargets(ItemStack stack) {
        Map<Integer, ScreenRectangle> targets = new LinkedHashMap<>();
        if (!isFullyOpen() || stack.isEmpty()) {
            return targets;
        }
        for (int i = 0; i < upgradeSlots().size(); i++) {
            ItemStack upgrade = settingUpgrade(i);
            if (upgradeSlots().get(i).configurable() && upgrade != null
                    && ((ItemSettingUpgradeItem) upgrade.getItem()).canBeSetTo(stack)) {
                targets.put(i, rect(new Element.UpgradeSetting(i)));
            }
        }
        return targets;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tooltips and input
    // ---------------------------------------------------------------------------------------------------------------

    @Nullable
    @Override
    protected Hover getContentTooltip(int mouseX, int mouseY) {
        Element element = elementAt(mouseX, mouseY);
        if (element == null) {
            return null;
        }
        List<Component> lines = switch (element) {
            case Element.Option option -> optionTooltip(menu.getOptionTypes().get(option.index()));
            case Element.ChannelIcon icon -> List.of(text.get("channel." + channels().get(icon.index()).name()),
                    text.get("channel." + channels().get(icon.index()).name() + ".desc").withStyle(ChatFormatting.GRAY));
            case Element.ChannelFace face -> faceTooltip(face);
            case Element.UpgradeSlot slot -> {
                // An installed upgrade shows its own tooltip, as a normal slot (handled by the screen).
                if (menu.getMachineUpgradeSlot(slot.index()).hasItem()) {
                    yield null;
                }
                MachineUpgradeSlotType type = upgradeSlots().get(slot.index());
                yield List.of(type.getDisplayName(), type.getDescription().copy().withStyle(ChatFormatting.GRAY));
            }
            case Element.UpgradeSetting setting -> settingTooltip(setting.index());
        };
        return lines == null ? null : new Hover(lines, rect(element));
    }

    private List<Component> optionTooltip(MachineOption<?> option) {
        String key = "option." + option.name() + "." + serializedName(selected(option));
        Component title = Component.translatable("gui.better_auto_crafter.setting.with_value",
                text.get("option." + option.name()), text.get(key).withStyle(ChatFormatting.LIGHT_PURPLE));
        Component hint = Component.translatable(option.values().size() == 2
                ? "gui.better_auto_crafter.setting.hint.toggle"
                : "gui.better_auto_crafter.setting.hint.cycle");
        return List.of(title, text.get(key + ".desc").withStyle(ChatFormatting.GRAY), hint.copy().withStyle(ChatFormatting.DARK_GRAY));
    }

    private List<Component> faceTooltip(Element.ChannelFace face) {
        FaceChannel channel = channels().get(face.index());
        boolean on = menu.isChannelFaceOn(face.index(), face.side());
        Component state = on
                ? Component.translatable("gui.better_auto_crafter.channel.face.on").withStyle(style -> style.withColor(channel.color()))
                : Component.translatable("gui.better_auto_crafter.channel.face.off").withStyle(ChatFormatting.GRAY);
        return List.of(
                Component.translatable("gui.better_auto_crafter.side_config.face", face.side().getDisplayName(),
                        Component.translatable("gui.better_auto_crafter.direction."
                                + face.side().toDirection(facing).getSerializedName())),
                Component.translatable("gui.better_auto_crafter.setting.with_value", text.get("channel." + channel.name()), state),
                Component.translatable("gui.better_auto_crafter.channel.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    private List<Component> settingTooltip(int slotIndex) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.better_auto_crafter.upgrade_setting"));
        ItemStack upgrade = settingUpgrade(slotIndex);
        if (upgrade == null) {
            lines.add(Component.translatable("gui.better_auto_crafter.upgrade_setting.no_upgrade").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        ItemStack setting = ItemSettingUpgradeItem.getSetting(upgrade);
        lines.add(setting.isEmpty()
                ? Component.translatable("gui.better_auto_crafter.upgrade_setting.not_set").withStyle(ChatFormatting.RED)
                : Component.translatable("item.better_auto_crafter.item_setting_upgrade.set_to", setting.getHoverName())
                .withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("gui.better_auto_crafter.upgrade_setting.hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** Clicks on an upgrade slot go to the screen (normal slot handling); anything else in the tab is handled here. */
    @Override
    protected boolean contentClicked(double mouseX, double mouseY, int button) {
        Element element = elementAt(mouseX, mouseY);
        if (element instanceof Element.UpgradeSlot) {
            return false;
        }
        if (element == null || (button != 0 && button != 1)) {
            return true;
        }
        switch (element) {
            case Element.Option option -> {
                MachineOption<?> type = menu.getOptionTypes().get(option.index());
                List<? extends Enum<?>> values = type.values();
                int step = button == 1 ? values.size() - 1 : 1;
                Enum<?> next = values.get((values.indexOf(selected(type)) + step) % values.size());
                sendMenuButton(menu, AutoMachineMenu.selectOptionButton(option.index(), next));
            }
            case Element.ChannelFace face -> sendMenuButton(menu, menu.toggleChannelFaceButton(face.index(), face.side()));
            case Element.UpgradeSetting setting -> sendMenuButton(menu, menu.setUpgradeSettingButton(setting.index()));
            default -> {
            }
        }
        return true;
    }
}
