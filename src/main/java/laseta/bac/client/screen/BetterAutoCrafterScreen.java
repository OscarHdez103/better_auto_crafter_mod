package laseta.bac.client.screen;

import java.util.List;

import laseta.bac.block.BetterAutoCrafterBlock;
import laseta.bac.client.screen.widget.CraftingSettingsTab;
import laseta.bac.client.screen.widget.SideConfigTab;
import laseta.bac.client.screen.widget.SideTabGroup;
import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.registry.ModBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class BetterAutoCrafterScreen extends AbstractContainerScreen<BetterAutoCrafterMenu> {
    // Vanilla crafting table background: 3x3 grid, arrow and result slot in exactly the positions the menu uses.
    // Replace with "better_auto_crafter:textures/gui/container/better_auto_crafter.png" if you make your own.
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int TAB_OFFSET_Y = 4;

    /** The arrow between the grid and the result, relative to the GUI's top-left corner (used by JEI's click area). */
    public static final int ARROW_X = 90;
    public static final int ARROW_Y = 35;
    public static final int ARROW_WIDTH = 22;
    public static final int ARROW_HEIGHT = 15;

    private SideTabGroup tabs;
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

        tabs = new SideTabGroup(List.of(
                new SideConfigTab(menu, unpowered, facing, font),
                new CraftingSettingsTab(menu, font)));
        tabs.setPosition(leftPos + imageWidth, topPos + TAB_OFFSET_Y);
    }

    /** Areas outside the main GUI that this screen draws on. Used by the JEI / EMI compat to keep their overlays clear. */
    public List<Rect2i> getExtraAreas() {
        return tabs == null ? List.of() : tabs.getExclusionAreas();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!tabs.renderTooltip(graphics, font, mouseX, mouseY)) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        tabs.render(graphics, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tabs.mouseClicked(mouseX, mouseY, button)) {
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
                && !tabs.isMouseOver(mouseX, mouseY);
    }
}
