package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared, server-safe silhouette of the rendered ring, in model pixels. */
public final class PortalGateGeometry {
    public static final float CENTER_H = 8.0F;
    public static final float CENTER_V = 48.0F;
    public static final float OUTER_RADIUS = 47.0F;
    public static final float INNER_RADIUS = 35.0F;
    public static final float RING_Z0 = 4.5F;
    public static final float RING_Z1 = 11.5F;

    record Cell(int x, int y) {}
    record Gate(BlockPos core, Direction right) {}
    private static final Map<Cell, VoxelShape> RING_X = build(true);
    private static final Map<Cell, VoxelShape> RING_Z = build(false);
    private static final Map<Cell, VoxelShape> BURIED_ROW_X = buildBuriedRow(RING_X, true);
    private static final Map<Cell, VoxelShape> BURIED_ROW_Z = buildBuriedRow(RING_Z, false);

    public static final int SEGMENT_COUNT = 6;
    public static final int PIECE_COUNT = 17;
    static final int CORE_MASK = 1;
    static final int FULL_FRAME_MASK = (1 << (PIECE_COUNT + 1)) - 1;
    /**
     * Frame cells (x along the gate, y up from the core), numbered 1..17 and grouped by ring segment.
     * Segment {@code s} covers the arc {@code 30 + 60s} to {@code 90 + 60s} degrees; the top cell, exactly
     * on the 90 degree seam, belongs to segment 1.
     */
    private static final int[][] PIECES = {
            {2, 4}, {2, 5}, {1, 5},
            {0, 5}, {-1, 5}, {-2, 5}, {-2, 4},
            {-2, 3}, {-2, 2},
            {-2, 1}, {-2, 0}, {-1, 0},
            {1, 0}, {2, 0}, {2, 1},
            {2, 2}, {2, 3}
    };
    private static final int[] SEGMENT_START = {1, 4, 8, 10, 13, 16, 18};
    private static final Map<Integer, VoxelShape> SEGMENT_X = buildSegments(true);
    private static final Map<Integer, VoxelShape> SEGMENT_Z = buildSegments(false);

    private PortalGateGeometry() {}

    public static int pieceX(int piece) {
        return PIECES[piece - 1][0];
    }

    public static int pieceY(int piece) {
        return PIECES[piece - 1][1];
    }

    public static int pieceSegment(int piece) {
        for (int segment = 0; segment < SEGMENT_COUNT; segment++) {
            if (piece < SEGMENT_START[segment + 1]) return segment;
        }
        throw new IllegalArgumentException("piece " + piece);
    }

    public static int[] segmentPieces(int segment) {
        int[] pieces = new int[SEGMENT_START[segment + 1] - SEGMENT_START[segment]];
        for (int i = 0; i < pieces.length; i++) pieces[i] = SEGMENT_START[segment] + i;
        return pieces;
    }

    /** Segment containing the ring direction {@code degrees} (counter-clockwise from the gate's right). */
    public static int segmentAtAngle(double degrees) {
        double a = ((degrees - 30.0D) % 360.0D + 360.0D) % 360.0D;
        return Math.min(SEGMENT_COUNT - 1, (int) (a / 60.0D));
    }

    public static int ringSegment(int h, int v) {
        return segmentAtAngle(Math.toDegrees(Math.atan2(v + 0.5D - CENTER_V, h + 0.5D - CENTER_H)));
    }

    /** Collision shape of an anchor of a segment that is not assembled into a gate yet. */
    public static VoxelShape segmentShape(int piece, Direction.Axis axis) {
        return (axis == Direction.Axis.X ? SEGMENT_X : SEGMENT_Z).getOrDefault(piece, Shapes.empty());
    }

    public static boolean ringPixel(int h, int v) {
        double dx = h + 0.5D - CENTER_H;
        double dy = v + 0.5D - CENTER_V;
        double distanceSquared = dx * dx + dy * dy;
        return distanceSquared >= INNER_RADIUS * INNER_RADIUS
                && distanceSquared < OUTER_RADIUS * OUTER_RADIUS;
    }

    static VoxelShape shape(BlockState state, BlockGetter world, BlockPos pos, VoxelShape fallback) {
        // Some physics queries supply a local view without the block whose shape is requested.
        if (!stateAt(world, pos).is(state.getBlock())) return fallback;
        Gate gate = findGate(world, pos);
        if (gate == null) return fallback;
        int dx = gate.right().getAxis() == Direction.Axis.X
                ? pos.getX() - gate.core().getX() : pos.getZ() - gate.core().getZ();
        Cell cell = new Cell(dx, pos.getY() - gate.core().getY());
        boolean xAxis = gate.right().getAxis() == Direction.Axis.X;
        if (cell.y() == 0 && PortalBuriedFloor.isBuried(world, gate.core(), gate.right())
                && PortalBuriedFloor.materialAt(world, pos, gate.right()) != null) {
            return (xAxis ? BURIED_ROW_X : BURIED_ROW_Z).get(cell);
        }
        return (xAxis ? RING_X : RING_Z).getOrDefault(cell, Shapes.empty());
    }

    static BlockState stateAt(BlockGetter world, BlockPos pos) {
        if (world instanceof Level level && !level.isLoaded(pos)) return Blocks.AIR.defaultBlockState();
        // Physics mods can query shapes with a synthetic view that has no state at the query position.
        BlockState state = world.getBlockState(pos);
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }

    private static boolean formed(BlockState state) {
        return (state.is(ModBlocks.PORTAL_FRAME) || state.is(ModBlocks.PORTAL_CORE))
                && state.getValue(PortalFrameBlock.FORMED);
    }

    static @Nullable Direction rememberedRight(BlockState core) {
        if (!core.is(ModBlocks.PORTAL_CORE) || !core.getValue(PortalCoreBlock.FORMED)) return null;
        return switch (core.getValue(PortalCoreBlock.AXIS)) {
            case X -> Direction.EAST;
            case Z -> Direction.SOUTH;
            case Y -> null;
        };
    }

    static @Nullable Direction resolveRight(BlockGetter world, BlockPos corePos) {
        BlockState core = stateAt(world, corePos);
        if (!core.is(ModBlocks.PORTAL_CORE) || !formed(core)) return null;
        Direction right = rememberedRight(core);
        if (right != null) return right;
        // Older saved gates have formed frame blocks but no axis on the core.
        int east = Integer.bitCount(frameMask(world, corePos, Direction.EAST));
        int south = Integer.bitCount(frameMask(world, corePos, Direction.SOUTH));
        return east > 1 || south > 1 ? (east >= south ? Direction.EAST : Direction.SOUTH) : null;
    }

    static int frameMask(BlockGetter world, BlockPos core, Direction right) {
        int mask = CORE_MASK;
        for (int piece = 1; piece <= PIECE_COUNT; piece++) {
            BlockState state = stateAt(world, PortalRingSegments.cell(core, right, piece));
            if (state.is(ModBlocks.PORTAL_FRAME) && state.getValue(PortalFrameBlock.FORMED)) mask |= 1 << piece;
        }
        return mask;
    }

    static int pieceAt(Cell cell) {
        if (cell.x() == 0 && cell.y() == 0) return 0;
        for (int piece = 1; piece <= PIECE_COUNT; piece++) {
            if (cell.x() == pieceX(piece) && cell.y() == pieceY(piece)) return piece;
        }
        return -1;
    }

    static @Nullable Gate findGate(BlockGetter world, BlockPos pos) {
        for (Direction right : List.of(Direction.EAST, Direction.SOUTH)) {
            for (int dy = 0; dy <= 5; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    if ((dx == 0 && dy == 0) || (dy != 0 && dy != 5 && Math.abs(dx) != 2)) continue;
                    BlockPos core = pos.relative(right, -dx).below(dy);
                    if (resolveRight(world, core) == right) return new Gate(core, right);
                }
            }
        }
        return null;
    }

    /**
     * A model segment can extend into an air cell next to its supporting frame block.
     * Vanilla ray traversal otherwise never queries that supporting block's shape.
     */
    public static BlockHitResult clip(BlockGetter world, ClipContext context, BlockHitResult vanillaHit) {
        Vec3 from = context.getFrom();
        Vec3 to = vanillaHit.getType() == HitResult.Type.MISS ? context.getTo() : vanillaHit.getLocation();
        Set<BlockPos> checked = new HashSet<>();
        BlockHitResult[] closest = {vanillaHit};
        BlockGetter.traverseBlocks(from, to, world, (getter, cell) -> {
            for (Direction direction : Direction.values()) {
                BlockPos pos = cell.relative(direction);
                if (!checked.add(pos)) continue;
                BlockState state = stateAt(getter, pos);
                if (!formed(state) && !isAnchor(state)) continue;
                BlockHitResult hit = context.getBlockShape(state, getter, pos).clip(from, context.getTo(), pos);
                if (hit != null && (closest[0].getType() == HitResult.Type.MISS
                        || from.distanceToSqr(hit.getLocation()) < from.distanceToSqr(closest[0].getLocation()))) {
                    closest[0] = hit;
                }
            }
            return null;
        }, getter -> null);
        return closest[0];
    }

    static Cell owner(double h, double v) {
        int x = Math.max(-2, Math.min(2, (int) Math.floor(h / 16.0D)));
        int y = Math.max(0, Math.min(5, (int) Math.floor(v / 16.0D)));
        if (y != 0 && y != 5 && x != -2 && x != 2) {
            int distance = Math.min(Math.min(x + 2, 2 - x), Math.min(y, 5 - y));
            if (distance == y) y = 0;
            else if (distance == 5 - y) y = 5;
            else x = distance == x + 2 ? -2 : 2;
        }
        return new Cell(x, y);
    }

    private static boolean isAnchor(BlockState state) {
        return state.is(ModBlocks.PORTAL_FRAME) && state.getValue(PortalFrameBlock.PIECE) != 0;
    }

    /** Nearest anchor cell of {@code segment} to a ring pixel, so each arc stays on its own anchors. */
    private static Cell segmentOwner(int segment, double h, double v) {
        Cell best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int piece : segmentPieces(segment)) {
            int x = pieceX(piece), y = pieceY(piece);
            double dx = Math.max(Math.max(x * 16.0D - h, h - (x * 16.0D + 16.0D)), 0.0D);
            double dy = Math.max(Math.max(y * 16.0D - v, v - (y * 16.0D + 16.0D)), 0.0D);
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Cell(x, y);
            }
        }
        return best;
    }

    private static Map<Integer, VoxelShape> buildSegments(boolean xAxis) {
        Map<Cell, List<VoxelShape>> parts = new HashMap<>();
        for (int v = 0; v < 96; v++) {
            for (int h = -40; h < 56; h++) {
                if (!ringPixel(h, v)) continue;
                int segment = ringSegment(h, v);
                Cell cell = segmentOwner(segment, h + 0.5D, v + 0.5D);
                int end = h + 1;
                while (end < 56 && ringPixel(end, v) && ringSegment(end, v) == segment
                        && cell.equals(segmentOwner(segment, end + 0.5D, v + 0.5D))) end++;
                add(parts, cell, h, end, v, v + 1, RING_Z0, RING_Z1, xAxis);
                h = end - 1;
            }
        }
        Map<Integer, VoxelShape> result = new HashMap<>();
        for (int piece = 1; piece <= PIECE_COUNT; piece++) {
            List<VoxelShape> boxes = parts.getOrDefault(new Cell(pieceX(piece), pieceY(piece)), List.of());
            result.put(piece, Shapes.or(Shapes.empty(), boxes.toArray(VoxelShape[]::new)).optimize());
        }
        return Map.copyOf(result);
    }

    private static void add(Map<Cell, List<VoxelShape>> parts, Cell cell, double h0, double h1, double v0, double v1,
                            double z0, double z1, boolean xAxis) {
        double a = h0 / 16.0D - cell.x();
        double b = h1 / 16.0D - cell.x();
        double c = v0 / 16.0D - cell.y();
        double d = v1 / 16.0D - cell.y();
        VoxelShape shape = xAxis ? Shapes.box(a, c, z0 / 16.0D, b, d, z1 / 16.0D)
                : Shapes.box(z0 / 16.0D, c, a, z1 / 16.0D, d, b);
        parts.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(shape);
    }

    private static Map<Cell, VoxelShape> buildBuriedRow(Map<Cell, VoxelShape> ring, boolean xAxis) {
        Map<Cell, List<VoxelShape>> sockets = new HashMap<>();
        for (PortalBuriedFloor.SocketBox box : PortalBuriedFloor.SOCKETS) {
            add(sockets, new Cell(box.column(), 0), box.h0(), box.h1(), 16, box.top(), box.z0(), box.z1(), xAxis);
        }
        Map<Cell, VoxelShape> result = new HashMap<>();
        for (int dx = -2; dx <= 2; dx++) {
            Cell cell = new Cell(dx, 0);
            VoxelShape shape = Shapes.or(Shapes.block(), ring.getOrDefault(cell, Shapes.empty()));
            result.put(cell, Shapes.or(shape, sockets.getOrDefault(cell, List.of()).toArray(VoxelShape[]::new)).optimize());
        }
        return Map.copyOf(result);
    }

    private static Map<Cell, VoxelShape> build(boolean xAxis) {
        Map<Cell, List<VoxelShape>> parts = new HashMap<>();
        for (int v = 0; v < 96; v++) {
            for (int h = -40; h < 56; h++) {
                if (!ringPixel(h, v)) continue;
                int end = h + 1;
                Cell cell = owner(h + 0.5D, v + 0.5D);
                while (end < 56 && ringPixel(end, v) && cell.equals(owner(end + 0.5D, v + 0.5D))) end++;
                add(parts, cell, h, end, v, v + 1, RING_Z0, RING_Z1, xAxis);
                h = end - 1;
            }
        }
        Map<Cell, VoxelShape> result = new HashMap<>();
        parts.forEach((cell, boxes) -> result.put(cell, Shapes.or(Shapes.empty(), boxes.toArray(VoxelShape[]::new)).optimize()));
        return Map.copyOf(result);
    }

}
