package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public class PortalFieldBlinkRenderer implements BlockEntityRenderer<PortalFieldBlockEntity> {

    private static final float EPSILON = 0.0015F;

    public PortalFieldBlinkRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PortalFieldBlockEntity blockEntity,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        if (!state.hasProperty(PortalFieldBlock.UNSTABLE) || !state.getValue(PortalFieldBlock.UNSTABLE)) return;
        if (blockEntity.getLevel() == null) return;

        float time = blockEntity.getLevel().getGameTime() + partialTick;
        float phase = (blockEntity.getBlockPos().asLong() & 31L) * 0.31F;
        float carrier = 0.5F + 0.5F * Mth.sin(time * 0.9F + phase);
        float strobe = Mth.sin(time * 3.6F + phase * 1.7F) > 0.15F ? 1.0F : 0.22F;
        float intensity = Mth.clamp(carrier * strobe, 0.0F, 1.0F);
        int alphaOuter = (int) (35 + intensity * 185);
        int alphaInner = Math.min(255, alphaOuter + 30);

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        Direction.Axis axis = state.getValue(PortalFieldBlock.AXIS);

        if (axis == Direction.Axis.X) {
            drawPlaneZ(consumer, matrix, 0.5F - EPSILON, alphaOuter, 0.02F);
            drawPlaneZ(consumer, matrix, 0.5F + EPSILON, alphaOuter, 0.02F);
            drawPlaneZ(consumer, matrix, 0.5F - EPSILON, alphaInner, 0.21F);
            drawPlaneZ(consumer, matrix, 0.5F + EPSILON, alphaInner, 0.21F);
        } else {
            drawPlaneX(consumer, matrix, 0.5F - EPSILON, alphaOuter, 0.02F);
            drawPlaneX(consumer, matrix, 0.5F + EPSILON, alphaOuter, 0.02F);
            drawPlaneX(consumer, matrix, 0.5F - EPSILON, alphaInner, 0.21F);
            drawPlaneX(consumer, matrix, 0.5F + EPSILON, alphaInner, 0.21F);
        }
    }

    private static void drawPlaneZ(VertexConsumer consumer, Matrix4f matrix, float z, int alpha, float inset) {
        addQuad(consumer, matrix,
                inset, inset, z,
                1.0F - inset, inset, z,
                1.0F - inset, 1.0F - inset, z,
                inset, 1.0F - inset, z,
                alpha);
    }

    private static void drawPlaneX(VertexConsumer consumer, Matrix4f matrix, float x, int alpha, float inset) {
        addQuad(consumer, matrix,
                x, inset, inset,
                x, 1.0F - inset, inset,
                x, 1.0F - inset, 1.0F - inset,
                x, inset, 1.0F - inset,
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
    }
}
