package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class WeatherMachineHelper {

    private static final int STRUCTURE_RADIUS = 1;
    private static final int STRUCTURE_Y_RADIUS = 1;
    private static final int MAX_CONDUIT_SEARCH = 4096;

    private WeatherMachineHelper() {}

    public record MachineStatus(boolean towerPresent,
                                boolean parabolaPresent,
                                boolean parabolaPowered,
                                boolean catalystPresent,
                                boolean catalystHasCrystal,
                                boolean energyLinked,
                                @Nullable BlockPos condenserBasePos,
                                @Nullable BlockPos condenserTopPos,
                                @Nullable BlockPos catalystPos) {

        public static MachineStatus empty() {
            return new MachineStatus(false, false, false, false, false, false, null, null, null);
        }

        public boolean structureReady() {
            return towerPresent && parabolaPresent && catalystHasCrystal;
        }

        public boolean canCharge() {
            return structureReady() && energyLinked && parabolaPowered;
        }
    }

    public static MachineStatus scan(ServerLevel level, BlockPos controllerPos) {
        BlockPos condenserBasePos = findNearestTowerBase(level, controllerPos);
        if (condenserBasePos == null) {
            return MachineStatus.empty();
        }

        BlockPos condenserTopPos = condenserBasePos.above();
        boolean parabolaPresent = EnergyNetworkHelper.isParabolaOnNetwork(level, condenserBasePos);
        boolean parabolaPowered = EnergyNetworkHelper.isPoweredParabolaOnNetwork(level, condenserBasePos);

        BlockPos expectedCatalystPos = condenserTopPos.above();
        BlockState catalystState = level.getBlockState(expectedCatalystPos);
        boolean catalystPresent = catalystState.is(ModBlocks.METEOROLOGICAL_CATALYST);
        boolean catalystHasCrystal = catalystPresent
                && catalystState.hasProperty(CrystalCondenserBlock.HAS_CRYSTAL)
                && catalystState.getValue(CrystalCondenserBlock.HAS_CRYSTAL);
        boolean energyLinked = hasEnergyLink(level, condenserBasePos, condenserTopPos);

        return new MachineStatus(
                true,
                parabolaPresent,
                parabolaPowered,
                catalystPresent,
                catalystHasCrystal,
                energyLinked,
                condenserBasePos,
                condenserTopPos,
                catalystPresent ? expectedCatalystPos : null
        );
    }

    @Nullable
    private static BlockPos findNearestTowerBase(ServerLevel level, BlockPos controllerPos) {
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (int dy = -STRUCTURE_Y_RADIUS; dy <= STRUCTURE_Y_RADIUS; dy++) {
            for (int dx = -STRUCTURE_RADIUS; dx <= STRUCTURE_RADIUS; dx++) {
                for (int dz = -STRUCTURE_RADIUS; dz <= STRUCTURE_RADIUS; dz++) {
                    BlockPos candidate = controllerPos.offset(dx, dy, dz);
                    if (!isCondenserTower(level, candidate)) continue;

                    double dist = candidate.distSqr(controllerPos);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestPos = candidate.immutable();
                    }
                }
            }
        }

        return bestPos;
    }

    private static boolean isCondenserTower(ServerLevel level, BlockPos basePos) {
        return level.getBlockState(basePos).is(ModBlocks.METEOROLOGICAL_CONDENSER)
                && level.getBlockState(basePos.above()).is(ModBlocks.METEOROLOGICAL_CONDENSER);
    }

    private static boolean hasEnergyLink(ServerLevel level, BlockPos condenserBasePos, BlockPos condenserTopPos) {
        Set<BlockPos> conduitStarts = new HashSet<>();
        collectAdjacentConduits(level, condenserBasePos, conduitStarts);
        collectAdjacentConduits(level, condenserTopPos, conduitStarts);
        if (conduitStarts.isEmpty()) return false;

        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos start : conduitStarts) {
            if (!visited.add(start)) continue;

            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);

            boolean touchesBaseCondenser = false;
            boolean touchesTopCondenser = false;
            int searched = 0;

            while (!queue.isEmpty()) {
                BlockPos conduitPos = queue.removeFirst();
                searched++;
                if (searched > MAX_CONDUIT_SEARCH) {
                    return false;
                }

                for (Direction direction : Direction.values()) {
                    BlockPos neighborPos = conduitPos.relative(direction);
                    BlockState neighborState = level.getBlockState(neighborPos);

                    if (neighborPos.equals(condenserBasePos)) {
                        touchesBaseCondenser = true;
                    }
                    if (neighborPos.equals(condenserTopPos)) {
                        touchesTopCondenser = true;
                    }

                    if (neighborState.is(ModBlocks.ENERGY_CONDUIT) && visited.add(neighborPos)) {
                        queue.addLast(neighborPos);
                    }
                }
            }

            if (touchesBaseCondenser && touchesTopCondenser) {
                return true;
            }
        }

        return false;
    }

    private static void collectAdjacentConduits(ServerLevel level, BlockPos origin, Set<BlockPos> output) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = origin.relative(direction);
            if (level.getBlockState(neighborPos).is(ModBlocks.ENERGY_CONDUIT)) {
                output.add(neighborPos.immutable());
            }
        }
    }
}
