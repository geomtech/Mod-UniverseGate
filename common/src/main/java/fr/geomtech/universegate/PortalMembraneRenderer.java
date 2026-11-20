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
 * Membrane d'énergie noire d'un portail ouvert, dessinée par le code (aucune texture).
 *
 * <p>Une seule case du champ (en bas à gauche) dessine toute la surface 3x4 : un voile sombre,
 * plus profond au centre, bordé d'un liseré violet, et parcouru de veines lumineuses qui tournent
 * lentement en spirale. Un portail instable tourne plus vite, vire à l'orange et vacille.</p>
 */
public class PortalMembraneRenderer extends ImmediateBlockEntityRenderer<PortalFieldBlockEntity> {

    private static final float WIDTH = PortalFrameDetector.INNER_WIDTH;
    private static final float HEIGHT = PortalFrameDetector.INNER_HEIGHT;
    private static final int GRID_U = 18;
    private static final int GRID_V = 24;
    private static final float DEPTH = 0.5F;
    private static final float GLOW_OFFSET = 0.012F;

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

        float time = (level.getGameTime() + partialTick) * (unstable ? 2.6F : 1.0F);
        float phase = coords.phase();
        Matrix4f matrix = poseStack.last().pose();

        // Valeurs aux sommets de la grille, calculées une fois par image.
        int[][] base = new int[(GRID_U + 1) * (GRID_V + 1)][];
        int[][] glow = new int[(GRID_U + 1) * (GRID_V + 1)][];
        for (int j = 0; j <= GRID_V; j++) {
            for (int i = 0; i <= GRID_U; i++) {
                float u = (float) i / GRID_U;
                float v = (float) j / GRID_V;
                int k = j * (GRID_U + 1) + i;
                base[k] = baseColor(u, v, time, phase, unstable);
                glow[k] = glowColor(u, v, time, phase, unstable);
            }
        }

        VertexConsumer veil = buffer.getBuffer(PortalFieldBlinkRenderer.PORTAL_OCCLUSION_RENDER_TYPE);
        drawGrid(veil, matrix, axis, base, DEPTH);
        VertexConsumer energy = buffer.getBuffer(PortalFieldBlinkRenderer.SINGULARITY_RENDER_TYPE);
        drawGrid(energy, matrix, axis, glow, DEPTH - GLOW_OFFSET);
        drawGrid(energy, matrix, axis, glow, DEPTH + GLOW_OFFSET);
    }

    /** Voile sombre : presque opaque au centre, un peu plus clair et violet vers les bords. */
    private static int[] baseColor(float u, float v, float time, float phase, boolean unstable) {
        float x = (u - 0.5F) * 2.0F;
        float y = (v - 0.5F) * 2.0F;
        float r = Mth.sqrt(x * x + y * y * 0.75F);
        float edge = smoothstep(0.55F, 1.0F, Math.max(Math.abs(x), Math.abs(y)));
        float breath = 0.5F + 0.5F * Mth.sin(time * 0.05F + phase);
        float heart = 1.0F - smoothstep(0.0F, 0.55F, r);

        int red = (int) (8 + 30 * edge + (unstable ? 26 * edge : 0));
        int green = (int) (5 + 14 * edge);
        int blue = (int) (20 + 70 * edge - 10 * heart);
        int alpha = (int) (236 - 46 * edge + 10 * heart * breath);
        return new int[]{clamp(red), clamp(green), clamp(blue), clamp(alpha)};
    }

    /** Veines lumineuses en spirale et liseré du bord (mélange additif). */
    private static int[] glowColor(float u, float v, float time, float phase, boolean unstable) {
        float x = (u - 0.5F) * 2.0F;
        float y = (v - 0.5F) * 2.0F;
        float r = Mth.sqrt(x * x + y * y * 0.75F);
        float angle = (float) Mth.atan2(y, x);

        float spiral = Mth.sin(3.0F * angle + r * 7.5F - time * 0.045F + phase);
        float vein = (float) Math.pow(Math.max(0.0F, spiral), 10.0F);
        float fine = (float) Math.pow(Math.max(0.0F, Mth.sin(5.0F * angle - r * 11.0F + time * 0.03F)), 18.0F) * 0.5F;
        float veinMask = smoothstep(0.08F, 0.6F, r) * (1.0F - smoothstep(0.95F, 1.15F, r));
        float veins = (vein + fine) * veinMask;

        float edge = smoothstep(0.82F, 1.0F, Math.max(Math.abs(x), Math.abs(y)));
        float pulse = 0.75F + 0.25F * Mth.sin(time * 0.08F + phase * 2.0F);
        float flicker = unstable ? 0.6F + 0.4F * Mth.sin(time * 0.9F + u * 13.0F + v * 7.0F) : 1.0F;

        float veinI = veins * flicker;
        float edgeI = edge * pulse * 0.8F;
        int red, green, blue;
        if (unstable) {
            red = (int) (255 * veinI + 200 * edgeI);
            green = (int) (130 * veinI + 90 * edgeI);
            blue = (int) (70 * veinI + 140 * edgeI);
        } else {
            red = (int) (110 * veinI + 120 * edgeI);
            green = (int) (180 * veinI + 80 * edgeI);
            blue = (int) (255 * veinI + 230 * edgeI);
        }
        int alpha = (int) (255 * Math.min(1.0F, veinI + edgeI));
        return new int[]{clamp(red), clamp(green), clamp(blue), clamp(alpha)};
    }

    private static void drawGrid(VertexConsumer consumer, Matrix4f matrix, Direction.Axis axis, int[][] colors, float depth) {
        for (int j = 0; j < GRID_V; j++) {
            for (int i = 0; i < GRID_U; i++) {
                float h0 = WIDTH * i / GRID_U, h1 = WIDTH * (i + 1) / GRID_U;
                float v0 = HEIGHT * j / GRID_V, v1 = HEIGHT * (j + 1) / GRID_V;
                int k00 = j * (GRID_U + 1) + i;
                int k10 = k00 + 1;
                int k01 = k00 + GRID_U + 1;
                int k11 = k01 + 1;
                vertex(consumer, matrix, axis, h0, v0, depth, colors[k00]);
                vertex(consumer, matrix, axis, h1, v0, depth, colors[k10]);
                vertex(consumer, matrix, axis, h1, v1, depth, colors[k11]);
                vertex(consumer, matrix, axis, h0, v1, depth, colors[k01]);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Direction.Axis axis,
                               float horizontal, float vertical, float depth, int[] c) {
        if (axis == Direction.Axis.X) {
            consumer.addVertex(matrix, horizontal, vertical, depth).setColor(c[0], c[1], c[2], c[3]);
        } else {
            consumer.addVertex(matrix, depth, vertical, horizontal).setColor(c[0], c[1], c[2], c[3]);
        }
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static int clamp(int value) {
        return Mth.clamp(value, 0, 255);
    }
}
