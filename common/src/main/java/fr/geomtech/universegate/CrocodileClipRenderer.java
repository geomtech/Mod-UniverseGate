package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.geomtech.universegate.compat.ImmediateBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/** The two leads end inside the crimp barrels. On a formed gate each spring jaw bites one face of the rim. */
public final class CrocodileClipRenderer extends ImmediateBlockEntityRenderer<CrocodileClipEntity> {
    private static final Vec3 CENTER = new Vec3(.5, .5, .5);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int RED = 0xe63c37, BLUE = 0x4678eb, STEEL = 0xb5c2c7, RUBBER = 0x34363b;
    private static final Direction[] DIRECTIONS = Direction.values();
    private final Map<CrocodileClipEntity, Geometry> cache = new WeakHashMap<>();

    public CrocodileClipRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected AABB renderBounds(CrocodileClipEntity clip) {
        return new AABB(clip.getBlockPos()).inflate(2);
    }

    @Override
    public void render(CrocodileClipEntity clip, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = clip.getLevel();
        if (level == null) return;
        Geometry geometry = cache.computeIfAbsent(clip, unused -> new Geometry());
        BlockPos pos = clip.getBlockPos();
        Direction grip = clip.getBlockState().getValue(CrocodileClipBlock.FACING);
        boolean changed = grip != geometry.grip;
        for (Direction side : DIRECTIONS) {
            BlockState state = level.getBlockState(pos.relative(side));
            int i = side.ordinal();
            changed |= state != geometry.neighbors[i];
            geometry.neighbors[i] = state;
        }
        // A core across a chunk boundary can arrive later than the clip and its immediate neighbours.
        if (changed || (geometry.waitingForCore && level.getGameTime() >= geometry.retryAt)) {
            geometry.rebuild(level, pos, grip);
        }
        float brightness = .32f + .68f * Math.max((light >> 20) & 15, (light >> 4) & 15) / 15f;
        geometry.draw(buffers.getBuffer(PortalGateRenderTypes.GATE_SOLID), poses.last().pose(), brightness);
    }

    private record Bite(Vec3 lip, Vec3 back, Vec3 opening, double gap, double lean) {}

    private static Vec3 vector(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    private static final class Geometry {
        private final BlockState[] neighbors = new BlockState[6];
        private final List<Quad> quads = new ArrayList<>();
        private Direction grip;
        private boolean waitingForCore;
        private long retryAt;

        void rebuild(Level level, BlockPos pos, Direction facing) {
            grip = facing;
            quads.clear();
            BlockPos supportPos = pos.relative(grip);
            BlockState support = neighbors[grip.ordinal()];
            boolean core = support.is(ModBlocks.PORTAL_CORE) && support.getValue(PortalCoreBlock.FORMED);
            boolean frame = support.is(ModBlocks.PORTAL_FRAME) && support.getValue(PortalFrameBlock.FORMED);
            BlockPos origin = core ? supportPos : frame ? PortalFrameDetector.findCoreOfFrame(level, supportPos).orElse(null) : null;
            Direction right = origin == null ? null : PortalGateAnimator.resolveRight(level, origin);
            waitingForCore = (core || frame) && right == null;
            retryAt = level.getGameTime() + 20;
            for (int lane : new int[]{1, -1}) {
                Bite bite = right != null ? ringBite(pos, origin, right, lane) : surfaceBite(support, lane);
                int color = lane > 0 ? RED : BLUE;
                Vec3 socket = hardware(bite, color);
                for (Direction side : DIRECTIONS) {
                    if (side == grip) continue;
                    BlockState wire = neighbors[side.ordinal()];
                    if (!ElectricWireBlock.isWire(wire) ||
                            wire.getValue(ElectricWireBlock.sideProperty(side.getOpposite())) == RedstoneSide.NONE) continue;
                    Direction mount = wire.getValue(ElectricWireBlock.FACING);
                    if (side.getAxis() == mount.getAxis()) continue;
                    // Same face centre, height and transverse lane as the baked model in wire-models.gradle.
                    Vec3 inward = vector(side).reverse();
                    Vec3 transverse = vector(side).cross(vector(mount));
                    transverse = new Vec3(Math.abs(transverse.x), Math.abs(transverse.y), Math.abs(transverse.z));
                    Vec3 start = CENTER.add(vector(side).scale(.5)).add(vector(mount).scale(.5 - .72/16))
                            .add(transverse.scale(lane*1.45/16));
                    lead(start, inward, socket, bite, color, mount);
                }
            }
        }

        private Bite ringBite(BlockPos pos, BlockPos core, Direction right, int lane) {
            Vec3 horizontal = vector(right);
            Vec3 depth = vector(right.getClockWise());
            Vec3 center = Vec3.atLowerCornerOf(core.subtract(pos)).add(.5, PortalGateRingLayer.CENTER_V/16, .5);
            Vec3 toward = CENTER.subtract(center);
            double angle = Math.atan2(toward.y, toward.dot(horizontal));
            double radius = Math.hypot(toward.y, toward.dot(horizontal));
            boolean inner = radius < (PortalGateRingLayer.OUTER_RADIUS + PortalGateRingLayer.INNER_RADIUS)/32;
            // At the foot, clip the exposed inner edge above the plinth instead of putting handles underground.
            if (Math.sin(angle) < -.8) inner = true;
            double rim = (inner ? PortalGateRingLayer.INNER_RADIUS : PortalGateRingLayer.OUTER_RADIUS)/16;
            angle += lane * .15 / rim;
            Vec3 radial = horizontal.scale(Math.cos(angle)).add(UP.scale(Math.sin(angle)));
            Vec3 back = radial.scale(inner ? -1 : 1);
            double front = toward.dot(depth) < 0 ? -1 : 1;
            double halfThickness = (PortalGateRingLayer.RING_Z1 - PortalGateRingLayer.RING_Z0)/32;
            return new Bite(center.add(radial.scale(rim)), back, depth.scale(front), halfThickness, .16);
        }

        private Bite surfaceBite(BlockState support, int lane) {
            Vec3 normal = vector(grip).reverse();
            Vec3 along = grip.getAxis().isVertical() ? new Vec3(0, 0, 1) : new Vec3(0, -1, 0);
            Vec3 across = normal.cross(along);
            double inset = 0;
            if (support.is(ModBlocks.LIGHTNING_ANTENNA) && grip.getAxis().isHorizontal()) {
                inset = switch (support.getValue(LightningAntennaBlock.PART)) {
                    case BASE, LOWER -> .25;
                    case UPPER -> .32;
                    case TOP -> .40;
                };
            }
            Vec3 lip = CENTER.add(vector(grip).scale(.47 + inset)).add(across.scale(lane*.14));
            return new Bite(lip, along, normal, .022, .14);
        }

        private Vec3 hardware(Bite bite, int color) {
            Vec3 back = bite.back, open = bite.opening, axle = back.cross(open).normalize();
            Vec3 pivot = bite.lip.add(back.scale(.22)).add(open.scale(bite.lean*.5));
            Vec3 socket = pivot.add(back.scale(.34)).add(open.scale(bite.lean));
            for (int sign : new int[]{-1, 1}) {
                Vec3 tip = bite.lip.subtract(back.scale(.065)).add(open.scale(sign*(bite.gap+.012)));
                Vec3 elbow = bite.lip.add(back.scale(.065)).add(open.scale(sign*(bite.gap+.047)));
                solid(pivot, elbow, axle, .056, .035, .047, .031, 4, STEEL);
                solid(elbow, tip, axle, .047, .028, .037, .017, 4, STEEL);
                for (int i = 0; i < 3; i++) {
                    Vec3 tooth = tip.lerp(elbow, .12 + i*.27);
                    solid(tooth, tooth.subtract(open.scale(sign*.038)), axle, .030, .018, .024, .009, 4, STEEL);
                }
            }
            solid(pivot.subtract(axle.scale(.068)), pivot.add(axle.scale(.068)), back, .046,.046,.046,.046,8,0x69767b);
            Vec3 boot = pivot.lerp(socket,.19);
            solid(pivot, boot, axle,.047,.045,.059,.059,8,STEEL);
            solid(boot, socket, axle,.070,.066,.049,.048,8,color);
            Vec3 lever = pivot.add(back.scale(.23)).add(open.scale(bite.lean + .105));
            solid(pivot, pivot.lerp(lever,.46), axle,.041,.028,.052,.025,4,STEEL);
            solid(pivot.lerp(lever,.40),lever,axle,.055,.035,.060,.031,8,color);
            Vec3 tail = socket.add(socket.subtract(pivot).normalize().scale(.055));
            solid(socket, tail, axle,.053,.052,.041,.041,8,RUBBER);
            return tail;
        }

        private void lead(Vec3 start, Vec3 inward, Vec3 socket, Bite bite, int color, Direction mount) {
            double length = start.distanceTo(socket);
            Vec3 b = start.add(inward.scale(Math.min(.30,length*.32)));
            Vec3 c = socket.add(bite.back.scale(Math.min(.34,length*.34))).add(bite.opening.scale(.09));
            Vec3[] path = new Vec3[13];
            path[0] = start;
            for (int i = 1; i < path.length; i++) {
                double t = i/12d, s = 1-t;
                Vec3 next = start.scale(s*s*s).add(b.scale(3*s*s*t)).add(c.scale(3*s*t*t)).add(socket.scale(t*t*t));
                next = next.add(0, -Math.min(.24,length*.14)*16*s*s*t*t, 0);
                if (mount == Direction.DOWN && start.y <= socket.y) next = new Vec3(next.x, Math.max(start.y,next.y), next.z);
                path[i] = next;
            }
            // Share the octagonal cross-section at each bend so the insulation stays closed.
            Vec3 side = vector(mount).cross(inward);
            Vec3[] previous = null;
            for (int i = 0; i < path.length; i++) {
                Vec3 tangent = path[Math.min(i+1,path.length-1)].subtract(path[Math.max(i-1,0)]).normalize();
                side = side.subtract(tangent.scale(side.dot(tangent))).normalize();
                Vec3[] current = ring(path[i], side, tangent.cross(side), .039, .039, 8);
                if (previous != null) join(previous, current, path[i-1], color);
                previous = current;
            }
        }

        // Tapered cross-sections give both the rubber insulation and the stamped metal visible thickness.
        private void solid(Vec3 a, Vec3 b, Vec3 reference, double wa, double ha, double wb, double hb, int sides, int color) {
            Vec3 axis = b.subtract(a).normalize();
            if (axis.lengthSqr() < .5) return;
            Vec3 v = axis.cross(reference);
            if (v.lengthSqr() < .0001) v = axis.cross(Math.abs(axis.y) < .9 ? UP : new Vec3(1,0,0));
            v = v.normalize();
            Vec3 u = v.cross(axis);
            Vec3[] first = ring(a,u,v,wa,ha,sides);
            Vec3[] last = ring(b,u,v,wb,hb,sides);
            join(first,last,a,color);
            // Close each end with triangle fans (a degenerate quad is a triangle).
            for (int i=1; i<sides-1; i++) {
                quads.add(new Quad(first[0],first[i],first[i+1],first[0],color));
                quads.add(new Quad(last[0],last[i],last[i+1],last[0],color));
            }
        }

        private static Vec3[] ring(Vec3 center, Vec3 u, Vec3 v, double width, double height, int sides) {
            Vec3[] points = new Vec3[sides];
            for (int i=0; i<sides; i++) {
                double angle = (i+.5)*Math.PI*2/sides;
                points[i] = center.add(u.scale(Math.cos(angle)*width)).add(v.scale(Math.sin(angle)*height));
            }
            return points;
        }

        private void join(Vec3[] first, Vec3[] last, Vec3 center, int color) {
            for (int i=0; i<first.length; i++) {
                int j = (i+1)%first.length;
                Vec3 normal = first[i].add(first[j]).scale(.5).subtract(center).normalize();
                float shade = (float)(.77 + normal.y*.16 - normal.x*.07);
                quads.add(new Quad(first[i], first[j], last[j], last[i], PortalGateMesh.shade(color,shade)));
            }
        }

        void draw(VertexConsumer consumer, Matrix4f matrix, float brightness) {
            for (Quad quad : quads) {
                int rgb = PortalGateMesh.shade(quad.color,brightness) | 0xff000000;
                vertex(consumer,matrix,quad.a,rgb); vertex(consumer,matrix,quad.b,rgb);
                vertex(consumer,matrix,quad.c,rgb); vertex(consumer,matrix,quad.d,rgb);
            }
        }
    }

    private record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {}

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, int argb) {
        out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setColor(argb);
    }
}
