package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Terrain and socket geometry shared by the renderer and the walkable threshold. */
public final class PortalBuriedFloor {
    record SocketBox(int column, float h0, float h1, float top, float z0, float z1, boolean floor) {}
    static final List<SocketBox> SOCKETS = sockets();

    private PortalBuriedFloor() {}

    public static boolean isBuried(BlockGetter world, BlockPos core, Direction right) {
        Direction depth = right.getClockWise();
        return ground(world, core.relative(depth)) != null && ground(world, core.relative(depth.getOpposite())) != null;
    }

    static @Nullable BlockState materialAt(BlockGetter world, BlockPos pos, Direction right) {
        Direction depth = right.getClockWise();
        BlockState material = ground(world, pos.relative(depth));
        if (material == null) material = ground(world, pos.relative(depth.getOpposite()));
        return material != null ? material : ground(world, pos.below());
    }

    /** Seat in block coordinates relative to the core, on the inner lip of the nearest intact socket. */
    static @Nullable Vec3 clipSeat(BlockGetter world, BlockPos core, Direction right, double lateral, boolean positiveDepth) {
        if (!isBuried(world, core, right)) return null;
        SocketBox nearest = null;
        double target = PortalGateGeometry.CENTER_H + lateral * 16;
        double best = Double.POSITIVE_INFINITY;
        double h = 0;
        for (SocketBox box : SOCKETS) {
            if (positiveDepth ? box.z1() != 16 : box.z0() != 0) continue;
            BlockPos pos = core.relative(right, box.column());
            BlockState frame = PortalGateGeometry.stateAt(world, pos);
            if (!frame.is(ModBlocks.PORTAL_FRAME) || !frame.getValue(PortalFrameBlock.FORMED)
                    || materialAt(world, pos, right) == null) continue;
            double left = box.h0(), rightEdge = box.h1();
            double margin = Math.min(6.4, (rightEdge - left) * .5);
            double center = Math.clamp(target, left + margin, rightEdge - margin);
            double distance = Math.abs(center - target);
            if (distance >= best) continue;
            best = distance;
            nearest = box;
            h = center;
        }
        if (nearest == null) return null;
        double depth = (positiveDepth ? nearest.z0() : nearest.z1()) / 16.0;
        return right.getAxis() == Direction.Axis.X ? new Vec3(h / 16, nearest.top() / 16, depth)
                : new Vec3(depth, nearest.top() / 16, h / 16);
    }

    private static @Nullable BlockState ground(BlockGetter world, BlockPos pos) {
        BlockState state = PortalGateGeometry.stateAt(world, pos);
        if (state.is(ModBlocks.PORTAL_CORE) || state.is(ModBlocks.PORTAL_FRAME)) return null;
        return state.getRenderShape() == RenderShape.MODEL && state.isCollisionShapeFullBlock(world, pos) ? state : null;
    }

    private static List<SocketBox> sockets() {
        float dy = PortalGateGeometry.CENTER_V - 16;
        float outer = (float) Math.sqrt(PortalGateGeometry.OUTER_RADIUS * PortalGateGeometry.OUTER_RADIUS - dy * dy);
        float inner = (float) Math.sqrt(PortalGateGeometry.INNER_RADIUS * PortalGateGeometry.INNER_RADIUS - dy * dy);
        List<SocketBox> boxes = new ArrayList<>();
        for (int side : new int[]{-1, 1}) {
            float a = PortalGateGeometry.CENTER_H + (side < 0 ? -outer : inner) - 1;
            float b = PortalGateGeometry.CENTER_H + (side < 0 ? -inner : outer) + 1;
            add(boxes, a, b, 16.1F, 2, 14, true);
            add(boxes, a - 2, b + 2, 17.5F, 0, 2, false);
            add(boxes, a - 2, b + 2, 17.5F, 14, 16, false);
            add(boxes, a - 2, a, 17.5F, 2, 14, false);
            add(boxes, b, b + 2, 17.5F, 2, 14, false);
        }
        return List.copyOf(boxes);
    }

    // Each base block owns its part of the socket, including when a neighboring block is broken.
    private static void add(List<SocketBox> boxes, float from, float to, float top, float z0, float z1, boolean floor) {
        for (float h = from; h < to;) {
            int column = (int) Math.floor(h / 16);
            float end = Math.min(to, (column + 1) * 16);
            boxes.add(new SocketBox(column, h, end, top, z0, z1, floor));
            h = end;
        }
    }
}
