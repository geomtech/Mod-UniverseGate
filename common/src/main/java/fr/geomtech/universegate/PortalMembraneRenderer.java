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
 * palette de la Faille : fond presque noir, bandes violettes et magenta qui tournent lentement en
 * spirale autour d'un cœur noir, et quelques points blancs qui scintillent comme ceux du cadre.
 * Un portail instable tourne plus vite, scintille davantage et sa transparence saute par rangées.</p>
 */
public class PortalMembraneRenderer extends ImmediateBlockEntityRenderer<PortalFieldBlockEntity> {

    /** 16 pixels par bloc, comme les textures du mod. */
    private static final int PIXELS_U = PortalFrameDetector.INNER_WIDTH * 16;
    private static final int PIXELS_V = PortalFrameDetector.INNER_HEIGHT * 16;
    private static final float DEPTH = 0.5F;

    // Palette des textures de la Faille (rift_crystal_cluster, rift_core) et des points blancs du cadre.
    private static final int[] BLACK = {8, 0, 12};
    private static final int[] DARK = {72, 35, 96};
    private static final int[] MID = {170, 60, 230};
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
                int alpha = color == WHITE ? 255 : rowAlpha;
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

    /**
     * Un pixel de la membrane : fond noir, bandes grises qui tournent lentement en spirale autour
     * d'un cœur noir, et quelques points blancs qui scintillent le long des bandes.
     */
    private static int[] pixelColor(int px, int py, float time, long tick, float phase, boolean unstable) {
        float x = ((px + 0.5F) / PIXELS_U - 0.5F) * 2.0F;
        float y = ((py + 0.5F) / PIXELS_V - 0.5F) * 2.0F;
        float r = Mth.sqrt(x * x + y * y * 0.75F);
        if (r < 0.18F) return BLACK;

        float angle = (float) Mth.atan2(y, x);
        float spiral = Mth.sin(3.0F * angle + r * 6.0F - time * 0.04F + phase);

        boolean onBand = spiral > 0.86F && r < 1.05F;
        // Points blancs : rares, plus fréquents sur les bandes, et chacun ne dure que quelques ticks.
        long twinkle = hash(px, py, (tick + (hash(px, py, 3L) % 16L)) / 16L) % 1000L;
        if (twinkle < (onBand ? 22L : (unstable ? 6L : 2L))) return WHITE;

        if (spiral > 0.97F && r > 0.3F && r < 0.95F) return MID;
        if (onBand) return DARK;
        return BLACK;
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
