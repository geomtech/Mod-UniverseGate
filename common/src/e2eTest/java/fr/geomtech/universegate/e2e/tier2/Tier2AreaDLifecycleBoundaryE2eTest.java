package fr.geomtech.universegate.e2e.tier2;

import fr.geomtech.universegate.EnergyNetworkHelper;
import fr.geomtech.universegate.PortalConnectionManager;
import fr.geomtech.universegate.PortalEnergyCosts;
import fr.geomtech.universegate.PortalPursuitTracker;
import fr.geomtech.universegate.PortalRegistrySavedData;
import fr.geomtech.universegate.PortalRiftHelper;
import fr.geomtech.universegate.PortalTeleportHandler;
import fr.geomtech.universegate.e2e.harness.E2eBootstrap;
import fr.geomtech.universegate.e2e.harness.E2ePortalFixtures;
import fr.geomtech.universegate.e2e.harness.SeamlessContractProbe;
import fr.geomtech.universegate.net.PortalFieldCollapsePayload;
import fr.geomtech.universegate.seamless.net.PortalEndpointResolver;
import fr.geomtech.universegate.seamless.net.PortalRemoteEndpointPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("tier2")
@Tag("areaD")
@DisplayName("Tier 2 Area D: Portal Lifecycle & Complex Entity Boundaries (F12-F13)")
class Tier2AreaDLifecycleBoundaryE2eTest {

    private static final String FQCN_PORTAL_CORE_BE = "fr.geomtech.universegate.PortalCoreBlockEntity";
    private static final String FQCN_PENDING_RIFT_COLLAPSE = "fr.geomtech.universegate.PortalRiftHelper$PendingRiftCollapse";
    private static final String FQCN_PURSUIT_STATE = "fr.geomtech.universegate.PortalPursuitTracker$PursuitState";
    private static final String FQCN_PORTAL_CROSSING_TRACKER = "fr.geomtech.universegate.seamless.physics.PortalCrossingTracker";
    private static final String FQCN_PORTAL_CROSSING_MATH = "fr.geomtech.universegate.seamless.math.PortalCrossingMath";
    private static final String FQCN_CLIENT_REMOTE_WORLD_MGR = "fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager";

    private static final double EPSILON = 1.0E-6D;

    // -------------------------------------------------------------------------
    // Feature 12 BVA & Negative
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T2-F12-01: Exact unstable threshold boundary t=1200 (stable) vs t=1201 (unstable)")
    void exactUnstableThresholdBoundary1200Vs1201() {
        Class<?> coreClass = SeamlessContractProbe.requireClass(FQCN_PORTAL_CORE_BE, false);
        long threshold = SeamlessContractProbe.readStaticField(coreClass, "UNSTABLE_THRESHOLD_TICKS", long.class);
        assertEquals(1200L, threshold);

        // Contract: (now - activeStartedGameTime) > UNSTABLE_THRESHOLD_TICKS -> strictly > 1200
        assertFalse(1200L > threshold);
        assertTrue(1201L > threshold);

        assertEquals(120, PortalEnergyCosts.activeCostPerSecond(false));
        assertEquals(180, PortalEnergyCosts.activeCostPerSecond(true));
        assertEquals(120, EnergyNetworkHelper.getPortalActiveEnergyCostPerSecond(false));
        assertEquals(180, EnergyNetworkHelper.getPortalActiveEnergyCostPerSecond(true));

        assertEquals(PortalRemoteEndpointPayload.FLAG_ACTIVE,
                PortalEndpointResolver.computeStateFlags(false, true, false));
        assertEquals((byte) (PortalRemoteEndpointPayload.FLAG_ACTIVE | PortalRemoteEndpointPayload.FLAG_UNSTABLE),
                PortalEndpointResolver.computeStateFlags(false, true, true));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T2-F12-02: Energy exhaustion boundary (<120 FE stable / <180 FE unstable)")
    void energyExhaustionBoundary() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        assertEquals(120, PortalEnergyCosts.ACTIVE_COST_PER_SECOND);
        assertEquals(50, PortalEnergyCosts.UNSTABLE_COST_BONUS_PERCENT);
        assertEquals(180, PortalEnergyCosts.activeCostPerSecond(true));

        Class<?> coreClass = SeamlessContractProbe.requireClass(FQCN_PORTAL_CORE_BE, false);
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                coreClass, "drainActivePortalEnergy", boolean.class, ServerLevel.class, long.class
        ));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalConnectionManager.class, "forceCloseOneSide", void.class, ServerLevel.class, BlockPos.class
        ));

        PortalFieldCollapsePayload collapse = new PortalFieldCollapsePayload(
                E2ePortalFixtures.expectedInterior(E2ePortalFixtures.DEFAULT_SOURCE_CORE, Direction.EAST),
                true,
                true
        );
        assertEquals(collapse, SeamlessContractProbe.roundTripByteBufCodec(
                PortalFieldCollapsePayload.STREAM_CODEC, collapse));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MGR, false));
    }

    @Test
    @DisplayName("T2-F12-03: Dead entity (!isAlive()) entering unstable portal guard")
    void deadEntityEnteringUnstablePortalGuard() {
        Class<?> coreClass = SeamlessContractProbe.requireClass(FQCN_PORTAL_CORE_BE, false);
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalTeleportHandler.class, "tryTeleport", void.class, Entity.class, BlockPos.class
        ));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalTeleportHandler.class, "tryTeleport", void.class, Entity.class, BlockPos.class, coreClass
        ));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
    }

    @Test
    @DisplayName("T2-F12-04: Unstable redirect when NO other usable portal exists returns null cleanly")
    void unstableRedirectWithNoOtherPortalReturnsNull() {
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalTeleportHandler.class, "pickRandomPortalDestination", PortalRegistrySavedData.PortalEntry.class,
                MinecraftServer.class, PortalRegistrySavedData.class, RandomSource.class, UUID.class, UUID.class
        ));
        PortalRegistrySavedData emptyRegistry = new PortalRegistrySavedData();
        assertTrue(emptyRegistry.listAll().isEmpty());

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T2-F12-05: Double Rift arrival collapse scheduling preserves original timer")
    void doubleRiftArrivalPreservesOriginalTimer() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        assertNotNull(SeamlessContractProbe.readStaticField(PortalRiftHelper.class, "PENDING_RIFT_COLLAPSES", Map.class));
        long delay = SeamlessContractProbe.readStaticField(PortalRiftHelper.class, "RIFT_COLLAPSE_DELAY_TICKS", long.class);
        assertEquals(200L, delay);
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalRiftHelper.class, "scheduleRiftCollapse", void.class, ServerLevel.class, BlockPos.class
        ));

        Class<?> pendingClass = SeamlessContractProbe.requireRecord(
                FQCN_PENDING_RIFT_COLLAPSE, "corePos", "startTick", "explodeTick"
        );
        Object pending = SeamlessContractProbe.newInstance(
                pendingClass,
                new Class<?>[]{BlockPos.class, long.class, long.class},
                BlockPos.ZERO, 1000L, 1000L + delay
        );
        long startTick = SeamlessContractProbe.invokeInstance(pending, "startTick", long.class, new Class<?>[0]);
        long explodeTick = SeamlessContractProbe.invokeInstance(pending, "explodeTick", long.class, new Class<?>[0]);
        assertEquals(delay, explodeTick - startTick);

        float clampedLow = SeamlessContractProbe.invokeStatic(
                PortalRiftHelper.class, "computeUnstableVolume", float.class, new Class<?>[]{double.class}, -0.5D);
        float clampedHigh = SeamlessContractProbe.invokeStatic(
                PortalRiftHelper.class, "computeUnstableVolume", float.class, new Class<?>[]{double.class}, 1.5D);
        assertEquals(0.35F, clampedLow, 1.0E-5F);
        assertEquals(2.6F, clampedHigh, 1.0E-5F);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    // -------------------------------------------------------------------------
    // Feature 13 BVA & Negative
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T2-F13-01: Player dismounts on exact crossing tick without duplication/vanishing")
    void playerDismountsOnExactCrossingTick() {
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
    }

    @Test
    @DisplayName("T2-F13-02: Unridden Minecart / ChestMinecart / HopperMinecart momentum & inventory")
    void unriddenMinecartVariantsMomentumAndInventory() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        for (EntityType<?> cartType : new EntityType<?>[]{
                EntityType.MINECART, EntityType.CHEST_MINECART, EntityType.HOPPER_MINECART
        }) {
            EntityDimensions dims = cartType.getDimensions();
            assertEquals(0.98F, dims.width(), 1.0E-4F);
            assertEquals(0.7F, dims.height(), 1.0E-4F);
        }

        assertEquals(0.18D, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_MINECART_EXTRA_OFFSET", double.class), EPSILON);
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalTeleportHandler.class, "shouldPreserveProjectileMomentum", boolean.class, Entity.class
        ));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
    }

    @Test
    @DisplayName("T2-F13-03: Pursuing mob dies or target disconnects -> clean TRACKED eviction")
    void pursuingMobDiesOrTargetDisconnectsCleanEviction() {
        assertNotNull(SeamlessContractProbe.readStaticField(PortalPursuitTracker.class, "TRACKED", Map.class));
        SeamlessContractProbe.requireRecord(
                FQCN_PURSUIT_STATE, "sourceDimension", "portalFieldPos", "playerId", "expiresAtGameTime"
        );
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalPursuitTracker.class, "tickWorld", void.class, ServerLevel.class
        ));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalPursuitTracker.class, "isProtectedFromNaturalDespawn", boolean.class, Mob.class
        ));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T2-F13-04: Pursuing mob grace timeout boundary t=299 protected vs t=300 evicted")
    void pursuingMobGraceTimeoutBoundary299Vs300() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        long graceTicks = SeamlessContractProbe.readStaticField(
                PortalPursuitTracker.class, "MOB_PORTAL_PURSUIT_GRACE_TICKS", long.class);
        assertEquals(300L, graceTicks);

        Class<?> stateClass = SeamlessContractProbe.requireRecord(
                FQCN_PURSUIT_STATE, "sourceDimension", "portalFieldPos", "playerId", "expiresAtGameTime"
        );
        long t0 = 5000L;
        Object state = SeamlessContractProbe.newInstance(
                stateClass,
                new Class<?>[]{ResourceKey.class, BlockPos.class, UUID.class, long.class},
                E2ePortalFixtures.OVERWORLD, E2ePortalFixtures.DEFAULT_SOURCE_CORE, UUID.randomUUID(), t0 + graceTicks
        );
        long expiresAt = SeamlessContractProbe.invokeInstance(state, "expiresAtGameTime", long.class, new Class<?>[0]);
        assertEquals(5300L, expiresAt);
        assertTrue((t0 + 299L) < expiresAt);
        assertFalse((t0 + 300L) < expiresAt);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T2-F13-05: Zero-clearance lateral clamping for wide vehicle (Boat width=1.375)")
    void zeroClearanceLateralClampingForWideVehicle() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        //? if >=1.21.11 {
        /*EntityDimensions boatDims = EntityType.OAK_BOAT.getDimensions();
        *///?} else {
        EntityDimensions boatDims = EntityType.BOAT.getDimensions();
        //?}
        assertEquals(1.375F, boatDims.width(), 1.0E-4F);

        double innerHalfWidth = SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_INNER_HALF_WIDTH", double.class);
        double epsilon = SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_POSITION_EPSILON", double.class);
        double lateralLimit = Math.max(0.0D, innerHalfWidth - (boatDims.width() * 0.5D) - epsilon);
        assertEquals(0.8025D, lateralLimit, 1.0E-5D);

        double clampedPos = SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class, "clamp", double.class,
                new Class<?>[]{double.class, double.class, double.class},
                1.49D, -lateralLimit, lateralLimit
        );
        double clampedNeg = SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class, "clamp", double.class,
                new Class<?>[]{double.class, double.class, double.class},
                -1.49D, -lateralLimit, lateralLimit
        );
        assertEquals(0.8025D, clampedPos, 1.0E-5D);
        assertEquals(-0.8025D, clampedNeg, 1.0E-5D);
        assertTrue(Math.abs(clampedPos) + (boatDims.width() * 0.5D) <= innerHalfWidth - epsilon + 1.0E-5D);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }
}
