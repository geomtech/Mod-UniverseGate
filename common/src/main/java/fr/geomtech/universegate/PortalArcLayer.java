package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Dessine les arcs électriques de {@link PortalGateArcs} : un trait blanc lumineux en zigzag avec un halo. */
final class PortalArcLayer implements PortalGateLayer {

    private static final float CORE_HALF_WIDTH = 0.6F / 16.0F;
    private static final float GLOW_HALF_WIDTH = 1.8F / 16.0F;

    @Override
    public void render(PortalGateRenderContext ctx, PoseStack poseStack, MultiBufferSource buffer) {
        PortalGateAnimator animator = ctx.core().getClientGateAnimator();
        if (animator == null || animator.arcs().active().isEmpty()) return;
        VertexConsumer consumer = buffer.getBuffer(PortalFieldBlinkRenderer.SINGULARITY_RENDER_TYPE);
        Matrix4f matrix = poseStack.last().pose();

        for (PortalGateArcs.Arc arc : animator.arcs().active()) {
            Vec3 start = PortalGateArcs.startPoint(ctx.right(), arc, ctx.ringAngle());
            Vec3 end = arc.target;
            double length = start.distanceTo(end);
            float amp = (float) Math.min(0.6D, length * 0.18D);
            int n = PortalGateArcs.POINTS;
            Vec3 prev = start;
            for (int k = 1; k <= n; k++) {
                float t = k / (float) n;
                Vec3 p = start.lerp(end, t);
                if (k < n) {
                    float env = Mth.sin(t * Mth.PI) * amp;
                    p = p.add(arc.jitter[k * 3] * env, arc.jitter[k * 3 + 1] * env, arc.jitter[k * 3 + 2] * env);
                }
                segment(consumer, matrix, prev, p, GLOW_HALF_WIDTH, 70);
                segment(consumer, matrix, prev, p, CORE_HALF_WIDTH, 255);
                prev = p;
            }
        }
    }

    /** Un segment de l'arc : deux rubans croisés, visibles de tous les côtés. */
    private static void segment(VertexConsumer consumer, Matrix4f matrix, Vec3 a, Vec3 b, float half, int alpha) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0E-6D) return;
        Vec3 up = Math.abs(dir.normalize().y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(up).normalize().scale(half);
        Vec3 w = dir.cross(u).normalize().scale(half);
        ribbon(consumer, matrix, a, b, u, alpha);
        ribbon(consumer, matrix, a, b, w, alpha);
    }

    private static void ribbon(VertexConsumer consumer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 o, int alpha) {
        put(consumer, matrix, a.subtract(o), alpha);
        put(consumer, matrix, a.add(o), alpha);
        put(consumer, matrix, b.add(o), alpha);
        put(consumer, matrix, b.subtract(o), alpha);
    }

    private static void put(VertexConsumer consumer, Matrix4f matrix, Vec3 p, int alpha) {
        consumer.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z).setColor(255, 255, 255, alpha);
    }
}
