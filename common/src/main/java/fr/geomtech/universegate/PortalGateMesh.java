package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.Arrays;

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

    /** Choix de la couleur d'un pixel étiqueté. {@code angle} : angle du pixel autour du centre de rotation. */
    interface Tint {
        int color(int tag, int base, float angle);
    }

    private final float[] quads;   // par face : h0, v0, h1, v1, z0, z1, kind (0 face, 1 côté vertical, 2 côté horizontal)
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

    /** Couleur d'une tranche, découpée en bandes de 1 px dans l'épaisseur. */
    interface EdgePaint {
        /** Renvoie {@link #EDGE_TINT} pour garder la couleur pilotée par l'étiquette du pixel. */
        int color(float arcPx, int slice, int slices, boolean outer, int tag);
    }

    static final int EDGE_TINT = -2;

    /**
     * @param edgePaint détail des tranches (null : teinte unie). La tranche est alors découpée en bandes de 1 px
     *                  dans l'épaisseur, colorées selon leur position le long du bord ({@code arcPx}).
     */
    static PortalGateMesh build(int width, int height, int[] color, int[] tag,
                                int originH, int originV, float z0, float z1, float pivotH, float pivotV,
                                EdgePaint edgePaint) {
        float[] q = new float[64];
        int[] c = new int[16];
        int[] t = new int[16];
        float[] a = new float[16];
        int n = 0;

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
                    if (edgePaint == null) {
                        if (n * 7 + 7 > q.length) { q = Arrays.copyOf(q, q.length * 2); c = Arrays.copyOf(c, c.length * 2); t = Arrays.copyOf(t, t.length * 2); a = Arrays.copyOf(a, a.length * 2); }
                        put(q, n, eh0, ev0, eh1, ev1, z0, z1, kind);
                        // Tranches d'une teinte unie selon l'orientation (dessus plus clair) : sinon chaque point
                        // blanc du motif dessinerait une barre blanche sur la tranche.
                        c[n] = d[1] > 0 ? EDGE_TOP : d[1] < 0 ? EDGE_BOTTOM : EDGE_SIDE;
                        t[n] = tag[i];
                        a[n] = angle;
                        n++;
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
                        } else {
                            c[n] = col; t[n] = 0;
                        }
                        a[n] = angle;
                        n++;
                    }
                }
            }
        }
        return new PortalGateMesh(q, c, t, a, n, pivotH, pivotV);
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
        float rad = rotationDeg * Mth.DEG_TO_RAD;
        float cos = Mth.cos(rad), sin = Mth.sin(rad);
        for (int n = 0; n < count; n++) {
            int o = n * 7;
            float h0 = quads[o], v0 = quads[o + 1], h1 = quads[o + 2], v1 = quads[o + 3];
            float z0 = quads[o + 4], z1 = quads[o + 5];
            int kind = (int) quads[o + 6];
            int rgb = tags[n] == 0 ? colors[n] : tint.color(tags[n], colors[n], angles[n] + rad);
            if (kind != 0 && tags[n] != 0) rgb = shade(rgb, 0.85F);
            if (kind == 0) {
                // Faces avant et arrière.
                quad(consumer, matrix, xAxis, cos, sin, h0, v0, z0, h1, v0, z0, h1, v1, z0, h0, v1, z0, rgb);
                quad(consumer, matrix, xAxis, cos, sin, h0, v0, z1, h1, v0, z1, h1, v1, z1, h0, v1, z1, rgb);
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
