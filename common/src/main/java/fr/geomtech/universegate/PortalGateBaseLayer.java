package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Socle fixe du portail : une base en trapèze qui tient l'anneau, habillée du motif du cadre, avec au centre la
 * plaque du Core (motif de sa texture) dont les traits blancs s'allument quand le portail s'ouvre.
 *
 * <p>Si le bas du portail est enterré (sol devant et derrière), le socle laisse place au terrain : la rangée du
 * bas est dessinée avec le bloc de sol voisin, et l'anneau a l'air de s'enfoncer dans le sol.</p>
 */
public final class PortalGateBaseLayer implements PortalGateLayer {

    static final int TAG_CORE = 2;

    private static final int WIDTH = 96;
    private static final int HEIGHT = 16;
    private static final int ORIGIN_H = -40;
    private static final int PLINTH_HEIGHT = 12;
    private static final PortalGateMesh BASE = build();

    @Override
    public void render(PortalGateRenderContext ctx, PoseStack poseStack, MultiBufferSource buffer) {
        if (renderBuried(ctx, poseStack, buffer)) return;
        VertexConsumer consumer = buffer.getBuffer(PortalGateRenderTypes.GATE_SOLID);
        BASE.render(consumer, poseStack.last().pose(), ctx.xAxis(), 0.0F, ctx.brightness(), (tag, base, angle) -> coreColor(ctx));
    }

    /**
     * Portail enterré : chaque bloc de la rangée du bas (Core compris) prend l'apparence du sol voisin.
     *
     * @return true si le socle a été remplacé par le terrain
     */
    private static boolean renderBuried(PortalGateRenderContext ctx, PoseStack poseStack, MultiBufferSource buffer) {
        Level level = ctx.level();
        Direction right = ctx.right();
        Direction depth = right.getClockWise();
        BlockPos core = ctx.corePos();
        if (!isBuried(level, core, right)) {
            return false;
        }

        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        for (int dx = -2; dx <= 2; dx++) {
            BlockPos pos = core.relative(right, dx);
            BlockState ground = groundAt(level, pos.relative(depth));
            if (ground == null) ground = groundAt(level, pos.relative(depth.getOpposite()));
            if (ground == null) ground = groundAt(level, pos.below());
            if (ground == null) continue;

            BlockPos lightPos = pos.above();
            int light = LightTexture.pack(level.getBrightness(LightLayer.BLOCK, lightPos),
                    level.getBrightness(LightLayer.SKY, lightPos));
            poseStack.pushPose();
            poseStack.translate(right.getStepX() * dx, 0.0D, right.getStepZ() * dx);
            // Légèrement réduit : les faces du vrai sol voisin passent devant, sans scintillement.
            poseStack.translate(0.5D, 0.5D, 0.5D);
            poseStack.scale(GROUND_SCALE, GROUND_SCALE, GROUND_SCALE);
            poseStack.translate(-0.5D, -0.5D, -0.5D);
            blockRenderer.renderSingleBlock(ground, poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        renderSockets(ctx, poseStack, buffer);
        return true;
    }

    public static boolean isBuried(Level level, BlockPos core, Direction right) {
        Direction depth = right.getClockWise();
        return groundAt(level, core.relative(depth)) != null && groundAt(level, core.relative(depth.getOpposite())) != null;
    }

    private static final float GROUND_SCALE = 0.996F;

    /**
     * Les deux fourreaux d'où l'anneau sort du sol : un rebord sombre en relief autour de chaque passage, avec un
     * fond noir. Ils cachent la jonction entre l'anneau et le terrain.
     */
    private static void renderSockets(PortalGateRenderContext ctx, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer consumer = buffer.getBuffer(PortalGateRenderTypes.GATE_SOLID);
        var matrix = poseStack.last().pose();
        boolean xAxis = ctx.xAxis();
        float groundV = 16.0F;
        float dy = PortalGateRingLayer.CENTER_V - groundV;
        float outer = (float) Math.sqrt(PortalGateRingLayer.OUTER_RADIUS * PortalGateRingLayer.OUTER_RADIUS - dy * dy);
        float inner = (float) Math.sqrt(Math.max(0.0F, PortalGateRingLayer.INNER_RADIUS * PortalGateRingLayer.INNER_RADIUS - dy * dy));
        float cx = PortalGateRingLayer.CENTER_H;
        float[][] slots = {{cx - outer, cx - inner}, {cx + inner, cx + outer}};
        int rimTop = PortalGateMesh.shade(0x2B2B2B, ctx.brightness()), rimSide = PortalGateMesh.shade(0x141414, ctx.brightness());
        float top = groundV + 1.5F;
        for (float[] slot : slots) {
            float a = slot[0] - 1.0F, b = slot[1] + 1.0F; // ouverture, un peu plus large que l'anneau
            // Fond noir du passage, juste au-dessus du sol.
            PortalGateMesh.box(consumer, matrix, xAxis, a, b, groundV, groundV + 0.1F, 2.0F, 14.0F, 0x000000, 0x000000);
            // Rebords : devant, derrière et aux deux bouts.
            PortalGateMesh.box(consumer, matrix, xAxis, a - 2.0F, b + 2.0F, groundV, top, 0.0F, 2.0F, rimTop, rimSide);
            PortalGateMesh.box(consumer, matrix, xAxis, a - 2.0F, b + 2.0F, groundV, top, 14.0F, 16.0F, rimTop, rimSide);
            PortalGateMesh.box(consumer, matrix, xAxis, a - 2.0F, a, groundV, top, 2.0F, 14.0F, rimTop, rimSide);
            PortalGateMesh.box(consumer, matrix, xAxis, b, b + 2.0F, groundV, top, 2.0F, 14.0F, rimTop, rimSide);
        }
    }

    /** Bloc de terrain plein (herbe, terre, pierre...), ou null. */
    private static BlockState groundAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.PORTAL_FRAME) || state.is(ModBlocks.PORTAL_CORE)) return null;
        if (state.getRenderShape() != RenderShape.MODEL) return null;
        if (!state.isCollisionShapeFullBlock(level, pos)) return null;
        return state;
    }

    private static int coreColor(PortalGateRenderContext ctx) {
        if (ctx.unstable()) return (ctx.tick() / 3L) % 2L == 0L ? 0xFFFFFF : 0x8A8A8A;
        return (ctx.running() || ctx.cooldownBlinkOn()) ? 0xFFFFFF : PortalGateMesh.shade(0x7A7A7A, ctx.brightness());
    }

    private static PortalGateMesh build() {
        int[] color = new int[WIDTH * HEIGHT];
        int[] tag = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int i = y * WIDTH + x;
                int h = x + ORIGIN_H;
                if (h >= 0 && h < 16) {
                    // Plaque du Core : exactement à la place du bloc du Core, à hauteur du socle.
                    if (y >= PLINTH_HEIGHT) {
                        color[i] = PortalGateMesh.EMPTY;
                    } else if (y == PLINTH_HEIGHT - 1 || h == 0 || h == 15) {
                        color[i] = 0x3A3A3A;
                    } else if (PortalGatePatterns.core(h, y + 2) > 0x80) {
                        color[i] = 0xFFFFFF;
                        tag[i] = TAG_CORE;
                    } else {
                        color[i] = 0x050505;
                    }
                    continue;
                }
                int inset = 6 + y;
                if (y >= PLINTH_HEIGHT || x < inset || x >= WIDTH - inset) {
                    color[i] = PortalGateMesh.EMPTY;
                    continue;
                }
                int g = PortalGatePatterns.frame(h, y);
                if (y == PLINTH_HEIGHT - 1) g = Math.max(g, 0x2B);
                color[i] = PortalGatePatterns.gray(g);
                if (g >= 0x80) tag[i] = TAG_CORE;
            }
        }
        return PortalGateMesh.build(WIDTH, HEIGHT, color, tag, ORIGIN_H, 0, 0.0F, 16.0F,
                PortalGateRingLayer.CENTER_H, PortalGateRingLayer.CENTER_V);
    }
}
