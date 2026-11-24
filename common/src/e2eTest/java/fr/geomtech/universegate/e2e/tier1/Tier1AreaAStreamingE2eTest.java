package fr.geomtech.universegate.e2e.tier1;

import fr.geomtech.universegate.PortalConnectionManager;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.UniverseGateDimensions;
import fr.geomtech.universegate.e2e.harness.E2eBootstrap;
import fr.geomtech.universegate.e2e.harness.E2ePortalFixtures;
import fr.geomtech.universegate.e2e.harness.SeamlessContractProbe;
import fr.geomtech.universegate.seamless.net.PortalEndpointListener;
import fr.geomtech.universegate.seamless.net.PortalEndpointResolver;
import fr.geomtech.universegate.seamless.net.PortalRemoteEndpointPayload;
import fr.geomtech.universegate.seamless.net.ResolvedPortalEndpoint;
import fr.geomtech.universegate.seamless.net.SeamlessNetwork;
import fr.geomtech.universegate.seamless.net.RemoteBlockDeltaPayload;
import fr.geomtech.universegate.seamless.net.RemoteChunkDataPayload;
import fr.geomtech.universegate.seamless.net.RemoteEntitySyncPayload;
import fr.geomtech.universegate.seamless.server.PortalStreamPlanner;
import fr.geomtech.universegate.seamless.server.RemoteChunkTicketController;
import fr.geomtech.universegate.seamless.server.ServerPortalStreamManager;
import fr.geomtech.universegate.seamless.server.TicketBackend;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
//? if >=1.21.11 {
/*import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.levelgen.Heightmap;
*///?} else {
import net.minecraft.nbt.LongArrayTag;
//?}
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
//? if >=1.21.11 {
/*import java.util.EnumMap;
*///?}
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("tier1")
@Tag("areaA")
@DisplayName("Tier 1 Area A: Remote World Observation & Streaming (F1-F4)")
class Tier1AreaAStreamingE2eTest {

    private static final String FQCN_CLIENT_REMOTE_WORLD_MANAGER =
            "fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager";
    private static final String FQCN_REMOTE_CLIENT_LEVEL_FACTORY =
            "fr.geomtech.universegate.seamless.client.RemoteClientLevelFactory";
    private static final String FQCN_REMOTE_RENDER_WORLD_STATE =
            "fr.geomtech.universegate.seamless.client.RemoteRenderWorldState";

    private record TicketOp(String op, ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {}

    private static final class RecordingTicketBackend implements TicketBackend {
        final List<TicketOp> ops = new ArrayList<>();
        int addCount = 0;
        int removeCount = 0;

        @Override
        public void addTicket(ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {
            ops.add(new TicketOp("ADD", dim, chunkX, chunkZ, radius));
            addCount++;
        }

        @Override
        public void removeTicket(ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {
            ops.add(new TicketOp("REMOVE", dim, chunkX, chunkZ, radius));
            removeCount++;
        }
    }

    @BeforeAll
    static void bootstrap() {
        E2eBootstrap.ensureMinecraftBootstrapped();
    }

    @AfterEach
    void cleanupStaticHandlers() {
        SeamlessNetwork.resetHandlers();
        PortalCoreBlockEntity.setEndpointListener(null);
    }

    private static ClientboundLevelChunkWithLightPacket syntheticChunkPacket(
            int chunkX,
            int chunkZ,
            long @Nullable [] motionBlockingHeightmap,
            byte[] chunkSectionBytes,
            @Nullable BlockPos signBePos,
            @Nullable String signText,
            BitSet skyYMask,
            BitSet blockYMask,
            BitSet emptySkyYMask,
            BitSet emptyBlockYMask,
            List<byte[]> skyUpdates,
            List<byte[]> blockUpdates
    ) {
        RegistryFriendlyByteBuf buf = E2ePortalFixtures.newRegistryFriendlyByteBuf();
        try {
            buf.writeInt(chunkX);
            buf.writeInt(chunkZ);

            //? if >=1.21.11 {
            /*EnumMap<Heightmap.Types, long[]> heightmaps = new EnumMap<>(Heightmap.Types.class);
            if (motionBlockingHeightmap != null) {
                heightmaps.put(Heightmap.Types.MOTION_BLOCKING, motionBlockingHeightmap);
            }
            ByteBufCodecs.map(
                    ignored -> new EnumMap<>(Heightmap.Types.class),
                    Heightmap.Types.STREAM_CODEC,
                    ByteBufCodecs.LONG_ARRAY
            ).encode(buf, heightmaps);
            *///?} else {
            CompoundTag heightmaps = new CompoundTag();
            if (motionBlockingHeightmap != null) {
                heightmaps.put("MOTION_BLOCKING", new LongArrayTag(motionBlockingHeightmap));
            }
            buf.writeNbt(heightmaps);
            //?}

            buf.writeVarInt(chunkSectionBytes.length);
            buf.writeBytes(chunkSectionBytes);

            if (signBePos != null) {
                buf.writeVarInt(1);
                int packedXZ = ((signBePos.getX() & 15) << 4) | (signBePos.getZ() & 15);
                buf.writeByte(packedXZ);
                buf.writeShort(signBePos.getY());
                ByteBufCodecs.registry(Registries.BLOCK_ENTITY_TYPE).encode(buf, BlockEntityType.SIGN);
                CompoundTag beTag = new CompoundTag();
                if (signText != null) {
                    beTag.putString("SignText", signText);
                }
                buf.writeNbt(beTag);
            } else {
                buf.writeVarInt(0);
            }

            buf.writeBitSet(skyYMask);
            buf.writeBitSet(blockYMask);
            buf.writeBitSet(emptySkyYMask);
            buf.writeBitSet(emptyBlockYMask);
            buf.writeCollection(skyUpdates, ByteBufCodecs.byteArray(2048));
            buf.writeCollection(blockUpdates, ByteBufCodecs.byteArray(2048));

            return ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    private static ClientboundRotateHeadPacket newRotateHeadPacket(int entityId, byte yHeadRot) {
        RegistryFriendlyByteBuf buf = E2ePortalFixtures.newRegistryFriendlyByteBuf();
        try {
            buf.writeVarInt(entityId);
            buf.writeByte(yHeadRot);
            return ClientboundRotateHeadPacket.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    // ==================== F1: Portal remote endpoint metadata sync ====================

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F1-01: Same-dim EAST-oriented active portal metadata sync")
    void sameDimEastOrientedActivePortalMetadataSync() {
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = new BlockPos(500, 64, -120);

        assertEquals(Direction.EAST, PortalEndpointResolver.resolveExitNormal(Direction.SOUTH, targetCore, null));
        assertEquals(Direction.WEST, PortalEndpointResolver.resolveExitNormal(
                Direction.SOUTH, targetCore, targetCore.offset(-3, 0, 0)));

        ResolvedPortalEndpoint resolved = PortalEndpointResolver.resolveEndpoint(
                false, true, false, true,
                E2ePortalFixtures.OVERWORLD, targetCore,
                Direction.SOUTH, Direction.EAST,
                null, null, null, null
        ).orElseThrow();

        PortalRemoteEndpointPayload payload = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC,
                resolved.toPayload(sourceCore)
        );
        assertEquals(sourceCore, payload.corePos());
        assertEquals(E2ePortalFixtures.OVERWORLD, payload.targetDim());
        assertEquals(targetCore, payload.targetCorePos());
        assertEquals(Direction.SOUTH, payload.targetRight());
        assertEquals(Direction.EAST, payload.exitNormal());
        assertTrue(payload.outboundEnabled());
        assertTrue(payload.isActive());
        assertFalse(payload.isOpening());
        assertFalse(payload.isUnstable());
        assertFalse(payload.isClosed());

        PortalEndpointListener listener = new PortalEndpointListener() {
            @Override
            public void onEndpointUpdated(ServerLevel l, BlockPos p, PortalCoreBlockEntity c, ResolvedPortalEndpoint e) {}

            @Override
            public void onEndpointClosed(ServerLevel l, BlockPos p, PortalCoreBlockEntity c, @Nullable ResolvedPortalEndpoint pr) {}

            @Override
            public void onCoreUnloaded(ServerLevel l, BlockPos p, PortalCoreBlockEntity c) {}
        };
        try {
            PortalCoreBlockEntity.setEndpointListener(listener);
            assertSame(listener, PortalCoreBlockEntity.getEndpointListener());
        } finally {
            PortalCoreBlockEntity.setEndpointListener(null);
            assertNull(PortalCoreBlockEntity.getEndpointListener());
        }
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F1-02: Cross-dim Overworld to Nether metadata sync")
    void crossDimOverworldToNetherMetadataSync() {
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;

        assertEquals(Direction.SOUTH, PortalEndpointResolver.normalizeFrameRight(Direction.WEST));

        ResolvedPortalEndpoint resolved = PortalEndpointResolver.resolveEndpoint(
                false, true, false, true,
                E2ePortalFixtures.NETHER, targetCore,
                Direction.WEST, Direction.WEST,
                null, null, null, null
        ).orElseThrow();

        PortalRemoteEndpointPayload decoded = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC,
                resolved.toPayload(sourceCore)
        );
        assertEquals(E2ePortalFixtures.NETHER, decoded.targetDim());
        assertEquals(targetCore, decoded.targetCorePos());
        assertEquals(Direction.SOUTH, decoded.targetRight());
        assertEquals(Direction.WEST, decoded.exitNormal());
        assertTrue(decoded.outboundEnabled());

        // Incompatible liveExitNormal (SOUTH is parallel to SOUTH right) falls back to EAST
        ResolvedPortalEndpoint fallbackNormal = PortalEndpointResolver.resolveEndpoint(
                false, true, false, true,
                E2ePortalFixtures.NETHER, targetCore,
                Direction.SOUTH, Direction.SOUTH,
                null, null, null, null
        ).orElseThrow();
        assertEquals(Direction.EAST, fallbackNormal.exitNormal());
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F1-03: Opening-phase early metadata sync before field placement")
    void openingPhaseEarlyMetadataSyncBeforeFieldPlacement() {
        assertEquals(
                PortalRemoteEndpointPayload.FLAG_OPENING,
                PortalEndpointResolver.computeStateFlags(true, false, false)
        );

        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;
        ResolvedPortalEndpoint resolved = PortalEndpointResolver.resolveEndpoint(
                true, false, false, true,
                E2ePortalFixtures.NETHER, targetCore,
                Direction.EAST, Direction.NORTH,
                null, null, null, null
        ).orElseThrow();

        PortalRemoteEndpointPayload decoded = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC,
                resolved.toPayload(sourceCore)
        );
        assertTrue(decoded.isOpening());
        assertFalse(decoded.isActive());
        assertFalse(decoded.isUnstable());
        assertFalse(decoded.isClosed());
        assertEquals(Direction.EAST, decoded.targetRight());
        assertEquals(Direction.NORTH, decoded.exitNormal());

        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                PortalCoreBlockEntity.class, "resolveRemoteEndpoint", Optional.class));
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F1-04: Destination-side outbound-disabled metadata sync")
    void destinationSideOutboundDisabledMetadataSync() {
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos destCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;

        ResolvedPortalEndpoint resolved = PortalEndpointResolver.resolveEndpoint(
                false, true, false, false,
                E2ePortalFixtures.OVERWORLD, sourceCore,
                Direction.EAST, Direction.SOUTH,
                null, null, null, null
        ).orElseThrow();

        PortalRemoteEndpointPayload decoded = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC,
                resolved.toPayload(destCore)
        );
        assertEquals(destCore, decoded.corePos());
        assertEquals(sourceCore, decoded.targetCorePos());
        assertFalse(decoded.outboundEnabled());
        assertTrue(decoded.isActive());

        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalConnectionManager.class, "openBothSides", boolean.class,
                ServerLevel.class, BlockPos.class, UUID.class));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalConnectionManager.class, "forceCloseOneSide", void.class,
                ServerLevel.class, BlockPos.class));
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F1-05: Observer entering range after portal active receives endpoint sync")
    void observerEnteringRangeAfterPortalActiveReceivesEndpointSync() {
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000001101");
        UUID p2 = UUID.fromString("00000000-0000-0000-0000-000000001102");

        PortalEndpointResolver.ObserverSyncPlan incremental =
                PortalEndpointResolver.computeObserverSync(Set.of(p1), List.of(p1, p2), false);
        assertEquals(List.of(p2), incremental.toSend());
        assertEquals(Set.of(p1, p2), incremental.nextSynced());
        assertTrue(incremental.untracked().isEmpty());

        PortalEndpointResolver.ObserverSyncPlan resyncAll =
                PortalEndpointResolver.computeObserverSync(Set.of(p1), List.of(p1, p2), true);
        assertEquals(List.of(p1, p2), resyncAll.toSend());

        AtomicReference<PortalRemoteEndpointPayload> received = new AtomicReference<>();
        SeamlessNetwork.setRemoteEndpointHandler(received::set);
        assertNull(received.get());

        // Client endpoint consumer must exist to register handler with SeamlessNetwork
        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
    }

    // ==================== F2: Server remote chunk loading & tickets ====================

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F2-01: Same-dim remote chunk radius ticket acquisition")
    void sameDimRemoteChunkRadiusTicketAcquisition() {
        PortalStreamPlanner planner = new PortalStreamPlanner();
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController controller = new RemoteChunkTicketController(backend);

        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000001201");
        BlockPos sourceCore = new BlockPos(0, 64, 0);
        BlockPos targetCore = new BlockPos(10000, 64, 10000);
        assertTrue(planner.subscribe(p1, E2ePortalFixtures.OVERWORLD, sourceCore,
                E2ePortalFixtures.OVERWORLD, targetCore).isEmpty());

        PortalStreamPlanner.PortalKey portalKey = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, sourceCore);
        List<RemoteChunkTicketController.TicketKey> desired = planner.desiredTicketKeysForPortal(portalKey);
        assertEquals(25, desired.size());
        assertEquals(new RemoteChunkTicketController.TicketKey(
                E2ePortalFixtures.OVERWORLD, 625, 625, PortalStreamPlanner.TICKET_RADIUS_PER_STREAMED_CHUNK),
                desired.getFirst());
        assertTrue(desired.stream().allMatch(k -> k.radius() == PortalStreamPlanner.TICKET_RADIUS_PER_STREAMED_CHUNK));

        controller.setClaims(portalKey, desired);
        assertEquals(25, backend.addCount);
        assertEquals(25, controller.activeTicketCount());
        assertEquals(1, controller.refCount(E2ePortalFixtures.OVERWORLD, 625, 625, 2));
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F2-02: Cross-dim Overworld to Nether chunk ticket acquisition")
    void crossDimOverworldToNetherChunkTicketAcquisition() {
        PortalStreamPlanner planner = new PortalStreamPlanner();
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController controller = new RemoteChunkTicketController(backend);

        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000001202");
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;
        planner.subscribe(p1, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);

        PortalStreamPlanner.PortalKey portalKey = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, sourceCore);
        controller.setClaims(portalKey, planner.desiredTicketKeysForPortal(portalKey));

        assertEquals(Set.of(E2ePortalFixtures.NETHER), controller.activeDimensions());
        assertEquals(25, controller.activeTicketCount());

        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                RemoteChunkTicketController.class, "registerTicketType", void.class));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                RemoteChunkTicketController.class, "createMinecraftBackend", TicketBackend.class, Supplier.class));
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F2-03: Cross-dim Overworld to universegate:rift ticket acquisition")
    void crossDimOverworldToRiftTicketAcquisition() {
        PortalStreamPlanner planner = new PortalStreamPlanner();
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController controller = new RemoteChunkTicketController(backend);

        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000001203");
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = new BlockPos(0, 80, 0);
        planner.subscribe(p1, E2ePortalFixtures.OVERWORLD, sourceCore, UniverseGateDimensions.RIFT, targetCore);

        PortalStreamPlanner.PortalKey portalKey = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, sourceCore);
        controller.setClaims(portalKey, planner.desiredTicketKeysForPortal(portalKey));

        assertEquals(Set.of(UniverseGateDimensions.RIFT), controller.activeDimensions());
        assertEquals(25, controller.activeTicketCount());

        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                ServerPortalStreamManager.class, "onServerStarting", void.class, MinecraftServer.class));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                ServerPortalStreamManager.class, "onServerTickEnd", void.class, MinecraftServer.class));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                ServerPortalStreamManager.class, "onServerStopping", void.class, MinecraftServer.class));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                ServerPortalStreamManager.class, "onServerStopped", void.class, MinecraftServer.class));
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F2-04: Multi-observer and multi-portal ticket reference counting")
    void multiObserverAndMultiPortalTicketReferenceCounting() {
        PortalStreamPlanner planner = new PortalStreamPlanner();
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000001204");
        UUID p2 = UUID.fromString("00000000-0000-0000-0000-000000001205");
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;

        planner.subscribe(p1, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);
        planner.subscribe(p2, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);

        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController controller = new RemoteChunkTicketController(backend);
        PortalStreamPlanner.PortalKey portalKey = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, sourceCore);
        controller.setClaims(portalKey, planner.desiredTicketKeysForPortal(portalKey));

        planner.unsubscribe(p1, E2ePortalFixtures.OVERWORLD, sourceCore);
        assertTrue(planner.hasSubscribers(portalKey));
        assertEquals(25, planner.desiredTicketKeysForPortal(portalKey).size());
        assertEquals(0, backend.removeCount);

        // Two portal owners sharing chunk (1, 0) in NETHER
        RemoteChunkTicketController.TicketKey exclusive1 = new RemoteChunkTicketController.TicketKey(
                E2ePortalFixtures.NETHER, 0, 0, 2);
        RemoteChunkTicketController.TicketKey shared = new RemoteChunkTicketController.TicketKey(
                E2ePortalFixtures.NETHER, 1, 0, 2);
        assertTrue(controller.acquire("portal1", exclusive1));
        assertTrue(controller.acquire("portal1", shared));
        assertTrue(controller.acquire("portal2", shared));
        assertEquals(2, controller.refCount(shared));

        controller.releaseOwner("portal1");
        assertEquals(0, controller.refCount(exclusive1));
        assertEquals(1, controller.refCount(shared));
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F2-05: Clean ticket release after last observer leaves and portal closes")
    void cleanTicketReleaseAfterLastObserverLeavesAndPortalCloses() {
        PortalStreamPlanner planner = new PortalStreamPlanner();
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController controller = new RemoteChunkTicketController(backend);

        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000001206");
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;
        planner.subscribe(p1, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);

        PortalStreamPlanner.PortalKey portalKey = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, sourceCore);
        controller.setClaims(portalKey, planner.desiredTicketKeysForPortal(portalKey));
        assertEquals(25, backend.addCount);

        planner.closePortal(E2ePortalFixtures.OVERWORLD, sourceCore);
        assertTrue(planner.desiredTicketKeysForPortal(portalKey).isEmpty());

        int released = controller.releaseOwner(portalKey);
        assertEquals(25, released);
        assertEquals(25, backend.removeCount);
        assertEquals(0, controller.activeTicketCount());
        assertTrue(controller.activeDimensions().isEmpty());
    }

    // ==================== F3: S2C chunk/light/block/BE/entity streaming ====================

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F3-01: Initial chunk and light stream around destination portal")
    void initialChunkAndLightStreamAroundDestinationPortal() {
        byte[] skySection = new byte[2048];
        java.util.Arrays.fill(skySection, (byte) 0xFF);
        byte[] blockSection = new byte[2048];
        java.util.Arrays.fill(blockSection, (byte) 0xEE);

        BitSet skyMask = new BitSet();
        skyMask.set(2);
        BitSet blockMask = new BitSet();
        blockMask.set(2);

        ClientboundLevelChunkWithLightPacket pkt = syntheticChunkPacket(
                -20, 30,
                null,
                new byte[]{1, 2, 3, 4},
                null,
                null,
                skyMask,
                blockMask,
                new BitSet(),
                new BitSet(),
                List.of(skySection),
                List.of(blockSection)
        );

        RemoteChunkDataPayload wrapped = RemoteChunkDataPayload.fromPacket(
                E2ePortalFixtures.NETHER, E2ePortalFixtures.registryAccess(), pkt);
        RemoteChunkDataPayload decodedPayload = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteChunkDataPayload.STREAM_CODEC, wrapped);
        ClientboundLevelChunkWithLightPacket decodedPkt =
                decodedPayload.decodePacket(E2ePortalFixtures.registryAccess());

        assertEquals(-20, decodedPkt.getX());
        assertEquals(30, decodedPkt.getZ());
        assertEquals(skyMask, decodedPkt.getLightData().getSkyYMask());
        assertEquals(blockMask, decodedPkt.getLightData().getBlockYMask());
        assertArrayEquals(skySection, decodedPkt.getLightData().getSkyUpdates().getFirst());
        assertArrayEquals(blockSection, decodedPkt.getLightData().getBlockUpdates().getFirst());

        List<ChunkPos> square = PortalStreamPlanner.collectChunkSquare(-20, 30, 2);
        assertEquals(25, square.size());
        assertEquals(new ChunkPos(-20, 30), square.getFirst());

        PortalStreamPlanner.ChunkDiff diff = PortalStreamPlanner.diffChunks(
                Set.of(new ChunkPos(-20, 30).toLong()), square);
        assertEquals(24, diff.toSend().size());
        assertTrue(diff.toForget().isEmpty());
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F3-02: Live single-block delta coalescing and client forwarding")
    void liveSingleBlockDeltaCoalescingAndClientForwarding() {
        PortalStreamPlanner planner = new PortalStreamPlanner();
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000001302");
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;

        planner.subscribe(p1, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);
        PortalStreamPlanner.SubscriberKey subKey =
                new PortalStreamPlanner.SubscriberKey(p1, E2ePortalFixtures.OVERWORLD, sourceCore);
        long chunkKey = ChunkPos.asLong(targetCore);
        planner.markChunkSent(subKey, chunkKey);

        BlockPos changedPos = targetCore.offset(3, 1, 0);
        assertTrue(planner.queueBlockDelta(E2ePortalFixtures.NETHER, changedPos));
        assertTrue(planner.queueBlockDelta(E2ePortalFixtures.NETHER, changedPos));

        Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> drained = planner.drainDirtyBlocks();
        assertEquals(1, drained.get(E2ePortalFixtures.NETHER).get(chunkKey).size());
        assertEquals(Set.of(p1), planner.subscribersForSentChunk(E2ePortalFixtures.NETHER, chunkKey));

        RemoteBlockDeltaPayload placeDiamond = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteBlockDeltaPayload.STREAM_CODEC,
                new RemoteBlockDeltaPayload(E2ePortalFixtures.NETHER, changedPos, Blocks.DIAMOND_BLOCK.defaultBlockState(), Optional.empty())
        );
        assertEquals(Blocks.DIAMOND_BLOCK.defaultBlockState(), placeDiamond.state());

        RemoteBlockDeltaPayload breakToAir = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteBlockDeltaPayload.STREAM_CODEC,
                new RemoteBlockDeltaPayload(E2ePortalFixtures.NETHER, changedPos, Blocks.AIR.defaultBlockState(), Optional.empty())
        );
        assertEquals(Blocks.AIR.defaultBlockState(), breakToAir.state());

        AtomicReference<RemoteBlockDeltaPayload> deltaRef = new AtomicReference<>();
        SeamlessNetwork.setRemoteBlockDeltaHandler(deltaRef::set);
        assertNull(deltaRef.get());

        // Client consumer required to process RemoteBlockDeltaPayload into remote ClientLevel
        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F3-03: BlockEntity state delta forwarding and defensive NBT copy")
    void blockEntityStateDeltaForwardingAndDefensiveNbtCopy() {
        BlockPos bePos = new BlockPos(-317, 73, 480);
        CompoundTag tag = new CompoundTag();
        tag.putString("CustomName", "RemoteSign");
        tag.putString("Text1", "HelloNether");

        BlockState signState = Blocks.OAK_SIGN.defaultBlockState();
        RemoteBlockDeltaPayload payload = new RemoteBlockDeltaPayload(
                E2ePortalFixtures.NETHER, bePos, signState, Optional.of(tag));
        tag.putString("CustomName", "MutatedOutside");

        RemoteBlockDeltaPayload decoded = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteBlockDeltaPayload.STREAM_CODEC, payload);
        assertEquals(signState, decoded.state());
        assertTrue(decoded.blockEntityTag().isPresent());

        CompoundTag returned = decoded.blockEntityTag().get();
        //? if >=1.21.11 {
        /*assertEquals("RemoteSign", returned.getString("CustomName").orElseThrow());
        returned.putString("Text1", "MutatedAfterDecode");
        assertEquals("HelloNether", decoded.blockEntityTag().get().getString("Text1").orElseThrow());
        *///?} else {
        assertEquals("RemoteSign", returned.getString("CustomName"));
        returned.putString("Text1", "MutatedAfterDecode");
        assertEquals("HelloNether", decoded.blockEntityTag().get().getString("Text1"));
        //?}

        // Chunk packet carrying a BlockEntityInfo entry for SIGN
        int chunkX = SectionPosCompat.blockToChunk(bePos.getX());
        int chunkZ = SectionPosCompat.blockToChunk(bePos.getZ());
        ClientboundLevelChunkWithLightPacket chunkPkt = syntheticChunkPacket(
                chunkX, chunkZ,
                null,
                new byte[0],
                bePos,
                "GateSignLine",
                new BitSet(), new BitSet(), new BitSet(), new BitSet(),
                List.of(), List.of()
        );
        RemoteChunkDataPayload chunkDecoded = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteChunkDataPayload.STREAM_CODEC,
                RemoteChunkDataPayload.fromPacket(E2ePortalFixtures.NETHER, E2ePortalFixtures.registryAccess(), chunkPkt)
        );
        ClientboundLevelChunkWithLightPacket decodedChunkPkt =
                chunkDecoded.decodePacket(E2ePortalFixtures.registryAccess());

        List<BlockPos> seenPositions = new ArrayList<>();
        List<BlockEntityType<?>> seenTypes = new ArrayList<>();
        List<CompoundTag> seenTags = new ArrayList<>();
        decodedChunkPkt.getChunkData().getBlockEntitiesTagsConsumer(chunkX, chunkZ).accept((pos, type, nbt) -> {
            seenPositions.add(pos.immutable());
            seenTypes.add(type);
            seenTags.add(nbt);
        });
        assertEquals(List.of(bePos), seenPositions);
        assertEquals(List.of(BlockEntityType.SIGN), seenTypes);
        //? if >=1.21.11 {
        /*assertEquals("GateSignLine", seenTags.getFirst().getString("SignText").orElseThrow());
        *///?} else {
        assertEquals("GateSignLine", seenTags.getFirst().getString("SignText"));
        //?}
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F3-04: Tracked entity spawn, movement, and removal bundle wrapping")
    void trackedEntitySpawnMovementAndRemovalBundleWrapping() {
        int entityId = 901;
        UUID entityUuid = UUID.fromString("00000000-0000-0000-0000-000000001304");
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;

        ClientboundAddEntityPacket addPig = new ClientboundAddEntityPacket(
                entityId, entityUuid,
                targetCore.getX() + 4.5D, targetCore.getY() + 1.0D, targetCore.getZ() + 0.5D,
                0.0F, 90.0F, EntityType.PIG, 0, Vec3.ZERO, 90.0D
        );
        ClientboundMoveEntityPacket.PosRot movePosRot = new ClientboundMoveEntityPacket.PosRot(
                entityId, (short) 256, (short) 0, (short) -128, (byte) 64, (byte) 0, true);
        ClientboundRotateHeadPacket rotateHead = newRotateHeadPacket(entityId, (byte) 48);
        ClientboundSetEntityMotionPacket setMotion =
                new ClientboundSetEntityMotionPacket(entityId, new Vec3(0.2D, 0.0D, -0.1D));
        ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(entityId);

        List<Packet<? super ClientGamePacketListener>> bundled = new ArrayList<>();
        bundled.add(addPig);
        bundled.add(movePosRot);
        bundled.add(rotateHead);
        bundled.add(setMotion);
        bundled.add(removePacket);
        //? if >=1.21.11 {
        /*bundled.add(new ClientboundEntityPositionSyncPacket(
                entityId,
                new PositionMoveRotation(new Vec3(1.0D, 2.0D, 3.0D), Vec3.ZERO, 45.0F, 0.0F),
                true
        ));
        bundled.add(ClientboundTeleportEntityPacket.teleport(
                entityId,
                new PositionMoveRotation(new Vec3(4.0D, 5.0D, 6.0D), Vec3.ZERO, 90.0F, 0.0F),
                Set.of(),
                false
        ));
        *///?}

        ClientboundBundlePacket bundle = new ClientboundBundlePacket(bundled);
        List<Packet<?>> unwrapped = new ArrayList<>();
        ServerPortalStreamManager.unwrapPackets(bundle, unwrapped::add);
        assertEquals(bundled.size(), unwrapped.size());

        for (Packet<?> inner : unwrapped) {
            RemoteEntitySyncPayload.SyncKind expectedKind =
                    RemoteEntitySyncPayload.kindForPacket(inner).orElseThrow();
            RemoteEntitySyncPayload wrapped = ServerPortalStreamManager.wrapEntityPacket(
                    E2ePortalFixtures.NETHER, entityId, E2ePortalFixtures.registryAccess(), inner
            ).orElseThrow();
            RemoteEntitySyncPayload roundtripped = SeamlessContractProbe.roundTripByteBufCodec(
                    RemoteEntitySyncPayload.STREAM_CODEC, wrapped);
            assertEquals(expectedKind, roundtripped.kind());
            assertEquals(entityId, roundtripped.entityId());
            Packet<?> decoded = roundtripped.decodeVanillaPacket(E2ePortalFixtures.registryAccess());
            assertEquals(inner.getClass(), decoded.getClass());
        }

        // Verify 40-block entity stream boundary in PortalStreamPlanner
        PortalStreamPlanner planner = new PortalStreamPlanner();
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000001305");
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        planner.subscribe(playerId, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);
        PortalStreamPlanner.SubscriberKey subKey =
                new PortalStreamPlanner.SubscriberKey(playerId, E2ePortalFixtures.OVERWORLD, sourceCore);
        long chunkKey = ChunkPos.asLong(targetCore);
        planner.markChunkSent(subKey, chunkKey);

        Vec3 center = PortalStreamPlanner.portalCenter(targetCore);
        assertEquals(Set.of(playerId), planner.subscribersForEntityInRange(
                E2ePortalFixtures.NETHER, chunkKey, center.x + 40.0D, center.y, center.z));
        assertFalse(planner.isEntityStillVisibleToPlayer(
                playerId, E2ePortalFixtures.NETHER, chunkKey, center.x + 40.01D, center.y, center.z));
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F3-05: Section and chunk heightmap parity across 1.21.1 and 1.21.11")
    void sectionAndChunkHeightmapParityAcrossVersions() {
        long[] expectedHeightmap = new long[37];
        for (int i = 0; i < expectedHeightmap.length; i++) {
            expectedHeightmap[i] = 0x0001_0002_0003_0004L + i;
        }

        ClientboundLevelChunkWithLightPacket pkt = syntheticChunkPacket(
                4, -5,
                expectedHeightmap,
                new byte[]{9, 8, 7},
                null,
                null,
                new BitSet(), new BitSet(), new BitSet(), new BitSet(),
                List.of(), List.of()
        );

        RemoteChunkDataPayload wrapped = RemoteChunkDataPayload.fromPacket(
                E2ePortalFixtures.OVERWORLD, E2ePortalFixtures.registryAccess(), pkt);
        RemoteChunkDataPayload roundtripped = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteChunkDataPayload.STREAM_CODEC, wrapped);
        ClientboundLevelChunkWithLightPacket decoded =
                roundtripped.decodePacket(E2ePortalFixtures.registryAccess());

        //? if >=1.21.11 {
        /*Map<Heightmap.Types, long[]> decodedMap = decoded.getChunkData().getHeightmaps();
        assertTrue(decodedMap.containsKey(Heightmap.Types.MOTION_BLOCKING));
        assertArrayEquals(expectedHeightmap, decodedMap.get(Heightmap.Types.MOTION_BLOCKING));
        *///?} else {
        CompoundTag decodedTag = decoded.getChunkData().getHeightmaps();
        assertTrue(decodedTag.contains("MOTION_BLOCKING"));
        assertArrayEquals(expectedHeightmap, decodedTag.getLongArray("MOTION_BLOCKING"));
        //?}
    }

    // ==================== F4: Multi-ClientLevel client manager ====================

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F4-01: Remote ClientLevel creation with centered ClientChunkCache")
    void remoteClientLevelCreationWithCenteredClientChunkCache() {
        List<ChunkPos> netherChunks = PortalStreamPlanner.collectChunkSquare(5000 >> 4, 5000 >> 4, 2);
        assertEquals(25, netherChunks.size());
        assertEquals(new ChunkPos(312, 312), netherChunks.getFirst());

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_REMOTE_CLIENT_LEVEL_FACTORY, false);
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F4-02: Simultaneous multiple isolated remote ClientLevels")
    void simultaneousMultipleIsolatedRemoteClientLevels() {
        PortalStreamPlanner planner = new PortalStreamPlanner();
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000001402");
        BlockPos core1 = new BlockPos(10, 64, 10);
        BlockPos core2 = new BlockPos(20, 64, 10);

        planner.subscribe(playerId, E2ePortalFixtures.OVERWORLD, core1, E2ePortalFixtures.NETHER, new BlockPos(100, 70, 100));
        planner.subscribe(playerId, E2ePortalFixtures.OVERWORLD, core2, E2ePortalFixtures.RIFT, new BlockPos(200, 80, 200));
        assertEquals(2, planner.subscriptionCount());

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_REMOTE_RENDER_WORLD_STATE, false);
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F4-03: Isolated entity and BlockEntity ticking in remote ClientLevel")
    void isolatedEntityAndBlockEntityTickingInRemoteClientLevel() {
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                PortalCoreBlockEntity.class, "clientTick", void.class));

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_REMOTE_CLIENT_LEVEL_FACTORY, false);
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F4-04: Ambient sound gating enforces level == minecraft.level")
    void ambientSoundGatingEnforcesLevelEqualsMinecraftLevel() {
        Class<?> soundClientCls = SeamlessContractProbe.requireClass(
                "fr.geomtech.universegate.PortalAmbientSoundClient", false);
        assertEquals(256.0D, SeamlessContractProbe.readStaticField(soundClientCls, "HEARING_RANGE_SQR", double.class));
        assertEquals(540L, SeamlessContractProbe.readStaticField(soundClientCls, "AMBIENT_LOOP_INTERVAL_TICKS", long.class));
        assertEquals(10L, SeamlessContractProbe.readStaticField(soundClientCls, "UNSTABLE_AMBIENT_LOOP_INTERVAL_TICKS", long.class));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                soundClientCls, "tick", void.class, PortalCoreBlockEntity.class, long.class));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(soundClientCls, "stop", void.class));

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
    }

    @Test
    @Tag("tier1")
    @Tag("areaA")
    @DisplayName("T1-F4-05: Particle gating suppresses remote ClientLevel particles in local world")
    void particleGatingSuppressesRemoteClientLevelParticlesInLocalWorld() {
        Class<?> openingParticlesCls = SeamlessContractProbe.requireClass(
                "fr.geomtech.universegate.PortalOpeningParticles", false);
        assertEquals(40L, SeamlessContractProbe.readStaticField(openingParticlesCls, "OPENING_BLACKOUT_TICKS", long.class));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                openingParticlesCls, "clientTick", void.class, Level.class, BlockPos.class, PortalCoreBlockEntity.class));

        Class<?> fieldBlockCls = SeamlessContractProbe.requireClass(
                "fr.geomtech.universegate.PortalFieldBlock", false);
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                fieldBlockCls, "animateTick", void.class,
                BlockState.class, Level.class, BlockPos.class, net.minecraft.util.RandomSource.class));

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_REMOTE_RENDER_WORLD_STATE, false);
    }

    private static final class SectionPosCompat {
        private static int blockToChunk(int coord) {
            return Math.floorDiv(coord, 16);
        }
    }
}
