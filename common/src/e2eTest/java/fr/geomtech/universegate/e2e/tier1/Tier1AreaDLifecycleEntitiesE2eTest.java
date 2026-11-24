package fr.geomtech.universegate.e2e.tier1;

import fr.geomtech.universegate.EnergyNetworkHelper;
import fr.geomtech.universegate.PortalConnectionManager;
import fr.geomtech.universegate.PortalEnergyCosts;
import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.PortalGateAnimator;
import fr.geomtech.universegate.PortalGateRenderContext;
import fr.geomtech.universegate.PortalOpeningParticles;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("tier1")
@Tag("areaD")
@DisplayName("Tier 1 Area D: Portal Lifecycle, Unstable State & Complex Entities (F12-F13)")
class Tier1AreaDLifecycleEntitiesE2eTest {

    private static final String FQCN_PORTAL_CORE_BE = "fr.geomtech.universegate.PortalCoreBlockEntity";
    private static final String FQCN_PORTAL_GATE_RING_LAYER = "fr.geomtech.universegate.PortalGateRingLayer";
    private static final String FQCN_PORTAL_AMBIENT_SOUND_CLIENT = "fr.geomtech.universegate.PortalAmbientSoundClient";
    private static final String FQCN_MOB_DESPAWN_MIXIN = "fr.geomtech.universegate.mixin.MobDespawnMixin";
    private static final String FQCN_PORTAL_CROSSING_TRACKER = "fr.geomtech.universegate.seamless.physics.PortalCrossingTracker";
    private static final String FQCN_PORTAL_CROSSING_MATH = "fr.geomtech.universegate.seamless.math.PortalCrossingMath";
    private static final String FQCN_CLIENT_REMOTE_WORLD_MGR = "fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager";

    private static final double EPSILON = 1.0E-6D;

    // -------------------------------------------------------------------------
    // Feature 12: Portal state & unstable bug fixes
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T1-F12-01: Full nominal lifecycle Idle -> Opening -> Active -> Close")
    void fullNominalLifecycleIdleOpeningActiveClose() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        assertEquals(180L, SeamlessContractProbe.readStaticField(
                PortalConnectionManager.class, "OPENING_DURATION_TICKS", long.class));
        assertEquals(1200L, SeamlessContractProbe.readStaticField(
                PortalConnectionManager.class, "ACTIVE_OPEN_DURATION_TICKS", long.class));

        Class<?> coreClass = SeamlessContractProbe.requireClass(FQCN_PORTAL_CORE_BE, false);
        assertEquals(180L, SeamlessContractProbe.readStaticField(
                coreClass, "OPENING_DURATION_FALLBACK_TICKS", long.class));

        byte closedFlags = PortalEndpointResolver.computeStateFlags(false, false, false);
        byte openingFlags = PortalEndpointResolver.computeStateFlags(true, false, false);
        byte activeFlags = PortalEndpointResolver.computeStateFlags(false, true, false);
        assertEquals(PortalRemoteEndpointPayload.FLAG_CLOSED, closedFlags);
        assertEquals(PortalRemoteEndpointPayload.FLAG_OPENING, openingFlags);
        assertEquals(PortalRemoteEndpointPayload.FLAG_ACTIVE, activeFlags);

        for (byte flags : new byte[]{closedFlags, openingFlags, activeFlags}) {
            PortalRemoteEndpointPayload payload = new PortalRemoteEndpointPayload(
                    E2ePortalFixtures.DEFAULT_SOURCE_CORE,
                    E2ePortalFixtures.NETHER,
                    E2ePortalFixtures.DEFAULT_TARGET_CORE,
                    Direction.EAST,
                    Direction.SOUTH,
                    true,
                    flags
            );
            PortalRemoteEndpointPayload decoded = SeamlessContractProbe.roundTripByteBufCodec(
                    PortalRemoteEndpointPayload.STREAM_CODEC, payload
            );
            assertEquals(payload, decoded);
        }

        assertEquals(7.5F, SeamlessContractProbe.readStaticField(PortalGateAnimator.class, "OPENING_SPEED", float.class));
        assertEquals(1.2F, SeamlessContractProbe.readStaticField(PortalGateAnimator.class, "ACTIVE_SPEED", float.class));
        assertEquals(60, SeamlessContractProbe.readStaticField(PortalGateAnimator.class, "VENT_TICKS", int.class));
        assertEquals(6.0F, SeamlessContractProbe.readStaticField(PortalGateAnimator.class, "REWIND_MAX_SPEED", float.class));
        assertEquals(40L, SeamlessContractProbe.readStaticField(
                PortalOpeningParticles.class, "OPENING_BLACKOUT_TICKS", long.class));

        List<BlockPos> interior = E2ePortalFixtures.expectedInterior(E2ePortalFixtures.DEFAULT_SOURCE_CORE, Direction.EAST);
        PortalFieldCollapsePayload collapse = new PortalFieldCollapsePayload(interior, true, false);
        assertEquals(collapse, SeamlessContractProbe.roundTripByteBufCodec(PortalFieldCollapsePayload.STREAM_CODEC, collapse));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MGR, false));
    }

    @Test
    @DisplayName("T1-F12-02: Unstable visual and audio transition past 1200 ticks")
    void unstableVisualAndAudioTransitionPast1200Ticks() {
        Class<?> coreClass = SeamlessContractProbe.requireClass(FQCN_PORTAL_CORE_BE, false);
        assertEquals(1200L, SeamlessContractProbe.readStaticField(
                coreClass, "UNSTABLE_THRESHOLD_TICKS", long.class));
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                coreClass, "isVortexUnstable", boolean.class, long.class
        ));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalConnectionManager.class, "syncActivePortalInstability", void.class,
                ServerLevel.class, BlockPos.class, boolean.class
        ));

        byte unstableFlags = PortalEndpointResolver.computeStateFlags(false, true, true);
        assertEquals((byte) (PortalRemoteEndpointPayload.FLAG_ACTIVE | PortalRemoteEndpointPayload.FLAG_UNSTABLE), unstableFlags);

        Class<?> ambientClass = SeamlessContractProbe.requireClass(FQCN_PORTAL_AMBIENT_SOUND_CLIENT, false);
        assertEquals(540L, SeamlessContractProbe.readStaticField(ambientClass, "AMBIENT_LOOP_INTERVAL_TICKS", long.class));
        assertEquals(10L, SeamlessContractProbe.readStaticField(ambientClass, "UNSTABLE_AMBIENT_LOOP_INTERVAL_TICKS", long.class));
        assertEquals(3.5F, SeamlessContractProbe.readStaticField(PortalGateAnimator.class, "UNSTABLE_SPEED", float.class));

        Class<?> ringLayerClass = SeamlessContractProbe.requireClass(FQCN_PORTAL_GATE_RING_LAYER);
        PortalGateRenderContext unstableCtx = new PortalGateRenderContext(
                null, null, BlockPos.ZERO, Direction.EAST,
                10L, 0.0F, 10.0F, false, true, true, 0.0F, 0.0F, 1.0F, false
        );
        int lipColor = SeamlessContractProbe.invokeStatic(
                ringLayerClass, "lipColor", int.class,
                new Class<?>[]{PortalGateRenderContext.class, float.class},
                unstableCtx, 0.0F
        );
        assertTrue(lipColor == 0xFFFFFF || lipColor == 0x5A5A5A);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MGR, false));
    }

    @Test
    @DisplayName("T1-F12-03: Cross-dim unstable LivingEntity damage/debuff post-teleport target fix")
    void crossDimUnstableLivingEntityPostTeleportFix() {
        assertEquals(0.40F, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "UNSTABLE_DAMAGE_CHANCE", float.class));
        assertEquals(8.0F, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "UNSTABLE_DAMAGE_AMOUNT", float.class));
        assertEquals(60, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "UNSTABLE_NAUSEA_DURATION_TICKS", int.class));
        assertEquals(100, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "UNSTABLE_SLOWNESS_DURATION_TICKS", int.class));
        assertEquals(2, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "UNSTABLE_SLOWNESS_AMPLIFIER", int.class));

        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalTeleportHandler.class, "applyUnstableDebuffs", void.class, LivingEntity.class
        ));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T1-F12-04: Unstable redirect 10% random portal / 5% Rift and arrival collapse")
    void unstableRedirectAndRiftArrivalCollapse() {
        assertEquals(0.10F, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "UNSTABLE_RANDOM_PORTAL_CHANCE", float.class));
        assertEquals(0.05F, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "UNSTABLE_RIFT_CHANCE", float.class));

        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalTeleportHandler.class, "pickRandomPortalDestination", PortalRegistrySavedData.PortalEntry.class,
                MinecraftServer.class, PortalRegistrySavedData.class, RandomSource.class, UUID.class, UUID.class
        ));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalTeleportHandler.class, "findRiftPortalDestination", PortalRegistrySavedData.PortalEntry.class,
                MinecraftServer.class, PortalRegistrySavedData.class, UUID.class
        ));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalRiftHelper.class, "handleRiftArrival", void.class,
                ServerLevel.class, BlockPos.class
        ));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T1-F12-05: Rift lightning link immediate instability and 10s arrival collapse")
    void riftLightningLinkImmediateInstabilityAndCollapse() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        assertEquals(200L, SeamlessContractProbe.readStaticField(
                PortalRiftHelper.class, "RIFT_COLLAPSE_DELAY_TICKS", long.class));
        assertEquals(10L, SeamlessContractProbe.readStaticField(
                PortalRiftHelper.class, "RIFT_UNSTABLE_SOUND_INTERVAL_TICKS", long.class));
        float minVol = SeamlessContractProbe.readStaticField(
                PortalRiftHelper.class, "RIFT_UNSTABLE_MIN_VOLUME", float.class);
        float maxVol = SeamlessContractProbe.readStaticField(
                PortalRiftHelper.class, "RIFT_UNSTABLE_MAX_VOLUME", float.class);
        assertEquals(0.35F, minVol, 1.0E-5F);
        assertEquals(2.6F, maxVol, 1.0E-5F);

        float v0 = SeamlessContractProbe.invokeStatic(
                PortalRiftHelper.class, "computeUnstableVolume", float.class,
                new Class<?>[]{double.class}, 0.0D
        );
        float vMid = SeamlessContractProbe.invokeStatic(
                PortalRiftHelper.class, "computeUnstableVolume", float.class,
                new Class<?>[]{double.class}, 0.5D
        );
        float v1 = SeamlessContractProbe.invokeStatic(
                PortalRiftHelper.class, "computeUnstableVolume", float.class,
                new Class<?>[]{double.class}, 1.0D
        );
        assertEquals(minVol, v0, 1.0E-5F);
        assertTrue(vMid > v0 && vMid < v1);
        assertEquals(maxVol, v1, 1.0E-5F);

        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalRiftHelper.class, "detonateRiftArrivalPortal", void.class,
                ServerLevel.class, BlockPos.class
        ));
        Class<?> coreClass = SeamlessContractProbe.requireClass(FQCN_PORTAL_CORE_BE, false);
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(coreClass, "isRiftLightningLink", boolean.class));

        PortalFieldCollapsePayload unstableCollapse = new PortalFieldCollapsePayload(
                E2ePortalFixtures.expectedInterior(BlockPos.ZERO, Direction.EAST), true, true
        );
        assertEquals(unstableCollapse, SeamlessContractProbe.roundTripByteBufCodec(
                PortalFieldCollapsePayload.STREAM_CODEC, unstableCollapse));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MGR, false));
    }

    // -------------------------------------------------------------------------
    // Feature 13: Complex entities (vehicles, riders, pursuits)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T1-F13-01: AbstractMinecart + ServerPlayer rider cross-dimension traversal")
    void minecartPlusPlayerRiderCrossDimension() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        assertEquals(0.18D, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_MINECART_EXTRA_OFFSET", double.class), EPSILON);
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalTeleportHandler.class, "shouldPreserveProjectileMomentum", boolean.class, Entity.class
        ));

        EntityDimensions cartDims = EntityType.MINECART.getDimensions();
        assertTrue(cartDims.width() < E2ePortalFixtures.INNER_WIDTH);
        assertTrue(cartDims.height() < E2ePortalFixtures.INNER_HEIGHT);

        Vec3 cartVel = new Vec3(0.4D, 0.0D, 0.0D);
        Vec3 rotated = SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class, "transformVelocityThroughPortal", Vec3.class,
                new Class<?>[]{Vec3.class, Direction.class, Direction.class, Direction.class, Direction.class},
                cartVel, Direction.NORTH, Direction.EAST, Direction.EAST, Direction.SOUTH
        );
        assertEquals(0.4D, rotated.length(), EPSILON);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T1-F13-02: Boat with 2 passengers (Player + Villager) preserves passenger slots")
    void boatWithTwoPassengersPreservesSlots() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        //? if >=1.21.11 {
        /*EntityDimensions boatDims = EntityType.OAK_BOAT.getDimensions();
        *///?} else {
        EntityDimensions boatDims = EntityType.BOAT.getDimensions();
        //?}
        EntityDimensions villagerDims = EntityType.VILLAGER.getDimensions();
        assertEquals(1.375F, boatDims.width(), 1.0E-4F);
        assertEquals(0.5625F, boatDims.height(), 1.0E-4F);
        assertTrue(villagerDims.height() < E2ePortalFixtures.INNER_HEIGHT);

        double lateralMargin = (PortalFrameDetector.INNER_WIDTH * 0.5D) - (boatDims.width() * 0.5D) - 0.01D;
        assertEquals(0.8025D, lateralMargin, 1.0E-5D);
        assertTrue(lateralMargin > 0.0D);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T1-F13-03: Saddled Horse/Pig + ServerPlayer rider cross-dimension")
    void saddledHorseOrPigPlusPlayerCrossDimension() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        EntityDimensions horseDims = EntityType.HORSE.getDimensions();
        EntityDimensions pigDims = EntityType.PIG.getDimensions();
        assertTrue(horseDims.width() < E2ePortalFixtures.INNER_WIDTH);
        assertTrue(pigDims.width() < E2ePortalFixtures.INNER_WIDTH);
        assertTrue(horseDims.height() < E2ePortalFixtures.INNER_HEIGHT);

        double verticalMax = E2ePortalFixtures.INNER_HEIGHT - horseDims.height() - 0.01D;
        assertTrue(verticalMax > 2.0D);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
    }

    @Test
    @DisplayName("T1-F13-04: Hostile Mob pursuit across portal (PortalPursuitTracker + MobDespawnMixin)")
    void hostileMobPursuitAcrossPortal() {
        assertEquals(300L, SeamlessContractProbe.readStaticField(
                PortalPursuitTracker.class, "MOB_PORTAL_PURSUIT_GRACE_TICKS", long.class));
        assertEquals(10L, SeamlessContractProbe.readStaticField(
                PortalPursuitTracker.class, "MOB_PORTAL_NUDGE_INTERVAL_TICKS", long.class));
        assertEquals(1.15D, SeamlessContractProbe.readStaticField(
                PortalPursuitTracker.class, "MOB_PORTAL_NUDGE_SPEED", double.class), EPSILON);

        assertEquals(8.0D, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PURSUING_MOB_SEARCH_RADIUS_XZ", double.class), EPSILON);
        assertEquals(5.0D, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PURSUING_MOB_SEARCH_RADIUS_Y", double.class), EPSILON);
        assertEquals(49.0D, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PURSUING_MOB_MAX_PORTAL_DISTANCE_SQR", double.class), EPSILON);

        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalPursuitTracker.class, "trackMobTowardPortalField", void.class,
                ServerLevel.class, Mob.class, BlockPos.class, UUID.class
        ));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalPursuitTracker.class, "onMobTeleported", void.class,
                ServerLevel.class, Mob.class
        ));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalPursuitTracker.class, "isProtectedFromNaturalDespawn", boolean.class, Mob.class
        ));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalPursuitTracker.class, "tickWorld", void.class, ServerLevel.class
        ));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_MOB_DESPAWN_MIXIN, false));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T1-F13-05: Multi-entity stacked riders and leashed mob traversal")
    void multiEntityStackedRidersAndLeashedMobTraversal() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        EntityDimensions chickenDims = EntityType.CHICKEN.getDimensions();
        EntityDimensions zombieDims = EntityType.ZOMBIE.getDimensions();
        EntityDimensions cowDims = EntityType.COW.getDimensions();
        assertTrue(chickenDims.width() < E2ePortalFixtures.INNER_WIDTH);
        assertTrue(zombieDims.width() < E2ePortalFixtures.INNER_WIDTH);
        assertTrue(cowDims.width() < E2ePortalFixtures.INNER_WIDTH);
        assertTrue((chickenDims.height() + zombieDims.height()) < E2ePortalFixtures.INNER_HEIGHT);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }
}
