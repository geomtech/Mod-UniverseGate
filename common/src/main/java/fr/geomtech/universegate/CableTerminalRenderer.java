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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
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
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "textures/block/cable_white.png");
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
                    && (geometry.buried != PortalGateBaseLayer.isBuried(level, geometry.corePos, geometry.right)
                    || geometry.terrain.entrySet().stream().anyMatch(e -> level.getBlockState(e.getKey()) != e.getValue())));
            geometry.retryAt = level.getGameTime() + 20;
        }
        if (changed) {
            geometry.rebuild(level, pos, grip, antenna);
        }
        //? if >=1.21.11 {
        /*VertexConsumer consumer = buffers.getBuffer(RenderTypes.entityCutoutNoCull(WHITE));
        *///?} else {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        //?}
        geometry.draw(consumer, poses.last(), light);
    }

    private record Bite(Vec3 lip, Vec3 back, Vec3 opening, double gap, double lean) {}
    private record Lug(Vec3 sleeve, Vec3 contact) {}
    private record WirePort(Vec3 center, Vec3 inward, Vec3 transverse, Direction mount) {
        Vec3 start(int lane) { return center.add(transverse.scale(lane * 1.45 / 16)); }
    }

    private static Vec3 vector(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    private static final class Geometry {
        private final BlockState[] neighbors = new BlockState[6];
        private final List<Quad> quads = new ArrayList<>();
        private final Map<BlockPos, BlockState> terrain = new HashMap<>();
        private Direction grip;
        private BlockPos corePos;
        private Direction right;
        private boolean buried;
        private boolean waitingForCore;
        private Direction keyboardFacing;
        private long retryAt;

        void rebuild(Level level, BlockPos pos, Direction facing, boolean antenna) {
            grip = facing;
            quads.clear();
            terrain.clear();
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
            keyboardFacing = !antenna && (support.is(ModBlocks.PORTAL_KEYBOARD)
                            || support.is(ModBlocks.PORTAL_ACTIVATOR))
                    ? support.getValue(PortalKeyboardBlock.FACING) : null;
            Vec3 mast = antenna ? Vec3.ZERO : onMast
                    ? Vec3.atLowerCornerOf(LightningAntennaBlock.basePos(supportPos,support).subtract(pos)) : null;
            Direction mastFacing = onMast ? support.getValue(LightningAntennaBlock.FACING) : facing;
            List<WirePort> ports = new ArrayList<>();
            for (Direction side : DIRECTIONS) {
                if (!antenna && side == grip) continue;
                BlockState wire = neighbors[side.ordinal()];
                if (!ElectricWireBlock.isWire(wire) ||
                        wire.getValue(ElectricWireBlock.sideProperty(side.getOpposite())) == RedstoneSide.NONE) continue;
                Direction mount = wire.getValue(ElectricWireBlock.FACING);
                if (side.getAxis() == mount.getAxis()) continue;
                Vec3 transverse = vector(side).cross(vector(mount));
                // Match the exact ports of the baked models, on floors, walls and ceilings.
                Vec3 center = CENTER.add(vector(side).scale(.5)).add(vector(mount).scale(.5 - .72/16));
                ports.add(new WirePort(center, vector(side).reverse(), new Vec3(Math.abs(transverse.x),
                        Math.abs(transverse.y), Math.abs(transverse.z)), mount));
            }
            if (right != null) {
                portalTerminal(level, pos, ports);
                return;
            }
            for (int lane : new int[]{1, -1}) {
                int color = lane > 0 ? RED : BLUE;
                Bite bite = mast == null ? surfaceBite(lane) : null;
                Vec3 socket = bite == null ? null : hardware(bite, color);
                for (WirePort port : ports) {
                    if (mast != null) mastLead(port.start(lane), port.inward, mast, mastFacing, lane, color, port.mount);
                    else lead(port.start(lane), port.inward, socket, bite, color, port.mount);
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

        private Lug bindingPost(Vec3 foot, Vec3 across, Vec3 approach, int lane) {
            Vec3 post = foot.add(across.scale(lane*.16)).add(0,.06,0);
            solid(post,post.add(0,.055,0),across,.091,.091,.086,.086,8,RUBBER);
            Vec3 washer = post.add(0,.056,0);
            solid(washer,washer.add(0,.022,0),across,.074,.074,.074,.074,12,COPPER);
            Vec3 screw = washer.add(0,.02,0);
            solid(screw,screw.add(0,.043,0),across,.054,.054,.054,.054,6,STEEL);
            Vec3 contact = washer.add(approach.scale(.048)).add(0,.012,0);
            return new Lug(contact.add(approach.scale(.088)).add(0,-.022,0), contact);
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

        private void portalTerminal(Level level, BlockPos pos, List<WirePort> ports) {
            Vec3 across = vector(right), front = vector(right.getClockWise());
            Vec3 origin = Vec3.atLowerCornerOf(corePos.subtract(pos)).add(.5,0,.5);
            WirePort feed = ports.isEmpty() ? null : ports.getFirst();
            Vec3 incoming = feed == null ? CENTER : feed.center;
            Vec3 relative = incoming.subtract(origin);
            double face = CENTER.subtract(origin).dot(front);
            if ((face == 0 ? relative.dot(front) : face) < 0) front = front.reverse();
            double side = relative.dot(across) < 0 ? -1 : 1;
            double floor = buried ? 1 + 1.5/16 : .75;
            Vec3 seat = origin.add(across.scale(side*1.53)).add(front.scale(.472)).add(0,floor,0);
            if (Math.abs(CENTER.subtract(origin).dot(across)) < .5 && CENTER.y - origin.y <= floor + .9) {
                // A clip on the core can bolt straight through its top, including a flush buried core.
                seat = origin.add(front.scale(.10)).add(0, buried ? 1 : floor, 0);
            } else if (!buried) {
                // The exposed plinth has a continuous top. Put the saddle under the actual clip.
                double lateral = Math.clamp(CENTER.subtract(origin).dot(across), -1.52, 1.52);
                seat = origin.add(across.scale(lateral)).add(front.scale(.472)).add(0,floor,0);
            }
            collar(seat, across, front);
            Vec3 delta = incoming.subtract(seat);
            // Angle the cable entries towards the feed but keep the copper on the outer face.
            Vec3 approach = across.scale(Math.clamp(delta.dot(across), -.85, .85))
                    .add(front.scale(Math.max(.45, delta.dot(front)))).normalize();
            Vec3 contactSide = across;
            if (feed != null) {
                double sourceOrder = UP.cross(feed.inward).dot(feed.transverse);
                double targetOrder = UP.cross(approach.reverse()).dot(across);
                if (sourceOrder * targetOrder < 0 || (sourceOrder == 0 && feed.transverse.dot(across) < 0))
                    contactSide = across.reverse();
            }
            for (int lane : new int[]{1, -1}) {
                Lug lug = bindingPost(seat, contactSide, approach, lane);
                if (ports.isEmpty()) continue;
                Vec3 strandSide = UP.cross(approach).normalize();
                stripped(lug.sleeve, lug.contact, strandSide);
                for (WirePort port : ports) {
                    portalLead(level, pos, port, lug.sleeve, contactSide, approach, front, lane);
                }
            }
        }

        // Sample actual collision boxes so both leads can settle on slabs, stairs or a full floor.
        private double restingHeight(Level level, BlockPos pos, Vec3 point, double ceiling) {
            Vec3 world = Vec3.atLowerCornerOf(pos).add(point);
            int x = (int)Math.floor(world.x), z = (int)Math.floor(world.z);
            int top = (int)Math.floor(pos.getY()+ceiling);
            for (int y=top; y>=corePos.getY()-3; y--) {
                BlockPos block = new BlockPos(x,y,z);
                BlockState state = level.getBlockState(block);
                terrain.put(block, state);
                if (state.is(ModBlocks.PORTAL_FRAME) || state.is(ModBlocks.PORTAL_CORE)) continue;
                double height = Double.NEGATIVE_INFINITY;
                for (AABB box : state.getCollisionShape(level, block).toAabbs()) {
                    if (world.x-x >= box.minX && world.x-x <= box.maxX && world.z-z >= box.minZ && world.z-z <= box.maxZ
                            && y+box.maxY <= pos.getY()+ceiling+.001) height = Math.max(height, y+box.maxY);
                }
                if (Double.isFinite(height)) return height-pos.getY()+.042;
            }
            return Double.NEGATIVE_INFINITY;
        }

        private void portalLead(Level level, BlockPos pos, WirePort port, Vec3 end, Vec3 across,
                                Vec3 approach, Vec3 front, int lane) {
            Vec3 start = port.center;
            Vec3 centerEnd = end.subtract(across.scale(lane*.16));
            double ceiling = Math.max(start.y, end.y) + .05;
            Vec3 entry = port.start(lane);
            List<Vec3> run = new ArrayList<>();
            boolean hanging = start.y > end.y + .5;
            if (hanging) {
                // Clear the lip, drop almost vertically, then turn onto the ground outside the plinth.
                Vec3 lip = start.add(port.inward.scale(.20));
                Vec3 landing = lip.add(front.scale(Math.max(.12, .88 - lip.subtract(centerEnd).dot(front))));
                double ground = restingHeight(level, pos, landing, start.y-.09);
                // Across a void only pay out a short free bight.
                if (!Double.isFinite(ground)) ground = centerEnd.y-.44;
                ground = Math.min(ground, start.y-.10);
                Vec3 floorDirection = centerEnd.subtract(landing).multiply(1,0,1).normalize();
                Vec3 lateral = UP.cross(floorDirection);
                if (lateral.dot(port.transverse) < -.001 ||
                        (Math.abs(lateral.dot(port.transverse)) <= .001 && lateral.dot(across) < 0)) lateral = lateral.reverse();
                Vec3 groundCenter = new Vec3(landing.x,ground,landing.z);
                Vec3 touch = groundCenter.add(lateral.scale(lane*1.45/16));
                Vec3 shoulder = lip.add(port.transverse.scale(lane*1.45/16));
                Vec3 knee = touch.subtract(floorDirection.scale(.23));
                run.add(entry);
                run.add(shoulder);
                run.add(shoulder.lerp(knee,.14).add(0,-Math.min(.25,(entry.y-ground)*.15),0));
                run.add(new Vec3(knee.x,ground + Math.min(.25,(entry.y-ground)*.32),knee.z));
                run.add(touch);
                port = new WirePort(groundCenter, floorDirection, lateral, Direction.DOWN);
            }
            // Route each conductor along its own Bézier from its port to its lug so tight inner bends never cusp.
            Vec3 laneStart = port.start(lane);
            double span = laneStart.distanceTo(end);
            Vec3 b = laneStart.add(port.inward.scale(Math.min(.28, span * .38)));
            Vec3 c = end.add(approach.scale(Math.min(.32, span * .38))).add(0, -Math.min(.06, span * .05), 0);
            double handedness = UP.cross(port.inward).dot(port.transverse);
            Vec3 exitNormal = UP.cross(approach.reverse()).scale(handedness);
            boolean crossover = exitNormal.dot(across) < 0;
            Vec3[] path = new Vec3[17];
            for (int i=0; i<path.length; i++) {
                double t = i/(double)(path.length-1), s = 1-t;
                Vec3 p = laneStart.scale(s*s*s).add(b.scale(3*s*s*t)).add(c.scale(3*s*t*t)).add(end.scale(t*t*t));
                double ground = restingHeight(level, pos, p, ceiling);
                if (Double.isFinite(ground)) {
                    // Settle early and lift only the last few centimetres as the wire reaches its lug.
                    double drop = Math.clamp(t/.25,0,1), rise = Math.clamp((t-.58)/.42,0,1);
                    double weight = drop*drop*(3-2*drop)*(1-rise*rise*(3-2*rise));
                    p = new Vec3(p.x, Math.max(ground, p.y+(ground-p.y)*weight), p.z);
                } else if (hanging) {
                    p = p.add(0,-.10*16*s*s*t*t,0);
                }
                path[i] = p;
                if (crossover && lane == 1) path[i] = path[i].add(0,.12*16*s*s*t*t,0);
            }
            // Fillet the knee together with the floor run to keep a continuous sleeve at touchdown.
            if (hanging) {
                for (int i=1; i<path.length; i++) run.add(path[i]);
                route(run, port.transverse, lane > 0 ? RED : BLUE);
            } else tube(path, vector(port.mount).cross(port.inward), lane > 0 ? RED : BLUE);
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
            if (keyboardFacing != null) return keyboardBite(keyboardFacing, lane);
            Vec3 normal = vector(grip).reverse();
            Vec3 along = grip.getAxis().isVertical() ? new Vec3(0, 0, 1) : new Vec3(0, -1, 0);
            Vec3 across = normal.cross(along);
            Vec3 lip = CENTER.add(vector(grip).scale(.47)).add(across.scale(lane*.14));
            return new Bite(lip, along, normal, .022, .14);
        }

        /**
         * Clamps onto the tilted desk rim of the player-built keyboard so the jaws straddle its top and
         * bottom faces and the insulated sleeves point outward toward the incoming wires.
         */
        private Bite keyboardBite(Direction kbFacing, int lane) {
            double cos = Math.cos(Math.toRadians(22.5)), sin = Math.sin(Math.toRadians(22.5));
            Vec3 forward = vector(kbFacing);
            Vec3 deskUp = UP.scale(cos).add(forward.scale(sin));
            Vec3 deskFwd = forward.scale(cos).subtract(UP.scale(sin));
            Vec3 deskCenter = CENTER.add(vector(grip)).add(0, 1.0 / 16.0, 0);

            if (grip.getAxis().isVertical()) {
                Vec3 back = vector(grip).reverse();
                Vec3 edge = deskCenter.add(deskUp.scale(grip == Direction.DOWN ? 1.5 / 16.0 : -1.0 / 16.0));
                Vec3 across = vector(kbFacing.getClockWise());
                if (across.x + across.z < 0) across = across.reverse();
                return new Bite(edge.add(across.scale(lane * .14)), back, forward, .035, .0);
            }

            Vec3 back;
            Vec3 edge;
            if (grip == kbFacing.getOpposite()) {
                edge = deskCenter.add(deskFwd.scale(5.0 / 16.0));
                back = deskFwd;
            } else if (grip == kbFacing) {
                edge = deskCenter.subtract(deskFwd.scale(5.0 / 16.0));
                back = deskFwd.reverse();
            } else {
                edge = deskCenter.add(vector(grip).reverse().scale(7.0 / 16.0));
                back = vector(grip).reverse();
            }
            Vec3 across = back.cross(deskUp).normalize();
            if (across.x + across.z < 0) across = across.reverse();
            Vec3 lip = edge.add(across.scale(lane * .14));
            return new Bite(lip, back, deskUp, .065, -.04);
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
