package fr.geomtech.universegate.seamless.net;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalEndpointResolverTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void computeStateFlagsProducesExpectedBitmasks() {
        assertEquals(
                PortalRemoteEndpointPayload.FLAG_CLOSED,
                PortalEndpointResolver.computeStateFlags(false, false, false)
        );
        assertEquals(
                PortalRemoteEndpointPayload.FLAG_CLOSED,
                PortalEndpointResolver.computeStateFlags(false, false, true)
        );
        assertEquals(
                PortalRemoteEndpointPayload.FLAG_OPENING,
                PortalEndpointResolver.computeStateFlags(true, false, false)
        );
        assertEquals(
                PortalRemoteEndpointPayload.FLAG_ACTIVE,
                PortalEndpointResolver.computeStateFlags(false, true, false)
        );
        assertEquals(
                (byte) (PortalRemoteEndpointPayload.FLAG_ACTIVE | PortalRemoteEndpointPayload.FLAG_UNSTABLE),
                PortalEndpointResolver.computeStateFlags(false, true, true)
        );
    }

    @Test
    void normalizeFrameRightMapsAxesToCanonicalDirections() {
        assertEquals(Direction.EAST, PortalEndpointResolver.normalizeFrameRight(Direction.EAST));
        assertEquals(Direction.EAST, PortalEndpointResolver.normalizeFrameRight(Direction.WEST));
        assertEquals(Direction.SOUTH, PortalEndpointResolver.normalizeFrameRight(Direction.SOUTH));
        assertEquals(Direction.SOUTH, PortalEndpointResolver.normalizeFrameRight(Direction.NORTH));
        assertEquals(Direction.EAST, PortalEndpointResolver.normalizeFrameRight(Direction.UP));
        assertEquals(Direction.EAST, PortalEndpointResolver.normalizeFrameRight(null));
    }

    @Test
    void resolveExitNormalMatchesKeyboardSideAndDefaults() {
        BlockPos core = new BlockPos(10, 64, 10);

        assertEquals(Direction.SOUTH, PortalEndpointResolver.resolveExitNormal(Direction.EAST, core, null));
        assertEquals(Direction.SOUTH, PortalEndpointResolver.resolveExitNormal(Direction.EAST, core, new BlockPos(12, 64, 10)));
        assertEquals(Direction.SOUTH, PortalEndpointResolver.resolveExitNormal(Direction.EAST, core, new BlockPos(10, 64, 15)));
        assertEquals(Direction.NORTH, PortalEndpointResolver.resolveExitNormal(Direction.EAST, core, new BlockPos(10, 64, 5)));

        assertEquals(Direction.EAST, PortalEndpointResolver.resolveExitNormal(Direction.SOUTH, core, null));
        assertEquals(Direction.EAST, PortalEndpointResolver.resolveExitNormal(Direction.SOUTH, core, new BlockPos(10, 64, 12)));
        assertEquals(Direction.EAST, PortalEndpointResolver.resolveExitNormal(Direction.SOUTH, core, new BlockPos(15, 64, 10)));
        assertEquals(Direction.WEST, PortalEndpointResolver.resolveExitNormal(Direction.SOUTH, core, new BlockPos(5, 64, 10)));
    }

    @Test
    void surfaceRightForExitNormalIsCounterClockwise() {
        assertEquals(Direction.EAST, PortalEndpointResolver.surfaceRightForExitNormal(Direction.SOUTH));
        assertEquals(Direction.WEST, PortalEndpointResolver.surfaceRightForExitNormal(Direction.NORTH));
        assertEquals(Direction.NORTH, PortalEndpointResolver.surfaceRightForExitNormal(Direction.EAST));
        assertEquals(Direction.SOUTH, PortalEndpointResolver.surfaceRightForExitNormal(Direction.WEST));
        assertEquals(Direction.EAST, PortalEndpointResolver.surfaceRightForExitNormal(Direction.UP));
    }

    @Test
    void resolveEndpointHandlesLiveCachedMissingChunkAndInactiveCases() {
        assertTrue(PortalEndpointResolver.resolveEndpoint(
                false, false, false, true,
                Level.NETHER, new BlockPos(1, 2, 3), Direction.EAST, Direction.SOUTH,
                null, null, null, null
        ).isEmpty());

        assertTrue(PortalEndpointResolver.resolveEndpoint(
                false, true, false, true,
                null, null, null, null,
                null, null, null, null
        ).isEmpty());

        // Live target overrides stale cache
        ResolvedPortalEndpoint liveResolved = PortalEndpointResolver.resolveEndpoint(
                false, true, true, true,
                Level.NETHER, new BlockPos(100, 64, -50), Direction.SOUTH, Direction.WEST,
                Level.NETHER, new BlockPos(100, 64, -50), Direction.EAST, Direction.NORTH
        ).orElseThrow();
        assertEquals(Level.NETHER, liveResolved.targetDim());
        assertEquals(new BlockPos(100, 64, -50), liveResolved.targetCorePos());
        assertEquals(Direction.SOUTH, liveResolved.targetRight());
        assertEquals(Direction.WEST, liveResolved.exitNormal());
        assertTrue(liveResolved.outboundEnabled());
        assertEquals(
                (byte) (PortalRemoteEndpointPayload.FLAG_ACTIVE | PortalRemoteEndpointPayload.FLAG_UNSTABLE),
                liveResolved.stateFlags()
        );

        PortalRemoteEndpointPayload payload = liveResolved.toPayload(new BlockPos(1, 70, 2));
        assertEquals(new BlockPos(1, 70, 2), payload.corePos());
        assertEquals(Direction.SOUTH, payload.targetRight());
        assertEquals(Direction.WEST, payload.exitNormal());
        assertTrue(payload.isActive());
        assertTrue(payload.isUnstable());

        // Unloaded destination chunk falls back to cached values when target matches
        ResolvedPortalEndpoint cachedFallback = PortalEndpointResolver.resolveEndpoint(
                true, false, false, false,
                Level.NETHER, new BlockPos(100, 64, -50), null, null,
                Level.NETHER, new BlockPos(100, 64, -50), Direction.SOUTH, Direction.WEST
        ).orElseThrow();
        assertEquals(Direction.SOUTH, cachedFallback.targetRight());
        assertEquals(Direction.WEST, cachedFallback.exitNormal());
        assertFalse(cachedFallback.outboundEnabled());
        assertEquals(PortalRemoteEndpointPayload.FLAG_OPENING, cachedFallback.stateFlags());

        // Mismatched target ignores old cache and falls back to canonical defaults
        ResolvedPortalEndpoint targetChangedDefaults = PortalEndpointResolver.resolveEndpoint(
                true, false, false, true,
                Level.END, new BlockPos(500, 80, 500), null, null,
                Level.NETHER, new BlockPos(100, 64, -50), Direction.SOUTH, Direction.WEST
        ).orElseThrow();
        assertEquals(Level.END, targetChangedDefaults.targetDim());
        assertEquals(new BlockPos(500, 80, 500), targetChangedDefaults.targetCorePos());
        assertEquals(Direction.EAST, targetChangedDefaults.targetRight());
        assertEquals(Direction.SOUTH, targetChangedDefaults.exitNormal());
    }

    @Test
    void closedPayloadPreservesPreviousTargetOrUsesFallback() {
        BlockPos sourceCore = new BlockPos(10, 64, 10);
        ResolvedPortalEndpoint prev = new ResolvedPortalEndpoint(
                Level.NETHER,
                new BlockPos(50, 70, -20),
                Direction.SOUTH,
                Direction.WEST,
                true,
                PortalRemoteEndpointPayload.FLAG_ACTIVE
        );

        PortalRemoteEndpointPayload withPrev = PortalEndpointResolver.closedPayload(sourceCore, Level.OVERWORLD, prev);
        assertEquals(sourceCore, withPrev.corePos());
        assertEquals(Level.NETHER, withPrev.targetDim());
        assertEquals(new BlockPos(50, 70, -20), withPrev.targetCorePos());
        assertEquals(Direction.SOUTH, withPrev.targetRight());
        assertEquals(Direction.WEST, withPrev.exitNormal());
        assertFalse(withPrev.outboundEnabled());
        assertTrue(withPrev.isClosed());

        PortalRemoteEndpointPayload withoutPrev = PortalEndpointResolver.closedPayload(sourceCore, Level.OVERWORLD, null);
        assertEquals(Level.OVERWORLD, withoutPrev.targetDim());
        assertEquals(BlockPos.ZERO, withoutPrev.targetCorePos());
        assertEquals(Direction.EAST, withoutPrev.targetRight());
        assertEquals(Direction.SOUTH, withoutPrev.exitNormal());
        assertFalse(withoutPrev.outboundEnabled());
        assertTrue(withoutPrev.isClosed());
    }

    @Test
    void computeObserverSyncHandlesInitialIncrementalForcedAndUntrackedObservers() {
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID p2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID p3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

        PortalEndpointResolver.ObserverSyncPlan initial = PortalEndpointResolver.computeObserverSync(
                Set.of(), List.of(p1, p2, p1), false
        );
        assertEquals(List.of(p1, p2), initial.toSend());
        assertEquals(Set.of(p1, p2), initial.nextSynced());
        assertEquals(List.of(), initial.untracked());

        PortalEndpointResolver.ObserverSyncPlan incremental = PortalEndpointResolver.computeObserverSync(
                initial.nextSynced(), List.of(p2, p3), false
        );
        assertEquals(List.of(p3), incremental.toSend());
        assertEquals(Set.of(p2, p3), incremental.nextSynced());
        assertEquals(List.of(p1), incremental.untracked());

        PortalEndpointResolver.ObserverSyncPlan forced = PortalEndpointResolver.computeObserverSync(
                incremental.nextSynced(), List.of(p2), true
        );
        assertEquals(List.of(p2), forced.toSend());
        assertEquals(Set.of(p2), forced.nextSynced());
        assertEquals(List.of(p3), forced.untracked());
    }
}
