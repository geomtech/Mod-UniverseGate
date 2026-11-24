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

    /** Épaisseur de l'anneau : 7 px, centrée dans le bloc. */
    static final float RING_Z0 = 4.5F;
    static final float RING_Z1 = 11.5F;

    static final int TAG_LIP = 1;
    static final int TAG_CRYSTAL = 3;

    private static final int SIZE = 96;
    private static final PortalGateMesh RING = buildRing();
    private static final PortalGateMesh CRYSTALS = buildCrystals();

    @Override
    public void render(PortalGateRenderContext ctx, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer consumer = buffer.getBuffer(PortalGateRenderTypes.GATE_SOLID);
        PortalGateMesh.Tint tint = (tag, base, angle) -> tag == TAG_CRYSTAL ? accentColor(ctx, base) : lipColor(ctx, angle);
        RING.render(consumer, poseStack.last().pose(), ctx.xAxis(), ctx.ringAngle(), ctx.brightness(), tint);
        CRYSTALS.render(consumer, poseStack.last().pose(), ctx.xAxis(), ctx.ringAngle(), ctx.brightness(), tint);
    }

    /** Cristaux et points blancs : éteints au repos, plein éclat à l'activation, clignotants pendant la désactivation. */
    private static int accentColor(PortalGateRenderContext ctx, int base) {
        if (ctx.running() || ctx.cooldownBlinkOn()) return base;
        return PortalGateMesh.shade(base, ctx.brightness() * 0.55F);
    }

    /**
     * Lèvre intérieure : grise au repos, se remplit de blanc depuis le bas pendant l'ouverture,
     * blanche tant que le portail est ouvert, crépitante s'il est instable.
     */
    static int lipColor(PortalGateRenderContext ctx, float angle) {
        int idle = PortalGateMesh.shade(0x3A3A3A, ctx.brightness());
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
                if (g >= 0x80) tag[i] = TAG_CRYSTAL;
            }
        }
        return PortalGateMesh.build(SIZE, SIZE, color, tag, (int) CENTER_H - SIZE / 2, (int) CENTER_V - SIZE / 2,
                RING_Z0, RING_Z1, CENTER_H, CENTER_V, PortalGateRingLayer::edgeColor);
    }

    /**
     * Tranches de l'anneau, en noir détaillé : biseaux gris sur les bords, rainure centrale, jointures des six
     * segments ; à l'extérieur des rivets blancs dans la rainure, à l'intérieur une rainure qui s'allume avec la
     * lèvre (même couleur selon l'état du portail).
     */
    private static int edgeColor(float arcPx, int slice, int slices, boolean outer, int tag) {
        int arc = Math.round(arcPx);
        boolean bevel = slice == 0 || slice == slices - 1;
        boolean groove = slice == slices / 2;
        if (outer) {
            // Jointures alignées sur celles de la face (tous les 60°, décalées de 30°).
            float seamSpacing = (float) (OUTER_RADIUS * Math.PI / 3.0D);
            float seamPos = arcPx - seamSpacing / 2.0F;
            float toSeam = Math.abs(seamPos - Math.round(seamPos / seamSpacing) * seamSpacing);
            if (toSeam < 0.6F) return 0x1C1C1C;
            if (bevel) return 0x262626;
            if (groove) return Math.floorMod(arc, 10) == 0 ? PortalGateMesh.edgeTagged(TAG_CRYSTAL) : 0x000000;
            if (Math.floorMod(arc, 10) == 0 && (slice == 1 || slice == slices - 2)) return 0x3A3A3A;
            return 0x0A0A0A;
        }
        if (bevel) return 0x262626;
        if (groove) return tag != 0 ? PortalGateMesh.EDGE_TINT : 0x000000;
        if (Math.floorMod(arc, 8) == 0) return 0x000000; // encoches
        return 0x0A0A0A;
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
                    if (m < 3.2F) tag[y * SIZE + x] = TAG_CRYSTAL;
                }
            }
        }
        return PortalGateMesh.build(SIZE, SIZE, color, tag, (int) CENTER_H - SIZE / 2, (int) CENTER_V - SIZE / 2,
                RING_Z0 - 1.5F, RING_Z1 + 1.5F, CENTER_H, CENTER_V);
    }
}
