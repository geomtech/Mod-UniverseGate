package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fractured faceplates, guide rails and conduits exposed by a missing piece. Dimensions are in model pixels. */
final class PortalGateBrokenJoints {
    private static final int[][] STEPS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    // Cross-sections in radial/depth coordinates. The channel opens towards the aperture.
    private static final double[][] CHANNEL = {
            {-1, -1}, {1, -1}, {1, 1}, {-1, 1}, {-1, .46}, {.40, .46}, {.40, -.46}, {-1, -.46}
    };
    private static final double[][] BORE = {
            {-1, -.48}, {-.48, -1}, {.48, -1}, {1, -.48}, {1, .48}, {.48, 1}, {-.48, 1}, {-1, .48}
    };
    private static final List<Joint> FRAME_JOINTS = build(false);
    private static final List<Joint> SEGMENT_JOINTS = build(true);

    private record Pair(int piece, int neighbor) {}
    private record Pixel(int h, int v) {
        Pixel next(int[] step) {
            return new Pixel(h + step[0], v + step[1]);
        }
    }

    private record Edge(double h, double v, int direction) {
        double radius() {
            return Math.hypot(h - PortalGateGeometry.CENTER_H, v - PortalGateGeometry.CENTER_V);
        }

        /** Travel along the circumference from this cut, with the channel staying underneath its trim. */
        Vec3 point(double travel, double radialOffset, double z) {
            double dx = h - PortalGateGeometry.CENTER_H, dy = v - PortalGateGeometry.CENTER_V;
            double radius = radius();
            int[] normal = STEPS[direction];
            double angle = Math.copySign(1.0, -dy * normal[0] + dx * normal[1]) * travel / radius;
            double cos = Math.cos(angle), sin = Math.sin(angle), scale = 1.0 + radialOffset / radius;
            return new Vec3(PortalGateGeometry.CENTER_H + (dx * cos - dy * sin) * scale,
                    PortalGateGeometry.CENTER_V + (dx * sin + dy * cos) * scale, z);
        }
    }

    private record Face(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {}
    private record Joint(Pair pair, List<Face> faces) {}

    private PortalGateBrokenJoints() {}

    static void renderDamage(int mask, VertexConsumer consumer, Matrix4f matrix, boolean xAxis, float brightness) {
        render(FRAME_JOINTS, mask, mask, consumer, matrix, xAxis, brightness);
    }

    static void renderConstruction(int placed, int occupied, VertexConsumer consumer, Matrix4f matrix,
                                   boolean xAxis, float brightness) {
        render(SEGMENT_JOINTS, placed, occupied, consumer, matrix, xAxis, brightness);
    }

    private static void render(List<Joint> joints, int drawn, int occupied, VertexConsumer consumer, Matrix4f matrix,
                               boolean xAxis, float brightness) {
        for (Joint joint : joints) {
            if ((drawn & (1 << joint.pair().piece())) == 0 || (occupied & (1 << joint.pair().neighbor())) != 0) continue;
            for (Face face : joint.faces()) {
                int color = PortalGateMesh.shade(face.color(), brightness);
                vertex(consumer, matrix, xAxis, face.a(), color);
                vertex(consumer, matrix, xAxis, face.b(), color);
                vertex(consumer, matrix, xAxis, face.c(), color);
                vertex(consumer, matrix, xAxis, face.d(), color);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, boolean xAxis, Vec3 pos, int color) {
        float h = (float) pos.x / 16.0F, v = (float) pos.y / 16.0F, z = (float) pos.z / 16.0F;
        consumer.addVertex(matrix, xAxis ? h : z, v, xAxis ? z : h)
                .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, 255);
    }

    private static List<Joint> build(boolean segments) {
        Map<Pair, List<Edge>> cuts = new LinkedHashMap<>();
        for (int v = 0; v < 96; v++) {
            for (int h = -40; h < 56; h++) {
                if (!PortalGateGeometry.ringPixel(h, v)) continue;
                int from = owner(h, v, segments);
                if (!segments && from == 0) continue; // The core cube surrounds this part of the ring.
                for (int direction = 0; direction < STEPS.length; direction++) {
                    int dh = STEPS[direction][0], dv = STEPS[direction][1];
                    if (!PortalGateGeometry.ringPixel(h + dh, v + dv)) continue;
                    int to = owner(h + dh, v + dv, segments);
                    if (from == to) continue;
                    cuts.computeIfAbsent(new Pair(from, to), unused -> new ArrayList<>())
                            .add(new Edge(h + 0.5D + dh * 0.5D, v + 0.5D + dv * 0.5D, direction));
                }
            }
        }

        List<Joint> joints = new ArrayList<>();
        cuts.forEach((pair, edges) -> {
            if (edges.size() < 4) return;
            int[] counts = new int[STEPS.length];
            for (Edge edge : edges) counts[edge.direction()]++;
            int dominant = 0;
            for (int i = 1; i < counts.length; i++) {
                if (counts[i] > counts[dominant]) dominant = i;
            }
            int side = dominant;
            List<Edge> face = edges.stream().filter(edge -> edge.direction() == side).toList();
            List<Face> mesh = new ArrayList<>();
            boolean longRail = pair.piece() < pair.neighbor();
            tornSkin(mesh, edges, pair, segments, true, longRail);
            tornSkin(mesh, edges, pair, segments, false, !longRail);
            guideRail(mesh, nearest(face, PortalGateGeometry.OUTER_RADIUS - 2.7), longRail);
            conduit(mesh, nearest(face, PortalGateGeometry.INNER_RADIUS + 3.6), !longRail);
            joints.add(new Joint(pair, List.copyOf(mesh)));
        });
        return List.copyOf(joints);
    }

    private static int owner(int h, int v, boolean segments) {
        return segments ? PortalGateGeometry.ringSegment(h, v)
                : PortalGateGeometry.pieceAt(PortalGateGeometry.owner(h + 0.5D, v + 0.5D));
    }

    private static Edge nearest(List<Edge> edges, double radius) {
        return edges.stream().min(Comparator.comparingDouble(edge -> Math.abs(edge.radius() - radius))).orElseThrow();
    }

    /** Grow each plate only from its attached edge and only into pixels owned by the absent neighbor. */
    private static void tornSkin(List<Face> mesh, List<Edge> edges, Pair pair, boolean segments,
                                 boolean front, boolean outerSpur) {
        Set<Pixel> skin = new HashSet<>();
        Map<Pixel, Integer> distances = new LinkedHashMap<>();
        ArrayDeque<Pixel> queue = new ArrayDeque<>();
        for (Edge edge : edges) {
            int[] step = STEPS[edge.direction()];
            Pixel first = new Pixel((int) Math.floor(edge.h() + step[0] * .5), (int) Math.floor(edge.v() + step[1] * .5));
            if (distances.putIfAbsent(first, 1) == null) queue.addLast(first);
        }
        while (!queue.isEmpty()) {
            Pixel pixel = queue.removeFirst();
            int distance = distances.get(pixel);
            double r = Math.hypot(pixel.h() + .5 - PortalGateGeometry.CENTER_H, pixel.v() + .5 - PortalGateGeometry.CENTER_V);
            double band = r - PortalGateGeometry.INNER_RADIUS;
            // A long diagonal shard on one skin, and a shorter torn return towards the opposite bead.
            if (!outerSpur) band = PortalGateGeometry.OUTER_RADIUS - PortalGateGeometry.INNER_RADIUS - band;
            double reach = Math.max(4.4 - 1.35 * Math.abs(band - 9.5), 2.7 - Math.abs(band - 2.0));
            if (distance > reach) continue;
            skin.add(pixel);
            for (int[] step : STEPS) {
                Pixel next = pixel.next(step);
                if (PortalGateGeometry.ringPixel(next.h(), next.v()) && owner(next.h(), next.v(), segments) == pair.neighbor()
                        && distances.putIfAbsent(next, distance + 1) == null) queue.addLast(next);
            }
        }

        double surface = front ? PortalGateGeometry.RING_Z0 : PortalGateGeometry.RING_Z1;
        double inside = surface + (front ? .65 : -.65);
        for (Pixel pixel : skin) {
            int h = pixel.h(), v = pixel.v();
            Vec3[] corners = {new Vec3(h, v, surface), new Vec3(h + 1, v, surface),
                    new Vec3(h + 1, v + 1, surface), new Vec3(h, v + 1, surface)};
            Vec3 dz = new Vec3(0, 0, inside - surface);
            int faceColor = PortalGateRingLayer.idleFaceColor(h, v);
            mesh.add(new Face(corners[0], corners[1], corners[2], corners[3], faceColor));
            mesh.add(new Face(corners[0].add(dz), corners[1].add(dz), corners[2].add(dz), corners[3].add(dz), 0x202020));
            for (int i = 0; i < 4; i++) {
                int[] step = STEPS[i];
                Pixel next = pixel.next(step);
                if (skin.contains(next) || (PortalGateGeometry.ringPixel(next.h(), next.v())
                        && owner(next.h(), next.v(), segments) == pair.piece())) continue;
                int corner = switch (i) {
                    case 0 -> 3; // left
                    case 1 -> 1; // right
                    case 2 -> 0; // bottom
                    default -> 2; // top
                };
                Vec3 a = corners[corner], b = corners[(corner + 1) % 4];
                int edge = i == 3 ? 0x484848 : i == 2 ? 0x242424 : 0x363636;
                mesh.add(new Face(a, b, b.add(dz), a.add(dz), edge));
            }
        }
    }

    /** Rolled C-section with its two flanges and web visible in the sheared end. */
    private static void guideRail(List<Face> mesh, Edge edge, boolean longRail) {
        Vec3[] root = section(edge, CHANNEL, -1.8, 0, 8.15, 1.15, 1.6, 0);
        Vec3[] middle = section(edge, CHANNEL, 1.1, 0, 8.15, 1.15, 1.6, 0);
        Vec3[] end = section(edge, CHANNEL, longRail ? 4.45 : 2.15, -.22, 8.02, 1.15, 1.6, longRail ? .65 : -.45);
        walls(mesh, root, middle, 0x404040);
        walls(mesh, middle, end, 0x404040);
        mesh.add(new Face(end[0], end[1], end[6], end[7], 0x595959));
        mesh.add(new Face(end[1], end[2], end[5], end[6], 0x333333));
        mesh.add(new Face(end[2], end[3], end[4], end[5], 0x4C4C4C));
    }

    /** An octagonal socket on each mating end. The fractured conduit stays captured in just one ferrule. */
    private static void conduit(List<Face> mesh, Edge edge, boolean carriesLine) {
        // A stamped rib continues the slant of the casing veins across the recessed bulkhead.
        Vec3[] ribFoot = {edge.point(-1.75, .7, 8.6), edge.point(-1.75, 1, 9.25),
                edge.point(-1.75, 5.2, 7.3), edge.point(-1.75, 4.9, 6.65)};
        Vec3[] rib = {edge.point(.1, .7, 8.6), edge.point(.1, 1, 9.25),
                edge.point(-.4, 5.2, 7.3), edge.point(-.4, 4.9, 6.65)};
        walls(mesh, ribFoot, rib, 0x242424);
        mesh.add(new Face(rib[0], rib[1], rib[2], rib[3], 0x373737));
        Vec3[] base = section(edge, BORE, -1.65, 0, 8, 1.55, 1.58, 0);
        Vec3[] rim = section(edge, BORE, .85, 0, 8, 1.45, 1.48, 0);
        Vec3[] mouth = section(edge, BORE, .85, 0, 8, 1.01, 1.04, 0);
        Vec3[] bottom = section(edge, BORE, -.65, 0, 8, .91, .94, 0);
        walls(mesh, base, rim, 0x303030);
        walls(mesh, rim, mouth, 0x5C5C5C);
        walls(mesh, mouth, bottom, 0x232323);
        cap(mesh, bottom, 0x080808);
        if (carriesLine) {
            Vec3[] stem = section(edge, BORE, -.65, 0, 8, .75, .76, 0);
            Vec3[] sleeve = section(edge, BORE, 2.1, -.12, 7.90, .75, .76, .12);
            walls(mesh, stem, sleeve, 0x434343);
            cap(mesh, sleeve, 0x1C1C1C);
            // Three torn strands spring apart as they leave the dark jacket.
            strand(mesh, edge, -.45, 7.74, 4.95, -2.05, 7.25, 0x777777);
            strand(mesh, edge, .16, 7.70, 4.10, .45, 7.38, 0x929292);
            strand(mesh, edge, -.09, 8.28, 4.50, -.9, 8.86, 0x585858);
        }
    }

    private static void strand(List<Face> mesh, Edge edge, double offset, double z, double length,
                               double tipOffset, double tipZ, int color) {
        Vec3[] root = section(edge, BORE, 1.85, offset, z, .21, .19, 0);
        Vec3[] bend = section(edge, BORE, 3.05, offset + (tipOffset - offset) * .22, z, .20, .18, 0);
        Vec3[] tip = section(edge, BORE, length, tipOffset, tipZ, .16, .14, .14);
        walls(mesh, root, bend, color);
        walls(mesh, bend, tip, color);
        cap(mesh, tip, 0x909090);
    }

    private static Vec3[] section(Edge edge, double[][] profile, double travel, double offset, double z,
                                  double halfWidth, double halfDepth, double shear) {
        Vec3[] points = new Vec3[profile.length];
        for (int i = 0; i < points.length; i++) {
            double r = profile[i][0], depth = profile[i][1];
            points[i] = edge.point(travel + shear * (r + .35 * depth), offset + r * halfWidth, z + depth * halfDepth);
        }
        return points;
    }

    private static void walls(List<Face> mesh, Vec3[] a, Vec3[] b, int color) {
        for (int i = 0; i < a.length; i++) {
            int next = (i + 1) % a.length;
            Vec3 normal = a[next].subtract(a[i]).cross(b[i].subtract(a[i])).normalize();
            float light = (float) (.64 + .30 * Math.abs(normal.y) + .12 * normal.y - .06 * normal.z);
            mesh.add(new Face(a[i], a[next], b[next], b[i], PortalGateMesh.shade(color, light)));
        }
    }

    private static void cap(List<Face> mesh, Vec3[] octagon, int color) {
        for (int i = 1; i < 7; i += 2) {
            mesh.add(new Face(octagon[0], octagon[i], octagon[i + 1], octagon[i + 2], color));
        }
    }
}
