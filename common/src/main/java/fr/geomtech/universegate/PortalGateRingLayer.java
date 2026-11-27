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
    /** Sous-pixels par pixel du modèle : motif et silhouette en 32 px par bloc. */
    private static final int SUB = 2;
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
        VertexConsumer consumer = GateSolidBuffer.begin(buffer);
        Matrix4f matrix = poseStack.last().pose();
        PortalGateMesh.Tint idle = idleTint(ctx.brightness());
        for (int piece = 0; piece < FRAME_RINGS.length; piece++) {
            if ((mask & (1 << piece)) == 0) continue;
            FRAME_RINGS[piece].render(consumer, matrix, ctx.xAxis(), 0.0F, ctx.brightness(), idle);
            FRAME_CRYSTALS[piece].render(consumer, matrix, ctx.xAxis(), 0.0F, ctx.brightness(), idle);
        }
        PortalGateBrokenJoints.renderDamage(mask, consumer, matrix, ctx.xAxis(), ctx.brightness());
        GateSolidBuffer.end(consumer);
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
        VertexConsumer consumer = GateSolidBuffer.begin(buffer);
        PortalGateMesh.Tint ringTint = (tag, base, angle) -> tag == TAG_CRYSTAL ? accentColor(ctx, base) : lipColor(ctx, angle);
        PortalGateMesh.Tint crystalTint = (tag, base, angle) -> crystalColor(ctx, base);
        RING.render(consumer, poseStack.last().pose(), ctx.xAxis(), ctx.ringAngle(), ctx.brightness(), ringTint);
        CRYSTALS.render(consumer, poseStack.last().pose(), ctx.xAxis(), ctx.ringAngle(), ctx.brightness(), crystalTint);
        smoothLip(ctx, consumer, poseStack.last().pose());
        GateSolidBuffer.end(consumer);
    }

    private static final int LIP_SEGMENTS = 192;
    /** Les sous-pixels dont le centre est à INNER débordent d'environ 0,35 px vers l'intérieur : on les recouvre. */
    private static final float LIP_INNER = INNER_RADIUS - 0.4F;
    /** Couvre aussi les sous-pixels de lèvre qui débordent du rayon nominal (INNER + 1,5). */
    private static final float LIP_OUTER = INNER_RADIUS + 1.8F;
    private static final float LIP_OFFSET = 0.06F;

    /**
     * Lèvre intérieure lisse posée sur l'escalier de voxels : l'horizon des événements suit un cercle net au lieu
     * d'un contour en dents de scie. Faces avant/arrière et paroi intérieure, de la couleur de la lèvre.
     */
    private static void smoothLip(PortalGateRenderContext ctx, VertexConsumer consumer, Matrix4f matrix) {
        boolean xAxis = ctx.xAxis();
        float z0 = RING_Z0 - LIP_OFFSET, z1 = RING_Z1 + LIP_OFFSET;
        for (int i = 0; i < LIP_SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / LIP_SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / LIP_SEGMENTS;
            int rgb = lipColor(ctx, (a0 + a1) * 0.5F);
            int wall = PortalGateMesh.shade(rgb, 0.85F);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            for (float z : new float[]{z0, z1}) {
                lipVertex(consumer, matrix, xAxis, c0 * LIP_INNER, s0 * LIP_INNER, z, rgb);
                lipVertex(consumer, matrix, xAxis, c1 * LIP_INNER, s1 * LIP_INNER, z, rgb);
                lipVertex(consumer, matrix, xAxis, c1 * LIP_OUTER, s1 * LIP_OUTER, z, rgb);
                lipVertex(consumer, matrix, xAxis, c0 * LIP_OUTER, s0 * LIP_OUTER, z, rgb);
            }
            innerWall(ctx, consumer, matrix, i, c0, s0, c1, s1, z0, z1, wall);
        }
    }

    private static final int WALL_SLICES = 7;
    private static final int WALL_PANEL = 8;

    /**
     * Paroi intérieure de l'anneau, en panneaux réguliers : biseaux clairs sur les deux arêtes, une gorge centrale
     * qui s'allume comme la lèvre, et des joints sombres avec deux rivets tous les {@link #WALL_PANEL} segments.
     */
    private static void innerWall(PortalGateRenderContext ctx, VertexConsumer consumer, Matrix4f matrix, int segment,
                                  float c0, float s0, float c1, float s1, float z0, float z1, int groove) {
        float b = ctx.brightness();
        int inPanel = Math.floorMod(segment, WALL_PANEL);
        boolean seam = inPanel == 0;
        boolean rivet = inPanel == WALL_PANEL / 2;
        float step = (z1 - z0) / WALL_SLICES;
        for (int slice = 0; slice < WALL_SLICES; slice++) {
            int edge = Math.min(slice, WALL_SLICES - 1 - slice);
            int rgb;
            if (slice == WALL_SLICES / 2) rgb = seam ? PortalGateMesh.shade(groove, 0.55F) : groove;
            else if (edge == 0) rgb = PortalGateMesh.shade(0x343434, b);
            else if (seam) rgb = 0x000000;
            else if (edge == 1) rgb = PortalGateMesh.shade(rivet ? 0x5A5A5A : 0x1A1A1A, b);
            else rgb = PortalGateMesh.shade(0x101010, b);
            float za = z0 + slice * step, zb = za + step;
            lipVertex(consumer, matrix, ctx.xAxis(), c0 * LIP_INNER, s0 * LIP_INNER, za, rgb);
            lipVertex(consumer, matrix, ctx.xAxis(), c1 * LIP_INNER, s1 * LIP_INNER, za, rgb);
            lipVertex(consumer, matrix, ctx.xAxis(), c1 * LIP_INNER, s1 * LIP_INNER, zb, rgb);
            lipVertex(consumer, matrix, ctx.xAxis(), c0 * LIP_INNER, s0 * LIP_INNER, zb, rgb);
        }
    }

    private static void lipVertex(VertexConsumer consumer, Matrix4f matrix, boolean xAxis,
                                  float dh, float dv, float z, int rgb) {
        float h = (CENTER_H + dh) / 16.0F, v = (CENTER_V + dv) / 16.0F, d = z / 16.0F;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        if (xAxis) consumer.addVertex(matrix, h, v, d).setColor(r, g, b, 255);
        else consumer.addVertex(matrix, d, v, h).setColor(r, g, b, 255);
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
        int n = SIZE * SUB;
        int[] color = new int[n * n];
        int[] tag = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int i = y * n + x;
                float h = CENTER_H - SIZE / 2.0F + (x + 0.5F) / SUB, v = CENTER_V - SIZE / 2.0F + (y + 0.5F) / SUB;
                if (!PortalGateGeometry.ringPoint(h, v)
                        || (segment >= 0 && PortalGateGeometry.segmentAt(h, v) != segment)
                        || (cell != null && !cell.equals(PortalGateGeometry.owner(h, v)))) {
                    color[i] = PortalGateMesh.EMPTY;
                    continue;
                }

                float radius = radius(h, v);
                color[i] = faceColor(h, v, radius);
                if (radius < INNER_RADIUS + 1.5F) tag[i] = TAG_LIP;
                else if (color[i] >= 0x808080) tag[i] = TAG_CRYSTAL;
            }
        }
        PortalGateMesh.CutEnd cutEnd = segment >= 0 || cell != null ? PortalGateGeometry::ringPoint : null;
        return PortalGateMesh.build(n, n, color, tag, (int) CENTER_H - SIZE / 2, (int) CENTER_V - SIZE / 2,
                RING_Z0, RING_Z1, CENTER_H, CENTER_V, PortalGateRingLayer::edgeColor, cutEnd, SUB);
    }

    private static float radius(float h, float v) {
        float dx = h - CENTER_H, dy = v - CENTER_V;
        return Mth.sqrt(dx * dx + dy * dy);
    }

    /** The torn skin continues the adjacent face, including its diagonal veins and unpowered inlays. */
    static int idleFaceColor(int h, int v) {
        int color = faceColor(h + 0.5F, v + 0.5F, radius(h + 0.5F, v + 0.5F));
        return color >= 0x808080 ? PortalGateMesh.shade(color, 0.55F) : color;
    }

    /**
     * Motif du cadre échantillonné en sous-pixels : les veines grises sont interpolées (plus fines et plus lisses),
     * les points blancs restent nets.
     */
    private static int frameSample(float h, float v) {
        float ph = h - CENTER_H + SIZE / 2.0F, pv = v - CENTER_V + SIZE / 2.0F;
        int nearest = PortalGatePatterns.frame((int) Math.floor(ph), (int) Math.floor(pv));
        if (nearest >= 0x80) return nearest;
        float fh = ph - 0.5F, fv = pv - 0.5F;
        int x0 = (int) Math.floor(fh), y0 = (int) Math.floor(fv);
        float th = fh - x0, tv = fv - y0;
        float top = Mth.lerp(th, vein(x0, y0 + 1), vein(x0 + 1, y0 + 1));
        float bottom = Mth.lerp(th, vein(x0, y0), vein(x0 + 1, y0));
        // Paliers de 4 niveaux : garde un rendu pixel art et laisse fusionner les pixels voisins.
        return Math.round(Mth.lerp(tv, bottom, top) / 4.0F) * 4;
    }

    private static float vein(int x, int y) {
        int g = PortalGatePatterns.frame(x, y);
        return g >= 0x80 ? 0.0F : g;
    }

    private static int faceColor(float h, float v, float radius) {
        if (radius < INNER_RADIUS + 1.5F) return 0x3A3A3A;
        int g = frameSample(h, v);
        if (radius > OUTER_RADIUS - 1.5F) g = Math.max(g, 0x2B);
        float a = (float) Mth.atan2(v - CENTER_V, h - CENTER_H) * Mth.RAD_TO_DEG + 30.0F;
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
        int n = SIZE * SUB;
        int[] color = new int[n * n];
        int[] tag = new int[n * n];
        java.util.Arrays.fill(color, PortalGateMesh.EMPTY);
        float mid = (OUTER_RADIUS + INNER_RADIUS) / 2.0F;
        for (float deg : new float[]{90.0F, 210.0F, 330.0F}) {
            float cx = SIZE / 2.0F + Mth.cos(deg * Mth.DEG_TO_RAD) * mid;
            float cy = SIZE / 2.0F + Mth.sin(deg * Mth.DEG_TO_RAD) * mid;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    float lx = (x + 0.5F) / SUB, ly = (y + 0.5F) / SUB;
                    float m = Math.abs(lx - cx) + Math.abs(ly - cy) * 0.8F;
                    if (m > 4.6F) continue;
                    float h = CENTER_H - SIZE / 2.0F + lx, v = CENTER_V - SIZE / 2.0F + ly;
                    if (segment >= 0 && PortalGateGeometry.segmentAt(h, v) != segment) continue;
                    if (cell != null && !cell.equals(PortalGateGeometry.owner(h, v))) continue;
                    // Facettes : cœur blanc, couronne argentée et un liseré clair entre les deux.
                    color[y * n + x] = m < 1.5F ? 0xFFFFFF : m < 2.0F ? 0xE6E6E6 : m < 3.2F ? 0xCDCDCD
                            : m < 3.7F ? 0x4A4A4A : 0x2B2B2B;
                    if (m < 3.2F) tag[y * n + x] = TAG_CRYSTAL;
                }
            }
        }
        return PortalGateMesh.buildFaceRelief(n, n, color, tag, (int) CENTER_H - SIZE / 2, (int) CENTER_V - SIZE / 2,
                RING_Z0, RING_Z1, 1.5F, CENTER_H, CENTER_V, SUB);
    }
}
