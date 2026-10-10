package laseta.bac.client.render;

import java.util.HashSet;
import java.util.OptionalDouble;
import java.util.Set;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.item.TranslocationUpgradeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * While the player holds a linked translocation upgrade, outlines the Translocator it is linked to (if it is in the
 * current dimension), visible through walls. Blue for pull upgrades, orange for push upgrades.
 */
@EventBusSubscriber(modid = BetterAutoCrafter.MODID, value = Dist.CLIENT)
public final class TranslocatorLinkRenderer {
    /** Like vanilla's line render type, but without depth testing so the outline shows through blocks. */
    private static final RenderType LINES_THROUGH_WALLS = RenderType.create(
            BetterAutoCrafter.MODID + ":lines_through_walls",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.0)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));
    /** Slightly bigger than the block so the outline doesn't flicker against its faces. */
    private static final double INFLATE = 0.004;

    private TranslocatorLinkRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // A stage that still has the camera transform applied to the model-view matrix.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        Set<BlockPos> drawn = new HashSet<>();
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof TranslocationUpgradeItem upgrade)) {
                continue;
            }
            GlobalPos link = TranslocationUpgradeItem.getLink(stack);
            if (link == null || !link.dimension().equals(minecraft.level.dimension()) || !drawn.add(link.pos())) {
                continue;
            }
            int color = upgrade.getMode().getColor();
            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(LINES_THROUGH_WALLS), new AABB(link.pos()).inflate(INFLATE),
                    ((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F);
            poseStack.popPose();
        }
        if (!drawn.isEmpty()) {
            buffers.endBatch(LINES_THROUGH_WALLS);
        }
    }
}
