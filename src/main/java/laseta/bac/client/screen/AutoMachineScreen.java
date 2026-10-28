package laseta.bac.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import laseta.bac.client.screen.widget.ChoicePopup;
import laseta.bac.client.screen.widget.MachineSettingsTab;
import laseta.bac.client.screen.widget.MachineStatusIcon;
import laseta.bac.client.screen.widget.MachineTab;
import laseta.bac.client.screen.widget.MachineText;
import laseta.bac.client.screen.widget.SideConfigTab;
import laseta.bac.client.screen.widget.SideTab;
import laseta.bac.client.screen.widget.SideTabGroup;
import laseta.bac.client.screen.widget.UpgradesTab;
import laseta.bac.item.LinkingUpgradeItem;
import laseta.bac.machine.AutoMachineBlock;
import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.machine.MachineLayout;
import laseta.bac.machine.MachineOption;
import laseta.bac.machine.RecipeChoices;
import laseta.bac.menu.slot.MachineUpgradeSlot;
import laseta.bac.menu.slot.UpgradeSlot;
import laseta.bac.network.SetUpgradeSettingPayload;
import laseta.bac.upgrade.UpgradeLayout;
import laseta.bac.upgrade.UpgradeStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Screen of an auto machine: a vanilla-style panel with the machine's slots and a status icon in its top right corner
 * ({@link MachineStatusIcon}), the side configuration and settings tabs on the right, and on the left the upgrades tab
 * and, for machines with features of their own, the machine tab. Subclasses
 * can draw their own background ({@link #renderMachineBackground}), give their options icons
 * ({@link #renderOptionIcon}) or add tabs ({@link #createRightTabs}, {@link #createLeftTabs}).
 *
 * <p>When the template makes several recipes, a small button next to the result ({@link #recipeButtonX()}) opens a
 * panel of their results to pick the one the machine makes.</p>
 */
public abstract class AutoMachineScreen<M extends AutoMachineMenu> extends AbstractContainerScreen<M> {
    /**
     * Somewhere on screen an item dragged from JEI / EMI can be dropped, and what dropping it there does.
     *
     * @param area on screen
     */
    public record GhostTarget(Rect2i area, Consumer<ItemStack> drop) {
    }

    // Vanilla GUI colours
    private static final int PANEL_OUTLINE = 0xFF000000;
    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int PANEL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int PANEL_SHADOW = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int AMOUNT_COLOR = 0xFFFFC94A;
    /** Above the slots' items, like their own count. */
    private static final float AMOUNT_Z = 300.0F;
    /** Space between the status icon and the GUI's top and right edges (just inside the panel's bevel). */
    private static final int STATUS_MARGIN = 4;

    /** Side configuration and settings, on the right. */
    private SideTabGroup rightTabs;
    /** Upgrades and the machine's own tab, on the left. */
    private SideTabGroup leftTabs;
    private UpgradesTab upgradesTab;
    @Nullable
    private MachineTab machineTab;
    private MachineStatusIcon statusIcon;
    private MachineText text;
    /** Set when a tab consumed a mouse press, so the matching release isn't handled as a slot click. */
    private boolean ignoreNextRelease;
    /** The panel of recipes (or anything else) to pick from, when open. */
    private final ChoicePopup choicePopup = new ChoicePopup();
    /** The choices the open panel was made from: it closes when they change. */
    @Nullable
    private RecipeChoices popupChoices;

    protected AutoMachineScreen(M menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        MachineLayout layout = menu.getLayout();
        this.imageWidth = layout.width();
        this.imageHeight = layout.height();
        this.inventoryLabelX = layout.inventoryLeft();
        this.inventoryLabelY = layout.inventoryLabelY();
    }

    @Override
    protected void init() {
        super.init();
        BlockState state = minecraft.level.getBlockState(menu.getBlockPos());
        if (!state.is(menu.getMachineBlock())) {
            state = menu.getMachineBlock().defaultBlockState();
        }
        Direction facing = state.getValue(AutoMachineBlock.FACING);
        // Face textures always come from the normal (not running, not failed) model, so they don't change with the state.
        BlockState unpowered = state.setValue(AutoMachineBlock.POWERED, false).setValue(AutoMachineBlock.FAILED, false);
        text = new MachineText(menu.getMachineBlock());
        statusIcon = new MachineStatusIcon(menu, text);

        rightTabs = new SideTabGroup("right", SideTab.Side.RIGHT, createRightTabs(text, unpowered, facing));
        rightTabs.setPosition(leftPos + imageWidth, topPos + UpgradeLayout.TAB_TOP);

        upgradesTab = new UpgradesTab(menu, font);
        upgradesTab.setGuiOrigin(leftPos, topPos);
        List<SideTab> left = new ArrayList<>();
        left.add(upgradesTab);
        machineTab = menu.getFeatures().isEmpty() ? null : new MachineTab(menu, text, font, this::renderOptionIcon, unpowered, facing);
        if (machineTab != null) {
            machineTab.setGuiOrigin(leftPos, topPos);
            left.add(machineTab);
        }
        left.addAll(createLeftTabs(text));
        leftTabs = new SideTabGroup("left", SideTab.Side.LEFT, left);
        leftTabs.setPosition(leftPos, topPos + UpgradeLayout.TAB_TOP);
        // One open tab at a time on the whole screen, whichever side it is on.
        SideTabGroup.linkExclusive(rightTabs, leftTabs);
    }

    /**
     * The tabs on the right of the GUI, top to bottom.
     *
     * @param faces state whose model provides the face textures of the side configuration
     */
    protected List<SideTab> createRightTabs(MachineText text, BlockState faces, Direction facing) {
        return List.of(new SideConfigTab(menu, text, faces, facing, font), new MachineSettingsTab(menu, text, font));
    }

    /**
     * More tabs on the left of the GUI, below the upgrades tab and the machine tab (which stay first, so the slots
     * in them never move). None by default.
     */
    protected List<SideTab> createLeftTabs(MachineText text) {
        return List.of();
    }

    /** GUI texts, worded for this machine where its translations do. */
    protected MachineText text() {
        return text;
    }

    /**
     * Writes how many items a slot takes per operation in its top right corner, small and gold, above its item (call
     * from {@link #renderLabels}).
     */
    protected void drawSlotAmount(GuiGraphics graphics, Slot slot, int amount) {
        final float scale = 0.75F;
        Component label = Component.translatable("gui.better_auto_crafter.amount", amount);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, AMOUNT_Z);
        graphics.pose().scale(scale, scale, 1.0F);
        int textX = (int) ((slot.x + 17) / scale) - font.width(label);  // right-aligned to the slot
        graphics.drawString(font, label, textX, (int) (slot.y / scale), AMOUNT_COLOR, true);
        graphics.pose().popPose();
    }

    /**
     * Left edge of the button offering the template's other recipes, relative to the GUI (next to the result), or -1
     * for machines without recipes. It only shows when there are others.
     */
    protected int recipeButtonX() {
        return -1;
    }

    protected int recipeButtonY() {
        return 0;
    }

    /** Indices of the recipes the button offers: those of the same group as the chosen one (all for most machines). */
    private List<Integer> buttonChoices() {
        RecipeChoices choices = menu.getRecipeChoices();
        RecipeChoices.Choice chosen = choices.chosenChoice();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; chosen != null && i < choices.choices().size(); i++) {
            if (choices.choices().get(i).group() == chosen.group()) {
                indices.add(i);
            }
        }
        return indices;
    }

    private boolean showsRecipeButton() {
        return recipeButtonX() >= 0 && buttonChoices().size() > 1;
    }

    private boolean isOverRecipeButton(double mouseX, double mouseY) {
        return showsRecipeButton() && ChoicePopup.isMouseOverButton(leftPos + recipeButtonX(), topPos + recipeButtonY(), mouseX, mouseY);
    }

    /** Opens the panel of the button's recipes, each shown as its result. */
    private void openRecipePopup() {
        RecipeChoices choices = menu.getRecipeChoices();
        List<ChoicePopup.Option> options = new ArrayList<>();
        for (int index : buttonChoices()) {
            ItemStack result = choices.choices().get(index).result();
            options.add(new ChoicePopup.Option(result, index == choices.chosen(), Screen.getTooltipFromItem(minecraft, result),
                    () -> chooseRecipe(index)));
        }
        openChoicePopup(options, leftPos + recipeButtonX(), topPos + recipeButtonY(), ChoicePopup.BUTTON_SIZE, ChoicePopup.BUTTON_SIZE);
    }

    /** Opens a panel of options to pick from, above the given area of the screen; it closes when the choices change. */
    protected void openChoicePopup(List<ChoicePopup.Option> options, int x, int y, int areaWidth, int areaHeight) {
        popupChoices = menu.getRecipeChoices();
        choicePopup.open(options, x, y, areaWidth, areaHeight, width);
    }

    protected boolean isChoicePopupOpen() {
        return choicePopup.isOpen();
    }

    /** Makes the machine use the recipe at {@code index} of the menu's recipe choices. */
    protected void chooseRecipe(int index) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, menu.chooseRecipeButton(index));
        }
    }

    /** For a mouse press the screen handled itself: its release mustn't be handled as a slot click either. */
    protected boolean consumeClick() {
        ignoreNextRelease = true;
        return true;
    }

    /** Draws the 16x16 icon of a value of one of the machine's options, in the machine tab. Draws nothing by default. */
    protected void renderOptionIcon(GuiGraphics graphics, MachineOption<?> option, Enum<?> value, int x, int y) {
    }

    /** Left edge of the status icon, relative to the GUI. Screens with something else in that corner can move it. */
    protected int statusIconX() {
        return imageWidth - STATUS_MARGIN - MachineStatusIcon.SIZE;
    }

    /** Top edge of the status icon, relative to the GUI. */
    protected int statusIconY() {
        return STATUS_MARGIN;
    }

    /** Areas outside the main GUI that this screen draws on. Used by the JEI / EMI compat to keep their overlays clear. */
    public List<Rect2i> getExtraAreas() {
        if (rightTabs == null) {
            return List.of();
        }
        List<Rect2i> areas = new ArrayList<>(rightTabs.getExclusionAreas());
        areas.addAll(leftTabs.getExclusionAreas());
        return areas;
    }

    /**
     * Where {@code stack}, dragged from JEI / EMI, can be dropped: the setting boxes of the machine tab's upgrades that
     * may be set to it (only while the tab is open). Dropping it sets the upgrade on the server; nothing is given.
     */
    public List<GhostTarget> getGhostTargets(ItemStack stack) {
        if (machineTab == null) {
            return List.of();
        }
        List<GhostTarget> targets = new ArrayList<>();
        machineTab.getSettingTargets(stack).forEach((slotIndex, rect) -> targets.add(new GhostTarget(
                new Rect2i(rect.left(), rect.top(), rect.width(), rect.height()),
                dropped -> PacketDistributor.sendToServer(
                        new SetUpgradeSettingPayload(menu.containerId, slotIndex, dropped.copyWithCount(1))))));
        return targets;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The upgrade slots live in the upgrades tab: only show (and allow clicking) them while it is fully open.
        menu.setUpgradeSlotsVisible(upgradesTab.isFullyOpen());
        // Same for the machine's own upgrade slots, which are only where the layout says once the upgrades tab is closed.
        menu.setMachineUpgradeSlotsVisible(machineTab != null && machineTab.isFullyOpen() && upgradesTab.isFullyClosed());
        if (choicePopup.isOpen() && popupChoices != menu.getRecipeChoices()) {
            choicePopup.close();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        choicePopup.render(graphics, mouseX, mouseY);
        if (choicePopup.isMouseOver(mouseX, mouseY)) {
            choicePopup.renderTooltip(graphics, font, mouseX, mouseY);
            return;
        }
        if (rightTabs.renderTooltip(graphics, font, mouseX, mouseY) || leftTabs.renderTooltip(graphics, font, mouseX, mouseY)) {
            return;
        }
        if (menu.getCarried().isEmpty() && !choicePopup.isOpen() && isOverRecipeButton(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.better_auto_crafter.recipe_choice"), mouseX, mouseY);
            return;
        }
        if (menu.getCarried().isEmpty()
                && MachineStatusIcon.isMouseOver(leftPos + statusIconX(), topPos + statusIconY(), mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, statusIcon.getTooltip(), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderMachineBackground(graphics);
        statusIcon.render(graphics, leftPos + statusIconX(), topPos + statusIconY(), partialTick);
        if (showsRecipeButton()) {
            ChoicePopup.renderButton(graphics, leftPos + recipeButtonX(), topPos + recipeButtonY(),
                    !choicePopup.isOpen() && isOverRecipeButton(mouseX, mouseY));
        }
        rightTabs.render(graphics, font, mouseX, mouseY);
        leftTabs.render(graphics, font, mouseX, mouseY);
    }

    /** The GUI's background: a vanilla-style panel with a frame behind every slot (the tabs draw their own). */
    protected void renderMachineBackground(GuiGraphics graphics) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            if (!(slot instanceof UpgradeSlot) && !(slot instanceof MachineUpgradeSlot)) {
                drawSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1);
            }
        }
    }

    /** Bevelled panel like vanilla's container backgrounds. */
    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x + 1, y, right - 1, y + 1, PANEL_OUTLINE);
        graphics.fill(x + 1, bottom - 1, right - 1, bottom, PANEL_OUTLINE);
        graphics.fill(x, y + 1, x + 1, bottom - 1, PANEL_OUTLINE);
        graphics.fill(right - 1, y + 1, right, bottom - 1, PANEL_OUTLINE);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, PANEL_FILL);
        graphics.fill(x + 1, y + 1, right - 3, y + 3, PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + 3, x + 3, bottom - 3, PANEL_HIGHLIGHT);
        graphics.fill(x + 3, bottom - 3, right - 1, bottom - 1, PANEL_SHADOW);
        graphics.fill(right - 3, y + 3, right - 1, bottom - 3, PANEL_SHADOW);
    }

    /** Recessed 18x18 slot frame at the given top-left corner. */
    protected static void drawSlot(GuiGraphics graphics, int x, int y) {
        int size = MachineLayout.SLOT_SIZE;
        graphics.fill(x, y, x + size - 1, y + 1, SLOT_DARK);
        graphics.fill(x, y + 1, x + 1, y + size - 1, SLOT_DARK);
        graphics.fill(x + 1, y + size - 1, x + size, y + size, SLOT_LIGHT);
        graphics.fill(x + size - 1, y + 1, x + size, y + size - 1, SLOT_LIGHT);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, SLOT_FILL);
    }

    /** Installed upgrades also show whether they work, and why not. */
    @Override
    protected @NotNull List<Component> getTooltipFromContainerItem(@NotNull ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot instanceof UpgradeSlot upgradeSlot) {
            UpgradeStatus status = menu.getUpgradeStatus(upgradeSlot.getType());
            // Only linked upgrades depend on the world; stocking upgrades' own tooltip says what they do.
            if (stack.getItem() instanceof LinkingUpgradeItem && status != UpgradeStatus.EMPTY) {
                tooltip.add(status.getDescription().copy()
                        .withStyle(status.isProblem() ? ChatFormatting.RED : status.isWarning() ? ChatFormatting.GOLD : ChatFormatting.GREEN));
            }
        }
        return tooltip;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (choicePopup.mouseClicked(mouseX, mouseY)) {
            return consumeClick();
        }
        if (menu.getCarried().isEmpty() && isOverRecipeButton(mouseX, mouseY)) {
            openRecipePopup();
            return consumeClick();
        }
        if (rightTabs.mouseClicked(mouseX, mouseY, button) || leftTabs.mouseClicked(mouseX, mouseY, button)) {
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
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, button)
                && !rightTabs.isMouseOver(mouseX, mouseY)
                && !leftTabs.isMouseOver(mouseX, mouseY);
    }
}
