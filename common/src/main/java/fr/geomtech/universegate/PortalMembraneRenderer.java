package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.geomtech.universegate.compat.ImmediateBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Membrane d'énergie noire d'un portail ouvert, dessinée par le code en pixel art (16 px par bloc).
 *
 * <p>Une seule case du champ (en bas à gauche) dessine toute la surface 3x4, pixel par pixel, avec la
 * identité du mod (noir et blanc lumineux) et le motif du bloc de cadre : fond noir, fissures grises en
 * diagonale où court parfois une lueur blanche, et carrés blancs 2x2 qui s'allument puis s'éteignent.
 * Un portail instable s'agite plus vite, scintille davantage et sa transparence saute par rangées.</p>
 */
public class PortalMembraneRenderer extends ImmediateBlockEntityRenderer<PortalFieldBlockEntity> {

    /** 16 pixels par bloc, comme les textures du mod. */
    private static final int PIXELS_U = PortalFrameDetector.INNER_WIDTH * 16;
    private static final int PIXELS_V = PortalFrameDetector.INNER_HEIGHT * 16;
    private static final float DEPTH = 0.5F;

    // Identité du mod : noir profond et blanc lumineux (cadre, Core, champ d'origine).
    private static final int[] BLACK = {0, 0, 0};
    private static final int[] FAINT = {20, 20, 20};
    private static final int[] DARK = {43, 43, 43};
    private static final int[] MID = {205, 205, 205};
    private static final int[] WHITE = {255, 255, 255};

    public PortalMembraneRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected boolean rendersOffScreen() {
        // La case qui dessine est dans un coin : la surface reste visible quand ce coin sort de l'écran.
        return true;
    }

    @Override
    public void render(PortalFieldBlockEntity blockEntity,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) return;

        BlockState state = blockEntity.getBlockState();
        if (!state.hasProperty(PortalFieldBlock.AXIS)) return;
        Direction.Axis axis = state.getValue(PortalFieldBlock.AXIS);
        boolean unstable = state.hasProperty(PortalFieldBlock.UNSTABLE) && state.getValue(PortalFieldBlock.UNSTABLE);

        PortalFieldBlinkRenderer.PortalCoords coords =
                PortalFieldBlinkRenderer.resolvePortalCoords(level, blockEntity.getBlockPos(), axis);
        if (coords == null || coords.horizontalIndex() != 0 || coords.verticalIndex() != 0) return;

        long tick = level.getGameTime();
        float time = (tick + partialTick) * (unstable ? 2.4F : 1.0F);
        float phase = coords.phase();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(PortalFieldBlinkRenderer.PORTAL_OCCLUSION_RENDER_TYPE);

        for (int py = 0; py < PIXELS_V; py++) {
            // Portail instable : la transparence saute par rangées, comme l'ancienne texture instable.
            int rowAlpha = unstable ? UNSTABLE_ALPHAS[(int) (hash(py, tick / 3L, 7L) % UNSTABLE_ALPHAS.length)] : 217;
            for (int px = 0; px < PIXELS_U; px++) {
                int[] color = pixelColor(px, py, time, tick, phase, unstable);
                int alpha = color == WHITE || color == MID ? 255 : rowAlpha;
                float h0 = px / 16.0F, h1 = (px + 1) / 16.0F;
                float v0 = py / 16.0F, v1 = (py + 1) / 16.0F;
                vertex(consumer, matrix, axis, h0, v0, color, alpha);
                vertex(consumer, matrix, axis, h1, v0, color, alpha);
                vertex(consumer, matrix, axis, h1, v1, color, alpha);
                vertex(consumer, matrix, axis, h0, v1, color, alpha);
            }
        }
    }

    private static final int[] UNSTABLE_ALPHAS = {102, 153, 217, 217};

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
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return h & Long.MAX_VALUE;
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Direction.Axis axis,
                               float horizontal, float vertical, int[] c, int alpha) {
        if (axis == Direction.Axis.X) {
            consumer.addVertex(matrix, horizontal, vertical, DEPTH).setColor(c[0], c[1], c[2], alpha);
        } else {
            consumer.addVertex(matrix, DEPTH, vertical, horizontal).setColor(c[0], c[1], c[2], alpha);
        }
    }
}
