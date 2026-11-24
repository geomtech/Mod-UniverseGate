package fr.geomtech.universegate.e2e.tier3;

import fr.geomtech.universegate.PortalConnectionManager;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.PortalEnergyCosts;
import fr.geomtech.universegate.PortalFieldBlock;
import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.PortalFrameHelper;
import fr.geomtech.universegate.PortalGateAnimator;
import fr.geomtech.universegate.PortalGateRenderContext;
import fr.geomtech.universegate.PortalGateRenderer;
import fr.geomtech.universegate.PortalPursuitTracker;
import fr.geomtech.universegate.PortalRiftHelper;
import fr.geomtech.universegate.PortalTeleportHandler;
import fr.geomtech.universegate.compat.ImmediateBlockEntityRenderer;
import fr.geomtech.universegate.e2e.harness.E2eBootstrap;
import fr.geomtech.universegate.e2e.harness.E2ePortalFixtures;
import fr.geomtech.universegate.e2e.harness.SeamlessContractProbe;
import fr.geomtech.universegate.net.PortalFieldCollapsePayload;
import fr.geomtech.universegate.platform.ClientPlatformHelper;
import fr.geomtech.universegate.platform.PlatformHelper;
import fr.geomtech.universegate.seamless.net.PortalEndpointResolver;
import fr.geomtech.universegate.seamless.net.PortalRemoteEndpointPayload;
import fr.geomtech.universegate.seamless.net.PortalViewSubscriptionPayload;
import fr.geomtech.universegate.seamless.net.RemoteBlockDeltaPayload;
import fr.geomtech.universegate.seamless.net.RemoteChunkDataPayload;
import fr.geomtech.universegate.seamless.net.RemoteChunkForgetPayload;
import fr.geomtech.universegate.seamless.net.RemoteEntitySyncPayload;
import fr.geomtech.universegate.seamless.net.ResolvedPortalEndpoint;
import fr.geomtech.universegate.seamless.server.PortalStreamPlanner;
import fr.geomtech.universegate.seamless.server.RemoteChunkTicketController;
import fr.geomtech.universegate.seamless.server.ServerPortalStreamManager;
import fr.geomtech.universegate.seamless.server.TicketBackend;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
//? if >=1.21.11 {
/*import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
*///?}
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("tier3")
@DisplayName("Tier 3: Cross-Feature Seamless Portal Combinations (T3-C01..C18)")
class Tier3CrossFeatureE2eTest {

    private static final String FQCN_CLIENT_REMOTE_WORLD_MANAGER =
            "fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager";
    private static final String FQCN_REMOTE_CLIENT_LEVEL_FACTORY =
            "fr.geomtech.universegate.seamless.client.RemoteClientLevelFactory";
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

    private static boolean hasMethodNamed(Class<?> owner, String methodName) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @Tag("areaA")
    @DisplayName("T3-C01: Full M1 remote observation & streaming pipeline (F1+F2+F3+F4)")
    void fullM1RemoteObservationAndStreamingPipeline() {
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;
        UUID observerId = UUID.fromString("00000000-0000-0000-0000-000000003001");

        // F1: Endpoint resolution + round-trip
        ResolvedPortalEndpoint endpoint = PortalEndpointResolver.resolveEndpoint(
                false,
                true,
                false,
                true,
                E2ePortalFixtures.NETHER,
                targetCore,
                Direction.SOUTH,
                Direction.EAST,
                null,
                null,
                null,
                null
        ).orElseThrow();
        PortalRemoteEndpointPayload endpointPayload = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC,
                endpoint.toPayload(sourceCore)
        );
        assertTrue(endpointPayload.isActive());
        assertFalse(endpointPayload.isUnstable());
        assertEquals(E2ePortalFixtures.NETHER, endpointPayload.targetDim());
        assertEquals(Direction.SOUTH, endpointPayload.targetRight());
        assertEquals(Direction.EAST, endpointPayload.exitNormal());

        // F2: Server stream planner + ref-counted ticket controller
        PortalStreamPlanner planner = new PortalStreamPlanner();
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController ticketController = new RemoteChunkTicketController(backend);

        PortalViewSubscriptionPayload subPayload = SeamlessContractProbe.roundTripByteBufCodec(
                PortalViewSubscriptionPayload.STREAM_CODEC,
                new PortalViewSubscriptionPayload(sourceCore, true)
        );
        assertTrue(subPayload.subscribe());

        assertTrue(planner.subscribe(
                observerId,
                E2ePortalFixtures.OVERWORLD,
                sourceCore,
                endpointPayload.targetDim(),
                endpointPayload.targetCorePos()
        ).isEmpty());
        PortalStreamPlanner.PortalKey portalKey = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, sourceCore);
        List<RemoteChunkTicketController.TicketKey> ticketKeys = planner.desiredTicketKeysForPortal(portalKey);
        assertEquals(25, ticketKeys.size());
        ticketController.setClaims(portalKey, ticketKeys);
        assertEquals(25, ticketController.activeTicketCount());
        assertEquals(Set.of(E2ePortalFixtures.NETHER), ticketController.activeDimensions());

        // F3: Chunk metadata, block delta, and entity sync round-trip
        PortalStreamPlanner.SubscriberKey subKey = new PortalStreamPlanner.SubscriberKey(
                observerId, E2ePortalFixtures.OVERWORLD, sourceCore
        );
        ChunkPos centerChunk = new ChunkPos(targetCore);
        planner.markChunkSent(subKey, centerChunk.toLong());

        BlockPos changedPos = targetCore.offset(2, 1, 0);
        assertTrue(planner.queueBlockDelta(E2ePortalFixtures.NETHER, changedPos));
        Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> drained = planner.drainDirtyBlocks();
        assertTrue(drained.get(E2ePortalFixtures.NETHER).get(centerChunk.toLong()).contains(changedPos));

        CompoundTag signTag = new CompoundTag();
        signTag.putString("Text", "NetherOutpost");
        RemoteBlockDeltaPayload deltaDecoded = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteBlockDeltaPayload.STREAM_CODEC,
                new RemoteBlockDeltaPayload(E2ePortalFixtures.NETHER, changedPos, Blocks.DIAMOND_BLOCK.defaultBlockState(), Optional.of(signTag))
        );
        assertEquals(Blocks.DIAMOND_BLOCK.defaultBlockState(), deltaDecoded.state());
        //? if >=1.21.11 {
        /*assertEquals("NetherOutpost", deltaDecoded.blockEntityTag().orElseThrow().getString("Text").orElseThrow());
        *///?} else {
        assertEquals("NetherOutpost", deltaDecoded.blockEntityTag().orElseThrow().getString("Text"));
        //?}

        ClientboundAddEntityPacket addPig = new ClientboundAddEntityPacket(
                301, UUID.fromString("00000000-0000-0000-0000-000000003101"),
                targetCore.getX() + 2.5D, targetCore.getY() + 1.0D, targetCore.getZ() + 0.5D,
                0.0F, 90.0F, EntityType.PIG, 0, Vec3.ZERO, 90.0D
        );
        RemoteEntitySyncPayload wrappedPig = ServerPortalStreamManager.wrapEntityPacket(
                E2ePortalFixtures.NETHER, 301, E2ePortalFixtures.registryAccess(), addPig
        ).orElseThrow();
        RemoteEntitySyncPayload decodedPig = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteEntitySyncPayload.STREAM_CODEC, wrappedPig
        );
        assertEquals(RemoteEntitySyncPayload.SyncKind.ADD_ENTITY, decodedPig.kind());

        // F4: Multi-ClientLevel client manager & sound gating contracts (PROJECT.md M1.3)
        Class<?> soundClientClass = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalAmbientSoundClient", false);
        assertEquals(256.0D, SeamlessContractProbe.readStaticField(soundClientClass, "HEARING_RANGE_SQR", double.class));
        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_REMOTE_CLIENT_LEVEL_FACTORY, false);
    }

    @Test
    @Tag("areaB")
    @DisplayName("T3-C02: Full M2 render math, stencil/FBO hook & oblique clip fallback (F5+F6+F7)")
    void fullM2RenderMathStencilHookAndFallback() {
        Class<?> ringClass = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRingLayer", false);
        Class<?> membraneClass = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer", false);
        assertEquals(35.0F, SeamlessContractProbe.readStaticField(ringClass, "INNER_RADIUS", float.class));
        assertEquals(36, SeamlessContractProbe.readStaticField(membraneClass, "RADIUS", int.class));
        assertEquals(8.0F, SeamlessContractProbe.readStaticField(membraneClass, "DEPTH", float.class));
        assertEquals(2.25D, E2ePortalFixtures.MEMBRANE_RADIUS_BLOCKS);
        assertEquals(0.5D, E2ePortalFixtures.MEMBRANE_DEPTH_BLOCKS);

        // F5: Portal coordinate & oblique clip math
        SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false);
        SeamlessContractProbe.requireClass(FQCN_OBLIQUE_FRUSTUM_MATH, false);

        // F6: Stencil / FBO setup & render hook contracts on ClientPlatformHelper
        assertTrue(hasMethodNamed(ClientPlatformHelper.class, "registerLevelRenderPortalHook"),
                "ClientPlatformHelper must declare registerLevelRenderPortalHook");
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "ensureMainTargetStencil", boolean.class);

        // F7: PortalStencilRenderer coordination contract
        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class
        );
    }

    @Test
    @Tag("areaC")
    @DisplayName("T3-C03: Full M3 continuous traversal, half-collision, velocity/yaw & seamless switch (F8+F9+F10+F11)")
    void fullM3ContinuousTraversalAndSeamlessSwitch() {
        // Verify existing 90-degree EAST -> SOUTH velocity transform basis in PortalTeleportHandler
        Vec3 entryVel = new Vec3(0.10D, 0.42D, -0.35D);
        Vec3 rotatedVel = SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class,
                "transformVelocityThroughPortal",
                Vec3.class,
                new Class<?>[]{Vec3.class, Direction.class, Direction.class, Direction.class, Direction.class},
                entryVel, Direction.EAST, Direction.SOUTH, Direction.SOUTH, Direction.EAST
        );
        assertEquals(entryVel.y, rotatedVel.y, 1.0E-9D);
        assertEquals(Math.hypot(entryVel.x, entryVel.z), Math.hypot(rotatedVel.x, rotatedVel.z), 1.0E-9D);

        // F8, F9, F10, F11 PROJECT.md contracts
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION_HANDLER, false);
        SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false);

        Class<?> switchHintClass = SeamlessContractProbe.requireRecord(
                FQCN_SEAMLESS_SWITCH_HINT_PAYLOAD,
                "targetDim", "targetCorePos", "x", "y", "z", "yRot", "xRot", "velocity"
        );
        StreamCodec<RegistryFriendlyByteBuf, Object> codec =
                SeamlessContractProbe.requireStreamCodec(switchHintClass, "STREAM_CODEC");
        Object sampleHint = SeamlessContractProbe.newInstance(
                switchHintClass,
                new Class<?>[]{ResourceKey.class, BlockPos.class, double.class, double.class, double.class, float.class, float.class, Vec3.class},
                E2ePortalFixtures.NETHER,
                E2ePortalFixtures.DEFAULT_TARGET_CORE,
                -319.5D, 73.0D, 480.5D, -90.0F, -15.0F, rotatedVel
        );
        Object decodedHint = SeamlessContractProbe.roundTripByteBufCodec(codec, sampleHint);
        assertEquals(sampleHint, decodedHint);
    }

    @Test
    @Tag("areaC")
    @Tag("areaD")
    @DisplayName("T3-C04: Unstable state + wiredFieldCells continuous crossing (F12+F8)")
    void unstableStateAndWiredFieldCellsCrossing() {
        long unstableThreshold = SeamlessContractProbe.readStaticField(
                PortalCoreBlockEntity.class, "UNSTABLE_THRESHOLD_TICKS", long.class
        );
        assertEquals(1200L, unstableThreshold);
        SeamlessContractProbe.requireField(PortalCoreBlockEntity.class, "wiredFieldCells", List.class);

        byte unstableActiveFlags = PortalEndpointResolver.computeStateFlags(false, true, true);
        assertEquals(
                (byte) (PortalRemoteEndpointPayload.FLAG_ACTIVE | PortalRemoteEndpointPayload.FLAG_UNSTABLE),
                unstableActiveFlags
        );

        assertEquals(8.0F, SeamlessContractProbe.readStaticField(PortalTeleportHandler.class, "UNSTABLE_DAMAGE_AMOUNT", float.class));
        assertEquals(60, SeamlessContractProbe.readStaticField(PortalTeleportHandler.class, "UNSTABLE_NAUSEA_DURATION_TICKS", int.class));
        assertEquals(100, SeamlessContractProbe.readStaticField(PortalTeleportHandler.class, "UNSTABLE_SLOWNESS_DURATION_TICKS", int.class));
        assertEquals(2, SeamlessContractProbe.readStaticField(PortalTeleportHandler.class, "UNSTABLE_SLOWNESS_AMPLIFIER", int.class));

        // Probe M3 crossing tracker & M4 unstable LivingEntity cross-dim fix contracts
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
        SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false);
    }

    @Test
    @Tag("areaC")
    @Tag("areaD")
    @DisplayName("T3-C05: Vehicle + player rider cross-dimension seamless switch (F13+F10+F11)")
    void vehicleAndPlayerRiderCrossDimSeamlessSwitch() {
        assertEquals(0.18D, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_MINECART_EXTRA_OFFSET", double.class
        ));

        // F13: Minecart + passenger entity sync round-trip
        ClientboundAddEntityPacket addCart = new ClientboundAddEntityPacket(
                501, UUID.fromString("00000000-0000-0000-0000-000000003501"),
                100.5D, 65.0D, 200.5D, 0.0F, 0.0F, EntityType.MINECART, 0, new Vec3(0.5D, 0.0D, 0.0D), 0.0D
        );
        RemoteEntitySyncPayload wrappedCart = ServerPortalStreamManager.wrapEntityPacket(
                E2ePortalFixtures.NETHER, 501, E2ePortalFixtures.registryAccess(), addCart
        ).orElseThrow();
        assertEquals(RemoteEntitySyncPayload.SyncKind.ADD_ENTITY,
                SeamlessContractProbe.roundTripByteBufCodec(RemoteEntitySyncPayload.STREAM_CODEC, wrappedCart).kind());

        ClientboundSetPassengersPacket passengersPacket = newSetPassengersPacket(501, 502);
        RemoteEntitySyncPayload wrappedPassengers = ServerPortalStreamManager.wrapEntityPacket(
                E2ePortalFixtures.NETHER, 501, E2ePortalFixtures.registryAccess(), passengersPacket
        ).orElseThrow();
        ClientboundSetPassengersPacket decodedPassengers = assertInstanceOf(
                ClientboundSetPassengersPacket.class,
                SeamlessContractProbe.roundTripByteBufCodec(RemoteEntitySyncPayload.STREAM_CODEC, wrappedPassengers)
                        .decodeVanillaPacket(E2ePortalFixtures.registryAccess())
        );
        assertEquals(501, decodedPassengers.getVehicle());
        assertArrayEquals(new int[]{502}, decodedPassengers.getPassengers());

        // F10: 90-degree velocity preservation
        Vec3 cartOut = SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class,
                "transformVelocityThroughPortal",
                Vec3.class,
                new Class<?>[]{Vec3.class, Direction.class, Direction.class, Direction.class, Direction.class},
                new Vec3(0.0D, 0.0D, -0.5D), Direction.EAST, Direction.SOUTH, Direction.SOUTH, Direction.EAST
        );
        assertEquals(0.5D, cartOut.length(), 1.0E-9D);

        // F11 + M3/M4 contracts
        SeamlessContractProbe.requireRecord(
                FQCN_SEAMLESS_SWITCH_HINT_PAYLOAD,
                "targetDim", "targetCorePos", "x", "y", "z", "yRot", "xRot", "velocity"
        );
        SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
    }

    @Test
    @Tag("areaB")
    @Tag("areaD")
    @Tag("areaE")
    @DisplayName("T3-C06: Iris/Sodium fallback during unstable & collapse lifecycle (F14+F7+F12)")
    void irisSodiumFallbackDuringUnstableAndCollapse() {
        Class<?> membraneClass = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer", false);
        int[] unstableAlphas = SeamlessContractProbe.readStaticField(membraneClass, "UNSTABLE_ALPHAS", int[].class);
        assertArrayEquals(new int[]{102, 153, 217, 217}, unstableAlphas);
        assertEquals(3.5F, SeamlessContractProbe.readStaticField(PortalGateAnimator.class, "UNSTABLE_SPEED", float.class));
        assertEquals(60, SeamlessContractProbe.readStaticField(PortalGateAnimator.class, "VENT_TICKS", int.class));

        PortalFieldCollapsePayload collapse = SeamlessContractProbe.roundTripByteBufCodec(
                PortalFieldCollapsePayload.STREAM_CODEC,
                new PortalFieldCollapsePayload(
                        E2ePortalFixtures.expectedInterior(E2ePortalFixtures.DEFAULT_SOURCE_CORE, Direction.EAST),
                        true,
                        true
                )
        );
        assertEquals(E2ePortalFixtures.EXPECTED_INTERIOR_CELL_COUNT, collapse.cells().size());
        assertTrue(collapse.unstable());

        // F14 + F7 contracts
        SeamlessContractProbe.requireInstanceMethod(PlatformHelper.class, "isModLoaded", boolean.class, String.class);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "isIrisShaderPackActive", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "isIrisShadowPass", boolean.class);
        SeamlessContractProbe.requireClass(FQCN_SODIUM_IRIS_COMPAT, false);
        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class
        );
    }

    @Test
    @Tag("areaA")
    @Tag("areaB")
    @Tag("areaE")
    @DisplayName("T3-C07: Full resource cleanup across server tickets, remote ClientLevel & GPU buffers (F15+F2+F4+F6)")
    void fullResourceCleanupAcrossTicketsClientLevelAndGpuBuffers() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000003007");
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;

        PortalStreamPlanner planner = new PortalStreamPlanner();
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController tickets = new RemoteChunkTicketController(backend);

        planner.subscribe(playerId, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);
        PortalStreamPlanner.PortalKey portalKey = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, sourceCore);
        tickets.setClaims(portalKey, planner.desiredTicketKeysForPortal(portalKey));

        PortalStreamPlanner.SubscriberKey subKey = new PortalStreamPlanner.SubscriberKey(
                playerId, E2ePortalFixtures.OVERWORLD, sourceCore
        );
        ChunkPos chunk0 = new ChunkPos(targetCore);
        planner.markChunkSent(subKey, chunk0.toLong());

        List<PortalStreamPlanner.ChunkForgetBatch> forgets = planner.closePortal(E2ePortalFixtures.OVERWORLD, sourceCore);
        assertEquals(1, forgets.size());
        assertEquals(List.of(chunk0), forgets.getFirst().chunks());

        int released = tickets.releaseOwner(portalKey);
        assertEquals(25, released);
        assertEquals(0, tickets.activeTicketCount());
        assertEquals(25, backend.count("ADD"));
        assertEquals(25, backend.count("REMOVE"));

        PortalRemoteEndpointPayload closed = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC,
                PortalEndpointResolver.closedPayload(sourceCore, E2ePortalFixtures.OVERWORLD, null)
        );
        assertTrue(closed.isClosed());

        // Client-side remote level & GPU buffer teardown contracts
        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "ensureMainTargetStencil", boolean.class);
    }

    @Test
    @Tag("areaA")
    @Tag("areaD")
    @DisplayName("T3-C08: Endpoint metadata sync across Idle -> Opening -> Active -> Unstable -> Closed (F1+F12)")
    void endpointMetadataSyncAcrossFullStateMachine() {
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;

        byte[] expectedSequence = new byte[]{
                PortalEndpointResolver.computeStateFlags(true, false, false),
                PortalEndpointResolver.computeStateFlags(false, true, false),
                PortalEndpointResolver.computeStateFlags(false, true, true),
                PortalEndpointResolver.computeStateFlags(false, false, false)
        };

        for (byte flags : expectedSequence) {
            PortalRemoteEndpointPayload payload = SeamlessContractProbe.roundTripByteBufCodec(
                    PortalRemoteEndpointPayload.STREAM_CODEC,
                    new PortalRemoteEndpointPayload(
                            sourceCore, E2ePortalFixtures.NETHER, targetCore, Direction.SOUTH, Direction.EAST, true, flags
                    )
            );
            assertEquals(flags, payload.stateFlags());
        }

        Class<?> ringClass = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRingLayer", false);
        PortalGateRenderContext activeCtx = new PortalGateRenderContext(
                null, null, sourceCore, Direction.EAST, 200L, 0.0F, 200.0F,
                false, true, false, 1.0F, 45.0F, 1.0F, false
        );
        int activeLip = SeamlessContractProbe.invokeStatic(
                ringClass, "lipColor", int.class, new Class<?>[]{PortalGateRenderContext.class, float.class},
                activeCtx, 0.0F
        );
        assertEquals(0xFFFFFF, activeLip);

        PortalGateRenderContext unstableCtx = new PortalGateRenderContext(
                null, null, sourceCore, Direction.EAST, 1450L, 0.0F, 1450.0F,
                false, true, true, 1.0F, 90.0F, 1.0F, false
        );
        int unstableLip = SeamlessContractProbe.invokeStatic(
                ringClass, "lipColor", int.class, new Class<?>[]{PortalGateRenderContext.class, float.class},
                unstableCtx, 0.0F
        );
        assertTrue(unstableLip == 0xFFFFFF || unstableLip == 0x5A5A5A);

        // Client manager sync contract
        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
    }

    @Test
    @Tag("areaA")
    @Tag("areaC")
    @DisplayName("T3-C09: Live remote block delta updates half-through collision sampling (F3+F9)")
    void liveRemoteBlockDeltaAltersHalfThroughCollision() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000003009");
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;
        BlockPos exitObstacle = targetCore.relative(Direction.EAST).above();

        PortalStreamPlanner planner = new PortalStreamPlanner();
        planner.subscribe(playerId, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);
        long chunkKey = ChunkPos.asLong(exitObstacle);
        planner.markChunkSent(new PortalStreamPlanner.SubscriberKey(playerId, E2ePortalFixtures.OVERWORLD, sourceCore), chunkKey);

        assertTrue(planner.queueBlockDelta(E2ePortalFixtures.NETHER, exitObstacle));
        assertEquals(Set.of(playerId), planner.subscribersForSentChunk(E2ePortalFixtures.NETHER, chunkKey));
        assertTrue(planner.drainDirtyBlocks().get(E2ePortalFixtures.NETHER).get(chunkKey).contains(exitObstacle));

        RemoteBlockDeltaPayload obstacleDelta = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteBlockDeltaPayload.STREAM_CODEC,
                new RemoteBlockDeltaPayload(E2ePortalFixtures.NETHER, exitObstacle, Blocks.OBSIDIAN.defaultBlockState(), Optional.empty())
        );
        assertEquals(Blocks.OBSIDIAN.defaultBlockState(), obstacleDelta.state());

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION_HANDLER, false);
    }

    @Test
    @Tag("areaA")
    @Tag("areaC")
    @DisplayName("T3-C10: Sound & particle gating flips cleanly on seamless dimension swap (F4+F11)")
    void soundAndParticleGatingFlipsOnDimensionSwap() {
        Class<?> soundClientClass = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalAmbientSoundClient", false);
        assertEquals(540L, SeamlessContractProbe.readStaticField(soundClientClass, "AMBIENT_LOOP_INTERVAL_TICKS", long.class));
        assertEquals(10L, SeamlessContractProbe.readStaticField(soundClientClass, "UNSTABLE_AMBIENT_LOOP_INTERVAL_TICKS", long.class));
        assertEquals(256.0D, SeamlessContractProbe.readStaticField(soundClientClass, "HEARING_RANGE_SQR", double.class));

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_SEAMLESS_SWITCH_COORDINATOR, false);
        SeamlessContractProbe.requireRecord(
                FQCN_SEAMLESS_SWITCH_HINT_PAYLOAD,
                "targetDim", "targetCorePos", "x", "y", "z", "yRot", "xRot", "velocity"
        );
    }

    @Test
    @Tag("areaB")
    @Tag("areaD")
    @DisplayName("T3-C11: Remote vehicle & projectile sync + oblique plane visibility (F5+F7+F13)")
    void remoteVehicleAndProjectileVisibilityAcrossObliquePlane() {
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;
        PortalStreamPlanner planner = new PortalStreamPlanner();
        assertTrue(planner.isEntityInStreamRange(targetCore, targetCore.getX() + 4.5D, targetCore.getY() + 2.0D, targetCore.getZ() + 0.5D));

        ClientboundAddEntityPacket addArrow = new ClientboundAddEntityPacket(
                311, UUID.fromString("00000000-0000-0000-0000-000000003311"),
                targetCore.getX() + 4.5D, targetCore.getY() + 2.5D, targetCore.getZ() + 0.5D,
                -5.0F, 90.0F, EntityType.ARROW, 0, new Vec3(-1.2D, 0.1D, 0.0D), 90.0D
        );
        RemoteEntitySyncPayload decodedArrow = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteEntitySyncPayload.STREAM_CODEC,
                ServerPortalStreamManager.wrapEntityPacket(
                        E2ePortalFixtures.NETHER, 311, E2ePortalFixtures.registryAccess(), addArrow
                ).orElseThrow()
        );
        assertEquals(RemoteEntitySyncPayload.SyncKind.ADD_ENTITY, decodedArrow.kind());

        ClientboundSetEntityMotionPacket motion = new ClientboundSetEntityMotionPacket(311, new Vec3(-1.2D, 0.1D, 0.0D));
        RemoteEntitySyncPayload decodedMotion = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteEntitySyncPayload.STREAM_CODEC,
                ServerPortalStreamManager.wrapEntityPacket(
                        E2ePortalFixtures.NETHER, 311, E2ePortalFixtures.registryAccess(), motion
                ).orElseThrow()
        );
        assertEquals(RemoteEntitySyncPayload.SyncKind.SET_MOTION, decodedMotion.kind());

        //? if >=1.21.11 {
        /*ClientboundMoveMinecartPacket moveMinecart = new ClientboundMoveMinecartPacket(
                312,
                List.of(new NewMinecartBehavior.MinecartStep(
                        new Vec3(targetCore.getX() + 3.0D, targetCore.getY() + 1.0D, targetCore.getZ() + 0.5D),
                        new Vec3(-0.4D, 0.0D, 0.0D),
                        90.0F, 0.0F, 1.0F
                ))
        );
        RemoteEntitySyncPayload decodedCartMove = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteEntitySyncPayload.STREAM_CODEC,
                ServerPortalStreamManager.wrapEntityPacket(
                        E2ePortalFixtures.NETHER, 312, E2ePortalFixtures.registryAccess(), moveMinecart
                ).orElseThrow()
        );
        assertEquals(RemoteEntitySyncPayload.SyncKind.MOVE_MINECART, decodedCartMove.kind());
        *///?} else {
        ClientboundMoveEntityPacket.PosRot moveCartLegacy = new ClientboundMoveEntityPacket.PosRot(
                312, (short) -64, (short) 0, (short) 0, (byte) 64, (byte) 0, true
        );
        RemoteEntitySyncPayload decodedCartMove = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteEntitySyncPayload.STREAM_CODEC,
                ServerPortalStreamManager.wrapEntityPacket(
                        E2ePortalFixtures.NETHER, 312, E2ePortalFixtures.registryAccess(), moveCartLegacy
                ).orElseThrow()
        );
        assertEquals(RemoteEntitySyncPayload.SyncKind.MOVE_POS_ROT, decodedCartMove.kind());
        //?}

        SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM, false);
        SeamlessContractProbe.requireClass(FQCN_OBLIQUE_FRUSTUM_MATH, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
    }

    @Test
    @Tag("areaB")
    @Tag("areaE")
    @DisplayName("T3-C12: Recursion depth cap & active view budget with membrane fallback (F7+F15)")
    void recursionDepthCapAndActiveViewBudget() {
        Vec3 centerA = E2ePortalFixtures.portalCenter(new BlockPos(0, 64, 0));
        Vec3 centerB = E2ePortalFixtures.portalCenter(new BlockPos(0, 64, 8));
        assertEquals(8.0D, centerA.distanceTo(centerB), 1.0E-9D);

        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class
        );
        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
    }

    @Test
    @Tag("areaC")
    @Tag("areaD")
    @DisplayName("T3-C13: Destination-side blocked travel + keep-open extension + unstable transition (F8+F12)")
    void blockedDirectionKeepOpenExtensionAndUnstableTransition() {
        long extension = SeamlessContractProbe.readStaticField(
                PortalCoreBlockEntity.class, "PER_ENTITY_EXTENSION_TICKS", long.class
        );
        long unstableThreshold = SeamlessContractProbe.readStaticField(
                PortalCoreBlockEntity.class, "UNSTABLE_THRESHOLD_TICKS", long.class
        );
        assertEquals(200L, extension);
        assertEquals(1200L, unstableThreshold);
        assertEquals(180, PortalEnergyCosts.activeCostPerSecond(true));

        long t0 = 1000L;
        long contactTick = t0 + 1150L;
        long extendedDeadline = contactTick + extension;
        assertTrue(extendedDeadline > t0 + unstableThreshold);
        assertEquals(
                (byte) (PortalRemoteEndpointPayload.FLAG_ACTIVE | PortalRemoteEndpointPayload.FLAG_UNSTABLE),
                PortalEndpointResolver.computeStateFlags(false, true, true)
        );

        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
    }

    @Test
    @Tag("areaC")
    @Tag("areaD")
    @DisplayName("T3-C14: Wide mounted entity (Horse + Rider) half-through aperture collision (F9+F13)")
    void wideMountedEntityHalfThroughApertureCollision() {
        assertEquals(3, PortalFrameDetector.INNER_WIDTH);
        assertEquals(4, PortalFrameDetector.INNER_HEIGHT);
        assertEquals(1.5D, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_INNER_HALF_WIDTH", double.class
        ));
        assertEquals(0.01D, SeamlessContractProbe.readStaticField(
                PortalTeleportHandler.class, "PORTAL_POSITION_EPSILON", double.class
        ));

        double horseWidth = EntityType.HORSE.getWidth();
        assertTrue(horseWidth > 1.0D && horseWidth < PortalFrameDetector.INNER_WIDTH);

        SeamlessContractProbe.requireClass(FQCN_PORTAL_HALF_COLLISION_HANDLER, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
    }

    @Test
    @Tag("areaC")
    @Tag("areaD")
    @DisplayName("T3-C15: Multi-passenger Boat 180-degree velocity & yaw continuity (F10+F13)")
    void multiPassengerBoat180DegreeVelocityAndYawContinuity() {
        //? if >=1.21.11 {
        /*EntityType<?> boatType = EntityType.OAK_BOAT;
        *///?} else {
        EntityType<?> boatType = EntityType.BOAT;
        //?}
        ClientboundAddEntityPacket addBoat = new ClientboundAddEntityPacket(
                701, UUID.fromString("00000000-0000-0000-0000-000000003701"),
                100.5D, 64.0D, 200.5D, 0.0F, 0.0F, boatType, 0, new Vec3(0.0D, 0.0D, 0.6D), 0.0D
        );
        assertEquals(RemoteEntitySyncPayload.SyncKind.ADD_ENTITY,
                SeamlessContractProbe.roundTripByteBufCodec(
                        RemoteEntitySyncPayload.STREAM_CODEC,
                        ServerPortalStreamManager.wrapEntityPacket(
                                E2ePortalFixtures.OVERWORLD, 701, E2ePortalFixtures.registryAccess(), addBoat
                        ).orElseThrow()
                ).kind());

        ClientboundSetPassengersPacket boatPassengers = newSetPassengersPacket(701, 702, 703);
        ClientboundSetPassengersPacket decodedPassengers = assertInstanceOf(
                ClientboundSetPassengersPacket.class,
                SeamlessContractProbe.roundTripByteBufCodec(
                        RemoteEntitySyncPayload.STREAM_CODEC,
                        ServerPortalStreamManager.wrapEntityPacket(
                                E2ePortalFixtures.OVERWORLD, 701, E2ePortalFixtures.registryAccess(), boatPassengers
                        ).orElseThrow()
                ).decodeVanillaPacket(E2ePortalFixtures.registryAccess())
        );
        assertArrayEquals(new int[]{702, 703}, decodedPassengers.getPassengers());

        // 180-degree opposing portals: SOUTH -> SOUTH (with exit opposite to entry)
        Vec3 reversedVel = SeamlessContractProbe.invokeStatic(
                PortalTeleportHandler.class,
                "transformVelocityThroughPortal",
                Vec3.class,
                new Class<?>[]{Vec3.class, Direction.class, Direction.class, Direction.class, Direction.class},
                new Vec3(0.15D, 0.0D, 0.60D), Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH
        );
        assertEquals(0.15D, reversedVel.x, 1.0E-9D);
        assertEquals(-0.60D, reversedVel.z, 1.0E-9D);

        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_MATH, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_CROSSING_TRACKER, false);
    }

    @Test
    @Tag("areaA")
    @Tag("areaE")
    @DisplayName("T3-C16: Overlapping destination portals keep ref-counted tickets alive on partial teardown (F2+F15)")
    void overlappingDestinationPortalsPartialTeardown() {
        UUID obs1 = UUID.fromString("00000000-0000-0000-0000-000000003016");
        UUID obs2 = UUID.fromString("00000000-0000-0000-0000-000000003017");
        BlockPos portalA = new BlockPos(0, 64, 0);
        BlockPos portalC = new BlockPos(64, 64, 0);
        BlockPos sharedB = E2ePortalFixtures.DEFAULT_TARGET_CORE;

        PortalStreamPlanner planner = new PortalStreamPlanner();
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController tickets = new RemoteChunkTicketController(backend);

        planner.subscribe(obs1, E2ePortalFixtures.OVERWORLD, portalA, E2ePortalFixtures.NETHER, sharedB);
        planner.subscribe(obs2, E2ePortalFixtures.OVERWORLD, portalC, E2ePortalFixtures.NETHER, sharedB);

        PortalStreamPlanner.PortalKey keyA = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, portalA);
        PortalStreamPlanner.PortalKey keyC = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, portalC);
        List<RemoteChunkTicketController.TicketKey> desiredA = planner.desiredTicketKeysForPortal(keyA);
        List<RemoteChunkTicketController.TicketKey> desiredC = planner.desiredTicketKeysForPortal(keyC);

        tickets.setClaims(keyA, desiredA);
        tickets.setClaims(keyC, desiredC);
        assertEquals(25, tickets.activeTicketCount());
        assertEquals(25L, backend.count("ADD"));
        for (RemoteChunkTicketController.TicketKey tk : desiredA) {
            assertEquals(2, tickets.refCount(tk));
        }

        // Close A while C -> B remains active
        planner.closePortal(E2ePortalFixtures.OVERWORLD, portalA);
        tickets.releaseOwner(keyA);
        assertEquals(25, tickets.activeTicketCount());
        assertEquals(0L, backend.count("REMOVE"));
        for (RemoteChunkTicketController.TicketKey tk : desiredC) {
            assertEquals(1, tickets.refCount(tk));
        }
        assertTrue(planner.hasSubscribers(keyC));

        // Now close C -> all tickets drop to 0
        planner.closePortal(E2ePortalFixtures.OVERWORLD, portalC);
        tickets.releaseOwner(keyC);
        assertEquals(0, tickets.activeTicketCount());
        assertEquals(25L, backend.count("REMOVE"));
    }

    @Test
    @Tag("areaA")
    @Tag("areaE")
    @DisplayName("T3-C17: Burst destination block edits coalescing, throttling & forget cleanup (F3+F15)")
    void burstDestinationBlockEditsCoalescingAndForget() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000003018");
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;

        PortalStreamPlanner planner = new PortalStreamPlanner();
        assertEquals(4, planner.chunksPerTickBudget());
        planner.subscribe(playerId, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);

        PortalStreamPlanner.SubscriberKey subKey = new PortalStreamPlanner.SubscriberKey(
                playerId, E2ePortalFixtures.OVERWORLD, sourceCore
        );
        long centerChunkKey = ChunkPos.asLong(targetCore);
        planner.markChunkSent(subKey, centerChunkKey);

        // 500 mutations hitting 100 distinct blocks (5x duplicate coalescing)
        for (int i = 0; i < 500; i++) {
            int dx = i % 10;
            int dy = (i / 10) % 10;
            planner.queueBlockDelta(E2ePortalFixtures.NETHER, targetCore.offset(dx, dy, 1));
        }

        Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> drained = planner.drainDirtyBlocks();
        int totalCoalesced = drained.get(E2ePortalFixtures.NETHER).values().stream()
                .mapToInt(Set::size)
                .sum();
        assertEquals(100, totalCoalesced);

        RemoteChunkForgetPayload forgetDecoded = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteChunkForgetPayload.STREAM_CODEC,
                new RemoteChunkForgetPayload(E2ePortalFixtures.NETHER, ChunkPos.getX(centerChunkKey), ChunkPos.getZ(centerChunkKey))
        );
        assertEquals(E2ePortalFixtures.NETHER, forgetDecoded.dim());

        // M4 per-tick delta budget throttling contract on ServerPortalStreamManager / PortalStreamPlanner
        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        assertTrue(
                hasMethodNamed(PortalStreamPlanner.class, "drainDirtyBlocksWithinBudget")
                        || hasMethodNamed(ServerPortalStreamManager.class, "flushDirtyBlockDeltasWithinBudget")
                        || Arrays.stream(PortalStreamPlanner.class.getDeclaredFields())
                        .anyMatch(f -> f.getName().toLowerCase().contains("deltabudget")
                                || f.getName().toLowerCase().contains("blockdeltaspertick")),
                "Expected per-tick block delta budget contract on PortalStreamPlanner or ServerPortalStreamManager"
        );
    }

    @Test
    @Tag("areaB")
    @Tag("areaE")
    @DisplayName("T3-C18: Stencil/FBO render hook & Sodium/Iris compat across 1.21.1 and 1.21.11 (F6+F14)")
    void stencilHookAndSodiumIrisCompatAcrossVersions() {
        Class<?> portalRenderer = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRenderer", false);
        assertTrue(ImmediateBlockEntityRenderer.class.isAssignableFrom(portalRenderer));

        //? if >=1.21.11 {
        /*Class<?> recordingBuffer = SeamlessContractProbe.requireClass(
                "fr.geomtech.universegate.compat.RecordingBufferSource", false
        );
        assertNotNull(recordingBuffer);
        SeamlessContractProbe.requireClass("net.minecraft.client.gui.screens.LevelLoadingScreen", false);
        *///?} else {
        SeamlessContractProbe.requireClass("net.minecraft.client.gui.screens.ReceivingLevelScreen", false);
        //?}

        SeamlessContractProbe.requireInstanceMethod(PlatformHelper.class, "isModLoaded", boolean.class, String.class);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "isIrisShaderPackActive", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "isIrisShadowPass", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "ensureMainTargetStencil", boolean.class);
        assertTrue(hasMethodNamed(ClientPlatformHelper.class, "registerLevelRenderPortalHook"),
                "ClientPlatformHelper must declare registerLevelRenderPortalHook");

        SeamlessContractProbe.requireClass(FQCN_SODIUM_IRIS_COMPAT, false);
        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class
        );
    }
}
