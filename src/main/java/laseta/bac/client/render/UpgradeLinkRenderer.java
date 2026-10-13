package laseta.bac.client.render;

import java.util.HashSet;
import java.util.OptionalDouble;
import java.util.Set;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.item.LinkingUpgradeItem;
import laseta.bac.item.MultiTranslocationUpgradeItem;
import laseta.bac.upgrade.LinkedFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * While the player holds a linked upgrade, outlines the blocks it is linked to (those in the current dimension),
 * visible through walls, in the upgrade's color: blue for pull upgrades, orange for push upgrades, and the storage
 * link upgrades' own colors. For multi translocation upgrades the linked face of each block is marked too.
 */
@EventBusSubscriber(modid = BetterAutoCrafter.MODID, value = Dist.CLIENT)
public final class UpgradeLinkRenderer {
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
    /** Margin of the square marking a linked face, inside the face's edges. */
    private static final double FACE_INSET = 0.2;
    /** How far the face marker floats off the face. */
    private static final double FACE_OFFSET = 0.02;

    private UpgradeLinkRenderer() {
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

        Set<Object> drawn = new HashSet<>();
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof LinkingUpgradeItem upgrade)) {
                continue;
            }
            int color = upgrade.getLinkColor();
            for (GlobalPos link : upgrade.getLinkedBlocks(stack)) {
                if (link.dimension().equals(minecraft.level.dimension()) && drawn.add(link.pos())) {
                    outline(poseStack, buffers, new AABB(link.pos()).inflate(INFLATE), color);
                }
            }
            for (LinkedFace target : MultiTranslocationUpgradeItem.getTargets(stack)) {
                if (target.pos().dimension().equals(minecraft.level.dimension()) && drawn.add(target)) {
                    outline(poseStack, buffers, faceMarker(target.pos().pos(), target.side()), color);
                }
            }
        }
        poseStack.popPose();
        if (!drawn.isEmpty()) {
            buffers.endBatch(LINES_THROUGH_WALLS);
        }
    }

    private static void outline(PoseStack poseStack, MultiBufferSource buffers, AABB box, int color) {
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(LINES_THROUGH_WALLS), box,
                ((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F);
    }

    /** A flat square just outside the middle of the block's face, so the linked face stands out from the block outline. */
    private static AABB faceMarker(BlockPos pos, Direction side) {
        Vec3 center = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(side.getNormal()).scale(0.5 + FACE_OFFSET));
        double half = 0.5 - FACE_INSET;
        Vec3 extent = new Vec3(side.getAxis() == Direction.Axis.X ? 0 : half, side.getAxis() == Direction.Axis.Y ? 0 : half,
                side.getAxis() == Direction.Axis.Z ? 0 : half);
        return new AABB(center.subtract(extent), center.add(extent));
    }
}
