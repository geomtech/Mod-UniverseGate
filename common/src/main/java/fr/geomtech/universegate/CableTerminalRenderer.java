package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.geomtech.universegate.compat.ImmediateBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Draws connections from the baked wire ports to the fixed mechanical terminals. */
public final class CableTerminalRenderer<T extends BlockEntity> extends ImmediateBlockEntityRenderer<T> {
    private static final Vec3 CENTER = new Vec3(.5, .5, .5);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int RED = 0xe63c37, BLUE = 0x4678eb, STEEL = 0xb5c2c7, RUBBER = 0x34363b;
    private static final int COPPER = 0xd9894f, GALVANIZED = 0x859499;
    private static final Direction[] DIRECTIONS = Direction.values();
    private final Map<T, Geometry> cache = new WeakHashMap<>();

    public CableTerminalRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected AABB renderBounds(T terminal) {
        return new AABB(terminal.getBlockPos()).inflate(terminal instanceof CrocodileClipEntity ? 7 : 1);
    }

    @Override
    public void render(T terminal, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = terminal.getLevel();
        if (level == null) return;
        Geometry geometry = cache.computeIfAbsent(terminal, unused -> new Geometry());
        BlockPos pos = terminal.getBlockPos();
        boolean antenna = terminal instanceof LightningAntennaBlockEntity;
        Direction grip = antenna ? terminal.getBlockState().getValue(LightningAntennaBlock.FACING)
                : terminal.getBlockState().getValue(CrocodileClipBlock.FACING);
        boolean changed = grip != geometry.grip;
        for (Direction side : DIRECTIONS) {
            BlockState state = level.getBlockState(pos.relative(side));
            int i = side.ordinal();
            changed |= state != geometry.neighbors[i];
            geometry.neighbors[i] = state;
        }
        // A core across a chunk boundary can arrive later than the clip and its immediate neighbours.
        if (!changed && level.getGameTime() >= geometry.retryAt) {
            changed = geometry.waitingForCore || (geometry.corePos != null
                    && geometry.buried != PortalGateBaseLayer.isBuried(level, geometry.corePos, geometry.right));
            geometry.retryAt = level.getGameTime() + 20;
        }
        if (changed) {
            geometry.rebuild(level, pos, grip, antenna);
        }
        float brightness = .32f + .68f * Math.max((light >> 20) & 15, (light >> 4) & 15) / 15f;
        geometry.draw(buffers.getBuffer(PortalGateRenderTypes.GATE_SOLID), poses.last().pose(), brightness);
    }

    private record Bite(Vec3 lip, Vec3 back, Vec3 opening, double gap, double lean) {}
    private record Lug(Vec3 sleeve, Vec3 contact) {}

    private static Vec3 vector(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    private static final class Geometry {
        private final BlockState[] neighbors = new BlockState[6];
        private final List<Quad> quads = new ArrayList<>();
        private Direction grip;
        private BlockPos corePos;
        private Direction right;
        private boolean buried;
        private boolean waitingForCore;
        private long retryAt;

        void rebuild(Level level, BlockPos pos, Direction facing, boolean antenna) {
            grip = facing;
            quads.clear();
            BlockPos supportPos = pos.relative(grip);
            BlockState support = neighbors[grip.ordinal()];
            boolean core = !antenna && support.is(ModBlocks.PORTAL_CORE) && support.getValue(PortalCoreBlock.FORMED);
            boolean frame = !antenna && support.is(ModBlocks.PORTAL_FRAME) && support.getValue(PortalFrameBlock.FORMED);
            corePos = core ? supportPos : frame ? PortalFrameDetector.findCoreOfFrame(level, supportPos).orElse(null) : null;
            right = corePos == null ? null : PortalGateAnimator.resolveRight(level, corePos);
            if (right == null) corePos = null;
            buried = right != null && PortalGateBaseLayer.isBuried(level, corePos, right);
            waitingForCore = (core || frame) && right == null;
            retryAt = level.getGameTime() + 20;
            boolean onMast = !antenna && support.is(ModBlocks.LIGHTNING_ANTENNA);
            Vec3 mast = antenna ? Vec3.ZERO : onMast
                    ? Vec3.atLowerCornerOf(LightningAntennaBlock.basePos(supportPos,support).subtract(pos)) : null;
            Direction mastFacing = onMast ? support.getValue(LightningAntennaBlock.FACING) : facing;
            Vec3 foot = null, front = null, across = null;
            if (right != null) {
                across = vector(right);
                front = vector(right.getClockWise());
                Vec3 origin = Vec3.atLowerCornerOf(corePos.subtract(pos)).add(.5,0,.5);
                Vec3 relative = CENTER.subtract(origin);
                if (relative.dot(front) < 0) front = front.reverse();
                double lateral = relative.dot(across) < 0 ? -1.53 : 1.53;
                foot = origin.add(across.scale(lateral)).add(front.scale(.472))
                        .add(0, buried ? 1 + 1.5/16 : .75, 0);
                collar(foot, across, front);
            }
            for (int lane : new int[]{1, -1}) {
                int color = lane > 0 ? RED : BLUE;
                Bite bite = mast == null && foot == null ? surfaceBite(lane) : null;
                Vec3 socket = bite == null ? null : hardware(bite, color);
                Lug lug = foot == null ? null : bindingPost(foot, across, front, lane);
                boolean connected = false;
                for (Direction side : DIRECTIONS) {
                    if (!antenna && side == grip) continue;
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
                    if (mast != null) mastLead(start, inward, mast, mastFacing, lane, color, mount);
                    else if (lug != null) {
                        if (!connected) stripped(lug.sleeve, lug.contact, across);
                        connected = true;
                        footLead(start, inward, lug.sleeve, front, across, pos, color, mount, lane);
                    }
                    else lead(start, inward, socket, bite, color, mount);
                }
            }
        }

        // A saddle bolted to the static foot. The two live lugs sit on separate insulating spacers.
        private void collar(Vec3 foot, Vec3 across, Vec3 front) {
            box(foot.add(0,.026,0), across, front, .40,.105,.032,GALVANIZED);
            box(foot.add(front.scale(.09)).add(0,-.04,0), across, front, .40,.023,.074,STEEL);
            for (int side : new int[]{-1,1}) {
                Vec3 bolt = foot.add(across.scale(side*.333)).add(0,.052,0);
                solid(bolt, bolt.add(0,.031,0), across,.039,.039,.039,.039,6,STEEL);
            }
        }

        private Lug bindingPost(Vec3 foot, Vec3 across, Vec3 front, int lane) {
            Vec3 post = foot.add(across.scale(lane*.16)).add(0,.06,0);
            solid(post,post.add(0,.055,0),across,.091,.091,.086,.086,8,RUBBER);
            Vec3 washer = post.add(0,.056,0);
            solid(washer,washer.add(0,.022,0),across,.074,.074,.074,.074,12,COPPER);
            Vec3 screw = washer.add(0,.02,0);
            solid(screw,screw.add(0,.043,0),across,.054,.054,.054,.054,6,STEEL);
            Vec3 entry = washer.add(front.scale(.24)).add(0,-.015,0);
            return new Lug(entry, washer.add(front.scale(.024)).add(0,.012,0));
        }

        private void stripped(Vec3 start, Vec3 end, Vec3 across) {
            // Three copper strands twisted together between the end of the insulation and the screw.
            Vec3 axis = end.subtract(start).normalize(), up = axis.cross(across).normalize();
            for (int strand=0; strand<3; strand++) {
                Vec3 prev = null;
                for (int i=0; i<=7; i++) {
                    double t = i/7d, angle = (strand/3d + t*.78)*Math.PI*2;
                    Vec3 p = start.lerp(end,t).add(across.scale(.012*Math.cos(angle))).add(up.scale(.012*Math.sin(angle)));
                    if (prev != null) solid(prev,p,across,.013,.013,.013,.013,5,strand==1?0xf3b174:COPPER);
                    prev = p;
                }
            }
        }

        private void box(Vec3 center, Vec3 across, Vec3 front, double w, double d, double h, int color) {
            Vec3[] bottom = new Vec3[4], top = new Vec3[4];
            for (int i=0; i<4; i++) {
                Vec3 p = center.add(across.scale(i<2 ? -w:w)).add(front.scale(i==0||i==3 ? -d:d));
                bottom[i] = p.add(0,-h,0);
                top[i] = p.add(0,h,0);
            }
            join(bottom,top,center.add(0,-h,0),color);
            quads.add(new Quad(top[0],top[1],top[2],top[3],color));
            quads.add(new Quad(bottom[3],bottom[2],bottom[1],bottom[0],color));
        }

        private void footLead(Vec3 start, Vec3 inward, Vec3 end, Vec3 front, Vec3 across,
                              BlockPos pos, int color, Direction mount, int lane) {
            List<Vec3> path = new ArrayList<>();
            path.add(start);
            path.add(start.add(inward.scale(.17)));
            if (start.y > end.y + 1.25) {
                // Bring elevated runs around the ring and down in front of the stationary foot.
                Vec3 center = Vec3.atLowerCornerOf(corePos.subtract(pos)).add(.5,0,.5);
                double sign = end.subtract(center).dot(across) < 0 ? -1 : 1;
                Vec3 guide = center.add(across.scale(sign*(3.13 + lane*.055))).add(front.scale(.75));
                path.add(new Vec3(guide.x,start.y,guide.z));
                path.add(new Vec3(guide.x,end.y+.10,guide.z));
            }
            path.add(end.add(front.scale(.24)).add(0,-.045,0));
            path.add(end);
            route(path, vector(mount).cross(inward), color);
        }

        private void mastLead(Vec3 start, Vec3 inward, Vec3 offset, Direction facing, int lane, int color, Direction mount) {
            Vec3 front = vector(facing), across = vector(facing.getClockWise());
            Vec3 base = offset.add(.5,0,.5);
            // Match the existing north-facing model, rotated with its blockstate: red at x=5.5, blue at x=10.5.
            Vec3 post = base.add(front.scale(5/16d)).add(across.scale(-lane*2.5/16)).add(0,6.12/16,0);
            Vec3 entry = post.add(front.scale(.145)).add(0,-.025,0);
            stripped(entry, post, across);
            List<Vec3> path = new ArrayList<>();
            path.add(start);
            Vec3 relative = start.subtract(base);
            double x = relative.dot(across), z = relative.dot(front);
            double side = Math.abs(x) > .27 ? Math.signum(x) : -lane;
            double clearance = .39 + lane*.033;
            double shelf = .20 + (lane+1)*.025;
            // Rise at the edge of the footplate before passing around the square housing.
            double lift = Math.max(start.y,base.y+shelf);
            Vec3 rise = start.add(inward.scale(.015));
            path.add(new Vec3(rise.x,lift,rise.z));
            if (z < -.26) path.add(base.add(across.scale(side*clearance)).subtract(front.scale(clearance)).add(0,shelf,0));
            if (z < .28) path.add(base.add(across.scale(side*clearance)).add(front.scale(clearance)).add(0,shelf,0));
            path.add(entry.add(front.scale(.095)).add(0,-.09,0));
            path.add(entry);
            route(path,vector(mount).cross(inward),color);
        }

        /** Fillet the bends while retaining the exact port and stripped-wire endpoints. */
        private void route(List<Vec3> points, Vec3 side, int color) {
            List<Vec3> smooth = new ArrayList<>();
            smooth.add(points.getFirst());
            for (int i=1; i<points.size()-1; i++) {
                Vec3 p=points.get(i), before=p.lerp(points.get(i-1),.31), after=p.lerp(points.get(i+1),.31);
                for (int j=0; j<=6; j++) {
                    double t=j/6d;
                    Vec3 next=before.lerp(p,t).lerp(p.lerp(after,t),t);
                    if (next.distanceToSqr(smooth.getLast()) > .000001) smooth.add(next);
                }
            }
            smooth.add(points.getLast());
            tube(smooth.toArray(Vec3[]::new),side,color);
        }

        private Bite surfaceBite(int lane) {
            Vec3 normal = vector(grip).reverse();
            Vec3 along = grip.getAxis().isVertical() ? new Vec3(0, 0, 1) : new Vec3(0, -1, 0);
            Vec3 across = normal.cross(along);
            Vec3 lip = CENTER.add(vector(grip).scale(.47)).add(across.scale(lane*.14));
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
            tube(path,vector(mount).cross(inward),color);
        }

        private void tube(Vec3[] path, Vec3 side, int color) {
            Vec3[] previous = null;
            for (int i = 0; i < path.length; i++) {
                Vec3 tangent = path[Math.min(i+1,path.length-1)].subtract(path[Math.max(i-1,0)]).normalize();
                side = side.subtract(tangent.scale(side.dot(tangent)));
                if (side.lengthSqr() < .0001) side = tangent.cross(Math.abs(tangent.y) < .9 ? UP : new Vec3(1,0,0));
                side = side.normalize();
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
