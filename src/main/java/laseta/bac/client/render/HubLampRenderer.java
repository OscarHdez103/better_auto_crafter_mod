package laseta.bac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.block.entity.BetterAutoHubBlockEntity;
import laseta.bac.machine.AutoMachineBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * Lights the hub's lamps: one per tab on the front of the block, glowing green while that tab's machines run and red
 * when they just failed, like the machines' own blocks show it. The block's texture has them unlit; this only draws
 * the lit ones over it, at full brightness, and covers the locked tabs' ones (lit like the block's front).
 */
public class HubLampRenderer implements BlockEntityRenderer<BetterAutoHubBlockEntity> {
    /** Top-left pixel of each tab's lamp on the front texture (as seen from the front), in tab order. */
    private static final int[][] LAMPS = {{4, 6}, {7, 6}, {10, 6}, {4, 9}, {7, 9}, {10, 9}};
    private static final int LAMP_PIXELS = 2;
    private static final float PIXEL = 1.0F / 16.0F;
    /** In front of the block's face, so the two don't flicker. */
    private static final float OFFSET = 0.0001F;
    private static final ResourceLocation RUNNING = ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "block/hub/lamp_running");
    private static final ResourceLocation FAILED = ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "block/hub/lamp_failed");
    private static final ResourceLocation LOCKED = ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "block/hub/lamp_locked");

    public HubLampRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull BetterAutoHubBlockEntity hub, float partialTick, @NotNull PoseStack pose,
                       @NotNull MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer consumer = null;
        for (int tab = 0; tab < Math.min(BetterAutoHubBlockEntity.TABS, LAMPS.length); tab++) {
            byte lamp = hub.getLamp(tab);
            if (lamp == BetterAutoHubBlockEntity.LAMP_OFF) {
                continue;
            }
            Direction facing = hub.getBlockState().getValue(AutoMachineBlock.FACING);
            if (consumer == null) {
                // Lamps are drawn on the north face, then turned with the block like its model is.
                consumer = buffers.getBuffer(RenderType.text(InventoryMenu.BLOCK_ATLAS));
                pose.pushPose();
                pose.translate(0.5F, 0.0F, 0.5F);
                pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
                pose.translate(-0.5F, 0.0F, -0.5F);
            }
            boolean locked = lamp == BetterAutoHubBlockEntity.LAMP_LOCKED;
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(locked ? LOCKED : lamp == BetterAutoHubBlockEntity.LAMP_FAILED ? FAILED : RUNNING);
            if (locked) {
                // Not glowing: lit and shaded like the front face around it.
                Level level = hub.getLevel();
                int frontLight = level == null ? light : LevelRenderer.getLightColor(level, hub.getBlockPos().relative(facing));
                int shade = level == null ? 255 : Math.round(level.getShade(facing, true) * 255.0F);
                drawLamp(consumer, pose.last().pose(), LAMPS[tab][0], LAMPS[tab][1], sprite, shade, frontLight);
            } else {
                drawLamp(consumer, pose.last().pose(), LAMPS[tab][0], LAMPS[tab][1], sprite, 255, LightTexture.FULL_BRIGHT);
            }
        }
        if (consumer != null) {
            pose.popPose();
        }
    }

    /** A lamp's pixels on the north face; seen from the north, the texture's left is the block's east side. */
    private static void drawLamp(VertexConsumer consumer, Matrix4f matrix, int pixelX, int pixelY, TextureAtlasSprite sprite,
                                 int shade, int light) {
        float left = 1.0F - pixelX * PIXEL;
        float right = 1.0F - (pixelX + LAMP_PIXELS) * PIXEL;
        float top = 1.0F - pixelY * PIXEL;
        float bottom = 1.0F - (pixelY + LAMP_PIXELS) * PIXEL;
        vertex(consumer, matrix, left, top, sprite.getU0(), sprite.getV0(), shade, light);
        vertex(consumer, matrix, left, bottom, sprite.getU0(), sprite.getV1(), shade, light);
        vertex(consumer, matrix, right, bottom, sprite.getU1(), sprite.getV1(), shade, light);
        vertex(consumer, matrix, right, top, sprite.getU1(), sprite.getV0(), shade, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float u, float v, int shade, int light) {
        consumer.addVertex(matrix, x, y, -OFFSET).setColor(shade, shade, shade, 255).setUv(u, v).setLight(light);
    }
}
