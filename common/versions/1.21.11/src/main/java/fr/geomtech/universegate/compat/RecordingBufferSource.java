package fr.geomtech.universegate.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link MultiBufferSource} for code written against the immediate rendering of 1.21.1:
 * vertices are recorded per render type, then {@link #submit()} hands them to the
 * {@link SubmitNodeCollector} as custom geometry, in the order the render types were first used.
 * Positions are recorded already transformed by the pose the caller used, so they are replayed
 * as they are.
 */
public final class RecordingBufferSource implements MultiBufferSource {
    private final SubmitNodeCollector collector;
    private final PoseStack poseStack;
    private final Map<RenderType, Recorder> recorders = new LinkedHashMap<>();

    public RecordingBufferSource(SubmitNodeCollector collector, PoseStack poseStack) {
        this.collector = collector;
        this.poseStack = poseStack;
    }

    /** For submissions that are not plain geometry (items, blocks, beacon beams). */
    public SubmitNodeCollector collector() {
        return collector;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return recorders.computeIfAbsent(renderType, type -> new Recorder());
    }

    public void submit() {
        for (Map.Entry<RenderType, Recorder> entry : recorders.entrySet()) {
            Recorder recorder = entry.getValue();
            if (recorder.vertices.isEmpty()) continue;
            collector.submitCustomGeometry(poseStack, entry.getKey(), (pose, consumer) -> recorder.replay(consumer));
        }
        recorders.clear();
    }

    private static final class Vertex {
        final float x, y, z;
        boolean hasColor; int r, g, b, a;
        boolean hasUv; float u, v;
        boolean hasUv1; int u1, v1;
        boolean hasUv2; int u2, v2;
        boolean hasNormal; float nx, ny, nz;
        boolean hasLineWidth; float lineWidth;

        Vertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class Recorder implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();

        private Vertex last() {
            if (vertices.isEmpty()) throw new IllegalStateException("No vertex started");
            return vertices.get(vertices.size() - 1);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices.add(new Vertex(x, y, z));
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            Vertex v = last();
            v.hasColor = true;
            v.r = r; v.g = g; v.b = b; v.a = a;
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            Vertex vertex = last();
            vertex.hasUv = true;
            vertex.u = u; vertex.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            Vertex vertex = last();
            vertex.hasUv1 = true;
            vertex.u1 = u; vertex.v1 = v;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            Vertex vertex = last();
            vertex.hasUv2 = true;
            vertex.u2 = u; vertex.v2 = v;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            Vertex vertex = last();
            vertex.hasNormal = true;
            vertex.nx = x; vertex.ny = y; vertex.nz = z;
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            Vertex vertex = last();
            vertex.hasLineWidth = true;
            vertex.lineWidth = width;
            return this;
        }

        void replay(VertexConsumer target) {
            for (Vertex v : vertices) {
                target.addVertex(v.x, v.y, v.z);
                if (v.hasColor) target.setColor(v.r, v.g, v.b, v.a);
                if (v.hasUv) target.setUv(v.u, v.v);
                if (v.hasUv1) target.setUv1(v.u1, v.v1);
                if (v.hasUv2) target.setUv2(v.u2, v.v2);
                if (v.hasNormal) target.setNormal(v.nx, v.ny, v.nz);
                if (v.hasLineWidth) target.setLineWidth(v.lineWidth);
            }
        }
    }
}
