package fr.geomtech.universegate.seamless.net;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PortalEndpointResolver {

    public record ObserverSyncPlan(
            List<UUID> toSend,
            Set<UUID> nextSynced,
            List<UUID> untracked
    ) {
        public ObserverSyncPlan {
            toSend = List.copyOf(toSend);
            nextSynced = Set.copyOf(nextSynced);
            untracked = List.copyOf(untracked);
        }
    }

    private PortalEndpointResolver() {}

    public static byte computeStateFlags(boolean opening, boolean active, boolean unstable) {
        if (!opening && !active) {
            return PortalRemoteEndpointPayload.FLAG_CLOSED;
        }
        byte flags = 0;
        if (opening) {
            flags |= PortalRemoteEndpointPayload.FLAG_OPENING;
        }
        if (active) {
            flags |= PortalRemoteEndpointPayload.FLAG_ACTIVE;
        }
        if (unstable) {
            flags |= PortalRemoteEndpointPayload.FLAG_UNSTABLE;
        }
        return flags;
    }

    public static Direction normalizeFrameRight(@Nullable Direction candidate) {
        if (candidate != null && candidate.getAxis() == Direction.Axis.Z) {
            return Direction.SOUTH;
        }
        return Direction.EAST;
    }

    public static Direction resolveExitNormal(Direction frameRight,
                                              BlockPos targetCorePos,
                                              @Nullable BlockPos keyboardPos) {
        Objects.requireNonNull(targetCorePos, "targetCorePos");
        Direction normalizedRight = normalizeFrameRight(frameRight);
        if (keyboardPos != null) {
            if (normalizedRight == Direction.EAST) {
                int dz = Integer.compare(keyboardPos.getZ(), targetCorePos.getZ());
                if (dz > 0) return Direction.SOUTH;
                if (dz < 0) return Direction.NORTH;
            } else {
                int dx = Integer.compare(keyboardPos.getX(), targetCorePos.getX());
                if (dx > 0) return Direction.EAST;
                if (dx < 0) return Direction.WEST;
            }
        }
        return defaultExitNormalForRight(normalizedRight);
    }

    public static Direction surfaceRightForExitNormal(Direction exitNormal) {
        Objects.requireNonNull(exitNormal, "exitNormal");
        if (exitNormal.getAxis() == Direction.Axis.Y) {
            return Direction.EAST;
        }
        return exitNormal.getCounterClockWise();
    }

    public static Optional<ResolvedPortalEndpoint> resolveEndpoint(
            boolean opening,
            boolean active,
            boolean unstable,
            boolean outboundEnabled,
            @Nullable ResourceKey<Level> registryTargetDim,
            @Nullable BlockPos registryTargetCorePos,
            @Nullable Direction liveTargetRight,
            @Nullable Direction liveExitNormal,
            @Nullable ResourceKey<Level> cachedTargetDim,
            @Nullable BlockPos cachedTargetCorePos,
            @Nullable Direction cachedTargetRight,
            @Nullable Direction cachedExitNormal
    ) {
        if (!opening && !active) {
            return Optional.empty();
        }

        ResourceKey<Level> targetDim = registryTargetDim != null ? registryTargetDim : cachedTargetDim;
        BlockPos targetCorePos = registryTargetCorePos != null ? registryTargetCorePos : cachedTargetCorePos;
        if (targetDim == null || targetCorePos == null) {
            return Optional.empty();
        }

        boolean cacheMatchesTarget = (cachedTargetDim == null || targetDim.equals(cachedTargetDim))
                && (cachedTargetCorePos == null || targetCorePos.equals(cachedTargetCorePos));

        Direction targetRight;
        if (liveTargetRight != null) {
            targetRight = normalizeFrameRight(liveTargetRight);
        } else if (cacheMatchesTarget && cachedTargetRight != null) {
            targetRight = normalizeFrameRight(cachedTargetRight);
        } else {
            targetRight = Direction.EAST;
        }

        Direction exitNormal;
        if (liveExitNormal != null && isValidExitNormalForRight(targetRight, liveExitNormal)) {
            exitNormal = liveExitNormal;
        } else if (cacheMatchesTarget && cachedExitNormal != null && isValidExitNormalForRight(targetRight, cachedExitNormal)) {
            exitNormal = cachedExitNormal;
        } else {
            exitNormal = defaultExitNormalForRight(targetRight);
        }

        byte stateFlags = computeStateFlags(opening, active, unstable);
        return Optional.of(new ResolvedPortalEndpoint(
                targetDim,
                targetCorePos.immutable(),
                targetRight,
                exitNormal,
                outboundEnabled,
                stateFlags
        ));
    }

    public static PortalRemoteEndpointPayload closedPayload(BlockPos sourceCorePos,
                                                            ResourceKey<Level> fallbackDim,
                                                            @Nullable ResolvedPortalEndpoint previous) {
        Objects.requireNonNull(sourceCorePos, "sourceCorePos");
        Objects.requireNonNull(fallbackDim, "fallbackDim");
        if (previous != null) {
            return new PortalRemoteEndpointPayload(
                    sourceCorePos.immutable(),
                    previous.targetDim(),
                    previous.targetCorePos(),
                    previous.targetRight(),
                    previous.exitNormal(),
                    false,
                    PortalRemoteEndpointPayload.FLAG_CLOSED
            );
        }
        return new PortalRemoteEndpointPayload(
                sourceCorePos.immutable(),
                fallbackDim,
                BlockPos.ZERO,
                Direction.EAST,
                Direction.SOUTH,
                false,
                PortalRemoteEndpointPayload.FLAG_CLOSED
        );
    }

    public static ObserverSyncPlan computeObserverSync(Set<UUID> previouslySynced,
                                                       List<UUID> currentTrackers,
                                                       boolean forceResyncAll) {
        Objects.requireNonNull(previouslySynced, "previouslySynced");
        Objects.requireNonNull(currentTrackers, "currentTrackers");

        LinkedHashSet<UUID> currentSet = new LinkedHashSet<>();
        for (UUID id : currentTrackers) {
            if (id != null) {
                currentSet.add(id);
            }
        }

        List<UUID> toSend = new ArrayList<>();
        for (UUID id : currentSet) {
            if (forceResyncAll || !previouslySynced.contains(id)) {
                toSend.add(id);
            }
        }

        List<UUID> untracked = new ArrayList<>();
        for (UUID id : previouslySynced) {
            if (!currentSet.contains(id)) {
                untracked.add(id);
            }
        }

        return new ObserverSyncPlan(toSend, currentSet, untracked);
    }

    private static Direction defaultExitNormalForRight(Direction normalizedRight) {
        return normalizedRight == Direction.EAST ? Direction.SOUTH : Direction.EAST;
    }

    private static boolean isValidExitNormalForRight(Direction normalizedRight, Direction candidate) {
        if (normalizedRight == Direction.EAST) {
            return candidate == Direction.NORTH || candidate == Direction.SOUTH;
        }
        return candidate == Direction.EAST || candidate == Direction.WEST;
    }
}
