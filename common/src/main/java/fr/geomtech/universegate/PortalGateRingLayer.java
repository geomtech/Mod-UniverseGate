package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * L'anneau du portail : un anneau noir habillé du motif du cadre (veines grises, points blancs), découpé en
 * six segments, avec trois cristaux de Faille et une lèvre intérieure qui s'illumine selon l'état du portail.
 * Il tourne sur lui-même (angle fourni par {@link PortalGateAnimator}).
 */
final class PortalGateRingLayer implements PortalGateLayer {

    /** Centre de l'anneau, en pixels depuis le coin du Core : milieu de l'ouverture 3x4. */
    static final float CENTER_H = PortalGateGeometry.CENTER_H;
    static final float CENTER_V = PortalGateGeometry.CENTER_V;
    static final float OUTER_RADIUS = PortalGateGeometry.OUTER_RADIUS;
    static final float INNER_RADIUS = PortalGateGeometry.INNER_RADIUS;

    /** Épaisseur de l'anneau : 7 px, centrée dans le bloc. */
    static final float RING_Z0 = PortalGateGeometry.RING_Z0;
    static final float RING_Z1 = PortalGateGeometry.RING_Z1;

    static final int TAG_LIP = 1;
    static final int TAG_CRYSTAL = 3;

    private static final int SIZE = 96;
    private static final PortalGateMesh RING = buildRing(-1, null);
    private static final PortalGateMesh CRYSTALS = buildCrystals(-1, null);
    private static final PortalGateMesh[] SEGMENT_RINGS = new PortalGateMesh[PortalGateGeometry.SEGMENT_COUNT];
    private static final PortalGateMesh[] SEGMENT_CRYSTALS = new PortalGateMesh[PortalGateGeometry.SEGMENT_COUNT];
    private static final PortalGateMesh[] FRAME_RINGS = new PortalGateMesh[PortalGateGeometry.PIECE_COUNT + 1];
    private static final PortalGateMesh[] FRAME_CRYSTALS = new PortalGateMesh[PortalGateGeometry.PIECE_COUNT + 1];

    static {
        for (int segment = 0; segment < PortalGateGeometry.SEGMENT_COUNT; segment++) {
            SEGMENT_RINGS[segment] = buildRing(segment, null);
            SEGMENT_CRYSTALS[segment] = buildCrystals(segment, null);
        }
        for (int piece = 0; piece < FRAME_RINGS.length; piece++) {
            var cell = piece == 0 ? new PortalGateGeometry.Cell(0, 0)
                    : new PortalGateGeometry.Cell(PortalGateGeometry.pieceX(piece), PortalGateGeometry.pieceY(piece));
            FRAME_RINGS[piece] = buildRing(-1, cell);
            FRAME_CRYSTALS[piece] = buildCrystals(-1, cell);
        }
    }

    /** Segment posé d'un portail pas encore assemblé : fixe, lumières éteintes. */
    static void renderSegment(int segment, VertexConsumer consumer, Matrix4f matrix, boolean xAxis, float brightness) {
        PortalGateMesh.Tint idle = idleTint(brightness);
        SEGMENT_RINGS[segment].render(consumer, matrix, xAxis, 0.0F, brightness, idle);
        SEGMENT_CRYSTALS[segment].render(consumer, matrix, xAxis, 0.0F, brightness, idle);
    }

    static void renderDamaged(PortalGateRenderContext ctx, PoseStack poseStack, MultiBufferSource buffer, int mask) {
        VertexConsumer consumer = buffer.getBuffer(PortalGateRenderTypes.GATE_SOLID);
        Matrix4f matrix = poseStack.last().pose();
        PortalGateMesh.Tint idle = idleTint(ctx.brightness());
        for (int piece = 0; piece < FRAME_RINGS.length; piece++) {
            if ((mask & (1 << piece)) == 0) continue;
            FRAME_RINGS[piece].render(consumer, matrix, ctx.xAxis(), 0.0F, ctx.brightness(), idle);
            FRAME_CRYSTALS[piece].render(consumer, matrix, ctx.xAxis(), 0.0F, ctx.brightness(), idle);
        }
        PortalGateBrokenJoints.renderDamage(mask, consumer, matrix, ctx.xAxis(), ctx.brightness());
    }

    private static PortalGateMesh.Tint idleTint(float brightness) {
        return (tag, base, angle) -> tag == TAG_LIP
                ? PortalGateMesh.shade(0x3A3A3A, brightness) : PortalGateMesh.shade(base, brightness * 0.55F);
    }

    /** Aperçu translucide d'un segment (rendu additif). */
    static void renderSegmentPreview(int segment, VertexConsumer consumer, Matrix4f matrix, boolean xAxis,
                                     int rgb, int alpha) {
        SEGMENT_RINGS[segment].renderFlat(consumer, matrix, xAxis, rgb, alpha);
    }

    static void renderRepairPreview(int missing, VertexConsumer consumer, Matrix4f matrix, boolean xAxis,
                                    int rgb, int alpha) {
        for (int piece = 1; piece < FRAME_RINGS.length; piece++) {
            if ((missing & (1 << piece)) != 0) FRAME_RINGS[piece].renderFlat(consumer, matrix, xAxis, rgb, alpha);
        }
    }

    @Override
    public void render(PortalGateRenderContext ctx, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer consumer = buffer.getBuffer(PortalGateRenderTypes.GATE_SOLID);
        PortalGateMesh.Tint ringTint = (tag, base, angle) -> tag == TAG_CRYSTAL ? accentColor(ctx, base) : lipColor(ctx, angle);
        PortalGateMesh.Tint crystalTint = (tag, base, angle) -> crystalColor(ctx, base);
        RING.render(consumer, poseStack.last().pose(), ctx.xAxis(), ctx.ringAngle(), ctx.brightness(), ringTint);
        CRYSTALS.render(consumer, poseStack.last().pose(), ctx.xAxis(), ctx.ringAngle(), ctx.brightness(), crystalTint);
    }

    /** Points blancs de l'anneau : éteints au repos, plein éclat à l'activation, clignotants pendant la désactivation. */
    private static int accentColor(PortalGateRenderContext ctx, int base) {
        if (ctx.running() || ctx.cooldownBlinkOn()) return base;
        return PortalGateMesh.shade(base, ctx.brightness() * 0.55F);
    }

    /** Les 3 cristaux de Faille : clignotent en blanc pendant une instabilité (plus vite avant l'effondrement). */
    private static int crystalColor(PortalGateRenderContext ctx, int base) {
        if (ctx.unstable()) {
            long activeUntil = ctx.core().getClientActiveUntil();
            boolean critical = activeUntil > 0L && (activeUntil - ctx.tick()) <= PortalCinematics.COLLAPSE_WARNING_TICKS;
            long period = critical ? 2L : 4L;
            boolean blinkOn = ((ctx.tick() / period) & 1L) == 0L;
            return blinkOn ? base : PortalGateMesh.shade(base, ctx.brightness() * 0.30F);
        }
        return accentColor(ctx, base);
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

    /** {@code segment} : un seul des six segments, ou -1 pour l'anneau complet. */
    private static PortalGateMesh buildRing(int segment, PortalGateGeometry.Cell cell) {
        int[] color = new int[SIZE * SIZE];
        int[] tag = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int i = y * SIZE + x;
                int h = (int) CENTER_H - SIZE / 2 + x, v = (int) CENTER_V - SIZE / 2 + y;
                if (!PortalGateGeometry.ringPixel(h, v)
                        || (segment >= 0 && PortalGateGeometry.ringSegment(h, v) != segment)
                        || (cell != null && !cell.equals(PortalGateGeometry.owner(h + 0.5D, v + 0.5D)))) {
                    color[i] = PortalGateMesh.EMPTY;
                    continue;
                }

                float radius = radius(h, v);
                color[i] = faceColor(h, v, radius);
                if (radius < INNER_RADIUS + 1.5F) tag[i] = TAG_LIP;
                else if (color[i] >= 0x808080) tag[i] = TAG_CRYSTAL;
            }
        }
        PortalGateMesh.CutEnd cutEnd = segment >= 0 || cell != null ? PortalGateGeometry::ringPixel : null;
        return PortalGateMesh.build(SIZE, SIZE, color, tag, (int) CENTER_H - SIZE / 2, (int) CENTER_V - SIZE / 2,
                RING_Z0, RING_Z1, CENTER_H, CENTER_V, PortalGateRingLayer::edgeColor, cutEnd);
    }

    private static float radius(int h, int v) {
        float dx = h + 0.5F - CENTER_H, dy = v + 0.5F - CENTER_V;
        return Mth.sqrt(dx * dx + dy * dy);
    }

    /** The torn skin continues the adjacent face, including its diagonal veins and unpowered inlays. */
    static int idleFaceColor(int h, int v) {
        int color = faceColor(h, v, radius(h, v));
        return color >= 0x808080 ? PortalGateMesh.shade(color, 0.55F) : color;
    }

    private static int faceColor(int h, int v, float radius) {
        if (radius < INNER_RADIUS + 1.5F) return 0x3A3A3A;
        int g = PortalGatePatterns.frame(h - (int) CENTER_H + SIZE / 2, v - (int) CENTER_V + SIZE / 2);
        if (radius > OUTER_RADIUS - 1.5F) g = Math.max(g, 0x2B);
        float a = (float) Mth.atan2(v + 0.5F - CENTER_V, h + 0.5F - CENTER_H) * Mth.RAD_TO_DEG + 30.0F;
        float toSeam = Math.abs(Mth.wrapDegrees(a - Math.round(a / 60.0F) * 60.0F)) * Mth.DEG_TO_RAD * radius;
        if (toSeam < 0.8F) g = 0x14;
        else if (toSeam < 1.8F && g < 0x2B) g = 0x2B;
        return PortalGatePatterns.gray(g);
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
    private static PortalGateMesh buildCrystals(int segment, PortalGateGeometry.Cell cell) {
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
                    int h = (int) CENTER_H - SIZE / 2 + x, v = (int) CENTER_V - SIZE / 2 + y;
                    if (segment >= 0 && PortalGateGeometry.ringSegment(h, v) != segment) continue;
                    if (cell != null && !cell.equals(PortalGateGeometry.owner(h + 0.5D, v + 0.5D))) continue;
                    color[y * SIZE + x] = m < 1.6F ? 0xFFFFFF : m < 3.2F ? 0xCDCDCD : 0x2B2B2B;
                    if (m < 3.2F) tag[y * SIZE + x] = TAG_CRYSTAL;
                }
            }
        }
        return PortalGateMesh.buildFaceRelief(SIZE, SIZE, color, tag, (int) CENTER_H - SIZE / 2, (int) CENTER_V - SIZE / 2,
                RING_Z0, RING_Z1, 1.5F, CENTER_H, CENTER_V);
    }
}
