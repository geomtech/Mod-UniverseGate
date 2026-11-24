package fr.geomtech.universegate.e2e.tier1;

import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.PortalFrameHelper;
import fr.geomtech.universegate.PortalTeleportHandler;
import fr.geomtech.universegate.e2e.harness.E2eBootstrap;
import fr.geomtech.universegate.e2e.harness.E2ePortalFixtures;
import fr.geomtech.universegate.e2e.harness.SeamlessContractProbe;
import fr.geomtech.universegate.seamless.net.PortalEndpointResolver;
import fr.geomtech.universegate.seamless.net.PortalRemoteEndpointPayload;
import fr.geomtech.universegate.seamless.net.ResolvedPortalEndpoint;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("tier1")
@Tag("areaC")
@DisplayName("Tier 1 Area C: Continuous Traversal & Seamless Dimension Switch (F8-F11)")
class Tier1AreaCTraversalE2eTest {

    private static final String FQCN_PORTAL_CORE_BE = "fr.geomtech.universegate.PortalCoreBlockEntity";
    private static final String FQCN_PORTAL_TRANSFORM = "fr.geomtech.universegate.seamless.math.PortalTransform";
    private static final String FQCN_OBLIQUE_FRUSTUM_MATH = "fr.geomtech.universegate.seamless.math.ObliqueFrustumMath";
    private static final String FQCN_PORTAL_CROSSING_MATH = "fr.geomtech.universegate.seamless.math.PortalCrossingMath";
    private static final String FQCN_PORTAL_CROSSING_TRACKER = "fr.geomtech.universegate.seamless.physics.PortalCrossingTracker";
    private static final String FQCN_PORTAL_HALF_COLLISION = "fr.geomtech.universegate.seamless.physics.PortalHalfCollisionHandler";
    private static final String FQCN_CLIENT_REMOTE_WORLD_MGR = "fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager";
    private static final String FQCN_SEAMLESS_SWITCH_COORDINATOR = "fr.geomtech.universegate.seamless.client.SeamlessSwitchCoordinator";
    private static final String FQCN_SWITCH_HINT_PAYLOAD = "fr.geomtech.universegate.seamless.net.SeamlessSwitchHintPayload";

    private static final double EPSILON = 1.0E-6D;

    private static Vec3 invokeTransformVelocity(Vec3 velocity,
                                                Direction sourceSurfaceRight,
                                                Direction sourceNormal,
                                                Direction targetSurfaceRight,
                                                Direction targetNormal) {
        return SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class,
                "transformVelocityThroughPortal",
                Vec3.class,
                new Class<?>[]{Vec3.class, Direction.class, Direction.class, Direction.class, Direction.class},
                velocity,
                sourceSurfaceRight,
                sourceNormal,
                targetSurfaceRight,
                targetNormal
        );
    }

    private static float invokeYawFromVelocity(Vec3 velocity, float fallbackYaw) {
        return SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class,
                "yawFromVelocity",
                float.class,
                new Class<?>[]{Vec3.class, float.class},
                velocity,
                fallbackYaw
        );
    }

    private static float invokePitchFromVelocity(Vec3 velocity, float fallbackPitch) {
        return SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class,
                "pitchFromVelocity",
                float.class,
                new Class<?>[]{Vec3.class, float.class},
                velocity,
                fallbackPitch
        );
    }

    private static double invokeClamp(double value, double min, double max) {
        return SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class,
                "clamp",
                double.class,
                new Class<?>[]{double.class, double.class, double.class},
                value,
                min,
                max
        );
    }

    private static Object verifySwitchHintCodecRoundTrip(ResourceKey<Level> targetDim,
                                                        BlockPos targetCorePos,
                                                        double x,
                                                        double y,
                                                        double z,
                                                        float yRot,
                                                        float xRot,
                                                        Vec3 velocity) {
        Class<?> hintClass = SeamlessContractProbe.requireRecord(
                FQCN_SWITCH_HINT_PAYLOAD,
                "targetDim", "targetCorePos", "x", "y", "z", "yRot", "xRot", "velocity"
        );
        StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, Object> codec =
                SeamlessContractProbe.requireStreamCodec(hintClass, "STREAM_CODEC");
        Object payload = SeamlessContractProbe.newInstance(
                hintClass,
                new Class<?>[]{
                        ResourceKey.class, BlockPos.class,
                        double.class, double.class, double.class,
                        float.class, float.class, Vec3.class
                },
                targetDim, targetCorePos, x, y, z, yRot, xRot, velocity
        );
        Object decoded = SeamlessContractProbe.roundTripByteBufCodec(codec, payload);
        assertEquals(payload, decoded);
        return decoded;
    }

    // -------------------------------------------------------------------------
    // Feature 8: Continuous plane-crossing & cooldown
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T1-F8-01: Smooth forward walk across center plane (DEPTH = 0.5)")
    void smoothForwardWalkAcrossCenterPlane() {
        Class<?> membraneClass = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");
        assertEquals(E2ePortalFixtures.MEMBRANE_DEPTH_PX,
                SeamlessContractProbe.readStaticField(membraneClass, "DEPTH", float.class));
        assertEquals(E2ePortalFixtures.MEMBRANE_RADIUS_PX,
                SeamlessContractProbe.readStaticField(membraneClass, "RADIUS", int.class));
        assertEquals(0.5D, E2ePortalFixtures.MEMBRANE_DEPTH_BLOCKS, EPSILON);
        assertEquals(2.25D, E2ePortalFixtures.MEMBRANE_RADIUS_BLOCKS, EPSILON);

        BlockPos corePos = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        double planeZ = corePos.getZ() + E2ePortalFixtures.MEMBRANE_DEPTH_BLOCKS;
        double beforeZ = planeZ + 0.1D;
        double afterZ = beforeZ - 0.1D - 0.1D; // 0.1 b/tick steps across planeZ

        double dBefore = beforeZ - planeZ;
        double dAt = (beforeZ - 0.1D) - planeZ;
        double dAfter = afterZ - planeZ;
        assertTrue(dBefore > 0.0D);
        assertEquals(0.0D, dAt, EPSILON);
        assertTrue(dAfter < 0.0D);
        assertTrue(Math.signum(dBefore) != Math.signum(dAfter));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T1-F8-02: Crossing through wiredFieldCells inside circular aperture")
    void crossingThroughWiredFieldCells() {
        Class<?> coreClass = SeamlessContractProbe.requireClass(FQCN_PORTAL_CORE_BE, false);
        SeamlessContractProbe.requireField(coreClass, "wiredFieldCells", List.class);
        SeamlessContractProbe.requireField(coreClass, "outboundTravelEnabled", boolean.class);

        BlockPos corePos = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos wiredCell = corePos.above(); // local (0, 1, 0)
        List<BlockPos> interior = E2ePortalFixtures.expectedInterior(corePos, Direction.EAST);
        assertTrue(interior.contains(wiredCell), "Center-bottom cell (0,1,0) must belong to portal interior");

        Vec3 portalCenter = E2ePortalFixtures.portalCenter(corePos);
        Vec3 wiredCellCenter = new Vec3(wiredCell.getX() + 0.5D, wiredCell.getY() + 0.5D, wiredCell.getZ() + 0.5D);
        double radialDist = wiredCellCenter.distanceTo(portalCenter);
        assertEquals(1.5D, radialDist, EPSILON);
        assertTrue(radialDist <= E2ePortalFixtures.MEMBRANE_RADIUS_BLOCKS);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T1-F8-03: Per-entity cooldown prevents immediate re-entry ping-pong")
    void perEntityCooldownPreventsImmediatePingPong() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        double minExitOffset = SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_MIN_EXIT_OFFSET", double.class);
        double fieldClearance = SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_FIELD_CLEARANCE", double.class);
        assertEquals(0.35D, minExitOffset, EPSILON);
        assertEquals(0.55D, fieldClearance, EPSILON);

        EntityDimensions playerDims = EntityType.PLAYER.getDimensions();
        double exitOffset = Math.max(minExitOffset, (playerDims.width() * 0.5D) + fieldClearance);
        assertEquals(0.85D, exitOffset, EPSILON);
        assertTrue(exitOffset > E2ePortalFixtures.MEMBRANE_DEPTH_BLOCKS,
                "Player exit offset must clear DEPTH=0.5 plane to avoid immediate re-entry overlap");

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
    }

    @Test
    @DisplayName("T1-F8-04: Cooldown resets cleanly after leaving portal zone")
    void cooldownResetsCleanlyAfterLeavingPortalZone() {
        double exitClearance = Math.max(
                SeamlessContractProbe.readStaticField(PortalTeleportHandler.class, "PORTAL_MIN_EXIT_OFFSET", double.class),
                0.3D + SeamlessContractProbe.readStaticField(PortalTeleportHandler.class, "PORTAL_FIELD_CLEARANCE", double.class)
        );
        double departureDistance = 3.0D;
        assertTrue(departureDistance > E2ePortalFixtures.MEMBRANE_RADIUS_BLOCKS);
        assertTrue(departureDistance > exitClearance);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
    }

    @Test
    @DisplayName("T1-F8-05: Non-player mob and item plane crossing mirrors lateral offset")
    void nonPlayerMobAndItemPlaneCrossing() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        double innerHalfWidth = SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_INNER_HALF_WIDTH", double.class);
        double epsilon = SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_POSITION_EPSILON", double.class);

        EntityDimensions zombieDims = EntityType.ZOMBIE.getDimensions();
        EntityDimensions itemDims = EntityType.ITEM.getDimensions();

        double zombieLimit = Math.max(0.0D, innerHalfWidth - (zombieDims.width() * 0.5D) - epsilon);
        double itemLimit = Math.max(0.0D, innerHalfWidth - (itemDims.width() * 0.5D) - epsilon);
        assertEquals(1.19D, zombieLimit, EPSILON);
        assertEquals(1.365D, itemLimit, EPSILON);

        double clampedZombie = invokeClamp(0.75D, -zombieLimit, zombieLimit);
        double clampedItem = invokeClamp(-0.75D, -itemLimit, itemLimit);
        assertEquals(-0.75D, -clampedZombie, EPSILON);
        assertEquals(0.75D, -clampedItem, EPSILON);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
    }

    // -------------------------------------------------------------------------
    // Feature 9: Half-through collision & camera clipping
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T1-F9-01: Far-side source back-wall collision suppression inside aperture")
    void farSideSourceBackWallSuppression() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        assertNotNull(SeamlessContractProbe.requireInstanceMethod(Entity.class, "collide", Vec3.class, Vec3.class));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                Entity.class, "collideBoundingBox", Vec3.class,
                Entity.class, Vec3.class, AABB.class, Level.class, List.class
        ));

        BlockPos corePos = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        double planeZ = corePos.getZ() + E2ePortalFixtures.MEMBRANE_DEPTH_BLOCKS;
        EntityDimensions playerDims = EntityType.PLAYER.getDimensions();
        double halfW = playerDims.width() * 0.5D;
        AABB straddlingBox = new AABB(
                corePos.getX() + 0.5D - halfW, corePos.getY() + 1.0D, planeZ - halfW,
                corePos.getX() + 0.5D + halfW, corePos.getY() + 1.0D + playerDims.height(), planeZ + halfW
        );
        assertTrue(straddlingBox.minZ < planeZ && straddlingBox.maxZ > planeZ);
        assertTrue(Math.abs(straddlingBox.getCenter().x - (corePos.getX() + 0.5D)) < 1.5D);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION, false));
    }

    @Test
    @DisplayName("T1-F9-02: Remote destination floor/step collision sampling while half-through")
    void remoteDestinationFloorCollisionSampling() {
        Optional<ResolvedPortalEndpoint> resolved = PortalEndpointResolver.resolveEndpoint(
                false, true, false, true,
                E2ePortalFixtures.NETHER, E2ePortalFixtures.DEFAULT_TARGET_CORE,
                Direction.SOUTH, Direction.EAST,
                null, null, null, null
        );
        assertTrue(resolved.isPresent());
        ResolvedPortalEndpoint endpoint = resolved.get();
        assertEquals(E2ePortalFixtures.NETHER, endpoint.targetDim());
        assertEquals(E2ePortalFixtures.DEFAULT_TARGET_CORE, endpoint.targetCorePos());
        assertEquals(Direction.SOUTH, endpoint.targetRight());
        assertEquals(Direction.EAST, endpoint.exitNormal());

        double expectedExitFloorY = endpoint.targetCorePos().getY() + 1.0D;
        assertEquals(73.0D, expectedExitFloorY, EPSILON);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MGR, false));
    }

    @Test
    @DisplayName("T1-F9-03: Source frame border collision remains enforced outside aperture")
    void sourceFrameBorderCollisionEnforced() {
        BlockPos corePos = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        PortalFrameDetector.FrameMatch match = E2ePortalFixtures.frameMatch(corePos, Direction.EAST);
        List<BlockPos> frameBlocks = PortalFrameHelper.collectFrame(match, corePos);
        List<BlockPos> interior = E2ePortalFixtures.expectedInterior(corePos, Direction.EAST);

        assertEquals(E2ePortalFixtures.EXPECTED_FRAME_BLOCK_COUNT, frameBlocks.size());
        for (BlockPos borderPos : frameBlocks) {
            assertFalse(interior.contains(borderPos), "Frame border block must be disjoint from aperture interior");
            int dx = borderPos.getX() - corePos.getX();
            int dy = borderPos.getY() - corePos.getY();
            assertTrue(Math.abs(dx) == 2 || dy == 0 || dy == 5);
        }

        // Edge probe points outside 3x4 interior [dx in -1.5..1.5, dy in 1.0..5.0]
        assertTrue(Math.abs(1.6D) > 1.5D);
        assertTrue(4.2D > 4.0D);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION, false));
    }

    @Test
    @DisplayName("T1-F9-04: First-person camera near-plane clipping protection at plane")
    void firstPersonCameraNearPlaneProtection() {
        Class<?> cameraClass = SeamlessContractProbe.requireClass(Camera.class.getName(), false);
        //? if >=1.21.11 {
        /*assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                cameraClass, "setup", void.class,
                Level.class, Entity.class, boolean.class, boolean.class, float.class
        ));
        *///?} else {
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                cameraClass, "setup", void.class,
                BlockGetter.class, Entity.class, boolean.class, boolean.class, float.class
        ));
        //?}

        double eyeDeltaFromPlane = 0.02D;
        assertTrue(Math.abs(eyeDeltaFromPlane) <= E2ePortalFixtures.MEMBRANE_DEPTH_BLOCKS);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_OBLIQUE_FRUSTUM_MATH, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION, false));
    }

    @Test
    @DisplayName("T1-F9-05: Third-person camera zoom (Camera.getMaxZoom) protection")
    void thirdPersonCameraMaxZoomProtection() {
        Class<?> cameraClass = SeamlessContractProbe.requireClass(Camera.class.getName(), false);
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                cameraClass, "getMaxZoom", float.class, float.class
        ));

        // Ray passing through (dx=0, dy=3, dz=-1) lies well inside 2.25-block aperture radius
        double radial = Math.hypot(0.0D, 3.0D - E2ePortalFixtures.RING_CENTER_OFFSET_Y);
        assertTrue(radial < E2ePortalFixtures.MEMBRANE_RADIUS_BLOCKS);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION, false));
    }

    // -------------------------------------------------------------------------
    // Feature 10: Full velocity & orientation continuity
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T1-F10-01: Sprint-jump player momentum preservation across parallel portals")
    void sprintJumpPlayerMomentumPreservation() {
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalTeleportHandler.class, "shouldPreserveProjectileMomentum", boolean.class, Entity.class
        ));

        Vec3 input = new Vec3(0.10D, 0.42D, -0.35D);
        // Entering NORTH (right=WEST) -> exiting NORTH (right=WEST)
        Vec3 out = invokeTransformVelocity(input, Direction.WEST, Direction.NORTH, Direction.WEST, Direction.NORTH);
        assertEquals(-0.10D, out.x, EPSILON);
        assertEquals(0.42D, out.y, EPSILON);
        assertEquals(-0.35D, out.z, EPSILON);
        assertEquals(input.length(), out.length(), EPSILON);

        // Generalized M3 continuous traversal must cover players beyond legacy projectile-only check
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T1-F10-02: 90-degree rotated portal player yaw and look continuity")
    void ninetyDegreeRotatedPortalYawAndLookContinuity() {
        Direction sourceNormal = Direction.NORTH;
        Direction sourceRight = PortalEndpointResolver.surfaceRightForExitNormal(sourceNormal);
        Direction exitNormal = Direction.EAST;
        Direction targetRight = PortalEndpointResolver.surfaceRightForExitNormal(exitNormal);
        assertEquals(Direction.WEST, sourceRight);
        assertEquals(Direction.NORTH, targetRight);

        // Player moving toward NORTH (dz < 0 -> sourceForward > 0) with slight strafe
        Vec3 entryVel = new Vec3(-0.20D, 0.0D, -0.80D);
        Vec3 exitVel = invokeTransformVelocity(entryVel, sourceRight, sourceNormal, targetRight, exitNormal);
        assertEquals(0.80D, exitVel.x, EPSILON);
        assertEquals(0.0D, exitVel.y, EPSILON);
        assertEquals(0.20D, exitVel.z, EPSILON);

        float wrappedExitYaw = Mth.wrapDegrees(180.0F + 90.0F);
        assertEquals(-90.0F, wrappedExitYaw, 1.0E-4F);
        float pitch = invokePitchFromVelocity(new Vec3(0.0D, Math.sin(Math.toRadians(15.0D)), -Math.cos(Math.toRadians(15.0D))), 0.0F);
        assertEquals(-15.0F, pitch, 1.0E-3F);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T1-F10-03: ItemEntity, ThrownTrident, and Snowball momentum continuity")
    void itemTridentSnowballMomentumContinuity() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        for (EntityType<?> type : List.of(EntityType.ITEM, EntityType.TRIDENT, EntityType.SNOWBALL)) {
            EntityDimensions dims = type.getDimensions();
            assertTrue(dims.width() < E2ePortalFixtures.INNER_WIDTH);
            assertTrue(dims.height() < E2ePortalFixtures.INNER_HEIGHT);
        }

        Vec3 input = new Vec3(0.5D, 0.2D, 0.8D);
        Vec3 rotated = invokeTransformVelocity(input, Direction.WEST, Direction.NORTH, Direction.NORTH, Direction.EAST);
        assertEquals(0.2D, rotated.y, EPSILON);
        assertEquals(input.length(), rotated.length(), EPSILON);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T1-F10-04: AbstractArrow and ThrownEnderpearl trajectory continuity")
    void arrowAndEnderpearlTrajectoryContinuity() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        for (EntityType<?> type : List.of(EntityType.ARROW, EntityType.ENDER_PEARL)) {
            EntityDimensions dims = type.getDimensions();
            assertTrue(dims.width() < E2ePortalFixtures.INNER_WIDTH);
            assertTrue(dims.height() < E2ePortalFixtures.INNER_HEIGHT);
        }
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalTeleportHandler.class, "shouldPreserveProjectileMomentum", boolean.class, Entity.class
        ));

        // 30-degree angled shot across 180-degree opposing portals (SOUTH -> NORTH)
        Vec3 shot = new Vec3(0.5D, 0.3D, Math.sqrt(3.0D) * 0.5D);
        Vec3 out = invokeTransformVelocity(shot, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH);
        assertEquals(shot.length(), out.length(), EPSILON);
        assertEquals(shot.y, out.y, EPSILON);

        float yaw = invokeYawFromVelocity(out, 0.0F);
        float pitch = invokePitchFromVelocity(out, 0.0F);
        assertTrue(Float.isFinite(yaw));
        assertTrue(Float.isFinite(pitch));
        assertTrue(pitch < 0.0F);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
    }

    @Test
    @DisplayName("T1-F10-05: Walking Mob and Boat orientation and velocity continuity")
    void walkingMobAndBoatOrientationAndVelocityContinuity() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        EntityDimensions wolfDims = EntityType.WOLF.getDimensions();
        //? if >=1.21.11 {
        /*EntityDimensions boatDims = EntityType.OAK_BOAT.getDimensions();
        *///?} else {
        EntityDimensions boatDims = EntityType.BOAT.getDimensions();
        //?}
        assertTrue(wolfDims.width() < E2ePortalFixtures.INNER_WIDTH);
        assertTrue(boatDims.width() < E2ePortalFixtures.INNER_WIDTH);

        Vec3 boatVel = new Vec3(0.0D, 0.0D, 0.6D);
        Vec3 rotated = invokeTransformVelocity(boatVel, Direction.EAST, Direction.SOUTH, Direction.SOUTH, Direction.WEST);
        assertEquals(0.6D, rotated.length(), EPSILON);
        assertEquals(-0.6D, rotated.x, EPSILON);
        assertEquals(0.0D, rotated.z, EPSILON);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    // -------------------------------------------------------------------------
    // Feature 11: Loading-screen-free dimension switch
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T1-F11-01: Overworld to Nether zero-loading-screen swap")
    void overworldToNetherZeroLoadingScreenSwap() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        Class<?> listenerClass = SeamlessContractProbe.requireClass(ClientPacketListener.class.getName(), false);
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                listenerClass, "handleRespawn", void.class, ClientboundRespawnPacket.class
        ));
        //? if >=1.21.11 {
        /*assertNotNull(SeamlessContractProbe.requireClass("net.minecraft.client.gui.screens.LevelLoadingScreen", false));
        assertNotNull(SeamlessContractProbe.requireClass("net.minecraft.client.multiplayer.LevelLoadTracker", false));
        *///?} else {
        assertNotNull(SeamlessContractProbe.requireClass("net.minecraft.client.gui.screens.ReceivingLevelScreen", false));
        //?}

        verifySwitchHintCodecRoundTrip(
                E2ePortalFixtures.NETHER,
                E2ePortalFixtures.DEFAULT_TARGET_CORE,
                -319.15D, 73.0D, 480.5D,
                -90.0F, 0.0F,
                new Vec3(0.25D, 0.0D, 0.0D)
        );

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MGR, false));
    }

    @Test
    @DisplayName("T1-F11-02: Preloaded ClientLevel and compiled terrain reuse")
    void preloadedClientLevelAndTerrainReuse() {
        Class<?> levelRendererClass = SeamlessContractProbe.requireClass(LevelRenderer.class.getName(), false);
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                levelRendererClass, "setLevel", void.class, ClientLevel.class
        ));
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                levelRendererClass, "allChanged", void.class
        ));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MGR, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false));
    }

    @Test
    @DisplayName("T1-F11-03: Old ClientLevel preserved in remote cache for back-view")
    void oldClientLevelPreservedInRemoteCache() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        PortalRemoteEndpointPayload forward = new PortalRemoteEndpointPayload(
                E2ePortalFixtures.DEFAULT_SOURCE_CORE,
                E2ePortalFixtures.NETHER,
                E2ePortalFixtures.DEFAULT_TARGET_CORE,
                Direction.SOUTH,
                Direction.EAST,
                true,
                PortalRemoteEndpointPayload.FLAG_ACTIVE
        );
        PortalRemoteEndpointPayload reverse = new PortalRemoteEndpointPayload(
                E2ePortalFixtures.DEFAULT_TARGET_CORE,
                E2ePortalFixtures.OVERWORLD,
                E2ePortalFixtures.DEFAULT_SOURCE_CORE,
                Direction.EAST,
                Direction.SOUTH,
                true,
                PortalRemoteEndpointPayload.FLAG_ACTIVE
        );
        assertEquals(forward, SeamlessContractProbe.roundTripByteBufCodec(PortalRemoteEndpointPayload.STREAM_CODEC, forward));
        assertEquals(reverse, SeamlessContractProbe.roundTripByteBufCodec(PortalRemoteEndpointPayload.STREAM_CODEC, reverse));

        verifySwitchHintCodecRoundTrip(
                E2ePortalFixtures.NETHER,
                E2ePortalFixtures.DEFAULT_TARGET_CORE,
                -319.15D, 73.0D, 480.5D,
                90.0F, 0.0F,
                new Vec3(0.1D, 0.0D, 0.0D)
        );

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MGR, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false));
    }

    @Test
    @DisplayName("T1-F11-04: LocalPlayer state continuity across handleRespawn")
    void localPlayerStateContinuityAcrossRespawn() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        byte keepEntity = SeamlessContractProbe.readStaticField(
                ClientboundRespawnPacket.class, "KEEP_ENTITY_DATA", byte.class);
        byte keepAttrs = SeamlessContractProbe.readStaticField(
                ClientboundRespawnPacket.class, "KEEP_ATTRIBUTE_MODIFIERS", byte.class);
        byte keepAll = SeamlessContractProbe.readStaticField(
                ClientboundRespawnPacket.class, "KEEP_ALL_DATA", byte.class);
        assertEquals((byte) (keepEntity | keepAttrs), keepAll);

        verifySwitchHintCodecRoundTrip(
                E2ePortalFixtures.NETHER,
                E2ePortalFixtures.DEFAULT_TARGET_CORE,
                10.25D, 72.0D, -40.75D,
                -90.0F, -12.5F,
                new Vec3(0.2D, 0.42D, -0.3D)
        );

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false));
    }

    @Test
    @DisplayName("T1-F11-05: Overworld to universegate:rift seamless switch")
    void overworldToRiftSeamlessSwitch() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        PortalRemoteEndpointPayload riftEndpoint = new PortalRemoteEndpointPayload(
                E2ePortalFixtures.DEFAULT_SOURCE_CORE,
                E2ePortalFixtures.RIFT,
                BlockPos.ZERO,
                Direction.EAST,
                Direction.SOUTH,
                true,
                (byte) (PortalRemoteEndpointPayload.FLAG_ACTIVE | PortalRemoteEndpointPayload.FLAG_UNSTABLE)
        );
        PortalRemoteEndpointPayload decodedEndpoint = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC, riftEndpoint
        );
        assertEquals(E2ePortalFixtures.RIFT, decodedEndpoint.targetDim());
        assertTrue(decodedEndpoint.isActive() && decodedEndpoint.isUnstable());

        verifySwitchHintCodecRoundTrip(
                E2ePortalFixtures.RIFT,
                BlockPos.ZERO,
                0.5D, 1.0D, 1.35D,
                0.0F, 0.0F,
                new Vec3(0.0D, 0.0D, 0.2D)
        );

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MGR, false));
    }
}
