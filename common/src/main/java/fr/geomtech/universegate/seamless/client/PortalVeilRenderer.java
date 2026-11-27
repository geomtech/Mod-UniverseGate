package fr.geomtech.universegate.seamless.client;

//? if <1.21.11 {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import fr.geomtech.universegate.seamless.math.PortalTransform;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
//?}

final class PortalVeilRenderer {
    private PortalVeilRenderer() {}

    //? if <1.21.11 {
    private static final double RADIUS = PortalTransform.APERTURE_RADIUS;
    private static final int FILAMENT = 0x9C9C9C;
    private static final int FILAMENT_CORE = 0xDADADA;
    private static final double[] FILAMENT_ORIGINS = {0.24D, 1.18D, 2.61D, 3.48D, 4.96D};

    static float approach(PortalTransform transform, Vec3 camera) {
        double distance = transform.sourceCenter().distanceTo(camera);
        float t = (float) Mth.clamp(1.0D - (distance - 1.0D) / 7.0D, 0.0D, 1.0D);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Draws the passage in portal-local coordinates. The main-world stencil clips it at the rim. */
    static void draw(PortalTransform transform, Vec3 camera, Matrix4f view, Matrix4f projection,
                     double time, float opacity, float approach, boolean backSide) {
        Vec3 center = transform.sourceCenter().subtract(camera);
        Matrix4f portalView = apertureView(transform, center, view);
        boolean detailed = center.lengthSqr() < 18.0D * 18.0D;
        double phase = time + (transform.sourceCorePos().hashCode() & 255) * 0.41D;

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        try {
            BufferBuilder mesh = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            if (backSide) {
                // The back is deep space: a far starfield, a slow grey nebula, a glowing core,
                // and stars streaking out of the passage towards the player.
                backdrop(mesh, portalView, 1.0F);
                coreGlow(mesh, portalView, phase, detailed ? 40 : 20);
                nebula(mesh, portalView, phase, approach, detailed ? 24 : 12);
                starField(mesh, portalView, phase, detailed ? 160 : 70);
                streaks(mesh, portalView, -phase, approach, detailed ? 130 : 45, true);
            } else {
                backdrop(mesh, portalView, opacity);
                mistSheets(mesh, portalView, phase, approach, detailed ? 20 : 10);
                filaments(mesh, portalView, phase, approach, detailed ? 28 : 12);
                streaks(mesh, portalView, phase, approach, detailed ? 62 : 22, false);
            }
            BufferUploader.drawWithShader(mesh.buildOrThrow());
        } finally {
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        }
    }

    static Matrix4f apertureView(PortalTransform transform, Vec3 center, Matrix4f view) {
        return new Matrix4f(view).translate((float) center.x, (float) center.y, (float) center.z)
                .rotateY((float) Math.atan2(-transform.sourceRight().getStepZ(), transform.sourceRight().getStepX()));
    }

    private static void backdrop(BufferBuilder mesh, Matrix4f view, float opacity) {
        int alpha = Math.round(opacity * 255.0F);
        vertex(mesh, view, -RADIUS, -RADIUS, 0, 0, alpha);
        vertex(mesh, view, RADIUS, -RADIUS, 0, 0, alpha);
        vertex(mesh, view, RADIUS, RADIUS, 0, 0, alpha);
        vertex(mesh, view, -RADIUS, RADIUS, 0, 0, alpha);
    }

    private static void filaments(BufferBuilder mesh, Matrix4f view, double phase, float approach, int steps) {
        for (int lane = 0; lane < FILAMENT_ORIGINS.length; lane++) {
            TrailPoint previous = filamentPoint(1.0D, lane, phase, approach);
            for (int step = steps - 1; step >= 0; step--) {
                TrailPoint next = filamentPoint((double) step / steps, lane, phase, approach);
                strip(mesh, view, previous, next, FILAMENT, true);
                previous = next;
            }
        }
    }

    private static TrailPoint filamentPoint(double t, int lane, double phase, float approach) {
        double fold = Math.sin(9.0D * t - phase * 0.115D + lane * 1.8D);
        double angle = FILAMENT_ORIGINS[lane] + 0.21D * Math.sin(4.4D * t + lane) + 0.09D * fold;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double radius = RADIUS * (0.94D - 0.47D * t + 0.05D * fold);
        double depth = 0.13D + 7.8D * t;
        double width = 0.08D + 0.13D * (1.0D - t) + 0.10D * approach;
        double head = fraction(phase * (0.015D + 0.0014D * lane) + lane * 0.29D);
        double gap = Math.abs(t - head);
        double pulse = 1.0D - Math.min(1.0D, Math.min(gap, 1.0D - gap) * 4.0D);
        double lipFade = Math.min(t * 10.0D, 1.0D);
        int alpha = (int) ((40.0D + 42.0D * approach + 78.0D * pulse * pulse)
                * Math.sin(Math.PI * (0.10D + 0.90D * t)) * lipFade * lipFade * (3.0D - 2.0D * lipFade));
        return new TrailPoint(radius * cos, radius * sin, -sin, cos, depth, width, alpha);
    }

    private static void mistSheets(BufferBuilder mesh, Matrix4f view, double phase, float approach, int segments) {
        for (int sheet = 0; sheet < 3; sheet++) {
            double progress = fraction(phase * (0.009D + 0.002D * sheet) + sheet * 0.29D);
            double depth = 0.19D + 6.1D * progress;
            double strength = Math.sin(Math.PI * progress) * (22.0D + 18.0D * approach);
            int color = sheet == 1 ? 0x3A3A3A : 0x505050;
            TrailPoint previous = mistPoint(-1.0D, sheet, depth, phase, strength);
            for (int segment = 1; segment <= segments; segment++) {
                double across = 2.0D * segment / segments - 1.0D;
                TrailPoint next = mistPoint(across, sheet, depth, phase, strength);
                strip(mesh, view, previous, next, color, false);
                previous = next;
            }
        }
    }

    private static TrailPoint mistPoint(double across, int sheet, double depth, double phase, double strength) {
        double x = across * RADIUS;
        double fold = Math.sin(across * 4.1D + sheet * 1.7D - phase * 0.064D);
        double y = (sheet - 1.0D) * 1.12D + 0.29D * x + 0.19D * fold;
        int alpha = (int) (strength * (1.0D - across * across) * (0.70D + 0.30D * fold));
        return new TrailPoint(x, y, -0.28D, 0.96D, depth + 0.17D * fold, 0.27D + 0.12D * sheet, alpha);
    }

    private static void streaks(BufferBuilder mesh, Matrix4f view, double phase, float approach, int count,
                                boolean outflow) {
        for (int i = 0; i < count; i++) {
            double offset = fraction(i * 0.61803398875D);
            double progress = fraction(offset + phase * (0.017D + 0.011D * fraction(i * 0.41421356D)));
            double angle = i * 2.39996323D + 0.055D * Math.sin(progress * 6.0D + i);
            double radius = RADIUS * (0.25D + 0.74D * fraction(i * 0.7320508D)) * (1.0D - 0.34D * progress);
            double depth = 0.06D + progress * 7.5D;
            double tail = (0.32D + 0.85D * approach) * (outflow ? -1.0D : 1.0D);
            double x = radius * Math.cos(angle);
            double y = radius * Math.sin(angle);
            double width = (0.012D + 0.016D * offset) * (0.7D + 0.3D * progress) * (outflow ? 2.2D : 1.0D);
            int alpha = (int) Math.min(255.0D, (80.0D + 90.0D * offset + 45.0D * approach) * (outflow ? 1.35D : 1.0D)
                    * Math.sin(Math.PI * progress));
            int color = i % 11 == 0 ? 0xFFFFFF : 0xBDBDBD;
            vertex(mesh, view, x - width, y, depth, color, alpha);
            vertex(mesh, view, x, y - width * 0.8D, depth, color, alpha);
            vertex(mesh, view, x + width, y, depth, color, alpha);
            vertex(mesh, view, x * 1.06D, y * 1.06D + width, Math.max(0.02D, depth - tail), color, 0);
        }
    }

    /** Soft white glow deep in the passage, breathing slowly. */
    private static void coreGlow(BufferBuilder mesh, Matrix4f view, double phase, int segments) {
        double breath = 0.5D + 0.5D * Math.sin(phase * 0.05D);
        double radius = RADIUS * (0.42D + 0.06D * breath);
        int alpha = (int) (70.0D + 40.0D * breath);
        double depth = 6.5D;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments, a1 = Math.PI * 2.0D * (i + 1) / segments;
            vertex(mesh, view, 0.0D, 0.0D, depth, 0xFFFFFF, alpha);
            vertex(mesh, view, radius * Math.cos(a0), radius * Math.sin(a0), depth, 0xCFCFCF, 0);
            vertex(mesh, view, radius * Math.cos(a1), radius * Math.sin(a1), depth, 0xCFCFCF, 0);
            vertex(mesh, view, 0.0D, 0.0D, depth, 0xFFFFFF, alpha);
        }
    }

    /**
     * Nebula: a few irregular clouds of grey gas, each made of overlapping soft lobes, drifting slowly deep
     * behind the stars.
     */
    private static void nebula(BufferBuilder mesh, Matrix4f view, double phase, float approach, int segments) {
        double drift = phase * 0.003D;
        for (int cloud = 0; cloud < 5; cloud++) {
            double seed = fraction(cloud * 0.6180339887D + 0.17D);
            double angle = cloud * 2.39996323D + 0.35D * Math.sin(drift + cloud);
            double distance = RADIUS * (0.25D + 0.5D * seed);
            double cx = distance * Math.cos(angle), cy = distance * Math.sin(angle);
            for (int lobe = 0; lobe < 3; lobe++) {
                double la = seed * 9.0D + lobe * 2.1D + 0.4D * Math.sin(drift * 1.7D + lobe);
                double offset = 0.32D * (lobe == 0 ? 0.0D : 1.0D);
                double lx = cx + offset * Math.cos(la), ly = cy + offset * Math.sin(la);
                double size = RADIUS * (0.20D + 0.14D * fraction(seed * 7.3D + lobe * 0.37D));
                int alpha = (int) ((lobe == 0 ? 44.0D : 30.0D) * (1.0D + 0.4D * approach));
                int color = (cloud + lobe) % 3 == 0 ? 0x9A9A9A : (cloud + lobe) % 3 == 1 ? 0x767676 : 0x5E5E5E;
                gasLobe(mesh, view, lx, ly, 5.4D + 0.4D * lobe, size, seed * 13.0D + lobe, drift, color, alpha, segments);
            }
        }
    }

    /** One soft puff: a fan bright in the middle, fading to nothing on an uneven, slowly shifting edge. */
    private static void gasLobe(BufferBuilder mesh, Matrix4f view, double x, double y, double depth, double size,
                                double seed, double drift, int color, int alpha, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments, a1 = Math.PI * 2.0D * (i + 1) / segments;
            double r0 = size * lobeEdge(a0, seed, drift), r1 = size * lobeEdge(a1, seed, drift);
            vertex(mesh, view, x, y, depth, color, alpha);
            vertex(mesh, view, x + r0 * Math.cos(a0), y + r0 * Math.sin(a0), depth, color, 0);
            vertex(mesh, view, x + r1 * Math.cos(a1), y + r1 * Math.sin(a1), depth, color, 0);
            vertex(mesh, view, x, y, depth, color, alpha);
        }
    }

    private static double lobeEdge(double angle, double seed, double drift) {
        return 1.0D + 0.28D * Math.sin(3.0D * angle + seed + drift * 2.0D)
                + 0.16D * Math.sin(5.0D * angle - seed * 1.3D - drift * 3.0D);
    }
    /** Fixed, twinkling pixel stars of a few sizes; a few bright ones carry a small cross. */
    private static void starField(BufferBuilder mesh, Matrix4f view, double phase, int count) {
        for (int i = 0; i < count; i++) {
            double u = fraction(i * 0.7548776662D), w = fraction(i * 0.5698402910D);
            double radius = RADIUS * 0.93D * Math.sqrt(u);
            double angle = w * Math.PI * 2.0D;
            double x = radius * Math.cos(angle), y = radius * Math.sin(angle);
            double seed = fraction(i * 0.38196601125D);
            double twinkle = 0.55D + 0.45D * Math.sin(phase * (0.08D + 0.10D * seed) + i * 1.7D);
            boolean big = i % 17 == 0;
            double size = big ? 0.045D : 0.014D + 0.022D * seed;
            int alpha = (int) ((big ? 235.0D : 120.0D + 110.0D * seed) * twinkle);
            int color = seed > 0.7D ? 0xFFFFFF : 0xD0D0D0;
            double depth = 0.35D + 0.5D * seed;
            square(mesh, view, x, y, depth, size, color, alpha);
            if (big) {
                square(mesh, view, x - size * 2.0D, y, depth, size * 0.7D, color, alpha / 2);
                square(mesh, view, x + size * 2.0D, y, depth, size * 0.7D, color, alpha / 2);
                square(mesh, view, x, y - size * 2.0D, depth, size * 0.7D, color, alpha / 2);
                square(mesh, view, x, y + size * 2.0D, depth, size * 0.7D, color, alpha / 2);
            }
        }
    }

    private static void square(BufferBuilder mesh, Matrix4f view, double x, double y, double depth, double half,
                               int color, int alpha) {
        vertex(mesh, view, x - half, y - half, depth, color, alpha);
        vertex(mesh, view, x + half, y - half, depth, color, alpha);
        vertex(mesh, view, x + half, y + half, depth, color, alpha);
        vertex(mesh, view, x - half, y + half, depth, color, alpha);
    }

    private record TrailPoint(double x, double y, double acrossX, double acrossY, double depth, double width, int alpha) {}

    private static void strip(BufferBuilder mesh, Matrix4f view, TrailPoint a, TrailPoint b, int color, boolean brightCore) {
        edge(mesh, view, a, -1, color);
        edge(mesh, view, a, 0, color);
        edge(mesh, view, b, 0, color);
        edge(mesh, view, b, -1, color);
        edge(mesh, view, a, 0, color);
        edge(mesh, view, a, 1, color);
        edge(mesh, view, b, 1, color);
        edge(mesh, view, b, 0, color);
        if (brightCore) {
            trailVertex(mesh, view, a, -0.18D, FILAMENT_CORE, a.alpha() / 2);
            trailVertex(mesh, view, a, 0.18D, FILAMENT_CORE, a.alpha() / 2);
            trailVertex(mesh, view, b, 0.18D, FILAMENT_CORE, b.alpha() / 2);
            trailVertex(mesh, view, b, -0.18D, FILAMENT_CORE, b.alpha() / 2);
        }
    }

    private static void edge(BufferBuilder mesh, Matrix4f view, TrailPoint point, int side, int color) {
        trailVertex(mesh, view, point, side, color, side == 0 ? point.alpha() : 0);
    }

    private static void trailVertex(BufferBuilder mesh, Matrix4f view, TrailPoint point, double side, int color, int alpha) {
        double distance = side * point.width();
        vertex(mesh, view, point.x() + point.acrossX() * distance, point.y() + point.acrossY() * distance,
                point.depth(), color, alpha);
    }

    private static void vertex(BufferBuilder mesh, Matrix4f view, double x, double y, double depth, int color, int alpha) {
        // With local X along sourceRight, -Z points into the portal.
        mesh.addVertex(view, (float) x, (float) y, (float) -depth)
                .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, alpha);
    }

    private static double fraction(double value) {
        return value - Math.floor(value);
    }
    //?}
}
