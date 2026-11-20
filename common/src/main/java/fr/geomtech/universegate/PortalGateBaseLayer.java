package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Socle fixe du portail : une base en trapèze qui tient l'anneau, habillée du motif du cadre, avec au centre la
 * plaque du Core (motif de sa texture) dont les traits blancs s'allument quand le portail s'ouvre.
 */
final class PortalGateBaseLayer implements PortalGateLayer {

    static final int TAG_CORE = 2;

    private static final int WIDTH = 96;
    private static final int HEIGHT = 16;
    private static final int ORIGIN_H = -40;
    private static final int PLINTH_HEIGHT = 12;
    private static final PortalGateMesh BASE = build();

    @Override
    public void render(PortalGateRenderContext ctx, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer consumer = buffer.getBuffer(PortalGateRenderTypes.GATE_SOLID);
        BASE.render(consumer, poseStack.last().pose(), ctx.xAxis(), 0.0F, (tag, base, angle) -> coreColor(ctx));
    }

    private static int coreColor(PortalGateRenderContext ctx) {
        if (ctx.unstable()) return (ctx.tick() / 3L) % 2L == 0L ? 0xFFFFFF : 0x8A8A8A;
        return ctx.running() ? 0xFFFFFF : 0x7A7A7A;
    }

    private static PortalGateMesh build() {
        int[] color = new int[WIDTH * HEIGHT];
        int[] tag = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int i = y * WIDTH + x;
                int h = x + ORIGIN_H;
                if (h >= 0 && h < 16) {
                    // Plaque du Core : exactement à la place du bloc du Core.
                    if (PortalGatePatterns.core(h, y) > 0x80) {
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
            }
        }
        return PortalGateMesh.build(WIDTH, HEIGHT, color, tag, ORIGIN_H, 0, 0.0F, 16.0F,
                PortalGateRingLayer.CENTER_H, PortalGateRingLayer.CENTER_V);
    }
}
