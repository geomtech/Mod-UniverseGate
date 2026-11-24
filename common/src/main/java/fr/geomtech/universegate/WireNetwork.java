package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import org.jetbrains.annotations.Nullable;

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

    public record Scan(List<BlockPos> cores, List<BlockPos> antennas, List<BlockPos> wires, List<BlockPos> keyboards) {}

    private WireNetwork() {}

    /**
     * The portal core a player-built keyboard controls: one of the crocodile clips biting the keyboard must be
     * wired to a clip biting that portal's frame or core. Returns null when the keyboard is not wired to any portal.
     */
    public static @Nullable BlockPos findWiredCore(Level level, BlockPos keyboardPos) {
        for (Direction side : Direction.values()) {
            BlockPos clip = keyboardPos.relative(side);
            if (!level.isLoaded(clip)) continue;
            BlockState state = level.getBlockState(clip);
            if (!state.is(ModBlocks.CROCODILE_CLIP) || state.getValue(CrocodileClipBlock.FACING) != side.getOpposite()) continue;
            List<BlockPos> cores = scan(level, clip).cores();
            if (!cores.isEmpty()) return cores.getFirst();
        }
        return null;
    }

    /** True when at least one crocodile clip bites the keyboard, wired or not. */
    public static boolean hasClip(Level level, BlockPos keyboardPos) {
        for (Direction side : Direction.values()) {
            BlockPos clip = keyboardPos.relative(side);
            if (!level.isLoaded(clip)) continue;
            BlockState state = level.getBlockState(clip);
            if (state.is(ModBlocks.CROCODILE_CLIP) && state.getValue(CrocodileClipBlock.FACING) == side.getOpposite()) return true;
        }
        return false;
    }

    /** Refresh the LIT state of every player-built keyboard reachable from {@code pos} or its immediate wire/clip neighbours. */
    public static void syncWiredKeyboardsAround(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        Set<BlockPos> keyboards = new LinkedHashSet<>();
        Set<BlockPos> seenNodes = new HashSet<>();
        BlockState center = level.getBlockState(pos);
        if (ElectricWireBlock.isWire(center) || center.is(ModBlocks.CROCODILE_CLIP)) {
            keyboards.addAll(scan(level, pos).keyboards());
        } else {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = pos.offset(dx, dy, dz);
                        if (!level.isLoaded(n) || seenNodes.contains(n)) continue;
                        BlockState ns = level.getBlockState(n);
                        if (!ElectricWireBlock.isWire(ns) && !ns.is(ModBlocks.CROCODILE_CLIP)) continue;
                        Scan scan = scan(level, n);
                        seenNodes.add(n.immutable());
                        seenNodes.addAll(scan.wires());
                        keyboards.addAll(scan.keyboards());
                    }
                }
            }
        }
        for (BlockPos kb : keyboards) {
            PortalConnectionManager.syncKeyboardLitFromNearbyCore(level, kb);
        }
    }

    /** Player-built keyboards wired to the portal of this core (through clips on its frame or core). */
    public static List<BlockPos> findWiredKeyboards(Level level, BlockPos corePos) {
        Set<BlockPos> keyboards = new LinkedHashSet<>();
        Set<BlockPos> scanned = new HashSet<>();
        int rXZ = PortalFrameDetector.INNER_WIDTH + 2;
        int up = PortalFrameDetector.INNER_HEIGHT + 3;
        for (BlockPos p : BlockPos.betweenClosed(corePos.offset(-rXZ, -2, -rXZ), corePos.offset(rXZ, up, rXZ))) {
            if (!level.isLoaded(p)) continue;
            BlockState state = level.getBlockState(p);
            if (!state.is(ModBlocks.CROCODILE_CLIP)) continue;
            BlockPos clip = p.immutable();
            if (scanned.contains(clip)) continue;
            BlockPos gripped = clip.relative(state.getValue(CrocodileClipBlock.FACING));
            BlockState grippedState = level.getBlockState(gripped);
            if (!grippedState.is(ModBlocks.PORTAL_CORE) && !grippedState.is(ModBlocks.PORTAL_FRAME)) continue;
            Scan scan = scan(level, clip);
            scanned.add(clip);
            if (!scan.cores().contains(corePos)) continue;
            keyboards.addAll(scan.keyboards());
        }
        return new ArrayList<>(keyboards);
    }

    public static Scan scan(Level level, BlockPos start) {
        Set<BlockPos> cores = new LinkedHashSet<>();
        Set<BlockPos> antennas = new LinkedHashSet<>();
        Set<BlockPos> keyboards = new LinkedHashSet<>();
        List<BlockPos> wires = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        if (!level.isLoaded(start)) return new Scan(List.of(), List.of(), List.of(), List.of());
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
                    if (link != null) {
                        BlockState targetState = level.getBlockState(link.target());
                        if (!ElectricWireBlock.isWire(targetState)
                                || ElectricWireBlock.isConnectedTo(level, link.target(), targetState, pos)) {
                            visit(level, link.target(), visited, queue);
                        }
                    }
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
                    } else if (grippedState.is(ModBlocks.PORTAL_KEYBOARD)) {
                        keyboards.add(gripped.immutable());
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
        return new Scan(new ArrayList<>(cores), new ArrayList<>(antennas), wires, new ArrayList<>(keyboards));
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
