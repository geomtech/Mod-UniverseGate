package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
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
        Level level = blockEntity.getLevel();
        if (level == null) return;
        if (!state.hasProperty(PortalFrameBlock.ACTIVE)) return;

        boolean active = state.getValue(PortalFrameBlock.ACTIVE);
        if (active && state.hasProperty(PortalFrameBlock.BLINK_ON) && !state.getValue(PortalFrameBlock.BLINK_ON)) return;

        if (!active && !isPartOfCompletedPortal(level, blockEntity.getBlockPos())) return;

        boolean unstable = state.hasProperty(PortalFrameBlock.UNSTABLE) && state.getValue(PortalFrameBlock.UNSTABLE);
        float time = level.getGameTime() + partialTick;
        float pulse;
        float half;
        int alpha;
        int red;
        int green;
        int blue;

        if (!active) {
            float phase = (blockEntity.getBlockPos().asLong() & 31L) * 0.21F;
            pulse = 0.45F + 0.55F * Mth.sin(time * 0.1F + phase);
            half = 0.045F + 0.012F * pulse;
            alpha = (int) (36 + 42 * pulse);
            red = 110;
            green = 220;
            blue = 255;
        } else if (unstable) {
            float phase = (blockEntity.getBlockPos().asLong() & 15L) * 0.47F;
            float strobe = Mth.sin(time * 4.1F + phase) > 0.1F ? 1.0F : 0.25F;
            float wave = 0.5F + 0.5F * Mth.sin(time * 1.35F + phase * 0.6F);
            pulse = Mth.clamp(strobe * (0.55F + 0.45F * wave), 0.0F, 1.0F);
            half = 0.06F + 0.045F * pulse;
            alpha = (int) (120 + 125 * pulse);
            red = 255;
            green = 166;
            blue = 108;
        } else {
            pulse = 0.72F + 0.28F * Mth.sin(time * 0.25F);
            half = 0.06F + 0.02F * pulse;
            alpha = (int) (110 + 80 * pulse);
            red = 150;
            green = 235;
            blue = 255;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        addQuad(consumer, matrix,
                0.5F - half, 0.5F - half, -FACE_EPSILON,
                0.5F + half, 0.5F - half, -FACE_EPSILON,
                0.5F + half, 0.5F + half, -FACE_EPSILON,
                0.5F - half, 0.5F + half, -FACE_EPSILON,
                red, green, blue, alpha);

        addQuad(consumer, matrix,
                0.5F - half, 0.5F - half, 1.0F + FACE_EPSILON,
                0.5F - half, 0.5F + half, 1.0F + FACE_EPSILON,
                0.5F + half, 0.5F + half, 1.0F + FACE_EPSILON,
                0.5F + half, 0.5F - half, 1.0F + FACE_EPSILON,
                red, green, blue, alpha);

        addQuad(consumer, matrix,
                -FACE_EPSILON, 0.5F - half, 0.5F - half,
                -FACE_EPSILON, 0.5F - half, 0.5F + half,
                -FACE_EPSILON, 0.5F + half, 0.5F + half,
                -FACE_EPSILON, 0.5F + half, 0.5F - half,
                red, green, blue, alpha);

        addQuad(consumer, matrix,
                1.0F + FACE_EPSILON, 0.5F - half, 0.5F - half,
                1.0F + FACE_EPSILON, 0.5F + half, 0.5F - half,
                1.0F + FACE_EPSILON, 0.5F + half, 0.5F + half,
                1.0F + FACE_EPSILON, 0.5F - half, 0.5F + half,
                red, green, blue, alpha);

        addQuad(consumer, matrix,
                0.5F - half, -FACE_EPSILON, 0.5F - half,
                0.5F - half, -FACE_EPSILON, 0.5F + half,
                0.5F + half, -FACE_EPSILON, 0.5F + half,
                0.5F + half, -FACE_EPSILON, 0.5F - half,
                red, green, blue, alpha);

        addQuad(consumer, matrix,
                0.5F - half, 1.0F + FACE_EPSILON, 0.5F - half,
                0.5F + half, 1.0F + FACE_EPSILON, 0.5F - half,
                0.5F + half, 1.0F + FACE_EPSILON, 0.5F + half,
                0.5F - half, 1.0F + FACE_EPSILON, 0.5F + half,
                red, green, blue, alpha);
    }

    private static boolean isPartOfCompletedPortal(Level level, BlockPos framePos) {
        BlockPos corePos = findCoreNear(level, framePos, 4, 5);
        if (corePos == null) return false;

        var match = PortalFrameDetector.find(level, corePos);
        if (match.isEmpty()) return false;

        for (BlockPos pos : PortalFrameHelper.collectFrame(match.get(), corePos)) {
            if (pos.equals(framePos)) return true;
        }
        return false;
    }

    private static BlockPos findCoreNear(Level level, BlockPos center, int rXZ, int rY) {
        for (int dy = -rY; dy <= rY; dy++) {
            for (int dx = -rXZ; dx <= rXZ; dx++) {
                for (int dz = -rXZ; dz <= rXZ; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(ModBlocks.PORTAL_CORE)) return p;
                }
            }
        }
        return null;
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
