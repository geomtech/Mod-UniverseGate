package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import fr.geomtech.universegate.PortalFrameHelper;
import fr.geomtech.universegate.PortalFrameDetector;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayDeque;

public final class DarkEnergyNetworkHelper {

    private static final int MAX_SEARCH_DEPTH = 64;

    private DarkEnergyNetworkHelper() {}

    public static boolean isPortalPowered(ServerLevel level, BlockPos corePos) {
        // Find the full frame to check all adjacent blocks
        var match = PortalFrameDetector.find(level, corePos);
        if (match.isEmpty()) return false;

        // Collect all relevant positions: Core + Frames
        Set<BlockPos> framePositions = new HashSet<>();
        framePositions.add(corePos);
        framePositions.addAll(PortalFrameHelper.collectFrame(match.get(), corePos));

        return isConnectedToPoweredGenerator(level, framePositions);
    }

    public static boolean isMachinePowered(ServerLevel level, BlockPos machinePos) {
        Set<BlockPos> machinePositions = new HashSet<>();
        machinePositions.add(machinePos.immutable());
        return isConnectedToPoweredGenerator(level, machinePositions);
    }

    private static boolean isConnectedToPoweredGenerator(ServerLevel level, Set<BlockPos> sourcePositions) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        for (BlockPos sourcePos : sourcePositions) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = sourcePos.relative(dir);
                if (isGeneratorOnline(level, neighbor)) {
                    return true;
                }

                if (isValidConduit(level, neighbor) && visited.add(neighbor.immutable())) {
                    queue.add(neighbor.immutable());
                }
            }
        }

        int steps = 0;
        while (!queue.isEmpty() && steps < MAX_SEARCH_DEPTH) {
            BlockPos current = queue.poll();
            steps++;

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (isGeneratorOnline(level, neighbor)) {
                    return true;
                }

                if (isValidConduit(level, neighbor) && visited.add(neighbor.immutable())) {
                    queue.add(neighbor.immutable());
                }
            }
        }

        return false;
    }

    private static boolean isGeneratorOnline(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.DARK_ENERGY_GENERATOR)) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof DarkEnergyGeneratorBlockEntity generator && generator.canSupplyNetwork();
    }

    private static boolean isValidConduit(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.DARK_ENERGY_CONDUIT);
    }
}
