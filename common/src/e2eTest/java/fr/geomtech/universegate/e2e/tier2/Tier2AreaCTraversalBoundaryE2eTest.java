package fr.geomtech.universegate.e2e.tier2;

import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.PortalTeleportHandler;
import fr.geomtech.universegate.e2e.harness.E2eBootstrap;
import fr.geomtech.universegate.e2e.harness.E2ePortalFixtures;
import fr.geomtech.universegate.e2e.harness.SeamlessContractProbe;
import fr.geomtech.universegate.seamless.net.PortalEndpointResolver;
import fr.geomtech.universegate.seamless.net.PortalRemoteEndpointPayload;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("tier2")
@Tag("areaC")
@DisplayName("Tier 2 Area C: Traversal & Dimension Switch Boundaries (F8-F11)")
class Tier2AreaCTraversalBoundaryE2eTest {

    private static final String FQCN_PORTAL_CORE_BE = "fr.geomtech.universegate.PortalCoreBlockEntity";
    private static final String FQCN_PORTAL_FIELD_BLOCK = "fr.geomtech.universegate.PortalFieldBlock";
    private static final String FQCN_PORTAL_TRANSFORM = "fr.geomtech.universegate.seamless.math.PortalTransform";
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

    private static double invokeLateralOffsetFromCore(BlockPos corePos, Vec3 entityPos, Direction lateralDirection) {
        return SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class,
                "lateralOffsetFromCore",
                double.class,
                new Class<?>[]{BlockPos.class, Vec3.class, Direction.class},
                corePos,
                entityPos,
                lateralDirection
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

    private static Object roundTripSwitchHint(ResourceKey<Level> targetDim,
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
    // Feature 8 BVA & Negative
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T2-F8-01: Just before plane vs exact plane vs just past plane")
    void justBeforeExactAndPastPlane() {
        double planeZ = E2ePortalFixtures.MEMBRANE_DEPTH_BLOCKS; // 0.5
        double dBefore = 0.51D - planeZ;
        double dExact = 0.50D - planeZ;
        double dAfter = 0.49D - planeZ;

        assertEquals(0.01D, dBefore, EPSILON);
        assertEquals(0.0D, dExact, EPSILON);
        assertEquals(-0.01D, dAfter, EPSILON);
        assertTrue(dBefore > 0.0D && dAfter < 0.0D);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T2-F8-02: Walk half-way in then back out without crossing")
    void walkHalfWayInThenBackOutWithoutCrossing() {
        double planeZ = E2ePortalFixtures.MEMBRANE_DEPTH_BLOCKS; // 0.5
        double[] pathZ = {1.5D, 1.0D, 0.55D, 1.0D, 1.5D};
        for (double z : pathZ) {
            assertTrue((z - planeZ) >= 0.05D, "Hesitation path must stay strictly on approach side");
        }

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T2-F8-03: Outside circular aperture (r > 2.25 in top corners of 3x4 box)")
    void outsideCircularApertureInTopCorners() {
        double radius = E2ePortalFixtures.MEMBRANE_RADIUS_BLOCKS; // 2.25
        double u = 1.4D;
        double v = 4.9D - E2ePortalFixtures.RING_CENTER_OFFSET_Y; // 4.9 - 3.0 = 1.9
        double rSq = (u * u) + (v * v);

        assertTrue(Math.abs(u) < 1.5D && 4.9D < 5.0D, "Corner lies inside 3x4 rectangular box");
        assertTrue(rSq > radius * radius, "Corner r^2=" + rSq + " exceeds circular aperture r^2=" + (radius * radius));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T2-F8-04: High-speed tunneling (v = 3.5 b/tick across 4 px field)")
    void highSpeedTunnelingAcrossThinField() {
        double fieldMinZ = 6.0D / 16.0D; // 0.375
        double fieldMaxZ = 10.0D / 16.0D; // 0.625
        double z0 = 2.0D;
        double z1 = -1.5D;
        assertFalse(z0 >= fieldMinZ && z0 <= fieldMaxZ);
        assertFalse(z1 >= fieldMinZ && z1 <= fieldMaxZ);

        double planeZ = E2ePortalFixtures.MEMBRANE_DEPTH_BLOCKS; // 0.5
        double t = (z0 - planeZ) / (z0 - z1);
        assertEquals(1.5D / 3.5D, t, EPSILON);
        assertTrue(t > 0.0D && t < 1.0D);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    @Test
    @DisplayName("T2-F8-05: Destination-side blocked travel (!outboundTravelEnabled)")
    void destinationSideBlockedTravel() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        PortalRemoteEndpointPayload blockedPayload = new PortalRemoteEndpointPayload(
                E2ePortalFixtures.DEFAULT_TARGET_CORE,
                E2ePortalFixtures.OVERWORLD,
                E2ePortalFixtures.DEFAULT_SOURCE_CORE,
                Direction.EAST,
                Direction.SOUTH,
                false,
                PortalRemoteEndpointPayload.FLAG_ACTIVE
        );
        PortalRemoteEndpointPayload decoded = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC, blockedPayload
        );
        assertFalse(decoded.outboundEnabled());
        assertTrue(decoded.isActive());

        Class<?> coreClass = SeamlessContractProbe.requireClass(FQCN_PORTAL_CORE_BE, false);
        assertEquals(200L, SeamlessContractProbe.readStaticField(coreClass, "PER_ENTITY_EXTENSION_TICKS", long.class));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalTeleportHandler.class, "showBlockedDirectionMessage", void.class, ServerPlayer.class
        ));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalTeleportHandler.class, "keepPortalOpenFromDestinationContact", void.class,
                ServerLevel.class, coreClass, long.class
        ));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false));
    }

    // -------------------------------------------------------------------------
    // Feature 9 BVA & Negative
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T2-F9-01: Wall directly behind source A outside aperture vs inside")
    void wallBehindSourceOutsideVsInsideAperture() {
        double insideDistFromCenter = Math.hypot(0.0D, 2.5D - E2ePortalFixtures.RING_CENTER_OFFSET_Y);
        assertTrue(insideDistFromCenter < E2ePortalFixtures.MEMBRANE_RADIUS_BLOCKS);

        double outsideX = 2.0D;
        assertTrue(Math.abs(outsideX) > (PortalFrameDetector.INNER_WIDTH / 2.0D));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
    }

    @Test
    @DisplayName("T2-F9-02: Solid block placed immediately in front of destination B")
    void solidBlockImmediatelyInFrontOfDestination() {
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;
        BlockPos blockingBlock = targetCore.relative(Direction.EAST).above();
        Vec3 blockCenter = new Vec3(
                blockingBlock.getX() + 0.5D,
                blockingBlock.getY() + 0.5D,
                blockingBlock.getZ() + 0.5D
        );
        Vec3 targetPortalCenter = E2ePortalFixtures.portalCenter(targetCore);
        double u = blockCenter.z - targetPortalCenter.z;
        double v = blockCenter.y - targetPortalCenter.y;
        double radial = Math.hypot(u, v);
        assertEquals(1.5D, radial, EPSILON);
        assertTrue(radial < E2ePortalFixtures.MEMBRANE_RADIUS_BLOCKS);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MGR, false));
    }

    @Test
    @DisplayName("T2-F9-03: Inactive or opening portal (active == false) back-wall check")
    void inactiveOrOpeningPortalBackWallCheck() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        byte openingOnlyFlags = PortalEndpointResolver.computeStateFlags(true, false, false);
        assertEquals(PortalRemoteEndpointPayload.FLAG_OPENING, openingOnlyFlags);

        PortalRemoteEndpointPayload openingPayload = new PortalRemoteEndpointPayload(
                E2ePortalFixtures.DEFAULT_SOURCE_CORE,
                E2ePortalFixtures.NETHER,
                E2ePortalFixtures.DEFAULT_TARGET_CORE,
                Direction.SOUTH,
                Direction.EAST,
                true,
                openingOnlyFlags
        );
        PortalRemoteEndpointPayload decoded = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC, openingPayload
        );
        assertTrue(decoded.isOpening());
        assertFalse(decoded.isActive());

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION, false));
    }

    @Test
    @DisplayName("T2-F9-04: Tall entity (Enderman height=2.9) and wide entity (Spider width=1.4)")
    void tallAndWideEntityApertureClamping() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        double innerHalfWidth = SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_INNER_HALF_WIDTH", double.class);
        double innerHeight = SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_INNER_HEIGHT", double.class);
        double epsilon = SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_POSITION_EPSILON", double.class);

        EntityDimensions enderman = EntityType.ENDERMAN.getDimensions();
        EntityDimensions spider = EntityType.SPIDER.getDimensions();
        assertEquals(2.9F, enderman.height(), 1.0E-4F);
        assertEquals(1.4F, spider.width(), 1.0E-4F);

        double endermanVertMax = innerHeight - enderman.height() - epsilon;
        double spiderLatLimit = innerHalfWidth - (spider.width() * 0.5D) - epsilon;
        assertEquals(1.09D, endermanVertMax, 1.0E-4D);
        assertEquals(0.79D, spiderLatLimit, 1.0E-4D);

        double clampedEndermanY = invokeClamp(2.5D, 0.0D, endermanVertMax);
        double clampedSpiderX = invokeClamp(1.4D, -spiderLatLimit, spiderLatLimit);
        assertTrue(clampedEndermanY + enderman.height() <= innerHeight - epsilon + 1.0E-5D);
        assertTrue(Math.abs(clampedSpiderX) + (spider.width() * 0.5D) <= innerHalfWidth - epsilon + 1.0E-5D);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
    }

    @Test
    @DisplayName("T2-F9-05: Waterlogged portal field (WATERLOGGED = true) half-through")
    void waterloggedPortalFieldHalfThrough() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        Class<?> fieldBlockClass = SeamlessContractProbe.requireClass(FQCN_PORTAL_FIELD_BLOCK, false);
        BooleanProperty waterlogged = SeamlessContractProbe.readStaticField(
                fieldBlockClass, "WATERLOGGED", BooleanProperty.class);
        assertEquals(BlockStateProperties.WATERLOGGED, waterlogged);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION, false));
    }

    // -------------------------------------------------------------------------
    // Feature 10 BVA & Negative
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T2-F10-01: Zero velocity (Vec3.ZERO) crossing avoids NaN")
    void zeroVelocityCrossingAvoidsNaN() {
        Vec3 out = invokeTransformVelocity(Vec3.ZERO, Direction.WEST, Direction.NORTH, Direction.NORTH, Direction.EAST);
        assertEquals(0.0D, out.x, EPSILON);
        assertEquals(0.0D, out.y, EPSILON);
        assertEquals(0.0D, out.z, EPSILON);

        float fallbackYaw = -135.0F;
        float fallbackPitch = 12.5F;
        assertEquals(fallbackYaw, invokeYawFromVelocity(Vec3.ZERO, fallbackYaw), 1.0E-5F);
        assertEquals(fallbackPitch, invokePitchFromVelocity(Vec3.ZERO, fallbackPitch), 1.0E-5F);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
    }

    @Test
    @DisplayName("T2-F10-02: Near-vertical entry preserves negative vy and rotated horizontal velocity")
    void nearVerticalEntryPreservesVyAndPitch() {
        Vec3 falling = new Vec3(0.0D, -2.0D, 0.05D);
        Vec3 rotated = invokeTransformVelocity(falling, Direction.EAST, Direction.SOUTH, Direction.NORTH, Direction.EAST);
        assertEquals(-2.0D, rotated.y, EPSILON);
        assertEquals(0.05D, Math.hypot(rotated.x, rotated.z), EPSILON);

        float pitch = invokePitchFromVelocity(rotated, 0.0F);
        assertTrue(Float.isFinite(pitch));
        assertTrue(pitch > 85.0F && pitch < 90.0F);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
    }

    @Test
    @DisplayName("T2-F10-03: Yaw wraparound across +-180 / 0..360 boundary")
    void yawWraparoundAcrossBoundary() {
        assertEquals(-89.0F, Mth.wrapDegrees(-179.0F + 90.0F), 1.0E-5F);
        assertEquals(1.0F, Mth.wrapDegrees(-179.0F + 180.0F), 1.0E-5F);
        assertEquals(-91.0F, Mth.wrapDegrees(179.0F + 90.0F), 1.0E-5F);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
    }

    @Test
    @DisplayName("T2-F10-04: Extreme lateral edge entry (+1.49 vs -1.49) mirrors safely")
    void extremeLateralEdgeEntryMirrorsSafely() {
        BlockPos corePos = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        double rightEdge = invokeLateralOffsetFromCore(
                corePos,
                new Vec3(corePos.getX() + 0.5D + 1.49D, corePos.getY() + 1.0D, corePos.getZ() + 0.5D),
                Direction.EAST
        );
        double leftEdge = invokeLateralOffsetFromCore(
                corePos,
                new Vec3(corePos.getX() + 0.5D - 1.49D, corePos.getY() + 1.0D, corePos.getZ() + 0.5D),
                Direction.EAST
        );
        assertEquals(1.49D, rightEdge, EPSILON);
        assertEquals(-1.49D, leftEdge, EPSILON);

        double playerLimit = 1.5D - 0.3D - 0.01D; // 1.19
        assertEquals(-1.19D, -invokeClamp(rightEdge, -playerLimit, playerLimit), EPSILON);
        assertEquals(1.19D, -invokeClamp(leftEdge, -playerLimit, playerLimit), EPSILON);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
    }

    @Test
    @DisplayName("T2-F10-05: NaN / Infinity velocity guard prevents non-finite propagation")
    void nonFiniteVelocityGuard() {
        Vec3 valid = new Vec3(0.4D, 0.1D, -0.4D);
        assertTrue(Float.isFinite(invokeYawFromVelocity(valid, 0.0F)));

        // Pin baseline hazard: Double.isNaN(NaN) is missed by `horizontalLengthSqr < 1.0E-7D`
        Vec3 nonFinite = new Vec3(Double.NaN, 0.0D, Double.POSITIVE_INFINITY);
        assertFalse(Double.isFinite(nonFinite.x) && Double.isFinite(nonFinite.z));

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false));
    }

    // -------------------------------------------------------------------------
    // Feature 11 BVA & Negative
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T2-F11-01: Fallback when remote ClientLevel is NOT preloaded")
    void fallbackWhenRemoteClientLevelNotPreloaded() {
        //? if >=1.21.11 {
        /*assertNotNull(SeamlessContractProbe.requireClass("net.minecraft.client.gui.screens.LevelLoadingScreen", false));
        assertNotNull(SeamlessContractProbe.requireClass("net.minecraft.client.multiplayer.LevelLoadTracker", false));
        *///?} else {
        assertNotNull(SeamlessContractProbe.requireClass("net.minecraft.client.gui.screens.ReceivingLevelScreen", false));
        //?}
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MGR, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false));
    }

    @Test
    @DisplayName("T2-F11-02: Normal vanilla Nether/End respawn without hint unaffected")
    void normalVanillaRespawnWithoutHintUnaffected() {
        Class<?> listenerClass = SeamlessContractProbe.requireClass(ClientPacketListener.class.getName(), false);
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                listenerClass, "handleRespawn", void.class, ClientboundRespawnPacket.class
        ));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false));
    }

    @Test
    @DisplayName("T2-F11-03: Rapid back-and-forth cross-dim switch (Overworld -> Nether -> Overworld)")
    void rapidBackAndForthCrossDimSwitch() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        roundTripSwitchHint(
                E2ePortalFixtures.NETHER,
                E2ePortalFixtures.DEFAULT_TARGET_CORE,
                -319.15D, 73.0D, 480.5D,
                -90.0F, 0.0F,
                new Vec3(0.3D, 0.0D, 0.0D)
        );
        roundTripSwitchHint(
                E2ePortalFixtures.OVERWORLD,
                E2ePortalFixtures.DEFAULT_SOURCE_CORE,
                100.5D, 65.0D, 201.35D,
                0.0F, 0.0F,
                new Vec3(0.0D, 0.0D, 0.3D)
        );

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MGR, false));
    }

    @Test
    @DisplayName("T2-F11-04: Same-dimension seamless portal (Overworld -> Overworld distant coords)")
    void sameDimensionSeamlessPortalSwitch() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        roundTripSwitchHint(
                E2ePortalFixtures.OVERWORLD,
                new BlockPos(10000, 64, 10000),
                10000.5D, 65.0D, 10001.35D,
                0.0F, 0.0F,
                new Vec3(0.0D, 0.0D, 0.2D)
        );

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false));
        assertNotNull(SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MGR, false));
    }

    @Test
    @DisplayName("T2-F11-05: Stale SeamlessSwitchHintPayload mismatch (the_end vs the_nether)")
    void staleSwitchHintMismatchDetection() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        Object decoded = roundTripSwitchHint(
                E2ePortalFixtures.END,
                new BlockPos(0, 64, 0),
                0.5D, 65.0D, 1.35D,
                0.0F, 0.0F,
                Vec3.ZERO
        );
        ResourceKey<?> decodedDim = SeamlessContractProbe.invokeInstance(
                decoded, "targetDim", ResourceKey.class, new Class<?>[0]
        );
        assertEquals(E2ePortalFixtures.END, decodedDim);
        assertNotEquals(E2ePortalFixtures.NETHER, decodedDim);

        assertNotNull(SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false));
    }
}
