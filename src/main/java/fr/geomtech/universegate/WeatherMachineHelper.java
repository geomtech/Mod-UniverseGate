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
    private static final int PORTAL_SEARCH_RADIUS_XZ = 24;
    private static final int PORTAL_SEARCH_RADIUS_Y = 8;
    private static final int MAX_CONDUIT_SEARCH = 4096;

    private WeatherMachineHelper() {}

    public record MachineStatus(boolean towerPresent,
                                boolean parabolaPresent,
                                boolean catalystPresent,
                                boolean catalystHasCrystal,
                                boolean energyLinked,
                                @Nullable BlockPos condenserBasePos,
                                @Nullable BlockPos condenserTopPos,
                                @Nullable BlockPos catalystPos) {

        public static MachineStatus empty() {
            return new MachineStatus(false, false, false, false, false, null, null, null);
        }

        public boolean structureReady() {
            return towerPresent && parabolaPresent && catalystHasCrystal;
        }

        public boolean canCharge() {
            return structureReady() && energyLinked;
        }
    }

    public static MachineStatus scan(ServerLevel level, BlockPos controllerPos) {
        BlockPos condenserBasePos = findNearestTowerBase(level, controllerPos);
        if (condenserBasePos == null) {
            return MachineStatus.empty();
        }

        BlockPos condenserTopPos = condenserBasePos.above();
        boolean parabolaPresent = hasParabolaOnNearbyPortal(level, condenserBasePos);

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
        return level.getBlockState(basePos).is(ModBlocks.ENERGY_CONDENSER)
                && level.getBlockState(basePos.above()).is(ModBlocks.ENERGY_CONDENSER);
    }

    private static boolean hasParabolaOnNearbyPortal(ServerLevel level, BlockPos center) {
        Set<BlockPos> checkedCores = new HashSet<>();
        for (int dy = -PORTAL_SEARCH_RADIUS_Y; dy <= PORTAL_SEARCH_RADIUS_Y; dy++) {
            for (int dx = -PORTAL_SEARCH_RADIUS_XZ; dx <= PORTAL_SEARCH_RADIUS_XZ; dx++) {
                for (int dz = -PORTAL_SEARCH_RADIUS_XZ; dz <= PORTAL_SEARCH_RADIUS_XZ; dz++) {
                    BlockPos candidate = center.offset(dx, dy, dz);
                    if (!level.getBlockState(candidate).is(ModBlocks.PORTAL_CORE)) continue;
                    if (!checkedCores.add(candidate.immutable())) continue;
                    if (hasParabolaAbovePortalTop(level, candidate)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasParabolaAbovePortalTop(ServerLevel level, BlockPos corePos) {
        var match = PortalFrameDetector.find(level, corePos);
        if (match.isEmpty()) return false;

        int topDy = PortalFrameDetector.INNER_HEIGHT + 1;
        int halfWidth = PortalFrameDetector.INNER_WIDTH / 2 + 1;
        Direction right = match.get().right();

        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            BlockPos topFramePos = corePos.offset(right.getStepX() * dx, topDy, right.getStepZ() * dx);
            if (!level.getBlockState(topFramePos).is(ModBlocks.PORTAL_FRAME)) continue;
            if (level.getBlockState(topFramePos.above()).is(ModBlocks.PARABOLA_BLOCK)) {
                return true;
            }
        }

        return false;
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
            Set<BlockPos> touchedPortalBlocks = new HashSet<>();
            Set<BlockPos> touchedRods = new HashSet<>();
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
                    if (isPortalNetworkBlock(neighborState)) {
                        touchedPortalBlocks.add(neighborPos.immutable());
                    }
                    if (neighborState.is(ModBlocks.CHARGED_LIGHTNING_ROD)) {
                        touchedRods.add(neighborPos.immutable());
                    }

                    if (neighborState.is(ModBlocks.ENERGY_CONDUIT) && visited.add(neighborPos)) {
                        queue.addLast(neighborPos);
                    }
                }
            }

            if (touchesBaseCondenser
                    && touchesTopCondenser
                    && hasChargedRodConnectedToPortal(level, touchedPortalBlocks, touchedRods)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasChargedRodConnectedToPortal(ServerLevel level,
                                                          Set<BlockPos> touchedPortalBlocks,
                                                          Set<BlockPos> touchedRods) {
        if (!touchedPortalBlocks.isEmpty() && portalComponentHasRod(level, touchedPortalBlocks)) {
            return true;
        }
        if (!touchedRods.isEmpty() && rodsTouchPortal(level, touchedRods)) {
            return true;
        }
        return false;
    }

    private static boolean portalComponentHasRod(ServerLevel level, Set<BlockPos> startPortalBlocks) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos pos : startPortalBlocks) {
            visited.add(pos.immutable());
            queue.addLast(pos.immutable());
        }

        int searched = 0;
        while (!queue.isEmpty()) {
            BlockPos portalPos = queue.removeFirst();
            searched++;
            if (searched > MAX_CONDUIT_SEARCH) {
                return false;
            }

            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = portalPos.relative(direction);
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.is(ModBlocks.CHARGED_LIGHTNING_ROD)) {
                    return true;
                }
                if (isPortalNetworkBlock(neighborState) && visited.add(neighborPos.immutable())) {
                    queue.addLast(neighborPos.immutable());
                }
            }
        }

        return false;
    }

    private static boolean rodsTouchPortal(ServerLevel level, Set<BlockPos> rods) {
        for (BlockPos rodPos : rods) {
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = rodPos.relative(direction);
                if (isPortalNetworkBlock(level.getBlockState(neighborPos))) {
                    return true;
                }
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

    private static boolean isPortalNetworkBlock(BlockState state) {
        return state.is(ModBlocks.PORTAL_CORE)
                || state.is(ModBlocks.PORTAL_FRAME)
                || state.is(ModBlocks.PORTAL_FIELD)
                || state.is(ModBlocks.PORTAL_KEYBOARD);
    }
}
