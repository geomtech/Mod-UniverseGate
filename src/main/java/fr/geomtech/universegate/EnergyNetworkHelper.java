package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EnergyNetworkHelper {

    public static final int PORTAL_OPEN_ENERGY_COST = 4000;
    public static final int PORTAL_ACTIVE_ENERGY_COST_PER_SECOND = 120;
    public static final int PORTAL_UNSTABLE_COST_BONUS_PERCENT = 50;

    private static final int MAX_CONDUIT_SEARCH = 8192;
    private static final Comparator<BlockPos> POS_COMPARATOR = (a, b) -> {
        int cmp = Integer.compare(a.getX(), b.getX());
        if (cmp != 0) return cmp;
        cmp = Integer.compare(a.getY(), b.getY());
        if (cmp != 0) return cmp;
        return Integer.compare(a.getZ(), b.getZ());
    };

    private EnergyNetworkHelper() {}

    public record CondenserNetwork(Set<BlockPos> condensers, Set<BlockPos> solarPanels, Set<BlockPos> parabolas) {}
    public record EnergyNetworkSnapshot(int storedEnergy,
                                        int capacity,
                                        int condenserCount,
                                        int panelCount,
                                        int activePanelCount,
                                        int parabolaCount) {
        public static final EnergyNetworkSnapshot EMPTY = new EnergyNetworkSnapshot(0, 0, 0, 0, 0, 0);

        public int chargePercent() {
            if (capacity <= 0) return 0;
            return (int) Math.round((storedEnergy * 100.0D) / (double) capacity);
        }
    }

    public static CondenserNetwork scanCondenserNetwork(ServerLevel level, BlockPos condenserPos) {
        Set<BlockPos> startConduits = new HashSet<>();
        collectAdjacentConduits(level, condenserPos, startConduits);

        Set<BlockPos> condensers = new HashSet<>();
        if (level.getBlockState(condenserPos).is(ModBlocks.ENERGY_CONDENSER)) {
            condensers.add(condenserPos.immutable());
        }

        if (startConduits.isEmpty()) {
            return new CondenserNetwork(condensers, Set.of(), Set.of());
        }

        ConduitScanResult scan = scanConduits(level, startConduits);
        condensers.addAll(scan.condensers());
        return new CondenserNetwork(condensers, scan.solarPanels(), scan.parabolas());
    }

    public static BlockPos findNetworkLeader(Set<BlockPos> condensers) {
        return condensers.stream().min(POS_COMPARATOR).orElse(null);
    }

    public static boolean isSolarPanelGenerating(ServerLevel level, BlockPos panelPos) {
        BlockState panelState = level.getBlockState(panelPos);
        if (!panelState.is(ModBlocks.SOLAR_PANEL)) return false;
        if (!panelState.hasProperty(SolarPanelBlock.PART)
                || panelState.getValue(SolarPanelBlock.PART) != SolarPanelBlock.PanelPart.BASE) {
            return false;
        }
        if (!level.dimensionType().hasSkyLight()) return false;
        if (!level.isDay()) return false;
        if (level.isThundering()) return false;
        return level.canSeeSky(panelPos.above(4));
    }

    public static EnergyNetworkSnapshot getNetworkSnapshot(ServerLevel level, BlockPos condenserPos) {
        BlockState state = level.getBlockState(condenserPos);
        if (!state.is(ModBlocks.ENERGY_CONDENSER)) {
            return EnergyNetworkSnapshot.EMPTY;
        }

        CondenserNetwork network = scanCondenserNetwork(level, condenserPos);
        List<EnergyCondenserBlockEntity> condensers = getCondensers(level, network.condensers());
        if (condensers.isEmpty()) {
            return EnergyNetworkSnapshot.EMPTY;
        }

        int stored = 0;
        int capacity = 0;
        for (EnergyCondenserBlockEntity condenser : condensers) {
            stored += condenser.getStoredEnergy();
            capacity += EnergyCondenserBlockEntity.CAPACITY;
        }

        int activePanels = 0;
        for (BlockPos panelPos : network.solarPanels()) {
            if (isSolarPanelGenerating(level, panelPos)) {
                activePanels++;
            }
        }

        return new EnergyNetworkSnapshot(
                stored,
                capacity,
                condensers.size(),
                network.solarPanels().size(),
                activePanels,
                network.parabolas().size()
        );
    }

    public static boolean isParabolaOnNetwork(ServerLevel level, BlockPos startNode) {
        Set<BlockPos> startConduits = new HashSet<>();
        collectAdjacentConduits(level, startNode, startConduits);
        if (startConduits.isEmpty()) return false;
        
        ConduitScanResult scan = scanConduits(level, startConduits);
        return !scan.parabolas().isEmpty();
    }

    public static boolean isPoweredParabolaOnNetwork(ServerLevel level, BlockPos startNode) {
        Set<BlockPos> startConduits = new HashSet<>();
        collectAdjacentConduits(level, startNode, startConduits);
        if (startConduits.isEmpty()) return false;
        
        ConduitScanResult scan = scanConduits(level, startConduits);
        for (BlockPos pos : scan.parabolas()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ParabolaBlockEntity parabola && parabola.isPowered()) {
                return true;
            }
        }
        return false;
    }

    public static boolean consumeEnergyFromNetwork(ServerLevel level, BlockPos startNode, int amount) {
        Set<BlockPos> startConduits = new HashSet<>();
        collectAdjacentConduits(level, startNode, startConduits);
        
        // Also check if startNode itself is a condenser (though usually we call this from a machine)
        if (level.getBlockState(startNode).is(ModBlocks.ENERGY_CONDENSER)) {
             // Logic if starting from a condenser, but usually machines connect TO conduits.
        }

        if (startConduits.isEmpty()) return false;

        ConduitScanResult scan = scanConduits(level, startConduits);
        return consumeFromCondensers(level, scan.condensers(), amount);
    }

    private static boolean consumeFromCondensers(ServerLevel level, Set<BlockPos> condenserPositions, int amount) {
        if (condenserPositions.isEmpty()) return false;
        int totalStored = 0;
        List<EnergyCondenserBlockEntity> condensers = getCondensers(level, condenserPositions);
        
        for (EnergyCondenserBlockEntity condenser : condensers) {
            totalStored += condenser.getStoredEnergy();
        }
        
        if (totalStored < amount) return false;
        
        condensers.sort(Comparator
                .comparingInt(EnergyCondenserBlockEntity::getStoredEnergy)
                .reversed()
                .thenComparing(be -> be.getBlockPos(), POS_COMPARATOR));
                
        int remaining = amount;
        for (EnergyCondenserBlockEntity condenser : condensers) {
            if (remaining <= 0) break;
            remaining -= condenser.extractEnergy(remaining);
        }
        
        return remaining <= 0;
    }

    public static int distributeEnergy(ServerLevel level, Set<BlockPos> condenserPositions, int amount) {
        if (amount <= 0 || condenserPositions.isEmpty()) return 0;

        List<EnergyCondenserBlockEntity> condensers = getCondensers(level, condenserPositions);
        if (condensers.isEmpty()) return 0;

        condensers.sort(Comparator
                .comparingInt(EnergyCondenserBlockEntity::getStoredEnergy)
                .thenComparing(be -> be.getBlockPos(), POS_COMPARATOR));

        int remaining = amount;
        for (EnergyCondenserBlockEntity condenser : condensers) {
            if (remaining <= 0) break;
            remaining -= condenser.addEnergy(remaining);
        }
        return amount - remaining;
    }

    public static int getAvailableEnergyForPortal(ServerLevel level, BlockPos corePos) {
        List<EnergyCondenserBlockEntity> condensers = getPortalCondensers(level, corePos);
        int total = 0;
        for (EnergyCondenserBlockEntity condenser : condensers) {
            total += condenser.getStoredEnergy();
        }
        return total;
    }

    public static boolean consumePortalEnergy(ServerLevel level, BlockPos corePos, int amount) {
        if (amount <= 0) return true;

        List<EnergyCondenserBlockEntity> condensers = getPortalCondensers(level, corePos);
        if (condensers.isEmpty()) return false;

        int total = 0;
        for (EnergyCondenserBlockEntity condenser : condensers) {
            total += condenser.getStoredEnergy();
        }
        if (total < amount) return false;

        condensers.sort(Comparator
                .comparingInt(EnergyCondenserBlockEntity::getStoredEnergy)
                .reversed()
                .thenComparing(be -> be.getBlockPos(), POS_COMPARATOR));

        int remaining = amount;
        for (EnergyCondenserBlockEntity condenser : condensers) {
            if (remaining <= 0) break;
            remaining -= condenser.extractEnergy(remaining);
        }
        return remaining <= 0;
    }

    public static int getPortalActiveEnergyCostPerSecond(boolean unstableByTime) {
        if (!unstableByTime) return PORTAL_ACTIVE_ENERGY_COST_PER_SECOND;

        double factor = 1.0D + (PORTAL_UNSTABLE_COST_BONUS_PERCENT / 100.0D);
        return (int) Math.ceil(PORTAL_ACTIVE_ENERGY_COST_PER_SECOND * factor);
    }

    public static boolean isRiftDimension(ServerLevel level) {
        return level.dimension().equals(UniverseGateDimensions.RIFT);
    }

    private static List<EnergyCondenserBlockEntity> getPortalCondensers(ServerLevel level, BlockPos corePos) {
        Set<BlockPos> startConduits = collectPortalStartConduits(level, corePos);
        if (startConduits.isEmpty()) return List.of();

        ConduitScanResult scan = scanConduits(level, startConduits);
        return getCondensers(level, scan.condensers());
    }

    private static Set<BlockPos> collectPortalStartConduits(ServerLevel level, BlockPos corePos) {
        Set<BlockPos> starts = new HashSet<>();
        collectAdjacentConduits(level, corePos, starts);

        var frameMatch = PortalFrameDetector.find(level, corePos);
        if (frameMatch.isPresent()) {
            for (BlockPos framePos : PortalFrameHelper.collectFrame(frameMatch.get(), corePos)) {
                collectAdjacentConduits(level, framePos, starts);
            }
        }
        return starts;
    }

    private static List<EnergyCondenserBlockEntity> getCondensers(ServerLevel level, Set<BlockPos> positions) {
        if (positions.isEmpty()) return List.of();
        List<EnergyCondenserBlockEntity> condensers = new ArrayList<>();
        for (BlockPos pos : positions) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof EnergyCondenserBlockEntity condenser) {
                condensers.add(condenser);
            }
        }
        return condensers;
    }

    private record ConduitScanResult(Set<BlockPos> condensers, Set<BlockPos> solarPanels, Set<BlockPos> parabolas) {}

    private static ConduitScanResult scanConduits(ServerLevel level, Set<BlockPos> startConduits) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> condensers = new HashSet<>();
        Set<BlockPos> solarPanels = new HashSet<>();
        Set<BlockPos> parabolas = new HashSet<>();

        for (BlockPos start : startConduits) {
            if (level.getBlockState(start).is(ModBlocks.ENERGY_CONDUIT) && visited.add(start.immutable())) {
                queue.add(start.immutable());
            }
        }

        int searched = 0;
        while (!queue.isEmpty()) {
            BlockPos conduitPos = queue.removeFirst();
            searched++;
            if (searched > MAX_CONDUIT_SEARCH) {
                break;
            }

            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = conduitPos.relative(direction);
                BlockState neighborState = level.getBlockState(neighborPos);

                if (neighborState.is(ModBlocks.ENERGY_CONDUIT) && visited.add(neighborPos.immutable())) {
                    queue.addLast(neighborPos.immutable());
                    continue;
                }

                if (neighborState.is(ModBlocks.ENERGY_CONDENSER)) {
                    condensers.add(neighborPos.immutable());
                    continue;
                }
                
                if (neighborState.is(ModBlocks.PARABOLA_BLOCK)) {
                    parabolas.add(neighborPos.immutable());
                    continue;
                }

                if (isSolarPanelSocketConnection(neighborState, direction)) {
                    solarPanels.add(neighborPos.immutable());
                }
            }
        }

        return new ConduitScanResult(condensers, solarPanels, parabolas);
    }

    private static boolean isSolarPanelSocketConnection(BlockState panelState, Direction directionFromConduitToPanel) {
        if (!panelState.is(ModBlocks.SOLAR_PANEL)) return false;
        if (!panelState.hasProperty(SolarPanelBlock.FACING)) return false;
        return SolarPanelBlock.canConnectConduit(panelState, directionFromConduitToPanel);
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
