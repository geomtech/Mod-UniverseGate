package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public class PortalFrameGlowRenderer implements BlockEntityRenderer<PortalFrameBlockEntity> {

    private static final float FACE_EPSILON = 0.0015F;

    public PortalFrameGlowRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PortalFrameBlockEntity blockEntity,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        if (!state.hasProperty(PortalFrameBlock.ACTIVE) || !state.getValue(PortalFrameBlock.ACTIVE)) return;
        if (blockEntity.getLevel() == null) return;
        if (state.hasProperty(PortalFrameBlock.BLINK_ON) && !state.getValue(PortalFrameBlock.BLINK_ON)) return;

        boolean unstable = state.hasProperty(PortalFrameBlock.UNSTABLE) && state.getValue(PortalFrameBlock.UNSTABLE);
        float time = blockEntity.getLevel().getGameTime() + partialTick;
        float pulse;
        float half;
        int alpha;
        if (unstable) {
            float phase = (blockEntity.getBlockPos().asLong() & 15L) * 0.47F;
            float strobe = Mth.sin(time * 4.1F + phase) > 0.1F ? 1.0F : 0.25F;
            float wave = 0.5F + 0.5F * Mth.sin(time * 1.35F + phase * 0.6F);
            pulse = Mth.clamp(strobe * (0.55F + 0.45F * wave), 0.0F, 1.0F);
            half = 0.06F + 0.045F * pulse;
            alpha = (int) (120 + 125 * pulse);
        } else {
            pulse = 0.72F + 0.28F * Mth.sin(time * 0.25F);
            half = 0.06F + 0.02F * pulse;
            alpha = (int) (110 + 80 * pulse);
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        addQuad(consumer, matrix,
                0.5F - half, 0.5F - half, -FACE_EPSILON,
                0.5F + half, 0.5F - half, -FACE_EPSILON,
                0.5F + half, 0.5F + half, -FACE_EPSILON,
                0.5F - half, 0.5F + half, -FACE_EPSILON,
                alpha);

        addQuad(consumer, matrix,
                0.5F - half, 0.5F - half, 1.0F + FACE_EPSILON,
                0.5F - half, 0.5F + half, 1.0F + FACE_EPSILON,
                0.5F + half, 0.5F + half, 1.0F + FACE_EPSILON,
                0.5F + half, 0.5F - half, 1.0F + FACE_EPSILON,
                alpha);

        addQuad(consumer, matrix,
                -FACE_EPSILON, 0.5F - half, 0.5F - half,
                -FACE_EPSILON, 0.5F - half, 0.5F + half,
                -FACE_EPSILON, 0.5F + half, 0.5F + half,
                -FACE_EPSILON, 0.5F + half, 0.5F - half,
                alpha);

        addQuad(consumer, matrix,
                1.0F + FACE_EPSILON, 0.5F - half, 0.5F - half,
                1.0F + FACE_EPSILON, 0.5F + half, 0.5F - half,
                1.0F + FACE_EPSILON, 0.5F + half, 0.5F + half,
                1.0F + FACE_EPSILON, 0.5F - half, 0.5F + half,
                alpha);

        addQuad(consumer, matrix,
                0.5F - half, -FACE_EPSILON, 0.5F - half,
                0.5F - half, -FACE_EPSILON, 0.5F + half,
                0.5F + half, -FACE_EPSILON, 0.5F + half,
                0.5F + half, -FACE_EPSILON, 0.5F - half,
                alpha);

        addQuad(consumer, matrix,
                0.5F - half, 1.0F + FACE_EPSILON, 0.5F - half,
                0.5F + half, 1.0F + FACE_EPSILON, 0.5F - half,
                0.5F + half, 1.0F + FACE_EPSILON, 0.5F + half,
                0.5F - half, 1.0F + FACE_EPSILON, 0.5F + half,
                alpha);
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
                                int alpha) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(255, 255, 255, alpha);
        consumer.addVertex(matrix, x2, y2, z2).setColor(255, 255, 255, alpha);
        consumer.addVertex(matrix, x3, y3, z3).setColor(255, 255, 255, alpha);
        consumer.addVertex(matrix, x4, y4, z4).setColor(255, 255, 255, alpha);

        consumer.addVertex(matrix, x4, y4, z4).setColor(255, 255, 255, alpha);
        consumer.addVertex(matrix, x3, y3, z3).setColor(255, 255, 255, alpha);
        consumer.addVertex(matrix, x2, y2, z2).setColor(255, 255, 255, alpha);
        consumer.addVertex(matrix, x1, y1, z1).setColor(255, 255, 255, alpha);
    }
}
