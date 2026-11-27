package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pièce du modèle 3D du portail, en pixel art : une image (16 px par bloc) extrudée sur une épaisseur.
 *
 * <p>Chaque pixel plein devient un petit volume ; les pixels voisins de même couleur sont fusionnés par
 * rangée pour limiter le nombre de faces. Un pixel peut porter une étiquette ({@code tag}) : sa couleur
 * est alors choisie à chaque image par la couche qui le dessine (lumières selon l'état du portail).</p>
 *
 * <p>Coordonnées en pixels depuis le coin du Core : {@code h} le long du portail, {@code v} vers le haut,
 * {@code z} dans l'épaisseur (0 à 16).</p>
 */
final class PortalGateMesh {

    static final int EMPTY = -1;
    private static final int EDGE_TOP = 0x2E2E2E;
    private static final int EDGE_SIDE = 0x202020;
    private static final int EDGE_BOTTOM = 0x141414;
    private static final int SINGLE_FACE = 3;

    /** Choix de la couleur d'un pixel étiqueté. {@code angle} : angle du pixel autour du centre de rotation. */
    interface Tint {
        int color(int tag, int base, float angle);
    }

    private final float[] quads;   // h0, v0, h1, v1, z0, z1, kind (0 faces, 1 côté vertical, 2 côté horizontal, 3 face seule)
    private final int[] colors;
    private final int[] tags;
    private final float[] angles;
    private final int count;
    private final float pivotH;
    private final float pivotV;

    private PortalGateMesh(float[] quads, int[] colors, int[] tags, float[] angles, int count, float pivotH, float pivotV) {
        this.quads = quads;
        this.colors = colors;
        this.tags = tags;
        this.angles = angles;
        this.count = count;
        this.pivotH = pivotH;
        this.pivotV = pivotV;
    }

    /**
     * @param color  couleur 0xRRGGBB de chaque pixel ({@link #EMPTY} = vide), indexée {@code y * width + x}, y vers le haut
     * @param tag    étiquette de chaque pixel (0 = couleur fixe)
     * @param originH position en pixels (depuis le coin du Core) du pixel x = 0
     * @param originV position en pixels du pixel y = 0
     * @param pivotH  centre de rotation, en pixels depuis le coin du Core
     */
    static PortalGateMesh build(int width, int height, int[] color, int[] tag,
                                int originH, int originV, float z0, float z1, float pivotH, float pivotV) {
        return build(width, height, color, tag, originH, originV, z0, z1, pivotH, pivotV, null);
    }

    /** Two reliefs mounted on the opposite skins. Only their tops and exposed rims are visible. */
    static PortalGateMesh buildFaceRelief(int width, int height, int[] color, int[] tag, int originH, int originV,
                                          float skinZ0, float skinZ1, float relief, float pivotH, float pivotV) {
        return build(width, height, color, tag, originH, originV, skinZ0 - relief, skinZ1 + relief,
                pivotH, pivotV, null, null, relief);
    }

    /** Couleur d'une tranche, découpée en bandes de 1 px dans l'épaisseur. */
    interface EdgePaint {
        /** Renvoie {@link #EDGE_TINT} pour garder la couleur pilotée par l'étiquette du pixel. */
        int color(float arcPx, int slice, int slices, boolean outer, int tag);
    }

    /** Indicates that an empty neighbor belongs to the intact shape and exposes a cut end. */
    interface CutEnd {
        boolean contains(int h, int v);
    }

    private record CutQuad(float h0, float v0, float h1, float v1, float z0, float z1, int kind, int color, int tag) {}

    static final int EDGE_TINT = -2;

    /** Renvoie un marqueur qui étiquette cette bande de tranche avec {@code customTag} (couleur de base blanche). */
    static int edgeTagged(int customTag) {
        return -100 - customTag;
    }

    /**
     * @param edgePaint détail des tranches (null : teinte unie). La tranche est alors découpée en bandes de 1 px
     *                  dans l'épaisseur, colorées selon leur position le long du bord ({@code arcPx}).
     */
    static PortalGateMesh build(int width, int height, int[] color, int[] tag,
                                int originH, int originV, float z0, float z1, float pivotH, float pivotV,
                                EdgePaint edgePaint) {
        return build(width, height, color, tag, originH, originV, z0, z1, pivotH, pivotV, edgePaint, null);
    }

    static PortalGateMesh build(int width, int height, int[] color, int[] tag,
                                int originH, int originV, float z0, float z1, float pivotH, float pivotV,
                                EdgePaint edgePaint, CutEnd cutEnd) {
        return build(width, height, color, tag, originH, originV, z0, z1, pivotH, pivotV, edgePaint, cutEnd, z1 - z0);
    }

    private static PortalGateMesh build(int width, int height, int[] color, int[] tag,
                                        int originH, int originV, float z0, float z1, float pivotH, float pivotV,
                                        EdgePaint edgePaint, CutEnd cutEnd, float sideDepth) {
        float[] q = new float[64];
        int[] c = new int[16];
        int[] t = new int[16];
        float[] a = new float[16];
        int n = 0;
        List<CutQuad> cutQuads = cutEnd == null ? List.of() : new ArrayList<>();

        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                int i = y * width + x;
                if (color[i] == EMPTY) { x++; continue; }
                int run = 1;
                if (tag[i] == 0) {
                    while (x + run < width && color[i + run] == color[i] && tag[i + run] == 0) run++;
                }
                float h0 = originH + x, h1 = h0 + run, v0 = originV + y, v1 = v0 + 1;
                if (n * 7 + 7 > q.length) { q = Arrays.copyOf(q, q.length * 2); c = Arrays.copyOf(c, c.length * 2); t = Arrays.copyOf(t, t.length * 2); a = Arrays.copyOf(a, a.length * 2); }
                put(q, n, h0, v0, h1, v1, z0, z1, 0);
                c[n] = color[i]; t[n] = tag[i]; a[n] = angleOf(h0, h1, v0, v1, pivotH, pivotV); n++;
                x += run;
            }
        }

        // Côtés : seulement là où le pixel voisin est vide.
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (color[i] == EMPTY) continue;
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    boolean open = nx < 0 || ny < 0 || nx >= width || ny >= height || color[ny * width + nx] == EMPTY;
                    if (!open) continue;
                    float h = originH + x, v = originV + y;
                    float eh0, ev0, eh1, ev1;
                    int kind;
                    if (d[0] != 0) {
                        float edge = d[0] < 0 ? h : h + 1;
                        eh0 = edge; ev0 = v; eh1 = edge; ev1 = v + 1; kind = 1;
                    } else {
                        float edge = d[1] < 0 ? v : v + 1;
                        eh0 = h; ev0 = edge; eh1 = h + 1; ev1 = edge; kind = 2;
                    }
                    float angle = angleOf(h, h + 1, v, v + 1, pivotH, pivotV);
                    if (cutEnd != null && cutEnd.contains(originH + nx, originV + ny)) {
                        float radius = (float) Math.hypot(h + 0.5F - pivotH, v + 0.5F - pivotV);
                        int alongX = Math.abs(d[1]), alongY = Math.abs(d[0]);
                        float before = adjacentCutRadius(x - alongX, y - alongY, d, width, height, color,
                                originH, originV, pivotH, pivotV, cutEnd);
                        float after = adjacentCutRadius(x + alongX, y + alongY, d, width, height, color,
                                originH, originV, pivotH, pivotV, cutEnd);
                        cutEnd(cutQuads, eh0, ev0, eh1, ev1, z0, z1, d[0], d[1], kind,
                                radius, before, after, color[i], tag[i]);
                        continue;
                    }
                    if (edgePaint == null) {
                        // Tranches d'une teinte unie selon l'orientation (dessus plus clair) : sinon chaque point
                        // blanc du motif dessinerait une barre blanche sur la tranche.
                        int edgeColor = d[1] > 0 ? EDGE_TOP : d[1] < 0 ? EDGE_BOTTOM : EDGE_SIDE;
                        int walls = sideDepth < z1 - z0 ? 2 : 1;
                        for (int wall = 0; wall < walls; wall++) {
                            if (n * 7 + 7 > q.length) { q = Arrays.copyOf(q, q.length * 2); c = Arrays.copyOf(c, c.length * 2); t = Arrays.copyOf(t, t.length * 2); a = Arrays.copyOf(a, a.length * 2); }
                            float front = wall == 0 ? z0 : z1 - sideDepth;
                            put(q, n, eh0, ev0, eh1, ev1, front, front + sideDepth, kind);
                            c[n] = edgeColor;
                            t[n] = tag[i];
                            a[n++] = angle;
                        }
                        continue;
                    }
                    // Tranche détaillée : bord extérieur si elle est plus loin du pivot que le pixel.
                    float pcH = h + 0.5F - pivotH, pcV = v + 0.5F - pivotV;
                    float ecH = (eh0 + eh1) * 0.5F - pivotH, ecV = (ev0 + ev1) * 0.5F - pivotV;
                    boolean outer = ecH * ecH + ecV * ecV > pcH * pcH + pcV * pcV;
                    float radius = (float) Math.sqrt(ecH * ecH + ecV * ecV);
                    float arcPx = (angle < 0 ? angle + Mth.TWO_PI : angle) * radius;
                    int slices = Math.max(1, Math.round(z1 - z0));
                    float step = (z1 - z0) / slices;
                    for (int sl = 0; sl < slices; sl++) {
                        if (n * 7 + 7 > q.length) { q = Arrays.copyOf(q, q.length * 2); c = Arrays.copyOf(c, c.length * 2); t = Arrays.copyOf(t, t.length * 2); a = Arrays.copyOf(a, a.length * 2); }
                        put(q, n, eh0, ev0, eh1, ev1, z0 + sl * step, z0 + (sl + 1) * step, kind);
                        int col = edgePaint.color(arcPx, sl, slices, outer, tag[i]);
                        if (col == EDGE_TINT) {
                            c[n] = 0; t[n] = tag[i];
                        } else if (col <= -100) {
                            c[n] = 0xFFFFFF; t[n] = -100 - col;
                        } else {
                            c[n] = col; t[n] = 0;
                        }
                        a[n] = angle;
                        n++;
                    }
                }
            }
        }
        if (!cutQuads.isEmpty()) {
            int total = n + cutQuads.size();
            q = Arrays.copyOf(q, total * 7);
            c = Arrays.copyOf(c, total);
            t = Arrays.copyOf(t, total);
            a = Arrays.copyOf(a, total);
            for (CutQuad face : cutQuads) {
                put(q, n, face.h0(), face.v0(), face.h1(), face.v1(), face.z0(), face.z1(), face.kind());
                c[n] = face.color();
                t[n++] = face.tag();
            }
        }
        return new PortalGateMesh(q, c, t, a, n, pivotH, pivotV);
    }

    private static float adjacentCutRadius(int x, int y, int[] direction, int width, int height, int[] color,
                                           int originH, int originV, float pivotH, float pivotV, CutEnd cut) {
        int nx = x + direction[0], ny = y + direction[1];
        if (x < 0 || y < 0 || x >= width || y >= height || color[y * width + x] == EMPTY
                || !cut.contains(originH + nx, originV + ny)
                || (nx >= 0 && ny >= 0 && nx < width && ny < height && color[ny * width + nx] != EMPTY)) {
            return 0.0F;
        }
        return (float) Math.hypot(originH + x + 0.5F - pivotH, originV + y + 0.5F - pivotV);
    }

    /** The outer laminations fold into the cut. The rebate and central pocket sit behind both skins. */
    private static float cutInset(float radius, int layer) {
        if (layer == 0 || radius < PortalGateGeometry.INNER_RADIUS + 0.9F
                || radius > PortalGateGeometry.OUTER_RADIUS - 0.9F) return 0.0F;
        if (layer == 1) return 0.375F;
        if (radius < PortalGateGeometry.INNER_RADIUS + 2.0F
                || radius > PortalGateGeometry.OUTER_RADIUS - 2.0F) return 0.625F;
        return layer == 2 ? 0.875F : 1.5F;
    }

    private static void cutEnd(List<CutQuad> faces, float h0, float v0, float h1, float v1, float z0, float z1,
                               int dh, int dv, int kind, float radius, float before, float after, int skin, int tag) {
        int slices = Math.max(1, Math.round(z1 - z0));
        float thickness = (z1 - z0) / slices;
        float previous = 0.0F;
        for (int slice = 0; slice < slices; slice++) {
            int layer = Math.min(slice, slices - 1 - slice);
            float inset = cutInset(radius, layer);
            float front = z0 + slice * thickness, back = front + thickness;
            float h = -dh * inset, v = -dv * inset;
            int graphite = layer == 1 ? 0x292929 : inset <= 0.625F ? 0x202020 : layer == 2 ? 0x141414 : 0x080808;
            faces.add(new CutQuad(h0 + h, v0 + v, h1 + h, v1 + v, front, back, kind,
                    layer == 0 ? skin : graphite, layer == 0 ? tag : 0));

            if (inset != previous) {
                float nextH = -dh * previous, nextV = -dv * previous;
                faces.add(new CutQuad(h0 + Math.min(h, nextH), v0 + Math.min(v, nextV),
                        h1 + Math.max(h, nextH), v1 + Math.max(v, nextV), front, front, SINGLE_FACE,
                        layer <= 1 ? 0x313131 : 0x1C1C1C, 0));
            }
            cutReturn(faces, h0, v0, dh, dv, front, back, cutInset(before, layer), inset);
            cutReturn(faces, h1, v1, dh, dv, front, back, cutInset(after, layer), inset);
            previous = inset;
        }
    }

    /** Only the deeper of two adjoining pixels exposes a side wall between their cut faces. */
    private static void cutReturn(List<CutQuad> faces, float h, float v, int dh, int dv,
                                  float front, float back, float adjacent, float inset) {
        if (inset <= adjacent) return;
        faces.add(new CutQuad(h - dh * inset, v - dv * inset, h - dh * adjacent, v - dv * adjacent,
                front, back, dh != 0 ? 2 : 1, 0x242424, 0));
    }

    private static void put(float[] q, int n, float h0, float v0, float h1, float v1, float z0, float z1, int kind) {
        int o = n * 7;
        q[o] = h0; q[o + 1] = v0; q[o + 2] = h1; q[o + 3] = v1; q[o + 4] = z0; q[o + 5] = z1; q[o + 6] = kind;
    }

    private static float angleOf(float h0, float h1, float v0, float v1, float ph, float pv) {
        return (float) Mth.atan2((v0 + v1) * 0.5F - pv, (h0 + h1) * 0.5F - ph);
    }

    static int shade(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f), g = (int) (((rgb >> 8) & 0xFF) * f), b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Dessine la pièce tournée de {@code rotationDeg} autour de son pivot, dans le plan du portail.
     *
     * @param xAxis portail aligné sur X (horizontal = x, profondeur = z) ou sur Z (horizontal = z, profondeur = x)
     */
    void render(VertexConsumer consumer, Matrix4f matrix, boolean xAxis, float rotationDeg, Tint tint) {
        render(consumer, matrix, xAxis, rotationDeg, 1.0F, tint);
    }

    void render(VertexConsumer consumer, Matrix4f matrix, boolean xAxis, float rotationDeg, float brightness, Tint tint) {
        float rad = rotationDeg * Mth.DEG_TO_RAD;
        float cos = Mth.cos(rad), sin = Mth.sin(rad);
        for (int n = 0; n < count; n++) {
            int o = n * 7;
            float h0 = quads[o], v0 = quads[o + 1], h1 = quads[o + 2], v1 = quads[o + 3];
            float z0 = quads[o + 4], z1 = quads[o + 5];
            int kind = (int) quads[o + 6];
            int rgb = tags[n] == 0 ? shade(colors[n], brightness) : tint.color(tags[n], colors[n], angles[n] + rad);
            if (kind != 0 && tags[n] != 0) rgb = shade(rgb, 0.85F);
            if (kind == 0 || kind == SINGLE_FACE) {
                // Faces avant et arrière.
                quad(consumer, matrix, xAxis, cos, sin, h0, v0, z0, h1, v0, z0, h1, v1, z0, h0, v1, z0, rgb);
                if (kind == 0) {
                    quad(consumer, matrix, xAxis, cos, sin, h0, v0, z1, h1, v0, z1, h1, v1, z1, h0, v1, z1, rgb);
                }
            } else {
                // Côté : segment (h0, v0) -> (h1, v1) étiré sur l'épaisseur.
                quad(consumer, matrix, xAxis, cos, sin, h0, v0, z0, h1, v1, z0, h1, v1, z1, h0, v0, z1, rgb);
            }
        }
    }

    private void quad(VertexConsumer consumer, Matrix4f matrix, boolean xAxis, float cos, float sin,
                      float ha, float va, float za, float hb, float vb, float zb,
                      float hc, float vc, float zc, float hd, float vd, float zd, int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        vertex(consumer, matrix, xAxis, cos, sin, ha, va, za, r, g, b);
        vertex(consumer, matrix, xAxis, cos, sin, hb, vb, zb, r, g, b);
        vertex(consumer, matrix, xAxis, cos, sin, hc, vc, zc, r, g, b);
        vertex(consumer, matrix, xAxis, cos, sin, hd, vd, zd, r, g, b);
    }

    private void vertex(VertexConsumer consumer, Matrix4f matrix, boolean xAxis, float cos, float sin,
                        float h, float v, float z, int r, int g, int b) {
        float dh = h - pivotH, dv = v - pivotV;
        float rh = (pivotH + dh * cos - dv * sin) / 16.0F;
        float rv = (pivotV + dh * sin + dv * cos) / 16.0F;
        float d = z / 16.0F;
        if (xAxis) {
            consumer.addVertex(matrix, rh, rv, d).setColor(r, g, b, 255);
        } else {
            consumer.addVertex(matrix, d, rv, rh).setColor(r, g, b, 255);
        }
    }

    /**
     * Dessine la pièce d'une seule couleur translucide, sans rotation, sur les deux faces : aperçu holographique
     * (avec un rendu additif, le noir resterait invisible).
     */
    void renderFlat(VertexConsumer consumer, Matrix4f matrix, boolean xAxis, int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float[][] corners = new float[4][3];
        for (int n = 0; n < count; n++) {
            int o = n * 7;
            float h0 = quads[o], v0 = quads[o + 1], h1 = quads[o + 2], v1 = quads[o + 3];
            float z0 = quads[o + 4], z1 = quads[o + 5];
            int kind = (int) quads[o + 6];
            if (kind == 0 || kind == SINGLE_FACE) {
                set(corners, h0, v0, z0, h1, v0, z0, h1, v1, z0, h0, v1, z0);
                flatQuad(consumer, matrix, xAxis, corners, r, g, b, alpha);
                if (kind == 0) {
                    set(corners, h0, v0, z1, h1, v0, z1, h1, v1, z1, h0, v1, z1);
                    flatQuad(consumer, matrix, xAxis, corners, r, g, b, alpha);
                }
            } else {
                set(corners, h0, v0, z0, h1, v1, z0, h1, v1, z1, h0, v0, z1);
                flatQuad(consumer, matrix, xAxis, corners, r, g, b, alpha);
            }
        }
    }

    private static void set(float[][] c, float ha, float va, float za, float hb, float vb, float zb,
                            float hc, float vc, float zc, float hd, float vd, float zd) {
        c[0][0] = ha; c[0][1] = va; c[0][2] = za;
        c[1][0] = hb; c[1][1] = vb; c[1][2] = zb;
        c[2][0] = hc; c[2][1] = vc; c[2][2] = zc;
        c[3][0] = hd; c[3][1] = vd; c[3][2] = zd;
    }

    private static void flatQuad(VertexConsumer consumer, Matrix4f matrix, boolean xAxis, float[][] c,
                                 int r, int g, int b, int alpha) {
        for (int i : new int[]{0, 1, 2, 3, 3, 2, 1, 0}) {
            float h = c[i][0] / 16.0F, v = c[i][1] / 16.0F, z = c[i][2] / 16.0F;
            if (xAxis) consumer.addVertex(matrix, h, v, z).setColor(r, g, b, alpha);
            else consumer.addVertex(matrix, z, v, h).setColor(r, g, b, alpha);
        }
    }

    /** Pavé fixe (sans rotation), en pixels depuis le coin du Core : dessus {@code top}, côtés {@code side}. */
    static void box(VertexConsumer consumer, Matrix4f matrix, boolean xAxis,
                    float h0, float h1, float v0, float v1, float z0, float z1, int top, int side) {
        boxQuad(consumer, matrix, xAxis, h0, v1, z0, h1, v1, z0, h1, v1, z1, h0, v1, z1, top);
        boxQuad(consumer, matrix, xAxis, h0, v0, z0, h1, v0, z0, h1, v0, z1, h0, v0, z1, side);
        boxQuad(consumer, matrix, xAxis, h0, v0, z0, h1, v0, z0, h1, v1, z0, h0, v1, z0, side);
        boxQuad(consumer, matrix, xAxis, h0, v0, z1, h1, v0, z1, h1, v1, z1, h0, v1, z1, side);
        boxQuad(consumer, matrix, xAxis, h0, v0, z0, h0, v0, z1, h0, v1, z1, h0, v1, z0, side);
        boxQuad(consumer, matrix, xAxis, h1, v0, z0, h1, v0, z1, h1, v1, z1, h1, v1, z0, side);
    }

    private static void boxQuad(VertexConsumer consumer, Matrix4f matrix, boolean xAxis,
                                float ha, float va, float za, float hb, float vb, float zb,
                                float hc, float vc, float zc, float hd, float vd, float zd, int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float[][] pts = {{ha, va, za}, {hb, vb, zb}, {hc, vc, zc}, {hd, vd, zd}};
        for (float[] p : pts) {
            float h = p[0] / 16.0F, v = p[1] / 16.0F, z = p[2] / 16.0F;
            if (xAxis) consumer.addVertex(matrix, h, v, z).setColor(r, g, b, 255);
            else consumer.addVertex(matrix, z, v, h).setColor(r, g, b, 255);
        }
    }
}
