package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * L'anneau du portail : un anneau noir habillé du motif du cadre (veines grises, points blancs), découpé en
 * six segments, avec trois cristaux de Faille et une lèvre intérieure qui s'illumine selon l'état du portail.
 * Il tourne sur lui-même (angle fourni par {@link PortalGateAnimator}).
 */
final class PortalGateRingLayer implements PortalGateLayer {

    /** Centre de l'anneau, en pixels depuis le coin du Core : milieu de l'ouverture 3x4. */
    static final float CENTER_H = 8.0F;
    static final float CENTER_V = 48.0F;
    static final float OUTER_RADIUS = 47.0F;
    static final float INNER_RADIUS = 35.0F;

    static final int TAG_LIP = 1;

    private static final int SIZE = 96;
    private static final PortalGateMesh RING = buildRing();
    private static final PortalGateMesh CRYSTALS = buildCrystals();

    @Override
    public void render(PortalGateRenderContext ctx, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer consumer = buffer.getBuffer(PortalGateRenderTypes.GATE_SOLID);
        PortalGateMesh.Tint tint = (tag, base, angle) -> lipColor(ctx, angle);
        RING.render(consumer, poseStack.last().pose(), ctx.xAxis(), ctx.ringAngle(), tint);
        CRYSTALS.render(consumer, poseStack.last().pose(), ctx.xAxis(), ctx.ringAngle(), tint);
    }

    /**
     * Lèvre intérieure : grise au repos, se remplit de blanc depuis le bas pendant l'ouverture,
     * blanche tant que le portail est ouvert, crépitante s'il est instable.
     */
    static int lipColor(PortalGateRenderContext ctx, float angle) {
        int idle = 0x3A3A3A;
        if (ctx.unstable()) {
            long h = PortalGatePatterns.hash((long) (angle * 40.0F), ctx.tick() / 2L, 17L);
            return h % 10L < 7L ? 0xFFFFFF : 0x5A5A5A;
        }
        if (ctx.active()) return 0xFFFFFF;
        if (ctx.opening()) {
            float fromBottom = Math.abs(Mth.wrapDegrees((angle + Mth.HALF_PI) * Mth.RAD_TO_DEG)) / 180.0F;
            if (fromBottom <= ctx.openingProgress()) return 0xFFFFFF;
            if (fromBottom <= ctx.openingProgress() + 0.04F) return 0xBBBBBB;
            return idle;
        }
        return idle;
    }

    private static PortalGateMesh buildRing() {
        int[] color = new int[SIZE * SIZE];
        int[] tag = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int i = y * SIZE + x;
                float dx = x + 0.5F - SIZE / 2.0F, dy = y + 0.5F - SIZE / 2.0F;
                float d = Mth.sqrt(dx * dx + dy * dy);
                if (d >= OUTER_RADIUS || d < INNER_RADIUS) { color[i] = PortalGateMesh.EMPTY; continue; }

                if (d < INNER_RADIUS + 1.5F) {
                    color[i] = 0x3A3A3A;
                    tag[i] = TAG_LIP;
                    continue;
                }
                int g = PortalGatePatterns.frame(x, y);
                if (d > OUTER_RADIUS - 1.5F) g = Math.max(g, 0x2B);
                // Six segments : fines jointures sombres tous les 60°.
                float a = (float) Mth.atan2(dy, dx) * Mth.RAD_TO_DEG + 30.0F;
                float toSeam = Math.abs(Mth.wrapDegrees(a - Math.round(a / 60.0F) * 60.0F)) * Mth.DEG_TO_RAD * d;
                if (toSeam < 0.8F) g = 0x14;
                else if (toSeam < 1.8F && g < 0x2B) g = 0x2B;
                color[i] = PortalGatePatterns.gray(g);
            }
        }
        return PortalGateMesh.build(SIZE, SIZE, color, tag, (int) CENTER_H - SIZE / 2, (int) CENTER_V - SIZE / 2,
                3.0F, 13.0F, CENTER_H, CENTER_V);
    }

    /** Trois cristaux de Faille en losange, un peu en relief sur l'anneau (à 90°, 210° et 330°). */
    private static PortalGateMesh buildCrystals() {
        int[] color = new int[SIZE * SIZE];
        int[] tag = new int[SIZE * SIZE];
        java.util.Arrays.fill(color, PortalGateMesh.EMPTY);
        float mid = (OUTER_RADIUS + INNER_RADIUS) / 2.0F;
        for (float deg : new float[]{90.0F, 210.0F, 330.0F}) {
            float cx = SIZE / 2.0F + Mth.cos(deg * Mth.DEG_TO_RAD) * mid;
            float cy = SIZE / 2.0F + Mth.sin(deg * Mth.DEG_TO_RAD) * mid;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    float m = Math.abs(x + 0.5F - cx) + Math.abs(y + 0.5F - cy) * 0.8F;
                    if (m > 4.6F) continue;
                    color[y * SIZE + x] = m < 1.6F ? 0xFFFFFF : m < 3.2F ? 0xCDCDCD : 0x2B2B2B;
                }
            }
        }
        return PortalGateMesh.build(SIZE, SIZE, color, tag, (int) CENTER_H - SIZE / 2, (int) CENTER_V - SIZE / 2,
                1.5F, 14.5F, CENTER_H, CENTER_V);
    }
}
