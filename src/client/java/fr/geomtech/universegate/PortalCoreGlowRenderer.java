package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public class PortalCoreGlowRenderer implements BlockEntityRenderer<PortalCoreBlockEntity> {

    private static final float FACE_EPSILON = 0.0015F;

    public PortalCoreGlowRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PortalCoreBlockEntity blockEntity,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) return;

        var match = PortalFrameDetector.find(level, blockEntity.getBlockPos());
        if (match.isEmpty()) return;

        boolean active = false;
        boolean unstable = false;
        for (var framePos : PortalFrameHelper.collectFrame(match.get(), blockEntity.getBlockPos())) {
            BlockState frameState = level.getBlockState(framePos);
            if (!frameState.is(ModBlocks.PORTAL_FRAME)) continue;
            if (frameState.hasProperty(PortalFrameBlock.ACTIVE) && frameState.getValue(PortalFrameBlock.ACTIVE)) {
                active = true;
            }
            if (frameState.hasProperty(PortalFrameBlock.UNSTABLE) && frameState.getValue(PortalFrameBlock.UNSTABLE)) {
                unstable = true;
            }
            if (active && unstable) break;
        }

        float time = level.getGameTime() + partialTick;
        float pulse = unstable
                ? 0.5F + 0.5F * Mth.sin(time * 0.9F)
                : active
                ? 0.5F + 0.5F * Mth.sin(time * 0.25F)
                : 0.5F + 0.5F * Mth.sin(time * 0.08F);

        int red = unstable ? 255 : active ? 112 : 84;
        int green = unstable ? 158 : active ? 235 : 182;
        int blue = unstable ? 102 : active ? 255 : 220;
        int alpha = unstable
                ? Mth.clamp((int) (120 + 110 * pulse), 100, 240)
                : active
                ? Mth.clamp((int) (95 + 80 * pulse), 80, 190)
                : Mth.clamp((int) (46 + 34 * pulse), 40, 90);

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        float halfInner = unstable ? 0.24F + 0.06F * pulse : active ? 0.21F + 0.04F * pulse : 0.18F + 0.02F * pulse;
        drawCube(consumer, matrix,
                0.5F - halfInner,
                0.5F + halfInner,
                0.5F - halfInner,
                0.5F + halfInner,
                0.5F - halfInner,
                0.5F + halfInner,
                red, green, blue, alpha);

        int shellAlpha = unstable
                ? Mth.clamp(alpha - 20, 80, 220)
                : active
                ? Mth.clamp(alpha - 26, 70, 170)
                : Mth.clamp(alpha - 12, 30, 70);
        float shellInset = unstable ? 0.03F : 0.05F;
        drawCube(consumer, matrix,
                shellInset,
                1.0F - shellInset,
                shellInset,
                1.0F - shellInset,
                shellInset,
                1.0F - shellInset,
                red, green, blue, shellAlpha);
    }

    private static void drawCube(VertexConsumer consumer,
                                 Matrix4f matrix,
                                 float x0,
                                 float x1,
                                 float y0,
                                 float y1,
                                 float z0,
                                 float z1,
                                 int red,
                                 int green,
                                 int blue,
                                 int alpha) {
        addQuad(consumer, matrix, x0, y0, z0 - FACE_EPSILON, x1, y0, z0 - FACE_EPSILON, x1, y1, z0 - FACE_EPSILON, x0, y1, z0 - FACE_EPSILON, red, green, blue, alpha);
        addQuad(consumer, matrix, x0, y0, z1 + FACE_EPSILON, x0, y1, z1 + FACE_EPSILON, x1, y1, z1 + FACE_EPSILON, x1, y0, z1 + FACE_EPSILON, red, green, blue, alpha);

        addQuad(consumer, matrix, x0 - FACE_EPSILON, y0, z0, x0 - FACE_EPSILON, y0, z1, x0 - FACE_EPSILON, y1, z1, x0 - FACE_EPSILON, y1, z0, red, green, blue, alpha);
        addQuad(consumer, matrix, x1 + FACE_EPSILON, y0, z0, x1 + FACE_EPSILON, y1, z0, x1 + FACE_EPSILON, y1, z1, x1 + FACE_EPSILON, y0, z1, red, green, blue, alpha);

        addQuad(consumer, matrix, x0, y0 - FACE_EPSILON, z0, x0, y0 - FACE_EPSILON, z1, x1, y0 - FACE_EPSILON, z1, x1, y0 - FACE_EPSILON, z0, red, green, blue, alpha);
        addQuad(consumer, matrix, x0, y1 + FACE_EPSILON, z0, x1, y1 + FACE_EPSILON, z0, x1, y1 + FACE_EPSILON, z1, x0, y1 + FACE_EPSILON, z1, red, green, blue, alpha);
    }

    private static void addQuad(VertexConsumer consumer,
                                Matrix4f matrix,
                                float x1,
                                float y1,
                                float z1,
                                float x2,
                                float y2,
                                float z2,
                                float x3,
                                float y3,
                                float z3,
                                float x4,
                                float y4,
                                float z4,
                                int red,
                                int green,
                                int blue,
                                int alpha) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x3, y3, z3).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x4, y4, z4).setColor(red, green, blue, alpha);

        consumer.addVertex(matrix, x4, y4, z4).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x3, y3, z3).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, alpha);
    }
}
