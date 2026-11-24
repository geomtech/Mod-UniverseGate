package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.geomtech.universegate.compat.ImmediateBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders electric wires as continuous 8-sided cylindrical tubes matching {@link CableTerminalRenderer},
 * with smooth Bézier bends, rounded step risers, outer-edge wraps and real Minecraft lightmap shading.
 */
public final class ElectricWireRenderer extends ImmediateBlockEntityRenderer<ElectricWireBlockEntity> {

    private static final Vec3 CENTER = new Vec3(0.5, 0.5, 0.5);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double H = 0.72 / 16.0;
    private static final double LANE = 1.45 / 16.0;
    private static final double RADIUS = 0.039;
    private static final double FILLET = 0.16;
    private static final int RED = 0xe63c37, BLUE = 0x4678eb, COPPER = 0xd9894f;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "textures/block/cable_white.png");

    private final Map<ElectricWireBlockEntity, Geometry> cache = new WeakHashMap<>();

    public ElectricWireRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected AABB renderBounds(ElectricWireBlockEntity wire) {
        return new AABB(wire.getBlockPos()).inflate(1.0);
    }

    @Override
    public void render(ElectricWireBlockEntity wire, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = wire.getLevel();
        if (level == null) return;
        BlockState state = wire.getBlockState();
        if (!ElectricWireBlock.isWire(state)) return;

        BlockPos pos = wire.getBlockPos();
        int wrapMask = computeOuterWrapMask(level, pos, state);
        Geometry geometry = cache.computeIfAbsent(wire, unused -> new Geometry());
        if (geometry.state != state || geometry.wrapMask != wrapMask) {
            geometry.rebuild(state, wrapMask);
        }

        //? if >=1.21.11 {
        /*VertexConsumer consumer = buffers.getBuffer(RenderTypes.entityCutoutNoCull(WHITE));
        *///?} else {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        //?}
        geometry.draw(consumer, poses.last(), light);
    }

    /** Bitmask of sides where this wire wraps over an outer 90° block ledge onto an adjacent wall wire. */
    private static int computeOuterWrapMask(Level level, BlockPos pos, BlockState state) {
        Direction mount = state.getValue(ElectricWireBlock.FACING);
        int mask = 0;
        for (Direction side : DIRECTIONS) {
            if (side.getAxis() == mount.getAxis()) continue;
            if (state.getValue(ElectricWireBlock.sideProperty(side)) != RedstoneSide.SIDE) continue;
            BlockPos next = pos.relative(side);
            BlockState nextState = level.getBlockState(next);
            if (ElectricWireBlock.isWire(nextState) || ElectricWireBlock.isTerminal(nextState)) continue;
            BlockState belowState = level.getBlockState(next.relative(mount));
            if (ElectricWireBlock.isWire(belowState)
                    && belowState.getValue(ElectricWireBlock.FACING) == side.getOpposite()) {
                mask |= (1 << side.ordinal());
            }
        }
        return mask;
    }

    private static Vec3 vector(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    /** Matches CableTerminalRenderer's positive transverse axis so opposing ports align across every face. */
    private static Vec3 transverse(Direction side, Direction mount) {
        Vec3 c = vector(side).cross(vector(mount));
        return new Vec3(Math.abs(c.x), Math.abs(c.y), Math.abs(c.z));
    }

    private static Vec3 port(Direction mount, Direction side, int lane) {
        return CENTER.add(vector(side).scale(0.5))
                .add(vector(mount).scale(0.5 - H))
                .add(transverse(side, mount).scale(lane * LANE));
    }

    private static final class Geometry {
        private final List<Quad> quads = new ArrayList<>();
        private BlockState state;
        private int wrapMask;

        void rebuild(BlockState newState, int newWrapMask) {
            this.state = newState;
            this.wrapMask = newWrapMask;
            quads.clear();

            Direction mount = newState.getValue(ElectricWireBlock.FACING);
            Vec3 normal = vector(mount).reverse();
            List<Direction> sides = new ArrayList<>(4);
            for (Direction d : DIRECTIONS) {
                if (d.getAxis() == mount.getAxis()) continue;
                if (newState.getValue(ElectricWireBlock.sideProperty(d)) != RedstoneSide.NONE) {
                    sides.add(d);
                }
            }

            // Two positive tangent basis vectors on the support face.
            Vec3 axisA = null, axisB = null;
            for (Direction d : DIRECTIONS) {
                if (d.getAxis() == mount.getAxis() || d.getAxisDirection() != Direction.AxisDirection.POSITIVE) continue;
                if (axisA == null) axisA = vector(d);
                else axisB = vector(d);
            }
            Vec3 surfaceCenter = CENTER.add(vector(mount).scale(0.5 - H));

            for (int lane : new int[]{1, -1}) {
                int color = lane > 0 ? RED : BLUE;

                // Build risers and outer-ledge wraps on each connected side first.
                for (Direction side : sides) {
                    RedstoneSide rs = newState.getValue(ElectricWireBlock.sideProperty(side));
                    if (rs == RedstoneSide.UP) {
                        addRiser(mount, normal, side, lane, color);
                    } else if ((newWrapMask & (1 << side.ordinal())) != 0) {
                        addOuterWrap(mount, normal, side, lane, color);
                    }
                }

                if (sides.isEmpty()) {
                    addCoil(surfaceCenter, axisA, axisB, normal, lane, color);
                } else if (sides.size() == 1) {
                    Direction s = sides.getFirst();
                    Vec3 start = anchor(newState, mount, s, lane);
                    Vec3 inward = vector(s).reverse();
                    Vec3 t = transverse(s, mount);
                    Vec3 end = surfaceCenter.add(t.scale(lane * LANE)).add(inward.scale(0.06));
                    Vec3 c1 = start.add(inward.scale(0.18));
                    Vec3 c2 = end.subtract(inward.scale(0.14)).add(t.scale(lane * 0.025));
                    Vec3[] path = sampleBezier(start, c1, c2, end, 7, normal, 0.0);
                    tube(path, normal, color, false, true);
                    // Short stripped copper tip at the loose end.
                    Vec3 tipDir = end.subtract(path[path.length - 2]).normalize();
                    Vec3 tipEnd = end.add(tipDir.scale(0.055));
                    tube(new Vec3[]{end, tipEnd}, normal, COPPER, false, true, RADIUS * 0.62);
                } else if (sides.size() == 2) {
                    Direction d1 = sides.get(0), d2 = sides.get(1);
                    Vec3 a = anchor(newState, mount, d1, lane);
                    Vec3 b = anchor(newState, mount, d2, lane);
                    if (d1.getAxis() == d2.getAxis()) {
                        tube(new Vec3[]{a, b}, normal, color, false, false);
                    } else {
                        addCorner(surfaceCenter, normal, d1, d2, a, b, lane, color);
                    }
                } else if (sides.size() == 3) {
                    // Find the straight-through pair and the perpendicular T-branch.
                    Direction straightA = null, straightB = null, branch = null;
                    for (int i = 0; i < 3; i++) {
                        for (int j = i + 1; j < 3; j++) {
                            if (sides.get(i).getAxis() == sides.get(j).getAxis()) {
                                straightA = sides.get(i);
                                straightB = sides.get(j);
                                branch = sides.get(3 - i - j);
                            }
                        }
                    }
                    Vec3 a = anchor(newState, mount, straightA, lane);
                    Vec3 b = anchor(newState, mount, straightB, lane);
                    tube(new Vec3[]{a, b}, normal, color, false, false);

                    Vec3 start = anchor(newState, mount, branch, lane);
                    Vec3 join = surfaceCenter
                            .add(transverse(straightA, mount).scale(lane * LANE))
                            .add(transverse(branch, mount).scale(lane * LANE));
                    // Curve cleanly from the branch port into the matching conductor on the main line.
                    Vec3 c1 = start.add(vector(branch).reverse().scale(0.24));
                    Vec3 c2 = join.add(vector(branch).scale(0.16));
                    Vec3[] path = sampleBezier(start, c1, c2, join, 8, normal, 0.0);
                    tube(path, normal, color, false, true);
                } else {
                    // 4-way cross: pair 0 runs flat, pair 1 arches smoothly over pair 0.
                    Direction a1 = sides.get(0), a2 = a1.getOpposite();
                    Direction b1 = null;
                    for (Direction d : sides) {
                        if (d.getAxis() != a1.getAxis()) { b1 = d; break; }
                    }
                    Direction b2 = b1.getOpposite();
                    tube(new Vec3[]{anchor(newState, mount, a1, lane), anchor(newState, mount, a2, lane)}, normal, color, false, false);

                    Vec3 p1 = anchor(newState, mount, b1, lane);
                    Vec3 p2 = anchor(newState, mount, b2, lane);
                    Vec3 mid1 = p1.lerp(p2, 0.35), mid2 = p1.lerp(p2, 0.65);
                    double arch = RADIUS * 2.15;
                    Vec3[] bridge = sampleBezier(p1, mid1, mid2, p2, 12, normal, arch);
                    tube(bridge, normal, color, false, false);
                }
            }
        }

        /** Surface endpoint for a side: pulled back by FILLET if that side climbs a vertical riser. */
        private Vec3 anchor(BlockState state, Direction mount, Direction side, int lane) {
            Vec3 p = port(mount, side, lane);
            if (state.getValue(ElectricWireBlock.sideProperty(side)) == RedstoneSide.UP) {
                return p.add(vector(side).reverse().scale(H + FILLET));
            }
            return p;
        }

        /** Smooth 90° bend; arches the red wire smoothly over the blue wire whenever the two lanes cross. */
        private void addCorner(Vec3 surfaceCenter, Vec3 normal, Direction d1, Direction d2,
                               Vec3 a, Vec3 b, int lane, int color) {
            Vec3 in1 = vector(d1).reverse();
            Vec3 in2 = vector(d2).reverse();
            double handle = Math.min(0.42, a.distanceTo(b) * 0.44);
            Vec3 c1 = a.add(in1.scale(handle));
            Vec3 c2 = b.add(in2.scale(handle));

            // Check whether lane +1 switches from inside to outside (crossing lane -1).
            double dot1 = a.subtract(surfaceCenter).dot(in2);
            double dot2 = b.subtract(surfaceCenter).dot(in1);
            boolean crosses = lane > 0 && dot1 * dot2 < 0.0;
            double lift = crosses ? RADIUS * 2.15 : 0.0;

            Vec3[] path = sampleBezier(a, c1, c2, b, 12, normal, lift);
            tube(path, normal, color, false, false);
        }

        /**
         * Continuous rounded riser: curves smoothly off the floor into the vertical step wall,
         * runs up the wall, and wraps over the top lip directly into the upper wire's port.
         */
        private void addRiser(Direction mount, Vec3 normal, Direction side, int lane, int color) {
            Vec3 sVec = vector(side);
            Vec3 t = transverse(side, mount).scale(lane * LANE);
            Vec3 innerCorner = CENTER.add(sVec.scale(0.5 - H)).add(vector(mount).scale(0.5 - H)).add(t);
            Vec3 topCorner = CENTER.add(sVec.scale(0.5)).add(normal.scale(0.5)).add(t);

            List<Vec3> pts = new ArrayList<>(14);
            // Bottom inner fillet: from (innerCorner - side * FILLET) curving up to (innerCorner + normal * FILLET).
            Vec3 filletCenter = innerCorner.subtract(sVec.scale(FILLET)).add(normal.scale(FILLET));
            int botSteps = 6;
            for (int i = 0; i <= botSteps; i++) {
                double ang = (Math.PI * 0.5) * i / botSteps;
                pts.add(filletCenter.add(sVec.scale(Math.sin(ang) * FILLET)).subtract(normal.scale(Math.cos(ang) * FILLET)));
            }
            // Top outer lip wrap: quarter circle of radius H around topCorner from vertical wall to upper floor port.
            int topSteps = 5;
            for (int i = 0; i <= topSteps; i++) {
                double ang = (Math.PI * 0.5) * i / topSteps;
                pts.add(topCorner.subtract(sVec.scale(Math.cos(ang) * H)).add(normal.scale(Math.sin(ang) * H)));
            }
            tube(pts.toArray(Vec3[]::new), transverse(side, mount), color, false, false);
        }

        /** Quarter-circle rubber wrap over a 90° outer block corner onto a wall wire below. */
        private void addOuterWrap(Direction mount, Vec3 normal, Direction side, int lane, int color) {
            Vec3 sVec = vector(side);
            Vec3 mVec = vector(mount);
            Vec3 t = transverse(side, mount).scale(lane * LANE);
            Vec3 corner = CENTER.add(sVec.scale(0.5)).add(mVec.scale(0.5)).add(t);

            int steps = 5;
            Vec3[] pts = new Vec3[steps + 1];
            for (int i = 0; i <= steps; i++) {
                double ang = (Math.PI * 0.5) * i / steps;
                pts[i] = corner.add(normal.scale(Math.cos(ang) * H)).add(sVec.scale(Math.sin(ang) * H));
            }
            tube(pts, transverse(side, mount), color, false, false);
        }

        /** Smooth circular resting coil for a lone unconnected wire block. */
        private void addCoil(Vec3 surfaceCenter, Vec3 axisA, Vec3 axisB, Vec3 normal, int lane, int color) {
            int steps = 18;
            Vec3[] pts = new Vec3[steps + 1];
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double ang = -0.65 + 4.8 * t;
                double r = (4.0 + lane * 0.9 + t * 0.35) / 16.0;
                pts[i] = surfaceCenter.add(axisA.scale(Math.cos(ang) * r)).add(axisB.scale(Math.sin(ang) * r));
            }
            tube(pts, normal, color, true, true);
        }

        private static Vec3[] sampleBezier(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int steps, Vec3 normal, double lift) {
            Vec3[] path = new Vec3[steps + 1];
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps, s = 1.0 - t;
                Vec3 p = a.scale(s * s * s)
                        .add(b.scale(3.0 * s * s * t))
                        .add(c.scale(3.0 * s * t * t))
                        .add(d.scale(t * t * t));
                if (lift > 0.0) {
                    double arch = Math.sin(Math.PI * t);
                    p = p.add(normal.scale(lift * arch * arch));
                }
                path[i] = p;
            }
            return path;
        }

        private void tube(Vec3[] path, Vec3 refSide, int color, boolean capStart, boolean capEnd) {
            tube(path, refSide, color, capStart, capEnd, RADIUS);
        }

        private void tube(Vec3[] path, Vec3 refSide, int color, boolean capStart, boolean capEnd, double radius) {
            if (path.length < 2) return;
            Vec3 side = refSide;
            Vec3[] previous = null;
            Vec3[] firstRing = null;
            for (int i = 0; i < path.length; i++) {
                Vec3 diff = path[Math.min(i + 1, path.length - 1)].subtract(path[Math.max(i - 1, 0)]);
                if (diff.lengthSqr() < 1e-8) continue;
                Vec3 tangent = diff.normalize();
                side = side.subtract(tangent.scale(side.dot(tangent)));
                if (side.lengthSqr() < 1e-4) {
                    side = tangent.cross(Math.abs(tangent.y) < 0.9 ? UP : new Vec3(1, 0, 0));
                }
                side = side.normalize();
                Vec3[] current = ring(path[i], side, tangent.cross(side), radius);
                if (firstRing == null) firstRing = current;
                if (previous != null) join(previous, current, path[i - 1], color);
                previous = current;
            }
            if (capStart && firstRing != null) cap(firstRing, color);
            if (capEnd && previous != null) cap(previous, color);
        }

        private static Vec3[] ring(Vec3 center, Vec3 u, Vec3 v, double radius) {
            Vec3[] points = new Vec3[8];
            for (int i = 0; i < 8; i++) {
                double angle = (i + 0.5) * Math.PI * 2.0 / 8.0;
                points[i] = center.add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
            }
            return points;
        }

        private void join(Vec3[] first, Vec3[] last, Vec3 center, int color) {
            for (int i = 0; i < first.length; i++) {
                int j = (i + 1) % first.length;
                Vec3 n = first[i].add(first[j]).scale(0.5).subtract(center).normalize();
                float shade = (float) (0.77 + n.y * 0.16 - n.x * 0.07);
                quads.add(new Quad(first[i], first[j], last[j], last[i], PortalGateMesh.shade(color, shade)));
            }
        }

        private void cap(Vec3[] ring, int color) {
            int shaded = PortalGateMesh.shade(color, 0.80F);
            for (int i = 1; i < ring.length - 1; i++) {
                quads.add(new Quad(ring[0], ring[i], ring[i + 1], ring[0], shaded));
            }
        }

        void draw(VertexConsumer consumer, PoseStack.Pose pose, int light) {
            Matrix4f matrix = pose.pose();
            for (Quad quad : quads) {
                int argb = quad.color | 0xff000000;
                vertex(consumer, pose, matrix, quad.a, argb, light);
                vertex(consumer, pose, matrix, quad.b, argb, light);
                vertex(consumer, pose, matrix, quad.c, argb, light);
                vertex(consumer, pose, matrix, quad.d, argb, light);
            }
        }
    }

    private record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {}

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Matrix4f matrix, Vec3 p, int argb, int light) {
        out.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .setColor(argb)
                .setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
