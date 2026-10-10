package laseta.bac.client.screen.widget;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import laseta.bac.menu.BetterAutoCrafterMenu;
import laseta.bac.sideconfig.RelativeSide;
import laseta.bac.sideconfig.SideMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Side configuration tab: one button per face of the block, laid out like an unfolded cube seen from the front.
 * Clicking a face cycles its {@link SideMode}.
 */
public class SideConfigTab extends SideTab {
    private static final Component TITLE = Component.translatable("gui.better_auto_crafter.side_config");
    private static final Component DESCRIPTION = Component.translatable("gui.better_auto_crafter.side_config.description");
    private static final Palette PALETTE = new Palette(0xFF0F1B2A, 0xFF3C6C9E, 0xFF6F9DCC, 0xFF2A4D73);

    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_GAP = 2;
    private static final int GRID_SIZE = BUTTON_SIZE * 3 + BUTTON_GAP * 2;
    /** Content width of this tab. Other tabs in the same column use it too, so they all have the same width. */
    public static final int CONTENT_WIDTH = GRID_SIZE + PADDING * 2;

    /** Grid position of each face: rows top to bottom, columns left to right. */
    private static final RelativeSide[][] LAYOUT = {
            {null, RelativeSide.TOP, null},
            {RelativeSide.LEFT, RelativeSide.FRONT, RelativeSide.RIGHT},
            {null, RelativeSide.BOTTOM, RelativeSide.BACK},
    };

    private static final int WELL_DARK = 0xFF1C324A;
    private static final int WELL_LIGHT = 0xFF8FB6DD;
    private static final int WELL_FILL = 0xFF2E5277;
    private static final int HOVER_OVERLAY = 0x80FFFFFF;

    private final BetterAutoCrafterMenu menu;
    private final Direction facing;
    private final Map<RelativeSide, TextureAtlasSprite> faceSprites = new EnumMap<>(RelativeSide.class);

    /**
     * @param blockState state whose model provides the face textures
     */
    public SideConfigTab(BetterAutoCrafterMenu menu, BlockState blockState, Direction facing, Font font) {
        super(TITLE, DESCRIPTION, PALETTE, font, CONTENT_WIDTH, GRID_SIZE);
        this.menu = menu;
        this.facing = facing;
        for (RelativeSide side : RelativeSide.values()) {
            faceSprites.put(side, findFaceSprite(blockState, side.toDirection(facing)));
        }
    }

    @Override
    protected void renderIcon(GuiGraphics graphics, int x, int y) {
        CogIcon.render(graphics, x, y);
    }

    private int buttonX(int column) {
        return x + (expandedWidth() - GRID_SIZE) / 2 + column * (BUTTON_SIZE + BUTTON_GAP);
    }

    private int buttonY(int row) {
        return contentTop() + row * (BUTTON_SIZE + BUTTON_GAP);
    }

    @Nullable
    private RelativeSide sideAt(double mouseX, double mouseY) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                RelativeSide side = LAYOUT[row][column];
                if (side != null && isInside(mouseX, mouseY, buttonX(column), buttonY(row), BUTTON_SIZE, BUTTON_SIZE)) {
                    return side;
                }
            }
        }
        return null;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        RelativeSide hovered = sideAt(mouseX, mouseY);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                RelativeSide side = LAYOUT[row][column];
                if (side != null) {
                    drawFaceButton(graphics, side, buttonX(column), buttonY(row), side == hovered);
                }
            }
        }
    }

    private void drawFaceButton(GuiGraphics graphics, RelativeSide side, int bx, int by, boolean hovered) {
        // Recessed slot-like well
        graphics.fill(bx, by, bx + BUTTON_SIZE - 1, by + 1, WELL_DARK);
        graphics.fill(bx, by + 1, bx + 1, by + BUTTON_SIZE - 1, WELL_DARK);
        graphics.fill(bx + 1, by + BUTTON_SIZE - 1, bx + BUTTON_SIZE, by + BUTTON_SIZE, WELL_LIGHT);
        graphics.fill(bx + BUTTON_SIZE - 1, by + 1, bx + BUTTON_SIZE, by + BUTTON_SIZE - 1, WELL_LIGHT);
        graphics.fill(bx + 1, by + 1, bx + BUTTON_SIZE - 1, by + BUTTON_SIZE - 1, WELL_FILL);

        // The block's real texture on that face
        int ix = bx + 1;
        int iy = by + 1;
        int inner = BUTTON_SIZE - 2;
        graphics.blit(ix, iy, 0, inner, inner, faceSprites.get(side));

        // Mode colour: translucent tint plus a solid inner frame
        SideMode mode = menu.getSideMode(side);
        if (mode != SideMode.NONE) {
            int solid = 0xFF000000 | mode.getColor();
            graphics.fill(ix, iy, ix + inner, iy + inner, 0x70000000 | mode.getColor());
            graphics.fill(ix, iy, ix + inner, iy + 1, solid);
            graphics.fill(ix, iy + inner - 1, ix + inner, iy + inner, solid);
            graphics.fill(ix, iy + 1, ix + 1, iy + inner - 1, solid);
            graphics.fill(ix + inner - 1, iy + 1, ix + inner, iy + inner - 1, solid);
        }

        if (hovered) {
            graphics.fill(ix, iy, ix + inner, iy + inner, HOVER_OVERLAY);
        }
    }

    private ScreenRectangle buttonRect(RelativeSide side) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (LAYOUT[row][column] == side) {
                    return new ScreenRectangle(buttonX(column), buttonY(row), BUTTON_SIZE, BUTTON_SIZE);
                }
            }
        }
        throw new IllegalArgumentException("Side not in layout: " + side);
    }

    @Nullable
    @Override
    protected Hover getContentTooltip(int mouseX, int mouseY) {
        RelativeSide side = sideAt(mouseX, mouseY);
        if (side == null) {
            return null;
        }
        SideMode mode = menu.getSideMode(side);
        Direction direction = side.toDirection(facing);
        return new Hover(List.of(
                Component.translatable("gui.better_auto_crafter.side_config.face", side.getDisplayName(),
                        Component.translatable("gui.better_auto_crafter.direction." + direction.getSerializedName())),
                mode.getDisplayName().copy().withStyle(style -> style.withColor(mode.getColor())),
                mode.getDescription().copy().withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.better_auto_crafter.side_config.hint.next").withStyle(ChatFormatting.DARK_GRAY),
                Component.translatable("gui.better_auto_crafter.side_config.hint.previous").withStyle(ChatFormatting.DARK_GRAY),
                Component.translatable("gui.better_auto_crafter.side_config.hint.reset").withStyle(ChatFormatting.DARK_GRAY)),
                buttonRect(side));
    }

    @Override
    protected boolean contentClicked(double mouseX, double mouseY, int button) {
        RelativeSide side = sideAt(mouseX, mouseY);
        if (side == null || (button != 0 && button != 1)) {
            return true;
        }
        int buttonId;
        if (Screen.hasShiftDown()) {
            buttonId = BetterAutoCrafterMenu.resetModeButton(side);
        } else if (button == 1) {
            buttonId = BetterAutoCrafterMenu.previousModeButton(side);
        } else {
            buttonId = BetterAutoCrafterMenu.nextModeButton(side);
        }
        sendMenuButton(menu, buttonId);
        return true;
    }

    /** Texture actually used by the block model on that face, so resource packs / model changes are reflected. */
    private static TextureAtlasSprite findFaceSprite(BlockState state, Direction direction) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(42L), ModelData.EMPTY, null);
        return quads.isEmpty() ? model.getParticleIcon(ModelData.EMPTY) : quads.getFirst().getSprite();
    }
}
