package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Follows electric wires from a lightning antenna (or any wire, clip or antenna) and reports what
 * they connect: the portals whose frame or core a crocodile clip bites, and the antennas.
 * Never loads chunks: a network crossing an unloaded chunk is cut there.
 */
public final class WireNetwork {

    /** Upper bound on the blocks walked, so a huge wire maze cannot stall the server. */
    public static final int MAX_NODES = 4096;

    public record Scan(List<BlockPos> cores, List<BlockPos> antennas, List<BlockPos> wires) {}

    private WireNetwork() {}

    public static Scan scan(Level level, BlockPos start) {
        Set<BlockPos> cores = new LinkedHashSet<>();
        Set<BlockPos> antennas = new LinkedHashSet<>();
        List<BlockPos> wires = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        if (!level.isLoaded(start)) return new Scan(List.of(), List.of(), List.of());
        BlockState startState = level.getBlockState(start);
        BlockPos first = startState.is(ModBlocks.LIGHTNING_ANTENNA) ? LightningAntennaBlock.basePos(start, startState) : start;
        queue.add(first.immutable());
        visited.add(first.immutable());

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);

            if (ElectricWireBlock.isWire(state)) {
                wires.add(pos);
                Direction attach = state.getValue(ElectricWireBlock.FACING);
                for (Direction direction : Direction.values()) {
                    if (direction.getAxis() == attach.getAxis()) continue;
                    if (state.getValue(ElectricWireBlock.sideProperty(direction)) == RedstoneSide.NONE) continue;
                    ElectricWireBlock.Link link = ElectricWireBlock.link(level, pos, attach, direction);
                    if (link != null) visit(level, link.target(), visited, queue);
                }
            } else if (state.is(ModBlocks.CROCODILE_CLIP)) {
                Direction facing = state.getValue(CrocodileClipBlock.FACING);
                BlockPos gripped = pos.relative(facing);
                if (level.isLoaded(gripped)) {
                    BlockState grippedState = level.getBlockState(gripped);
                    if (grippedState.is(ModBlocks.PORTAL_CORE)) {
                        cores.add(gripped.immutable());
                    } else if (grippedState.is(ModBlocks.PORTAL_FRAME)) {
                        PortalFrameDetector.findCoreOfFrame(level, gripped).ifPresent(cores::add);
                    } else if (grippedState.is(ModBlocks.LIGHTNING_ANTENNA)) {
                        visit(level, LightningAntennaBlock.basePos(gripped, grippedState), visited, queue);
                    }
                }
                for (Direction direction : Direction.values()) {
                    if (direction == facing) continue;
                    BlockPos next = pos.relative(direction);
                    if (wirePointsBack(level, next, direction)) {
                        visit(level, next, visited, queue);
                    }
                }
            } else if (LightningAntennaBlock.isBase(state)) {
                antennas.add(pos);
                for (int height = 0; height < LightningAntennaBlock.HEIGHT; height++) {
                    BlockPos part = pos.above(height);
                    for (Direction direction : Direction.values()) {
                        BlockPos next = part.relative(direction);
                        if (!level.isLoaded(next)) continue;
                        BlockState neighbor = level.getBlockState(next);
                        boolean gripsMast = neighbor.is(ModBlocks.CROCODILE_CLIP)
                                && neighbor.getValue(CrocodileClipBlock.FACING) == direction.getOpposite();
                        if (gripsMast || (height == 0 && wirePointsBack(level, next, direction))) {
                            visit(level, next, visited, queue);
                        }
                    }
                }
            }
        }
        return new Scan(new ArrayList<>(cores), new ArrayList<>(antennas), wires);
    }

    private static void visit(Level level, BlockPos pos, Set<BlockPos> visited, ArrayDeque<BlockPos> queue) {
        BlockPos immutable = pos.immutable();
        if (visited.size() >= MAX_NODES || !level.isLoaded(immutable)) return;
        if (visited.add(immutable)) queue.add(immutable);
    }

    private static boolean wirePointsBack(Level level, BlockPos next, Direction direction) {
        if (!level.isLoaded(next)) return false;
        BlockState wire = level.getBlockState(next);
        return ElectricWireBlock.isWire(wire)
                && wire.getValue(ElectricWireBlock.sideProperty(direction.getOpposite())) != RedstoneSide.NONE;
    }
}
