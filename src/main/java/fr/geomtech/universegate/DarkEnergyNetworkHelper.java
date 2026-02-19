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
        
        // Search for connected Dark Energy Generators via Dark Energy Conduits
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        
        // Add initial adjacent conduits/generators from ANY portal part
        for (BlockPos partPos : framePositions) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = partPos.relative(dir);
                BlockState neighborState = level.getBlockState(neighbor);

                if (neighborState.is(ModBlocks.DARK_ENERGY_GENERATOR)) {
                    BlockEntity be = level.getBlockEntity(neighbor);
                    if (be instanceof DarkEnergyGeneratorBlockEntity generator && (generator.isGenerating() || generator.hasFuel())) {
                        return true;
                    }
                } else if (isValidConduit(level, neighbor)) {
                    queue.add(neighbor);
                    visited.add(neighbor);
                }
            }
        }

        int steps = 0;
        while (!queue.isEmpty() && steps < MAX_SEARCH_DEPTH) {
            BlockPos current = queue.poll();
            steps++;

            // Check neighbors of current conduit
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (visited.contains(neighbor)) continue;

                BlockState state = level.getBlockState(neighbor);
                
                if (state.is(ModBlocks.DARK_ENERGY_GENERATOR)) {
                    BlockEntity be = level.getBlockEntity(neighbor);
                    if (be instanceof DarkEnergyGeneratorBlockEntity generator && (generator.isGenerating() || generator.hasFuel())) {
                        return true;
                    }
                } else if (isValidConduit(level, neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return false;
    }

    private static boolean isValidConduit(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.DARK_ENERGY_CONDUIT);
    }
}
