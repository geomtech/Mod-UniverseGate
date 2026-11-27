package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Membrane d'énergie noire d'un portail ouvert, en pixel art (16 px par bloc), dans l'ouverture ronde de l'anneau.
 *
 * <p>Identité du mod (noir et blanc lumineux) et motif du bloc de cadre : fond noir, fissures grises en
 * diagonale où court parfois une lueur blanche, et carrés blancs 2x2 qui s'allument puis s'éteignent.
 * Un portail instable s'agite plus vite, scintille davantage et sa transparence saute par rangées.</p>
 */
final class PortalMembraneLayer implements PortalGateLayer {

    /** Rayon de la membrane en pixels : elle passe juste sous la lèvre de l'anneau. */
    private static final int RADIUS = 36;
    private static final float DEPTH = 8.0F;

    private static final int[] BLACK = {0, 0, 0};
    private static final int[] FAINT = {20, 20, 20};
    private static final int[] DARK = {43, 43, 43};
    private static final int[] MID = {205, 205, 205};
    private static final int[] WHITE = {255, 255, 255};
    private static final int[] UNSTABLE_ALPHAS = {102, 153, 217, 217};

    @Override
    public void render(PortalGateRenderContext ctx, PoseStack poseStack, MultiBufferSource buffer) {
        // La case au-dessus du cœur peut contenir un câble même lorsque le champ est actif.
        if (!ctx.active()) return;

        boolean unstable = ctx.unstable();
        boolean composited = fr.geomtech.universegate.seamless.client.PortalStencilRenderer.replacesMembrane(ctx.corePos());
        // The compositor draws its own veil. Keep the unstable membrane flashes.
        if (composited && !unstable) return;

        long tick = ctx.tick();
        float time = ctx.time() * (unstable ? 2.4F : 1.0F);
        float phase = (PortalGatePatterns.hash(ctx.corePos().asLong(), 3L, 5L) % 628L) / 100.0F;
        boolean xAxis = ctx.xAxis();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(PortalFieldBlinkRenderer.PORTAL_OCCLUSION_RENDER_TYPE);

        int originH = (int) PortalGateRingLayer.CENTER_H - RADIUS;
        int originV = (int) PortalGateRingLayer.CENTER_V - RADIUS;
        for (int py = 0; py < RADIUS * 2; py++) {
            int rowAlpha = unstable ? UNSTABLE_ALPHAS[(int) (PortalGatePatterns.hash(py, tick / 3L, 7L) % UNSTABLE_ALPHAS.length)] : 217;
            float dy = py + 0.5F - RADIUS;
            for (int px = 0; px < RADIUS * 2; px++) {
                float dx = px + 0.5F - RADIUS;
                if (dx * dx + dy * dy > RADIUS * RADIUS) continue;
                int[] color = pixelColor(px, py, time, tick, phase, unstable);
                if (composited && color != WHITE && color != MID) continue;
                int alpha = color == WHITE || color == MID ? 255 : rowAlpha;
                float h0 = (originH + px) / 16.0F, h1 = (originH + px + 1) / 16.0F;
                float v0 = (originV + py) / 16.0F, v1 = (originV + py + 1) / 16.0F;
                vertex(consumer, matrix, xAxis, h0, v0, color, alpha);
                vertex(consumer, matrix, xAxis, h1, v0, color, alpha);
                vertex(consumer, matrix, xAxis, h1, v1, color, alpha);
                vertex(consumer, matrix, xAxis, h0, v1, color, alpha);
            }
        }
    }

    /** Écart (en pixels, mesuré en travers) entre deux fissures possibles. */
    private static final int VEIN_PERIOD = 19;

    /**
     * Un pixel de la membrane, dans le style du cadre : fond noir traversé de fissures grises en diagonale
     * (comme les veines du bloc de cadre), une lueur blanche qui court de temps en temps le long d'une fissure,
     * et des carrés blancs 2x2 qui s'allument puis s'éteignent, comme les points du cadre.
     */
    private static int[] pixelColor(int px, int py, float time, long tick, float phase, boolean unstable) {
        // Carrés blancs 2x2 : une case sur ~16 s'allume brièvement à son propre rythme.
        int cx = px >> 1, cy = py >> 1;
        long cellSeed = hash(cx, cy, 11L);
        long period = unstable ? 40L : 90L;
        long cycle = (tick + cellSeed % period) / period;
        if (hash(cx, cy, cycle) % 1000L < (unstable ? 110L : 65L)) {
            long life = (tick + cellSeed % period) % period;
            if (life < 3L || (life >= 11L && life < 14L)) return MID;
            if (life < 11L) return WHITE;
        }

        // Fissures en diagonale (même pente que les veines du cadre) qui dérivent lentement.
        float drift = time * 0.025F + phase * 4.0F;
        int across = Mth.floor(px + 2 * py + drift);
        int band = Math.floorDiv(across, VEIN_PERIOD);
        long bandSeed = hash(band, 5L, 29L);
        if (bandSeed % 3L == 0L) return BLACK; // toutes les bandes n'ont pas de fissure
        // Petits décrochages tous les 4 pixels pour un tracé de fissure plutôt que de règle.
        int along = 2 * px - py;
        int jitter = (int) (hash(band, Math.floorDiv(along, 4), 31L) % 3L) - 1;
        int d = Math.floorMod(across, VEIN_PERIOD) - (int) (bandSeed % 5L) - jitter;
        if (d != 0 && d != 1) return BLACK;

        // Une lueur blanche parcourt parfois la fissure.
        float speed = unstable ? 1.6F : 0.7F;
        float pulse = Math.floorMod((long) (time * speed) + bandSeed % 400L, 400L);
        float gap = Math.abs(along + 64 - pulse);
        if (d == 0 && gap < 1.5F) return WHITE;
        if (gap < 4.0F) return MID;
        return d == 0 ? DARK : FAINT;
    }

    private static long hash(long a, long b, long c) {
        return PortalGatePatterns.hash(a, b, c);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, boolean xAxis,
                               float horizontal, float vertical, int[] c, int alpha) {
        float depth = DEPTH / 16.0F;
        if (xAxis) {
            consumer.addVertex(matrix, horizontal, vertical, depth).setColor(c[0], c[1], c[2], alpha);
        } else {
            consumer.addVertex(matrix, depth, vertical, horizontal).setColor(c[0], c[1], c[2], alpha);
        }
    }
}
