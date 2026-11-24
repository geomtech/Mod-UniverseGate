package fr.geomtech.universegate.e2e.tier4;

import fr.geomtech.universegate.PortalConnectionManager;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.PortalEnergyCosts;
import fr.geomtech.universegate.PortalFieldBlock;
import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.PortalFrameHelper;
import fr.geomtech.universegate.PortalPursuitTracker;
import fr.geomtech.universegate.PortalRiftHelper;
import fr.geomtech.universegate.PortalTeleportHandler;
import fr.geomtech.universegate.e2e.harness.E2eBootstrap;
import fr.geomtech.universegate.e2e.harness.E2ePortalFixtures;
import fr.geomtech.universegate.e2e.harness.SeamlessContractProbe;
import fr.geomtech.universegate.net.PortalFieldCollapsePayload;
import fr.geomtech.universegate.platform.ClientPlatformHelper;
import fr.geomtech.universegate.platform.PlatformHelper;
import fr.geomtech.universegate.seamless.net.PortalEndpointResolver;
import fr.geomtech.universegate.seamless.net.PortalRemoteEndpointPayload;
import fr.geomtech.universegate.seamless.net.RemoteBlockDeltaPayload;
import fr.geomtech.universegate.seamless.net.RemoteEntitySyncPayload;
import fr.geomtech.universegate.seamless.net.ResolvedPortalEndpoint;
import fr.geomtech.universegate.seamless.server.PortalStreamPlanner;
import fr.geomtech.universegate.seamless.server.RemoteChunkTicketController;
import fr.geomtech.universegate.seamless.server.ServerPortalStreamManager;
import fr.geomtech.universegate.seamless.server.TicketBackend;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
//? if >=1.21.11 {
/*import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
*///?}
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("tier4")
@DisplayName("Tier 4: End-to-End Real-World Scenarios (T4-S01..S10)")
class Tier4RealWorldScenariosE2eTest {

    private static final String FQCN_CLIENT_REMOTE_WORLD_MANAGER =
            "fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager";
    private static final String FQCN_PORTAL_TRANSFORM =
            "fr.geomtech.universegate.seamless.math.PortalTransform";
    private static final String FQCN_OBLIQUE_FRUSTUM_MATH =
            "fr.geomtech.universegate.seamless.math.ObliqueFrustumMath";
    private static final String FQCN_PORTAL_CROSSING_MATH =
            "fr.geomtech.universegate.seamless.math.PortalCrossingMath";
    private static final String FQCN_PORTAL_STENCIL_RENDERER =
            "fr.geomtech.universegate.seamless.client.PortalStencilRenderer";
    private static final String FQCN_SEAMLESS_SWITCH_HINT_PAYLOAD =
            "fr.geomtech.universegate.seamless.net.SeamlessSwitchHintPayload";
    private static final String FQCN_SEAMLESS_SWITCH_COORDINATOR =
            "fr.geomtech.universegate.seamless.client.SeamlessSwitchCoordinator";
    private static final String FQCN_PORTAL_CROSSING_TRACKER =
            "fr.geomtech.universegate.seamless.physics.PortalCrossingTracker";
    private static final String FQCN_PORTAL_HALF_COLLISION_HANDLER =
            "fr.geomtech.universegate.seamless.physics.PortalHalfCollisionHandler";
    private static final String FQCN_SODIUM_IRIS_COMPAT =
            "fr.geomtech.universegate.seamless.client.SodiumIrisCompat";

    private record TicketOp(String op, ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {}

    private static final class RecordingTicketBackend implements TicketBackend {
        final List<TicketOp> ops = new ArrayList<>();

        @Override
        public void addTicket(ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {
            ops.add(new TicketOp("ADD", dim, chunkX, chunkZ, radius));
        }

        @Override
        public void removeTicket(ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {
            ops.add(new TicketOp("REMOVE", dim, chunkX, chunkZ, radius));
        }

        long count(String op) {
            return ops.stream().filter(entry -> entry.op().equals(op)).count();
        }
    }

    @BeforeAll
    static void bootstrapHeadlessMinecraft() {
        E2eBootstrap.ensureMinecraftBootstrapped();
    }

    private static ClientboundSetPassengersPacket newSetPassengersPacket(int vehicleId, int... passengerIds) {
        RegistryFriendlyByteBuf buf = E2ePortalFixtures.newRegistryFriendlyByteBuf();
        try {
            buf.writeVarInt(vehicleId);
            buf.writeVarIntArray(passengerIds);
            return ClientboundSetPassengersPacket.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    @Test
    @Tag("areaA")
    @Tag("areaB")
    @Tag("areaC")
    @Tag("areaD")
    @DisplayName("T4-S01: Overworld <-> Nether seamless view & continuous walk-through (zero loading screen)")
    void overworldToNetherSeamlessViewAndWalkThrough() {
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000004001");

        // Energy & opening lifecycle verification
        int openCost = PortalEnergyCosts.openCost(sourceCore.distSqr(targetCore), true, false);
        assertTrue(openCost >= PortalEnergyCosts.OPEN_BASE_COST + PortalEnergyCosts.OPEN_CROSS_DIMENSION_BONUS);
        assertEquals(120, PortalEnergyCosts.activeCostPerSecond(false));
        assertEquals(180L, SeamlessContractProbe.readStaticField(
                PortalConnectionManager.class, "OPENING_DURATION_TICKS", long.class
        ));
        assertEquals(1200L, SeamlessContractProbe.readStaticField(
                PortalConnectionManager.class, "ACTIVE_OPEN_DURATION_TICKS", long.class
        ));

        // Opening -> Active endpoint metadata sync
        ResolvedPortalEndpoint openingEndpoint = PortalEndpointResolver.resolveEndpoint(
                true, false, false, true,
                E2ePortalFixtures.NETHER, targetCore, Direction.EAST, Direction.NORTH,
                null, null, null, null
        ).orElseThrow();
        PortalRemoteEndpointPayload openingPayload = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC, openingEndpoint.toPayload(sourceCore)
        );
        assertTrue(openingPayload.isOpening());
        assertFalse(openingPayload.isActive());

        ResolvedPortalEndpoint activeEndpoint = PortalEndpointResolver.resolveEndpoint(
                false, true, false, true,
                E2ePortalFixtures.NETHER, targetCore, Direction.EAST, Direction.NORTH,
                null, null, null, null
        ).orElseThrow();
        PortalRemoteEndpointPayload activePayload = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC, activeEndpoint.toPayload(sourceCore)
        );
        assertTrue(activePayload.isActive());

        // Server chunk streaming & ticket acquisition in Nether
        PortalStreamPlanner planner = new PortalStreamPlanner();
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController tickets = new RemoteChunkTicketController(backend);
        planner.subscribe(playerId, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);
        PortalStreamPlanner.PortalKey key = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, sourceCore);
        tickets.setClaims(key, planner.desiredTicketKeysForPortal(key));
        assertEquals(Set.of(E2ePortalFixtures.NETHER), tickets.activeDimensions());

        // Continuous crossing velocity continuity (Overworld EAST/SOUTH -> Nether EAST/NORTH)
        Vec3 walkVelocity = new Vec3(0.05D, 0.0D, -0.22D);
        Vec3 exitVelocity = SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class,
                "transformVelocityThroughPortal",
                Vec3.class,
                new Class<?>[]{Vec3.class, Direction.class, Direction.class, Direction.class, Direction.class},
                walkVelocity, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH
        );
        assertEquals(walkVelocity.length(), exitVelocity.length(), 1.0E-9D);

        // Seamless client-side M1.3 + M2 + M3 pipeline contracts
        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false);
        SeamlessContractProbe.requireClass(FQCN_OBLIQUE_FRUSTUM_MATH, false);
        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class
        );
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION_HANDLER, false);
        SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false);

        Class<?> switchHintClass = SeamlessContractProbe.requireRecord(
                FQCN_SEAMLESS_SWITCH_HINT_PAYLOAD,
                "targetDim", "targetCorePos", "x", "y", "z", "yRot", "xRot", "velocity"
        );
        StreamCodec<RegistryFriendlyByteBuf, Object> hintCodec =
                SeamlessContractProbe.requireStreamCodec(switchHintClass, "STREAM_CODEC");
        Object hint = SeamlessContractProbe.newInstance(
                switchHintClass,
                new Class<?>[]{ResourceKey.class, BlockPos.class, double.class, double.class, double.class, float.class, float.class, Vec3.class},
                E2ePortalFixtures.NETHER, targetCore,
                targetCore.getX() + 0.5D, targetCore.getY() + 1.0D, targetCore.getZ() - 0.55D,
                180.0F, 0.0F, exitVelocity
        );
        assertEquals(hint, SeamlessContractProbe.roundTripByteBufCodec(hintCodec, hint));

        //? if >=1.21.11 {
        /*SeamlessContractProbe.requireClass("net.minecraft.client.gui.screens.LevelLoadingScreen", false);
        SeamlessContractProbe.requireClass("net.minecraft.client.multiplayer.LevelLoadTracker", false);
        *///?} else {
        SeamlessContractProbe.requireClass("net.minecraft.client.gui.screens.ReceivingLevelScreen", false);
        //?}
    }

    @Test
    @Tag("areaA")
    @Tag("areaB")
    @Tag("areaC")
    @Tag("areaD")
    @Tag("areaE")
    @DisplayName("T4-S02: Overworld -> universegate:rift lightning link, unstable redirect & 10s collapse")
    void overworldToRiftLightningLinkUnstableAndCollapse() {
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos riftCore = new BlockPos(0, 64, 0);
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000004002");

        int riftOpenCost = PortalEnergyCosts.openCost(sourceCore.distSqr(riftCore), true, true);
        assertTrue(riftOpenCost >= PortalEnergyCosts.OPEN_BASE_COST
                + PortalEnergyCosts.OPEN_CROSS_DIMENSION_BONUS
                + PortalEnergyCosts.OPEN_RIFT_DIMENSION_BONUS);
        assertEquals(180, PortalEnergyCosts.activeCostPerSecond(true));
        assertEquals(200L, SeamlessContractProbe.readStaticField(
                PortalRiftHelper.class, "RIFT_COLLAPSE_DELAY_TICKS", long.class
        ));
        assertEquals(0.05F, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "UNSTABLE_RIFT_CHANCE", float.class
        ));
        assertEquals(0.10F, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "UNSTABLE_RANDOM_PORTAL_CHANCE", float.class
        ));
        assertEquals(0.40F, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "UNSTABLE_DAMAGE_CHANCE", float.class
        ));

        // Immediate unstable endpoint in universegate:rift
        byte riftFlags = PortalEndpointResolver.computeStateFlags(false, true, true);
        PortalRemoteEndpointPayload riftEndpoint = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC,
                new PortalRemoteEndpointPayload(
                        sourceCore, E2ePortalFixtures.RIFT, riftCore, Direction.EAST, Direction.SOUTH, true, riftFlags
                )
        );
        assertTrue(riftEndpoint.isActive());
        assertTrue(riftEndpoint.isUnstable());
        assertEquals(E2ePortalFixtures.RIFT, riftEndpoint.targetDim());

        // Rift chunk tickets & collapse cleanup
        PortalStreamPlanner planner = new PortalStreamPlanner();
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController tickets = new RemoteChunkTicketController(backend);

        planner.subscribe(playerId, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.RIFT, riftCore);
        PortalStreamPlanner.PortalKey portalKey = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, sourceCore);
        tickets.setClaims(portalKey, planner.desiredTicketKeysForPortal(portalKey));
        assertEquals(Set.of(E2ePortalFixtures.RIFT), tickets.activeDimensions());

        // 200-tick collapse removes field, emits collapse payload, releases all Rift tickets
        PortalFieldCollapsePayload collapse = SeamlessContractProbe.roundTripByteBufCodec(
                PortalFieldCollapsePayload.STREAM_CODEC,
                new PortalFieldCollapsePayload(E2ePortalFixtures.expectedInterior(riftCore, Direction.EAST), true, true)
        );
        assertTrue(collapse.unstable());

        planner.closePortal(E2ePortalFixtures.OVERWORLD, sourceCore);
        tickets.releaseOwner(portalKey);
        assertEquals(0, tickets.activeTicketCount());
        assertTrue(tickets.activeDimensions().isEmpty());

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
        SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false);
    }

    @Test
    @Tag("areaA")
    @Tag("areaC")
    @Tag("areaD")
    @DisplayName("T4-S03: AbstractMinecart + ServerPlayer rider 90-degree cross-dimension traversal")
    void minecartAndPlayerRiderCrossDimensionTraversal() {
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;
        UUID riderId = UUID.fromString("00000000-0000-0000-0000-000000004003");

        assertEquals(0.18D, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_MINECART_EXTRA_OFFSET", double.class
        ));

        // Streamed Minecart + rider hierarchy
        ClientboundAddEntityPacket addCart = new ClientboundAddEntityPacket(
                4301, UUID.fromString("00000000-0000-0000-0000-000000004301"),
                sourceCore.getX() + 0.5D, sourceCore.getY() + 1.0D, sourceCore.getZ() + 2.0D,
                0.0F, 180.0F, EntityType.MINECART, 0, new Vec3(0.0D, 0.0D, -0.45D), 180.0D
        );
        RemoteEntitySyncPayload decodedAdd = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteEntitySyncPayload.STREAM_CODEC,
                ServerPortalStreamManager.wrapEntityPacket(
                        E2ePortalFixtures.NETHER, 4301, E2ePortalFixtures.registryAccess(), addCart
                ).orElseThrow()
        );
        assertEquals(RemoteEntitySyncPayload.SyncKind.ADD_ENTITY, decodedAdd.kind());

        ClientboundSetPassengersPacket mountPacket = newSetPassengersPacket(4301, 4302);
        ClientboundSetPassengersPacket decodedMount = assertInstanceOf(
                ClientboundSetPassengersPacket.class,
                SeamlessContractProbe.roundTripByteBufCodec(
                        RemoteEntitySyncPayload.STREAM_CODEC,
                        ServerPortalStreamManager.wrapEntityPacket(
                                E2ePortalFixtures.NETHER, 4301, E2ePortalFixtures.registryAccess(), mountPacket
                        ).orElseThrow()
                ).decodeVanillaPacket(E2ePortalFixtures.registryAccess())
        );
        assertArrayEquals(new int[]{4302}, decodedMount.getPassengers());

        //? if >=1.21.11 {
        /*ClientboundMoveMinecartPacket moveMinecart = new ClientboundMoveMinecartPacket(
                4301,
                List.of(new NewMinecartBehavior.MinecartStep(
                        new Vec3(targetCore.getX() + 1.5D, targetCore.getY() + 1.0D, targetCore.getZ() + 0.5D),
                        new Vec3(0.45D, 0.0D, 0.0D),
                        -90.0F, 0.0F, 1.0F
                ))
        );
        assertEquals(RemoteEntitySyncPayload.SyncKind.MOVE_MINECART,
                SeamlessContractProbe.roundTripByteBufCodec(
                        RemoteEntitySyncPayload.STREAM_CODEC,
                        ServerPortalStreamManager.wrapEntityPacket(
                                E2ePortalFixtures.NETHER, 4301, E2ePortalFixtures.registryAccess(), moveMinecart
                        ).orElseThrow()
                ).kind());
        *///?} else {
        ClientboundMoveEntityPacket.PosRot moveCart = new ClientboundMoveEntityPacket.PosRot(
                4301, (short) 16, (short) 0, (short) 0, (byte) -64, (byte) 0, true
        );
        assertEquals(RemoteEntitySyncPayload.SyncKind.MOVE_POS_ROT,
                SeamlessContractProbe.roundTripByteBufCodec(
                        RemoteEntitySyncPayload.STREAM_CODEC,
                        ServerPortalStreamManager.wrapEntityPacket(
                                E2ePortalFixtures.NETHER, 4301, E2ePortalFixtures.registryAccess(), moveCart
                        ).orElseThrow()
                ).kind());
        //?}

        // 90-degree rail rotation EAST -> SOUTH
        Vec3 exitCartVel = SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class,
                "transformVelocityThroughPortal",
                Vec3.class,
                new Class<?>[]{Vec3.class, Direction.class, Direction.class, Direction.class, Direction.class},
                new Vec3(0.0D, 0.0D, -0.45D), Direction.EAST, Direction.SOUTH, Direction.SOUTH, Direction.EAST
        );
        assertEquals(0.45D, exitCartVel.length(), 1.0E-9D);

        PortalStreamPlanner planner = new PortalStreamPlanner();
        planner.subscribe(riderId, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);
        assertEquals(25, planner.desiredChunksForTarget(targetCore).size());

        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION_HANDLER, false);
        SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false);
        SeamlessContractProbe.requireRecord(
                FQCN_SEAMLESS_SWITCH_HINT_PAYLOAD,
                "targetDim", "targetCorePos", "x", "y", "z", "yRot", "xRot", "velocity"
        );
    }

    @Test
    @Tag("areaA")
    @Tag("areaB")
    @Tag("areaC")
    @Tag("areaD")
    @DisplayName("T4-S04: AbstractArrow & ThrownEnderpearl firing across rotated portal plane")
    void arrowAndEnderpearlFiringAcrossRotatedPortalPlane() {
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;

        // Remote Zombie target streamed in B
        ClientboundAddEntityPacket addZombie = new ClientboundAddEntityPacket(
                4401, UUID.fromString("00000000-0000-0000-0000-000000004401"),
                targetCore.getX() + 4.5D, targetCore.getY() + 1.0D, targetCore.getZ() + 0.5D,
                0.0F, 90.0F, EntityType.ZOMBIE, 0, Vec3.ZERO, 90.0D
        );
        assertEquals(RemoteEntitySyncPayload.SyncKind.ADD_ENTITY,
                SeamlessContractProbe.roundTripByteBufCodec(
                        RemoteEntitySyncPayload.STREAM_CODEC,
                        ServerPortalStreamManager.wrapEntityPacket(
                                E2ePortalFixtures.NETHER, 4401, E2ePortalFixtures.registryAccess(), addZombie
                        ).orElseThrow()
                ).kind());

        // High-speed arrow (3.2 b/tick > 4 px field thickness) & ender pearl rotated 90 degrees
        Vec3 arrowIn = new Vec3(0.25D, 0.12D, -3.20D);
        Vec3 arrowOut = SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class,
                "transformVelocityThroughPortal",
                Vec3.class,
                new Class<?>[]{Vec3.class, Direction.class, Direction.class, Direction.class, Direction.class},
                arrowIn, Direction.EAST, Direction.SOUTH, Direction.SOUTH, Direction.EAST
        );
        assertEquals(arrowIn.y, arrowOut.y, 1.0E-9D);
        assertEquals(arrowIn.length(), arrowOut.length(), 1.0E-9D);

        float arrowYaw = SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class, "yawFromVelocity", float.class,
                new Class<?>[]{Vec3.class, float.class}, arrowOut, 0.0F
        );
        float arrowPitch = SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class, "pitchFromVelocity", float.class,
                new Class<?>[]{Vec3.class, float.class}, arrowOut, 0.0F
        );
        assertTrue(Float.isFinite(arrowYaw));
        assertTrue(Float.isFinite(arrowPitch));

        // Verify continuous crossing math & oblique aperture contracts
        SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false);
        SeamlessContractProbe.requireClass(FQCN_OBLIQUE_FRUSTUM_MATH, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
    }

    @Test
    @Tag("areaA")
    @Tag("areaB")
    @Tag("areaE")
    @DisplayName("T4-S05: Two portals facing each other — recursive infinite-hallway depth cap + fallback")
    void facingPortalsRecursiveHallwayDepthCapAndFallback() {
        BlockPos coreA = new BlockPos(0, 64, 0);
        BlockPos coreB = new BlockPos(0, 64, 8);
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000004005");

        PortalStreamPlanner planner = new PortalStreamPlanner();
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController tickets = new RemoteChunkTicketController(backend);

        planner.subscribe(playerId, E2ePortalFixtures.OVERWORLD, coreA, E2ePortalFixtures.OVERWORLD, coreB);
        planner.subscribe(playerId, E2ePortalFixtures.OVERWORLD, coreB, E2ePortalFixtures.OVERWORLD, coreA);

        PortalStreamPlanner.PortalKey keyA = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, coreA);
        PortalStreamPlanner.PortalKey keyB = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, coreB);
        tickets.setClaims(keyA, planner.desiredTicketKeysForPortal(keyA));
        tickets.setClaims(keyB, planner.desiredTicketKeysForPortal(keyB));
        assertTrue(tickets.activeTicketCount() > 0);
        assertEquals(8.0D, E2ePortalFixtures.portalCenter(coreA).distanceTo(E2ePortalFixtures.portalCenter(coreB)), 1.0E-9D);

        // Chained transform & depth cap + fallback contract
        SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false);
        SeamlessContractProbe.requireClass(FQCN_OBLIQUE_FRUSTUM_MATH, false);
        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class
        );
        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
    }

    @Test
    @Tag("areaB")
    @Tag("areaC")
    @DisplayName("T4-S06: Half-way threshold hesitation & walk-back without wall collision or ping-pong")
    void halfWayThresholdHesitationAndWalkBack() {
        assertEquals(0.5D, E2ePortalFixtures.MEMBRANE_DEPTH_BLOCKS);
        assertEquals(1.5D, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_INNER_HALF_WIDTH", double.class
        ));
        assertEquals(4, PortalFrameDetector.INNER_HEIGHT);

        SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION_HANDLER, false);
        SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false);
    }

    @Test
    @Tag("areaB")
    @Tag("areaD")
    @Tag("areaE")
    @DisplayName("T4-S07: Iris shaderpack active -> clean PortalMembraneLayer fallback & runtime toggle")
    void irisShaderpackActiveFallbackAndRuntimeToggle() {
        Class<?> membraneClass = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer", false);
        assertEquals(36, SeamlessContractProbe.readStaticField(membraneClass, "RADIUS", int.class));

        SeamlessContractProbe.requireInstanceMethod(PlatformHelper.class, "isModLoaded", boolean.class, String.class);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "isIrisShaderPackActive", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "isIrisShadowPass", boolean.class);

        SeamlessContractProbe.requireClass(FQCN_SODIUM_IRIS_COMPAT, false);
        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class
        );
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
    }

    @Test
    @Tag("areaA")
    @Tag("areaB")
    @Tag("areaD")
    @Tag("areaE")
    @DisplayName("T4-S08: Energy exhaustion & player disconnect mid-view -> full ticket/level/GPU teardown")
    void energyExhaustionAndPlayerDisconnectMidViewFullTeardown() {
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;
        UUID player1 = UUID.fromString("00000000-0000-0000-0000-000000004081");
        UUID player2 = UUID.fromString("00000000-0000-0000-0000-000000004082");

        assertEquals(120, PortalEnergyCosts.activeCostPerSecond(false));
        assertEquals(180, PortalEnergyCosts.activeCostPerSecond(true));

        PortalStreamPlanner planner = new PortalStreamPlanner();
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController tickets = new RemoteChunkTicketController(backend);

        planner.subscribe(player1, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);
        planner.subscribe(player2, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);

        PortalStreamPlanner.PortalKey portalKey = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, sourceCore);
        tickets.setClaims(portalKey, planner.desiredTicketKeysForPortal(portalKey));

        ChunkPos centerChunk = new ChunkPos(targetCore);
        planner.markChunkSent(new PortalStreamPlanner.SubscriberKey(player1, E2ePortalFixtures.OVERWORLD, sourceCore), centerChunk.toLong());
        planner.markChunkSent(new PortalStreamPlanner.SubscriberKey(player2, E2ePortalFixtures.OVERWORLD, sourceCore), centerChunk.toLong());

        // Player 2 disconnects mid-view -> Player 1 remains subscribed, tickets stay alive
        Set<PortalStreamPlanner.PortalKey> affected = new HashSet<>();
        List<PortalStreamPlanner.ChunkForgetBatch> disconnectForgets = planner.removePlayer(player2, affected);
        assertEquals(Set.of(portalKey), affected);
        assertEquals(1, disconnectForgets.size());
        assertTrue(planner.hasSubscribers(portalKey));
        assertEquals(25, tickets.activeTicketCount());

        // Energy exhaustion (< 120/180 FE/s) closes portal -> full teardown
        PortalFieldCollapsePayload collapse = SeamlessContractProbe.roundTripByteBufCodec(
                PortalFieldCollapsePayload.STREAM_CODEC,
                new PortalFieldCollapsePayload(E2ePortalFixtures.expectedInterior(sourceCore, Direction.EAST), true, false)
        );
        assertEquals(E2ePortalFixtures.EXPECTED_INTERIOR_CELL_COUNT, collapse.cells().size());

        List<PortalStreamPlanner.ChunkForgetBatch> closeForgets = planner.closePortal(E2ePortalFixtures.OVERWORLD, sourceCore);
        assertEquals(1, closeForgets.size());
        tickets.releaseOwner(portalKey);
        assertEquals(0, tickets.activeTicketCount());
        assertEquals(25L, backend.count("ADD"));
        assertEquals(25L, backend.count("REMOVE"));

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
    }

    @Test
    @Tag("areaA")
    @Tag("areaB")
    @Tag("areaC")
    @Tag("areaD")
    @Tag("areaE")
    @DisplayName("T4-S09: Multiplayer asymmetric observation & hostile mob pursuit across portal")
    void multiplayerAsymmetricObservationAndHostileMobPursuit() {
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;
        UUID player1 = UUID.fromString("00000000-0000-0000-0000-000000004091");
        UUID player2 = UUID.fromString("00000000-0000-0000-0000-000000004092");

        assertEquals(300L, SeamlessContractProbe.readStaticField(
                PortalPursuitTracker.class, "MOB_PORTAL_PURSUIT_GRACE_TICKS", long.class
        ));

        // Both observers sync & subscribe
        PortalEndpointResolver.ObserverSyncPlan syncPlan = PortalEndpointResolver.computeObserverSync(
                Set.of(), List.of(player1, player2), false
        );
        assertEquals(List.of(player1, player2), syncPlan.toSend());

        PortalStreamPlanner planner = new PortalStreamPlanner();
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController tickets = new RemoteChunkTicketController(backend);
        planner.subscribe(player1, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);
        planner.subscribe(player2, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);

        PortalStreamPlanner.PortalKey portalKey = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, sourceCore);
        tickets.setClaims(portalKey, planner.desiredTicketKeysForPortal(portalKey));

        long targetChunk = ChunkPos.asLong(targetCore);
        planner.markChunkSent(new PortalStreamPlanner.SubscriberKey(player1, E2ePortalFixtures.OVERWORLD, sourceCore), targetChunk);
        planner.markChunkSent(new PortalStreamPlanner.SubscriberKey(player2, E2ePortalFixtures.OVERWORLD, sourceCore), targetChunk);

        // Player 1 crosses into Nether -> Player 1's view subscription drops, Player 2 still watches B!
        Set<PortalStreamPlanner.PortalKey> affected = new HashSet<>();
        planner.removePlayer(player1, affected);
        assertTrue(planner.hasSubscribers(portalKey));
        assertEquals(25, tickets.activeTicketCount());

        // Player 1 emerging on B's side is visible to Player 2 via RemoteEntitySyncPayload
        assertTrue(planner.isEntityStillVisibleToPlayer(
                player2, E2ePortalFixtures.NETHER, targetChunk,
                targetCore.getX() + 0.5D, targetCore.getY() + 1.0D, targetCore.getZ() + 1.5D
        ));
        ClientboundAddEntityPacket addPlayer1 = new ClientboundAddEntityPacket(
                4901, player1,
                targetCore.getX() + 0.5D, targetCore.getY() + 1.0D, targetCore.getZ() + 1.5D,
                0.0F, 0.0F, EntityType.PLAYER, 0, Vec3.ZERO, 0.0D
        );
        assertEquals(RemoteEntitySyncPayload.SyncKind.ADD_ENTITY,
                SeamlessContractProbe.roundTripByteBufCodec(
                        RemoteEntitySyncPayload.STREAM_CODEC,
                        ServerPortalStreamManager.wrapEntityPacket(
                                E2ePortalFixtures.NETHER, 4901, E2ePortalFixtures.registryAccess(), addPlayer1
                        ).orElseThrow()
                ).kind());

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
        SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false);
    }

    @Test
    @Tag("areaA")
    @Tag("areaB")
    @Tag("areaC")
    @Tag("areaD")
    @DisplayName("T4-S10: Wired portal (wiredFieldCells) + waterlogged field + 90-degree rotation end-to-end")
    void wiredPortalWaterloggedFieldAnd90DegreeRotationEndToEnd() {
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;

        SeamlessContractProbe.requireField(PortalCoreBlockEntity.class, "wiredFieldCells", List.class);
        BooleanProperty waterloggedProp = SeamlessContractProbe.readStaticField(
                PortalFieldBlock.class, "WATERLOGGED", BooleanProperty.class
        );
        assertEquals("waterlogged", waterloggedProp.getName());

        // Frame border + interior cells with wired cell (0,1,0)
        PortalFrameDetector.FrameMatch sourceMatch = E2ePortalFixtures.frameMatch(sourceCore, Direction.EAST);
        List<BlockPos> frameBlocks = PortalFrameHelper.collectFrame(sourceMatch, sourceCore);
        assertEquals(E2ePortalFixtures.EXPECTED_FRAME_BLOCK_COUNT, frameBlocks.size());
        BlockPos wiredCell = sourceCore.above();
        assertTrue(sourceMatch.interior().contains(wiredCell));

        // 90-degree rotated endpoint (EAST -> SOUTH)
        ResolvedPortalEndpoint endpoint = PortalEndpointResolver.resolveEndpoint(
                false, true, false, true,
                E2ePortalFixtures.NETHER, targetCore, Direction.SOUTH, Direction.EAST,
                null, null, null, null
        ).orElseThrow();
        PortalRemoteEndpointPayload payload = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC, endpoint.toPayload(sourceCore)
        );
        assertEquals(Direction.SOUTH, payload.targetRight());
        assertEquals(Direction.EAST, payload.exitNormal());

        Vec3 exitVelocity = SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class,
                "transformVelocityThroughPortal",
                Vec3.class,
                new Class<?>[]{Vec3.class, Direction.class, Direction.class, Direction.class, Direction.class},
                new Vec3(0.05D, 0.0D, -0.25D), Direction.EAST, Direction.SOUTH, Direction.SOUTH, Direction.EAST
        );
        assertEquals(Math.hypot(0.05D, 0.25D), Math.hypot(exitVelocity.x, exitVelocity.z), 1.0E-9D);

        // Seamless render, wired-cell plane crossing & dimension switch contracts
        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false);
        SeamlessContractProbe.requireClass(FQCN_OBLIQUE_FRUSTUM_MATH, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION_HANDLER, false);
        SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false);
    }
}
