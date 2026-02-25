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
import java.util.UUID;

public final class EnergyNetworkHelper {

    public static final int PORTAL_OPEN_BASE_ENERGY_COST = 1200;
    public static final int PORTAL_OPEN_MIN_ENERGY_COST = 900;
    public static final double PORTAL_OPEN_DISTANCE_COST_PER_BLOCK = 0.05D;
    public static final int PORTAL_OPEN_CROSS_DIMENSION_BONUS = 700;
    public static final int PORTAL_OPEN_RIFT_DIMENSION_BONUS = 900;
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

    public record CondenserNetwork(Set<BlockPos> condensers,
                                   Set<BlockPos> solarPanels,
                                   Set<BlockPos> parabolas,
                                   Set<BlockPos> zpcInterfaces) {}
    public record EnergyNetworkSnapshot(long storedEnergy,
                                        long capacity,
                                        int condenserCount,
                                        int panelCount,
                                        int activePanelCount,
                                        int parabolaCount) {
        public static final EnergyNetworkSnapshot EMPTY = new EnergyNetworkSnapshot(0L, 0L, 0, 0, 0, 0);

        public int chargePercent() {
            if (capacity <= 0) return 0;
            return (int) Math.max(0L, Math.min(100L, Math.round((storedEnergy * 100.0D) / (double) capacity)));
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
            return new CondenserNetwork(condensers, Set.of(), Set.of(), Set.of());
        }

        ConduitScanResult scan = scanConduits(level, startConduits);
        condensers.addAll(scan.condensers());
        return new CondenserNetwork(condensers, scan.solarPanels(), scan.parabolas(), scan.zpcInterfaces());
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
        List<ZpcInterfaceControllerBlockEntity> zpcInterfaces = getZpcInterfaces(level, network.zpcInterfaces());
        if (condensers.isEmpty() && zpcInterfaces.isEmpty()) {
            return EnergyNetworkSnapshot.EMPTY;
        }

        long stored = 0L;
        long capacity = 0L;
        int reservoirs = 0;

        for (EnergyCondenserBlockEntity condenser : condensers) {
            stored += condenser.getStoredEnergy();
            capacity += EnergyCondenserBlockEntity.CAPACITY;
            reservoirs++;
        }

        for (ZpcInterfaceControllerBlockEntity zpcInterface : zpcInterfaces) {
            long zpcCapacity = Math.max(0L, zpcInterface.getTotalCapacity());
            long zpcStored = Math.max(0L, zpcInterface.getStoredEnergy());
            if (zpcCapacity <= 0L && zpcStored <= 0L) {
                continue;
            }

            stored += zpcStored;
            capacity += zpcCapacity;
            reservoirs++;
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
                reservoirs,
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

        if (startConduits.isEmpty()) return false;

        ConduitScanResult scan = scanConduits(level, startConduits);
        return consumeFromSources(level, scan.condensers(), scan.zpcInterfaces(), amount);
    }

    public static long getAvailableEnergyLongFromNetwork(ServerLevel level, BlockPos startNode) {
        Set<BlockPos> startConduits = new HashSet<>();
        collectAdjacentConduits(level, startNode, startConduits);
        if (startConduits.isEmpty()) return 0L;

        ConduitScanResult scan = scanConduits(level, startConduits);
        List<EnergyCondenserBlockEntity> condensers = getCondensers(level, scan.condensers());
        List<ZpcInterfaceControllerBlockEntity> zpcInterfaces = getZpcInterfaces(level, scan.zpcInterfaces());
        zpcInterfaces.removeIf(controller -> !controller.canSupplyEnergy() || controller.getStoredEnergy() <= 0L);

        return getTotalEnergyStored(condensers, zpcInterfaces);
    }

    public static int getAvailableEnergyFromNetwork(ServerLevel level, BlockPos startNode) {
        long total = getAvailableEnergyLongFromNetwork(level, startNode);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public static boolean isNodeLinkedToEnergyConduit(ServerLevel level, BlockPos startNode) {
        Set<BlockPos> startConduits = new HashSet<>();
        collectAdjacentConduits(level, startNode, startConduits);
        return !startConduits.isEmpty();
    }

    private static boolean consumeFromSources(ServerLevel level,
                                              Set<BlockPos> condenserPositions,
                                              Set<BlockPos> zpcInterfacePositions,
                                              int amount) {
        if (amount <= 0) return true;

        List<ZpcInterfaceControllerBlockEntity> zpcInterfaces = getZpcInterfaces(level, zpcInterfacePositions);
        List<EnergyCondenserBlockEntity> condensers = getCondensers(level, condenserPositions);
        zpcInterfaces.removeIf(controller -> !controller.canSupplyEnergy() || controller.getStoredEnergy() <= 0L);
        if (zpcInterfaces.isEmpty() && condensers.isEmpty()) return false;

        long totalStored = getTotalEnergyStored(condensers, zpcInterfaces);
        if (totalStored < amount) return false;

        zpcInterfaces.sort(Comparator
                .comparingLong(ZpcInterfaceControllerBlockEntity::getStoredEnergy)
                .reversed()
                .thenComparing(be -> be.getBlockPos(), POS_COMPARATOR));

        condensers.sort(Comparator
                .comparingInt(EnergyCondenserBlockEntity::getStoredEnergy)
                .reversed()
                .thenComparing(be -> be.getBlockPos(), POS_COMPARATOR));

        int remaining = amount;

        for (ZpcInterfaceControllerBlockEntity controller : zpcInterfaces) {
            if (remaining <= 0) break;
            remaining -= controller.extractEnergy(remaining);
        }

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
        List<ZpcInterfaceControllerBlockEntity> zpcInterfaces = getPortalZpcInterfaces(level, corePos);
        zpcInterfaces.removeIf(controller -> !controller.canSupplyEnergy() || controller.getStoredEnergy() <= 0L);
        long total = getTotalEnergyStored(condensers, zpcInterfaces);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public static boolean consumePortalEnergy(ServerLevel level, BlockPos corePos, int amount) {
        if (amount <= 0) return true;

        List<EnergyCondenserBlockEntity> condensers = getPortalCondensers(level, corePos);
        List<ZpcInterfaceControllerBlockEntity> zpcInterfaces = getPortalZpcInterfaces(level, corePos);
        return consumeFromSources(level,
                toPosSet(condensers),
                toPosSet(zpcInterfaces),
                amount);
    }

    public static int getPortalActiveEnergyCostPerSecond(boolean unstableByTime) {
        if (!unstableByTime) return PORTAL_ACTIVE_ENERGY_COST_PER_SECOND;

        double factor = 1.0D + (PORTAL_UNSTABLE_COST_BONUS_PERCENT / 100.0D);
        return (int) Math.ceil(PORTAL_ACTIVE_ENERGY_COST_PER_SECOND * factor);
    }

    public static int getPortalOpenEnergyCost(ServerLevel sourceLevel, BlockPos sourceCorePos, UUID targetPortalId) {
        if (targetPortalId == null) return PORTAL_OPEN_BASE_ENERGY_COST;

        PortalRegistrySavedData.PortalEntry targetEntry =
                PortalRegistrySavedData.get(sourceLevel.getServer()).get(targetPortalId);
        if (targetEntry == null) return PORTAL_OPEN_BASE_ENERGY_COST;

        return getPortalOpenEnergyCost(sourceLevel, sourceCorePos, targetEntry);
    }

    public static int getPortalOpenEnergyCost(ServerLevel sourceLevel,
                                              BlockPos sourceCorePos,
                                              PortalRegistrySavedData.PortalEntry targetEntry) {
        double distance = Math.sqrt(sourceCorePos.distSqr(targetEntry.pos()));
        int distanceBlocks = (int) Math.ceil(distance);
        int distanceCost = (int) Math.ceil(distanceBlocks * PORTAL_OPEN_DISTANCE_COST_PER_BLOCK);

        int dimensionCost = 0;
        boolean crossDimension = !sourceLevel.dimension().equals(targetEntry.dim());
        if (crossDimension) {
            dimensionCost += PORTAL_OPEN_CROSS_DIMENSION_BONUS;
        }
        if (sourceLevel.dimension().equals(UniverseGateDimensions.RIFT)
                || targetEntry.dim().equals(UniverseGateDimensions.RIFT)) {
            dimensionCost += PORTAL_OPEN_RIFT_DIMENSION_BONUS;
        }

        int total = PORTAL_OPEN_BASE_ENERGY_COST + distanceCost + dimensionCost;
        return Math.max(PORTAL_OPEN_MIN_ENERGY_COST, total);
    }

    public static boolean isRiftDimension(ServerLevel level) {
        return level.dimension().equals(UniverseGateDimensions.RIFT);
    }

    public static BlockPos findNearestMobClonerOnNetwork(ServerLevel level, BlockPos startNode) {
        Set<BlockPos> startConduits = collectMachineStartConduits(level, startNode);
        if (startConduits.isEmpty()) return null;

        Set<BlockPos> mobCloners = scanMobCloners(level, startConduits);
        if (mobCloners.isEmpty()) return null;

        BlockPos origin = startNode.immutable();
        return mobCloners.stream()
                .min(Comparator
                        .comparingDouble((BlockPos pos) -> pos.distSqr(origin))
                        .thenComparing(POS_COMPARATOR))
                .orElse(null);
    }

    private static Set<BlockPos> collectMachineStartConduits(ServerLevel level, BlockPos startNode) {
        Set<BlockPos> startConduits = new HashSet<>();
        collectAdjacentConduits(level, startNode, startConduits);
        if (!startConduits.isEmpty()) {
            return startConduits;
        }

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = startNode.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);

            if (neighborState.is(ModBlocks.MOB_CLONER) || neighborState.is(ModBlocks.MOB_CLONER_CONTROLLER)) {
                collectAdjacentConduits(level, neighborPos, startConduits);
            }
        }

        return startConduits;
    }

    private static List<EnergyCondenserBlockEntity> getPortalCondensers(ServerLevel level, BlockPos corePos) {
        Set<BlockPos> startConduits = collectPortalStartConduits(level, corePos);
        if (startConduits.isEmpty()) return new ArrayList<>();

        ConduitScanResult scan = scanConduits(level, startConduits);
        return getCondensers(level, scan.condensers());
    }

    private static List<ZpcInterfaceControllerBlockEntity> getPortalZpcInterfaces(ServerLevel level, BlockPos corePos) {
        Set<BlockPos> startConduits = collectPortalStartConduits(level, corePos);
        if (startConduits.isEmpty()) return new ArrayList<>();

        ConduitScanResult scan = scanConduits(level, startConduits);
        return getZpcInterfaces(level, scan.zpcInterfaces());
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

    private static Set<BlockPos> scanMobCloners(ServerLevel level, Set<BlockPos> startConduits) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> mobCloners = new HashSet<>();

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

                if (neighborState.is(ModBlocks.MOB_CLONER)) {
                    mobCloners.add(neighborPos.immutable());
                }
            }
        }

        return mobCloners;
    }

    private static List<EnergyCondenserBlockEntity> getCondensers(ServerLevel level, Set<BlockPos> positions) {
        if (positions.isEmpty()) return new ArrayList<>();
        List<EnergyCondenserBlockEntity> condensers = new ArrayList<>();
        for (BlockPos pos : positions) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof EnergyCondenserBlockEntity condenser) {
                condensers.add(condenser);
            }
        }
        return condensers;
    }

    private static List<ZpcInterfaceControllerBlockEntity> getZpcInterfaces(ServerLevel level, Set<BlockPos> positions) {
        if (positions.isEmpty()) return new ArrayList<>();

        List<ZpcInterfaceControllerBlockEntity> controllers = new ArrayList<>();
        for (BlockPos pos : positions) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ZpcInterfaceControllerBlockEntity controller) {
                controllers.add(controller);
            }
        }
        return controllers;
    }

    private static long getTotalEnergyStored(List<EnergyCondenserBlockEntity> condensers,
                                             List<ZpcInterfaceControllerBlockEntity> zpcInterfaces) {
        long total = 0L;
        for (EnergyCondenserBlockEntity condenser : condensers) {
            total += condenser.getStoredEnergy();
        }
        for (ZpcInterfaceControllerBlockEntity zpcInterface : zpcInterfaces) {
            total += zpcInterface.getStoredEnergy();
        }
        return total;
    }

    private static Set<BlockPos> toPosSet(List<? extends BlockEntity> blockEntities) {
        if (blockEntities.isEmpty()) return Set.of();
        Set<BlockPos> positions = new HashSet<>();
        for (BlockEntity blockEntity : blockEntities) {
            positions.add(blockEntity.getBlockPos().immutable());
        }
        return positions;
    }

    private record ConduitScanResult(Set<BlockPos> condensers,
                                     Set<BlockPos> solarPanels,
                                     Set<BlockPos> parabolas,
                                     Set<BlockPos> zpcInterfaces) {}

    private static ConduitScanResult scanConduits(ServerLevel level, Set<BlockPos> startConduits) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> condensers = new HashSet<>();
        Set<BlockPos> solarPanels = new HashSet<>();
        Set<BlockPos> parabolas = new HashSet<>();
        Set<BlockPos> zpcInterfaces = new HashSet<>();

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

                if (neighborState.is(ModBlocks.ZPC_INTERFACE_CONTROLLER)) {
                    zpcInterfaces.add(neighborPos.immutable());
                    continue;
                }

                if (isSolarPanelSocketConnection(neighborState, direction)) {
                    solarPanels.add(neighborPos.immutable());
                }
            }
        }

        return new ConduitScanResult(condensers, solarPanels, parabolas, zpcInterfaces);
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
