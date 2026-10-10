package laseta.bac.client.screen;

import java.util.ArrayList;
import java.util.List;

import laseta.bac.block.BetterAutoCrafterBlock;
import laseta.bac.client.screen.widget.CraftingSettingsTab;
import laseta.bac.client.screen.widget.SideConfigTab;
import laseta.bac.client.screen.widget.SideTab;
import laseta.bac.client.screen.widget.SideTabGroup;
import laseta.bac.client.screen.widget.UpgradesTab;
import laseta.bac.item.TranslocationUpgradeItem;
import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.menu.slot.UpgradeSlot;
import laseta.bac.registry.ModBlocks;
import laseta.bac.upgrade.UpgradeLayout;
import laseta.bac.upgrade.UpgradeStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class BetterAutoCrafterScreen extends AbstractContainerScreen<BetterAutoCrafterMenu> {
    // Vanilla crafting table background: 3x3 grid, arrow and result slot in exactly the positions the menu uses.
    // Replace with "better_auto_crafter:textures/gui/container/better_auto_crafter.png" if you make your own.
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");

    /** The arrow between the grid and the result, relative to the GUI's top-left corner (used by JEI's click area). */
    public static final int ARROW_X = 90;
    public static final int ARROW_Y = 35;
    public static final int ARROW_WIDTH = 22;
    public static final int ARROW_HEIGHT = 15;

    /** Side configuration and crafting settings, on the right. */
    private SideTabGroup rightTabs;
    /** Upgrades, on the left. */
    private SideTabGroup leftTabs;
    private UpgradesTab upgradesTab;
    /** Set when a tab consumed a mouse press, so the matching release isn't handled as a slot click. */
    private boolean ignoreNextRelease;

    public BetterAutoCrafterScreen(BetterAutoCrafterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 29;

        BlockState state = minecraft.level.getBlockState(menu.getBlockPos());
        if (!state.is(ModBlocks.BETTER_AUTO_CRAFTER.get())) {
            state = ModBlocks.BETTER_AUTO_CRAFTER.get().defaultBlockState();
        }
        Direction facing = state.getValue(BetterAutoCrafterBlock.FACING);
        // Face textures always come from the normal (unpowered, not failed) model, so they don't change with the state.
        BlockState unpowered = state.setValue(BetterAutoCrafterBlock.POWERED, false).setValue(BetterAutoCrafterBlock.FAILED, false);

        rightTabs = new SideTabGroup("right", SideTab.Side.RIGHT, List.of(
                new SideConfigTab(menu, unpowered, facing, font),
                new CraftingSettingsTab(menu, font)));
        rightTabs.setPosition(leftPos + imageWidth, topPos + UpgradeLayout.TAB_TOP);

        upgradesTab = new UpgradesTab(menu, font);
        upgradesTab.setGuiOrigin(leftPos, topPos);
        leftTabs = new SideTabGroup("left", SideTab.Side.LEFT, List.of(upgradesTab));
        leftTabs.setPosition(leftPos, topPos + UpgradeLayout.TAB_TOP);
        // One open tab at a time on the whole screen, whichever side it is on.
        SideTabGroup.linkExclusive(rightTabs, leftTabs);
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

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The upgrade slots live in the upgrades tab: only show (and allow clicking) them while it is fully open.
        menu.setUpgradeSlotsVisible(upgradesTab.isFullyOpen());
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!rightTabs.renderTooltip(graphics, font, mouseX, mouseY) && !leftTabs.renderTooltip(graphics, font, mouseX, mouseY)) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        rightTabs.render(graphics, font, mouseX, mouseY);
        leftTabs.render(graphics, font, mouseX, mouseY);
    }

    /** Installed upgrades also show whether they work, and why not. */
    @Override
    protected @NotNull List<Component> getTooltipFromContainerItem(@NotNull ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot instanceof UpgradeSlot upgradeSlot) {
            UpgradeStatus status = menu.getUpgradeStatus(upgradeSlot.getType());
            // Only translocation upgrades depend on the world; stocking upgrades' own tooltip says what they do.
            if (stack.getItem() instanceof TranslocationUpgradeItem && status != UpgradeStatus.EMPTY) {
                tooltip.add(status.getDescription().copy()
                        .withStyle(status.isProblem() ? ChatFormatting.RED : ChatFormatting.GREEN));
            }
        }
        return tooltip;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
