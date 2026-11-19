package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

public class MeteorologicalCatalystCrystalGlowRenderer implements BlockEntityRenderer<MeteorologicalCatalystBlockEntity> {

    private static final float FACE_EPSILON = 0.0010F;
    private static final float ROD_HALF = 0.5F / 16.0F;
    private static final float ROD_MIN_Y = 4.0F / 16.0F;
    private static final float ROD_MAX_Y = 16.0F / 16.0F;
    private static final float ROD_RADIUS = 7.78F / 16.0F;
    private static final float ROD_START_ANGLE = (float) (Math.PI / 4.0D);
    private static final float ROD_MAX_SPEED = (float) (Math.PI / 9.0D);

    public MeteorologicalCatalystCrystalGlowRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MeteorologicalCatalystBlockEntity blockEntity,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        if (blockEntity.getLevel() == null) return;

        boolean hasCrystal = state.hasProperty(CrystalCondenserBlock.HAS_CRYSTAL)
                && state.getValue(CrystalCondenserBlock.HAS_CRYSTAL);

        float time = blockEntity.getLevel().getGameTime() + partialTick;
        float phase = (blockEntity.getBlockPos().asLong() & 15L) * 0.37F;
        float pulse = 0.70F + 0.30F * (0.5F + 0.5F * Mth.sin(time * 0.20F + phase));
        int alpha = (int) (92 + 98 * pulse);

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        if (hasCrystal) {
            addBox(consumer, matrix, 6.5F / 16.0F, 6.0F / 16.0F, 6.5F / 16.0F, 9.5F / 16.0F, 10.0F / 16.0F, 9.5F / 16.0F, alpha);
            addBox(consumer, matrix, 7.0F / 16.0F, 10.0F / 16.0F, 7.0F / 16.0F, 9.0F / 16.0F, 14.0F / 16.0F, 9.0F / 16.0F, alpha);
            addBox(consumer, matrix, 7.25F / 16.0F, 14.0F / 16.0F, 7.25F / 16.0F, 8.75F / 16.0F, 16.0F / 16.0F, 8.75F / 16.0F, alpha);
        }

        MeteorologicalControllerBlockEntity activeController = findNearbyActiveController(blockEntity);
        float angle = computeRodAngle(activeController, partialTick);
        int rodAlpha = activeController != null ? 220 : 185;
        renderRods(consumer, matrix, angle, rodAlpha);
    }

    @Nullable
    private static MeteorologicalControllerBlockEntity findNearbyActiveController(MeteorologicalCatalystBlockEntity catalyst) {
        if (catalyst.getLevel() == null) return null;
        BlockPos catalystPos = catalyst.getBlockPos();

        for (int dy = -3; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos scanPos = catalystPos.offset(dx, dy, dz);
                    BlockEntity be = catalyst.getLevel().getBlockEntity(scanPos);
                    if (!(be instanceof MeteorologicalControllerBlockEntity controller)) continue;
                    if (controller.isAnimatingCatalyst(catalystPos)) {
                        return controller;
                    }
                }
            }
        }
        return null;
    }

    private static float computeRodAngle(@Nullable MeteorologicalControllerBlockEntity controller,
                                         float partialTick) {
        if (controller == null || !controller.isSequenceActiveForRender()) {
            return 0.0F;
        }

        float ticks = controller.getSequenceTicksForRender() + partialTick;
        float spinup = MeteorologicalControllerBlockEntity.SPINUP_TICKS;
        float acceleration = ROD_MAX_SPEED / spinup;

        float spinupAngle = 0.5F * acceleration * spinup * spinup;
        float beamEnd = MeteorologicalControllerBlockEntity.BEAM_END_TICKS;
        float cruiseDuration = Math.max(0.0F, Math.min(ticks, beamEnd) - spinup);
        float angle = spinupAngle + cruiseDuration * ROD_MAX_SPEED;

        if (ticks <= spinup) {
            return 0.5F * acceleration * ticks * ticks;
        }

        if (ticks <= beamEnd) {
            return angle;
        }

        float spinDownTicks = Math.min(ticks - beamEnd, MeteorologicalControllerBlockEntity.SPINDOWN_TICKS);
        float deceleration = ROD_MAX_SPEED / MeteorologicalControllerBlockEntity.SPINDOWN_TICKS;
        float spinDownAngle = ROD_MAX_SPEED * spinDownTicks - 0.5F * deceleration * spinDownTicks * spinDownTicks;
        return angle + spinDownAngle;
    }

    private static void renderRods(VertexConsumer consumer, Matrix4f matrix, float angle, int alpha) {
        for (int i = 0; i < 4; i++) {
            float a = angle + ROD_START_ANGLE + i * ((float) Math.PI / 2.0F);
            float cx = 0.5F + Mth.cos(a) * ROD_RADIUS;
            float cz = 0.5F + Mth.sin(a) * ROD_RADIUS;

            float minX = cx - ROD_HALF;
            float maxX = cx + ROD_HALF;
            float minZ = cz - ROD_HALF;
            float maxZ = cz + ROD_HALF;

            addBox(consumer, matrix, minX, ROD_MIN_Y, minZ, maxX, ROD_MAX_Y, maxZ, alpha, 235, 194, 66);
            addBox(consumer, matrix, minX, 0.56F, minZ, maxX, 0.64F, maxZ, alpha, 250, 235, 185);
        }
    }

    private static void addBox(VertexConsumer consumer,
                               Matrix4f matrix,
                               float minX,
                               float minY,
                               float minZ,
                               float maxX,
                               float maxY,
                               float maxZ,
                               int alpha) {
        addBox(consumer, matrix, minX, minY, minZ, maxX, maxY, maxZ, alpha, 170, 238, 255);
    }

    private static void addBox(VertexConsumer consumer,
                               Matrix4f matrix,
                               float minX,
                               float minY,
                               float minZ,
                               float maxX,
                               float maxY,
                               float maxZ,
                               int alpha,
                               int red,
                               int green,
                               int blue) {
        addQuad(consumer, matrix, minX, minY, minZ - FACE_EPSILON, maxX, minY, minZ - FACE_EPSILON, maxX, maxY, minZ - FACE_EPSILON, minX, maxY, minZ - FACE_EPSILON, alpha, red, green, blue);
        addQuad(consumer, matrix, minX, minY, maxZ + FACE_EPSILON, minX, maxY, maxZ + FACE_EPSILON, maxX, maxY, maxZ + FACE_EPSILON, maxX, minY, maxZ + FACE_EPSILON, alpha, red, green, blue);
        addQuad(consumer, matrix, minX - FACE_EPSILON, minY, minZ, minX - FACE_EPSILON, minY, maxZ, minX - FACE_EPSILON, maxY, maxZ, minX - FACE_EPSILON, maxY, minZ, alpha, red, green, blue);
        addQuad(consumer, matrix, maxX + FACE_EPSILON, minY, minZ, maxX + FACE_EPSILON, maxY, minZ, maxX + FACE_EPSILON, maxY, maxZ, maxX + FACE_EPSILON, minY, maxZ, alpha, red, green, blue);
        addQuad(consumer, matrix, minX, minY - FACE_EPSILON, minZ, minX, minY - FACE_EPSILON, maxZ, maxX, minY - FACE_EPSILON, maxZ, maxX, minY - FACE_EPSILON, minZ, alpha, red, green, blue);
        addQuad(consumer, matrix, minX, maxY + FACE_EPSILON, minZ, maxX, maxY + FACE_EPSILON, minZ, maxX, maxY + FACE_EPSILON, maxZ, minX, maxY + FACE_EPSILON, maxZ, alpha, red, green, blue);
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
                                int alpha,
                                int red,
                                int green,
                                int blue) {
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
