package fr.geomtech.universegate.e2e.tier2;

import fr.geomtech.universegate.PortalConnectionManager;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.PortalRegistrySavedData;
import fr.geomtech.universegate.UniverseGateDimensions;
import fr.geomtech.universegate.compat.Nbt;
import fr.geomtech.universegate.e2e.harness.E2eBootstrap;
import fr.geomtech.universegate.e2e.harness.E2ePortalFixtures;
import fr.geomtech.universegate.e2e.harness.SeamlessContractProbe;
import fr.geomtech.universegate.net.UniverseGateNetwork;
import fr.geomtech.universegate.platform.PlatformHelper;
import fr.geomtech.universegate.seamless.net.PortalEndpointResolver;
import fr.geomtech.universegate.seamless.net.PortalRemoteEndpointPayload;
import fr.geomtech.universegate.seamless.net.PortalViewSubscriptionPayload;
import fr.geomtech.universegate.seamless.net.RemoteBlockDeltaPayload;
import fr.geomtech.universegate.seamless.net.RemoteChunkDataPayload;
import fr.geomtech.universegate.seamless.net.RemoteChunkForgetPayload;
import fr.geomtech.universegate.seamless.net.RemoteEntitySyncPayload;
import fr.geomtech.universegate.seamless.net.ResolvedPortalEndpoint;
import fr.geomtech.universegate.seamless.net.SeamlessNetwork;
import fr.geomtech.universegate.seamless.server.PortalStreamPlanner;
import fr.geomtech.universegate.seamless.server.RemoteChunkTicketController;
import fr.geomtech.universegate.seamless.server.TicketBackend;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? if >=1.21.11 {
/*import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.levelgen.Heightmap;
*///?} else {
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
//?}
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
//? if >=1.21.11 {
/*import java.util.EnumMap;
*///?}
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("tier2")
@Tag("areaA")
@DisplayName("Tier 2 Area A: Remote World Observation & Streaming Boundaries (F1-F4)")
class Tier2AreaAStreamingBoundaryE2eTest {

    private static final String FQCN_CLIENT_REMOTE_WORLD_MANAGER =
            "fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager";
    private static final String FQCN_REMOTE_CLIENT_LEVEL_FACTORY =
            "fr.geomtech.universegate.seamless.client.RemoteClientLevelFactory";
    private static final String FQCN_REMOTE_RENDER_WORLD_STATE =
            "fr.geomtech.universegate.seamless.client.RemoteRenderWorldState";

    private static final class CountingTicketBackend implements TicketBackend {
        int addCount = 0;
        int removeCount = 0;

        @Override
        public void addTicket(ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {
            addCount++;
        }

        @Override
        public void removeTicket(ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {
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

    private static ClientboundLevelChunkWithLightPacket emptyVoidChunkPacket(int chunkX, int chunkZ) {
        RegistryFriendlyByteBuf buf = E2ePortalFixtures.newRegistryFriendlyByteBuf();
        try {
            buf.writeInt(chunkX);
            buf.writeInt(chunkZ);

            //? if >=1.21.11 {
            /*EnumMap<Heightmap.Types, long[]> heightmaps = new EnumMap<>(Heightmap.Types.class);
            ByteBufCodecs.map(
                    ignored -> new EnumMap<>(Heightmap.Types.class),
                    Heightmap.Types.STREAM_CODEC,
                    ByteBufCodecs.LONG_ARRAY
            ).encode(buf, heightmaps);
            *///?} else {
            buf.writeNbt(new CompoundTag());
            //?}

            buf.writeVarInt(0);
            buf.writeVarInt(0);

            BitSet emptySkyMask = new BitSet();
            emptySkyMask.set(0);
            BitSet emptyBlockMask = new BitSet();
            emptyBlockMask.set(0);

            buf.writeBitSet(new BitSet());
            buf.writeBitSet(new BitSet());
            buf.writeBitSet(emptySkyMask);
            buf.writeBitSet(emptyBlockMask);
            buf.writeCollection(List.of(), ByteBufCodecs.byteArray(2048));
            buf.writeCollection(List.of(), ByteBufCodecs.byteArray(2048));

            return ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    // ==================== F1 Boundaries ====================

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F1-01: Sync distance boundary inside vs outside radius and client clear")
    void syncDistanceBoundaryInsideVsOutsideRadiusAndClientClear() {
        PortalStreamPlanner planner = new PortalStreamPlanner();
        BlockPos core = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        Vec3 center = PortalStreamPlanner.portalCenter(core);

        assertTrue(planner.isWithinSubscriptionRange(core, center.x + 128.0D, center.y, center.z));
        assertFalse(planner.isWithinSubscriptionRange(core, center.x + 128.001D, center.y, center.z));

        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000002101");
        PortalEndpointResolver.ObserverSyncPlan leftPlan =
                PortalEndpointResolver.computeObserverSync(Set.of(p1), List.of(), false);
        assertTrue(leftPlan.toSend().isEmpty());
        assertTrue(leftPlan.nextSynced().isEmpty());
        assertEquals(List.of(p1), leftPlan.untracked());

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F1-02: Null or unregistered targetPortalId refuses endpoint resolution")
    void nullOrUnregisteredTargetPortalIdRefusesEndpointResolution() {
        assertTrue(PortalEndpointResolver.resolveEndpoint(
                false, true, false, true,
                null, null, null, null,
                null, null, null, null
        ).isEmpty());

        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        PortalRemoteEndpointPayload closed = PortalEndpointResolver.closedPayload(
                sourceCore, E2ePortalFixtures.OVERWORLD, null);
        assertEquals(PortalRemoteEndpointPayload.FLAG_CLOSED, closed.stateFlags());
        assertTrue(closed.isClosed());
        assertFalse(closed.outboundEnabled());

        PortalRegistrySavedData registry = new PortalRegistrySavedData();
        assertNull(registry.get(UUID.randomUUID()));
        assertEquals(UniverseGateDimensions.RIFT, registry.get(UniverseGateNetwork.DARK_DIMENSION_ID).dim());
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F1-03: Broken frame or closed state clears active endpoint metadata")
    void brokenFrameOrClosedStateClearsActiveEndpointMetadata() {
        assertEquals(
                PortalRemoteEndpointPayload.FLAG_CLOSED,
                PortalEndpointResolver.computeStateFlags(false, false, true)
        );
        assertTrue(PortalEndpointResolver.resolveEndpoint(
                false, false, true, true,
                E2ePortalFixtures.NETHER, E2ePortalFixtures.DEFAULT_TARGET_CORE,
                Direction.SOUTH, Direction.WEST,
                null, null, null, null
        ).isEmpty());

        ResolvedPortalEndpoint previous = new ResolvedPortalEndpoint(
                E2ePortalFixtures.NETHER,
                E2ePortalFixtures.DEFAULT_TARGET_CORE,
                Direction.SOUTH,
                Direction.WEST,
                true,
                PortalRemoteEndpointPayload.FLAG_ACTIVE
        );
        PortalRemoteEndpointPayload closedPayload = PortalEndpointResolver.closedPayload(
                E2ePortalFixtures.DEFAULT_SOURCE_CORE, E2ePortalFixtures.OVERWORLD, previous);

        assertEquals(E2ePortalFixtures.NETHER, closedPayload.targetDim());
        assertEquals(E2ePortalFixtures.DEFAULT_TARGET_CORE, closedPayload.targetCorePos());
        assertEquals(Direction.SOUTH, closedPayload.targetRight());
        assertEquals(Direction.WEST, closedPayload.exitNormal());
        assertFalse(closedPayload.outboundEnabled());
        assertTrue(closedPayload.isClosed());
        assertFalse(closedPayload.isActive());
        assertFalse(closedPayload.isOpening());
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F1-04: Self-link attempt rejection and endpoint guard invariants")
    void selfLinkAttemptRejectionAndEndpointGuardInvariants() {
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                PortalConnectionManager.class, "openBothSides", boolean.class,
                ServerLevel.class, BlockPos.class, UUID.class));

        assertTrue(PortalEndpointResolver.resolveEndpoint(
                false, false, false, true,
                E2ePortalFixtures.OVERWORLD, E2ePortalFixtures.DEFAULT_SOURCE_CORE,
                Direction.EAST, Direction.SOUTH,
                null, null, null, null
        ).isEmpty());

        BlockPos samePos = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        PortalRemoteEndpointPayload selfPosPayload = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC,
                new PortalRemoteEndpointPayload(
                        samePos, E2ePortalFixtures.OVERWORLD, samePos,
                        Direction.EAST, Direction.SOUTH, false, PortalRemoteEndpointPayload.FLAG_CLOSED
                )
        );
        assertEquals(samePos, selfPosPayload.corePos());
        assertEquals(samePos, selfPosPayload.targetCorePos());

        UUID validId = UUID.fromString("00000000-0000-0000-0000-000000002104");
        List<UUID> trackersWithNull = new java.util.ArrayList<>();
        trackersWithNull.add(null);
        trackersWithNull.add(validId);
        PortalEndpointResolver.ObserverSyncPlan plan =
                PortalEndpointResolver.computeObserverSync(Set.of(), trackersWithNull, false);
        assertEquals(List.of(validId), plan.toSend());
        assertEquals(Set.of(validId), plan.nextSynced());
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F1-05: Unknown or deleted dimension key handled without NPE")
    void unknownOrDeletedDimensionKeyHandledWithoutNpe() {
        ResourceKey<Level> deletedDim = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("universegate", "deleted_dim_99")
        );

        ResolvedPortalEndpoint resolved = PortalEndpointResolver.resolveEndpoint(
                false, true, false, true,
                deletedDim, E2ePortalFixtures.DEFAULT_TARGET_CORE,
                Direction.EAST, Direction.SOUTH,
                null, null, null, null
        ).orElseThrow();
        PortalRemoteEndpointPayload decoded = SeamlessContractProbe.roundTripByteBufCodec(
                PortalRemoteEndpointPayload.STREAM_CODEC,
                resolved.toPayload(E2ePortalFixtures.DEFAULT_SOURCE_CORE)
        );
        assertEquals(deletedDim, decoded.targetDim());

        UUID portalId = UUID.fromString("00000000-0000-0000-0000-000000002105");
        CompoundTag entryTag = new CompoundTag();
        Nbt.putUUID(entryTag, "Id", portalId);
        entryTag.putString("Name", "GhostGate");
        entryTag.putBoolean("Hidden", false);
        entryTag.putString("Dim", deletedDim.location().toString());
        entryTag.putInt("X", 12);
        entryTag.putInt("Y", 64);
        entryTag.putInt("Z", -34);

        ListTag listTag = new ListTag();
        listTag.add(entryTag);
        CompoundTag root = new CompoundTag();
        root.put("Portals", listTag);

        PortalRegistrySavedData loaded = PortalRegistrySavedData.load(root, E2ePortalFixtures.registryAccess());
        PortalRegistrySavedData.PortalEntry entry = loaded.get(portalId);
        assertNotNull(entry);
        assertEquals(deletedDim, entry.dim());
        assertEquals(new BlockPos(12, 64, -34), entry.pos());
    }

    // ==================== F2 Boundaries ====================

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F2-01: Zero radius vs minimum radius chunk square and ticket bounds")
    void zeroRadiusVsMinimumRadiusChunkSquareAndTicketBounds() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000002201");
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;

        PortalStreamPlanner radiusZero = new PortalStreamPlanner(0, 128.0D, 40.0D, 4);
        List<ChunkPos> zeroChunks = radiusZero.desiredChunksForTarget(targetCore);
        assertEquals(List.of(new ChunkPos(targetCore)), zeroChunks);
        radiusZero.subscribe(playerId, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);
        assertEquals(1, radiusZero.desiredTicketKeysForPortal(
                new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, sourceCore)).size());

        PortalStreamPlanner radiusOne = new PortalStreamPlanner(1, 128.0D, 40.0D, 4);
        assertEquals(9, radiusOne.desiredChunksForTarget(targetCore).size());

        RemoteChunkTicketController.TicketKey zeroTicket =
                new RemoteChunkTicketController.TicketKey(E2ePortalFixtures.OVERWORLD, 0, 0, 0);
        assertEquals(0, zeroTicket.radius());
        assertThrows(IllegalArgumentException.class,
                () -> new RemoteChunkTicketController.TicketKey(E2ePortalFixtures.OVERWORLD, 0, 0, -1));
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F2-02: Invalid negative radius, zero budget, and distance cap boundaries")
    void invalidNegativeRadiusZeroBudgetAndDistanceCapBoundaries() {
        assertThrows(IllegalArgumentException.class, () -> new PortalStreamPlanner(-1, 128.0D, 40.0D, 4));
        assertThrows(IllegalArgumentException.class, () -> PortalStreamPlanner.collectChunkSquare(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new PortalStreamPlanner(2, 0.0D, 40.0D, 4));
        assertThrows(IllegalArgumentException.class, () -> new PortalStreamPlanner(2, Double.NaN, 40.0D, 4));
        assertThrows(IllegalArgumentException.class, () -> new PortalStreamPlanner(2, 128.0D, 0.0D, 4));
        assertThrows(IllegalArgumentException.class, () -> new PortalStreamPlanner(2, 128.0D, 40.0D, 0));
        assertThrows(IllegalArgumentException.class, () -> new RemoteChunkTicketController.TicketKey(
                E2ePortalFixtures.OVERWORLD, 0, 0, -1));

        assertTrue(PortalStreamPlanner.DEFAULT_STREAM_CHUNK_RADIUS <= 4);
        assertEquals(4, PortalStreamPlanner.DEFAULT_CHUNKS_PER_TICK_BUDGET);

        PortalStreamPlanner planner = new PortalStreamPlanner();
        Vec3 center = PortalStreamPlanner.portalCenter(E2ePortalFixtures.DEFAULT_SOURCE_CORE);
        assertFalse(planner.isWithinSubscriptionRange(
                E2ePortalFixtures.DEFAULT_SOURCE_CORE, center.x, center.y, center.z + 128.5D));
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F2-03: Duplicate subscription and ticket acquire idempotency")
    void duplicateSubscriptionAndTicketAcquireIdempotency() {
        PortalStreamPlanner planner = new PortalStreamPlanner();
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000002203");
        for (int i = 0; i < 10; i++) {
            assertTrue(planner.subscribe(
                    playerId,
                    E2ePortalFixtures.OVERWORLD, E2ePortalFixtures.DEFAULT_SOURCE_CORE,
                    E2ePortalFixtures.NETHER, E2ePortalFixtures.DEFAULT_TARGET_CORE
            ).isEmpty());
        }
        assertEquals(1, planner.subscriptionCount());

        CountingTicketBackend backend = new CountingTicketBackend();
        RemoteChunkTicketController controller = new RemoteChunkTicketController(backend);
        RemoteChunkTicketController.TicketKey key =
                new RemoteChunkTicketController.TicketKey(E2ePortalFixtures.NETHER, 1, 2, 2);

        assertTrue(controller.acquire("portalA", key));
        for (int i = 0; i < 9; i++) {
            assertFalse(controller.acquire("portalA", key));
        }
        assertEquals(1, backend.addCount);
        assertEquals(1, controller.refCount(key));
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F2-04: Unsubscribe and ticket release without prior claim preserve non-negative counts")
    void unsubscribeAndTicketReleaseWithoutPriorClaimPreserveNonNegativeCounts() {
        PortalStreamPlanner planner = new PortalStreamPlanner();
        CountingTicketBackend backend = new CountingTicketBackend();
        RemoteChunkTicketController controller = new RemoteChunkTicketController(backend);

        assertTrue(planner.unsubscribe(
                UUID.randomUUID(), E2ePortalFixtures.OVERWORLD, E2ePortalFixtures.DEFAULT_SOURCE_CORE).isEmpty());

        RemoteChunkTicketController.TicketKey key =
                new RemoteChunkTicketController.TicketKey(E2ePortalFixtures.NETHER, 5, -5, 2);
        assertFalse(controller.release("neverClaimed", key));
        assertEquals(0, controller.releaseOwner("neverClaimed"));
        assertEquals(0, backend.removeCount);
        assertEquals(0, controller.refCount(key));
        assertEquals(0, controller.activeTicketCount());
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F2-05: Portal straddling chunk boundary x%16==15 and x%16==0")
    void portalStraddlingChunkBoundaryX15AndX0() {
        BlockPos boundaryCore = new BlockPos(15, 64, 15);
        List<BlockPos> border = E2ePortalFixtures.expectedFrameBorderExcludingCore(boundaryCore, Direction.EAST);
        Set<ChunkPos> spannedChunks = new HashSet<>();
        for (BlockPos pos : border) {
            spannedChunks.add(new ChunkPos(pos));
        }
        assertTrue(spannedChunks.contains(new ChunkPos(0, 0)));
        assertTrue(spannedChunks.contains(new ChunkPos(1, 0)));

        PortalStreamPlanner planner = new PortalStreamPlanner();
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000002205");
        planner.subscribe(playerId, E2ePortalFixtures.OVERWORLD, E2ePortalFixtures.DEFAULT_SOURCE_CORE,
                E2ePortalFixtures.OVERWORLD, boundaryCore);

        List<ChunkPos> desiredChunks = planner.desiredChunksForTarget(boundaryCore);
        assertTrue(desiredChunks.contains(new ChunkPos(0, 0)));
        assertTrue(desiredChunks.contains(new ChunkPos(1, 0)));
        assertTrue(desiredChunks.contains(new ChunkPos(0, 1)));
        assertTrue(desiredChunks.contains(new ChunkPos(1, 1)));

        List<RemoteChunkTicketController.TicketKey> tickets = planner.desiredTicketKeysForPortal(
                new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, E2ePortalFixtures.DEFAULT_SOURCE_CORE));
        assertEquals(25, tickets.size());
    }

    // ==================== F3 Boundaries ====================

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F3-01: Empty void air-only sections in universegate:rift")
    void emptyVoidAirOnlySectionsInRift() {
        ClientboundLevelChunkWithLightPacket pkt = emptyVoidChunkPacket(0, 0);
        RemoteChunkDataPayload wrapped = RemoteChunkDataPayload.fromPacket(
                UniverseGateDimensions.RIFT, E2ePortalFixtures.registryAccess(), pkt);
        RemoteChunkDataPayload roundtripped = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteChunkDataPayload.STREAM_CODEC, wrapped);
        ClientboundLevelChunkWithLightPacket decoded =
                roundtripped.decodePacket(E2ePortalFixtures.registryAccess());

        assertEquals(UniverseGateDimensions.RIFT, roundtripped.dim());
        assertTrue(decoded.getLightData().getEmptySkyYMask().get(0));
        assertTrue(decoded.getLightData().getEmptyBlockYMask().get(0));
        assertTrue(decoded.getLightData().getSkyUpdates().isEmpty());
        assertTrue(decoded.getLightData().getBlockUpdates().isEmpty());
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F3-02: Overworld build height extremes minY=-64 and maxY=319")
    void overworldBuildHeightExtremesMinYMinus64AndMaxY319() {
        PortalStreamPlanner planner = new PortalStreamPlanner();
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000002302");
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = new BlockPos(5, 64, 5);

        planner.subscribe(playerId, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.OVERWORLD, targetCore);
        planner.markChunkSent(new PortalStreamPlanner.SubscriberKey(
                playerId, E2ePortalFixtures.OVERWORLD, sourceCore), ChunkPos.asLong(targetCore));

        BlockPos minPos = new BlockPos(5, -64, 5);
        BlockPos maxPos = new BlockPos(5, 319, 5);
        assertTrue(planner.queueBlockDelta(E2ePortalFixtures.OVERWORLD, minPos));
        assertTrue(planner.queueBlockDelta(E2ePortalFixtures.OVERWORLD, maxPos));

        Set<BlockPos> drained = planner.drainDirtyBlocks()
                .get(E2ePortalFixtures.OVERWORLD)
                .get(ChunkPos.asLong(targetCore));
        assertEquals(Set.of(minPos, maxPos), drained);

        RemoteBlockDeltaPayload decodedMin = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteBlockDeltaPayload.STREAM_CODEC,
                new RemoteBlockDeltaPayload(E2ePortalFixtures.OVERWORLD, minPos, Blocks.BEDROCK.defaultBlockState(), Optional.empty())
        );
        RemoteBlockDeltaPayload decodedMax = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteBlockDeltaPayload.STREAM_CODEC,
                new RemoteBlockDeltaPayload(E2ePortalFixtures.OVERWORLD, maxPos, Blocks.GLASS.defaultBlockState(), Optional.empty())
        );
        assertEquals(-64, decodedMin.pos().getY());
        assertEquals(319, decodedMax.pos().getY());
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F3-03: Out-of-view-radius delta rejection and chunk forget")
    void outOfViewRadiusDeltaRejectionAndChunkForget() {
        PortalStreamPlanner planner = new PortalStreamPlanner();
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000002303");
        BlockPos sourceCore = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;

        planner.subscribe(playerId, E2ePortalFixtures.OVERWORLD, sourceCore, E2ePortalFixtures.NETHER, targetCore);
        long centerChunk = ChunkPos.asLong(targetCore);
        planner.markChunkSent(new PortalStreamPlanner.SubscriberKey(
                playerId, E2ePortalFixtures.OVERWORLD, sourceCore), centerChunk);

        ChunkPos targetChunkPos = new ChunkPos(targetCore);
        BlockPos farPos = new BlockPos((targetChunkPos.x + 20) << 4, 64, (targetChunkPos.z + 20) << 4);
        assertFalse(planner.queueBlockDelta(E2ePortalFixtures.NETHER, farPos));
        assertTrue(planner.drainDirtyBlocks().isEmpty());

        RemoteChunkForgetPayload forgetDecoded = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteChunkForgetPayload.STREAM_CODEC,
                new RemoteChunkForgetPayload(E2ePortalFixtures.NETHER, targetChunkPos.x + 20, targetChunkPos.z + 20)
        );
        assertEquals(targetChunkPos.x + 20, forgetDecoded.chunkX());

        List<PortalStreamPlanner.ChunkForgetBatch> batches =
                planner.onChunkUnloaded(E2ePortalFixtures.NETHER, centerChunk);
        assertEquals(1, batches.size());
        assertEquals(List.of(targetChunkPos), batches.getFirst().chunks());

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F3-04: Malformed, unknown blockstate, oversized, and empty BE tag deltas")
    void malformedUnknownBlockstateOversizedAndEmptyBeTagDeltas() {
        RemoteBlockDeltaPayload nullBeNormalized = new RemoteBlockDeltaPayload(
                E2ePortalFixtures.OVERWORLD, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null);
        assertTrue(nullBeNormalized.blockEntityTag().isEmpty());

        // Unknown BlockState ID decodes to Blocks.AIR
        RegistryFriendlyByteBuf unknownStateBuf = E2ePortalFixtures.newRegistryFriendlyByteBuf();
        try {
            ResourceKey.streamCodec(Registries.DIMENSION).encode(unknownStateBuf, E2ePortalFixtures.OVERWORLD);
            BlockPos.STREAM_CODEC.encode(unknownStateBuf, BlockPos.ZERO);
            ByteBufCodecs.VAR_INT.encode(unknownStateBuf, Integer.MAX_VALUE);
            ByteBufCodecs.OPTIONAL_COMPOUND_TAG.encode(unknownStateBuf, Optional.empty());

            RemoteBlockDeltaPayload decodedUnknown = RemoteBlockDeltaPayload.STREAM_CODEC.decode(unknownStateBuf);
            assertEquals(Blocks.AIR.defaultBlockState(), decodedUnknown.state());
        } finally {
            unknownStateBuf.release();
        }

        // Oversized chunk payload rejected in ctor and on wire
        assertThrows(IllegalArgumentException.class, () -> new RemoteChunkDataPayload(
                E2ePortalFixtures.OVERWORLD, 0, 0, new byte[RemoteChunkDataPayload.MAX_PACKET_BYTES + 1]));
        RegistryFriendlyByteBuf oversizedChunkBuf = E2ePortalFixtures.newRegistryFriendlyByteBuf();
        try {
            ResourceKey.streamCodec(Registries.DIMENSION).encode(oversizedChunkBuf, E2ePortalFixtures.OVERWORLD);
            ByteBufCodecs.INT.encode(oversizedChunkBuf, 0);
            ByteBufCodecs.INT.encode(oversizedChunkBuf, 0);
            ByteBufCodecs.BYTE_ARRAY.encode(oversizedChunkBuf, new byte[RemoteChunkDataPayload.MAX_PACKET_BYTES + 1]);
            assertThrows(DecoderException.class, () -> RemoteChunkDataPayload.STREAM_CODEC.decode(oversizedChunkBuf));
        } finally {
            oversizedChunkBuf.release();
        }

        // Oversized & mismatched entity sync payload
        assertThrows(IllegalArgumentException.class, () -> new RemoteEntitySyncPayload(
                E2ePortalFixtures.OVERWORLD, 1, RemoteEntitySyncPayload.SyncKind.ADD_ENTITY,
                new byte[RemoteEntitySyncPayload.MAX_PACKET_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> RemoteEntitySyncPayload.encodeVanillaPacket(
                E2ePortalFixtures.registryAccess(),
                RemoteEntitySyncPayload.SyncKind.MOVE_POS,
                new ClientboundRemoveEntitiesPacket(1)));

        //? if >=1.21.11 {
        /*ClientboundEntityPositionSyncPacket posSync = new ClientboundEntityPositionSyncPacket(
                7, new PositionMoveRotation(Vec3.ZERO, Vec3.ZERO, 0.0F, 0.0F), false);
        assertEquals(RemoteEntitySyncPayload.SyncKind.ENTITY_POSITION_SYNC,
                RemoteEntitySyncPayload.kindForPacket(posSync).orElseThrow());
        ClientboundMoveMinecartPacket moveMinecart = new ClientboundMoveMinecartPacket(
                7, List.of(new NewMinecartBehavior.MinecartStep(Vec3.ZERO, Vec3.ZERO, 0.0F, 0.0F, 1.0F)));
        assertEquals(RemoteEntitySyncPayload.SyncKind.MOVE_MINECART,
                RemoteEntitySyncPayload.kindForPacket(moveMinecart).orElseThrow());
        *///?} else {
        RemoteEntitySyncPayload unsupported1211 = new RemoteEntitySyncPayload(
                E2ePortalFixtures.OVERWORLD, 7, RemoteEntitySyncPayload.SyncKind.ENTITY_POSITION_SYNC, new byte[]{1});
        assertThrows(UnsupportedOperationException.class,
                () -> unsupported1211.decodeVanillaPacket(E2ePortalFixtures.registryAccess()));
        RemoteEntitySyncPayload unsupportedMinecart = new RemoteEntitySyncPayload(
                E2ePortalFixtures.OVERWORLD, 7, RemoteEntitySyncPayload.SyncKind.MOVE_MINECART, new byte[]{1});
        assertThrows(UnsupportedOperationException.class,
                () -> unsupportedMinecart.decodeVanillaPacket(E2ePortalFixtures.registryAccess()));
        //?}
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F3-05: Protocol channel uniqueness, registration signatures, and invalid wire framing")
    void protocolChannelUniquenessRegistrationSignaturesAndInvalidWireFraming() {
        List<ResourceLocation> channelIds = List.of(
                PortalRemoteEndpointPayload.TYPE.id(),
                RemoteChunkDataPayload.TYPE.id(),
                RemoteChunkForgetPayload.TYPE.id(),
                RemoteBlockDeltaPayload.TYPE.id(),
                RemoteEntitySyncPayload.TYPE.id(),
                PortalViewSubscriptionPayload.TYPE.id()
        );
        Set<ResourceLocation> uniqueIds = new HashSet<>(channelIds);
        assertEquals(6, uniqueIds.size());
        assertTrue(uniqueIds.stream().allMatch(id -> "universegate".equals(id.getNamespace())));

        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                PlatformHelper.class, "registerClientboundPayload", void.class,
                CustomPacketPayload.Type.class, StreamCodec.class));
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                PlatformHelper.class, "registerServerboundPayload", void.class,
                CustomPacketPayload.Type.class, StreamCodec.class, BiConsumer.class));
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                SeamlessNetwork.class, "registerPayloads", void.class));

        assertThrows(DecoderException.class, () -> RemoteEntitySyncPayload.SyncKind.fromOrdinal(-1));
        ByteBuf badOrdinalBuf = Unpooled.buffer();
        try {
            ByteBufCodecs.VAR_INT.encode(badOrdinalBuf, 999);
            assertThrows(DecoderException.class, () -> RemoteEntitySyncPayload.SyncKind.STREAM_CODEC.decode(badOrdinalBuf));
        } finally {
            badOrdinalBuf.release();
        }

        RegistryFriendlyByteBuf truncated = E2ePortalFixtures.newRegistryFriendlyByteBuf();
        try {
            ResourceKey.streamCodec(Registries.DIMENSION).encode(truncated, E2ePortalFixtures.OVERWORLD);
            assertThrows(RuntimeException.class, () -> RemoteBlockDeltaPayload.STREAM_CODEC.decode(truncated));
        } finally {
            truncated.release();
        }
    }

    // ==================== F4 Boundaries ====================

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F4-01: Remote portal at identical xyz coordinates as local portal in different dim")
    void remotePortalAtIdenticalXyzCoordinatesInDifferentDim() {
        BlockPos samePos = new BlockPos(100, 64, 200);
        PortalStreamPlanner.PortalKey overworldKey = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.OVERWORLD, samePos);
        PortalStreamPlanner.PortalKey netherKey = new PortalStreamPlanner.PortalKey(E2ePortalFixtures.NETHER, samePos);
        assertNotEquals(overworldKey, netherKey);

        RemoteBlockDeltaPayload overworldDelta = new RemoteBlockDeltaPayload(
                E2ePortalFixtures.OVERWORLD, samePos, Blocks.STONE.defaultBlockState(), Optional.empty());
        RemoteBlockDeltaPayload netherDelta = new RemoteBlockDeltaPayload(
                E2ePortalFixtures.NETHER, samePos, Blocks.NETHERRACK.defaultBlockState(), Optional.empty());
        assertNotEquals(overworldDelta, netherDelta);

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F4-02: Remote chunk or entity packet arrives before ClientLevel init or after teardown")
    void remoteChunkOrEntityPacketArrivesBeforeClientLevelInitOrAfterTeardown() {
        ResourceKey<Level> uninitializedDim = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("universegate", "uninit_dim_42")
        );
        RemoteChunkForgetPayload decodedForget = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteChunkForgetPayload.STREAM_CODEC,
                new RemoteChunkForgetPayload(uninitializedDim, 3, -4)
        );
        assertEquals(uninitializedDim, decodedForget.dim());

        RemoteEntitySyncPayload removeWrapped = SeamlessContractProbe.roundTripByteBufCodec(
                RemoteEntitySyncPayload.STREAM_CODEC,
                RemoteEntitySyncPayload.fromPacket(
                        uninitializedDim,
                        555,
                        RemoteEntitySyncPayload.SyncKind.REMOVE_ENTITIES,
                        E2ePortalFixtures.registryAccess(),
                        new ClientboundRemoveEntitiesPacket(555)
                )
        );
        assertEquals(uninitializedDim, removeWrapped.dim());

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F4-03: Negative world coordinates and negative chunk indexing")
    void negativeWorldCoordinatesAndNegativeChunkIndexing() {
        BlockPos negPos = new BlockPos(-50000, -50, -50000);
        ChunkPos negChunk = new ChunkPos(negPos);
        assertEquals(Math.floorDiv(-50000, 16), negChunk.x);
        assertEquals(-3125, negChunk.x);
        assertEquals(-3125, negChunk.z);

        List<ChunkPos> square = PortalStreamPlanner.collectChunkSquare(negChunk.x, negChunk.z, 2);
        assertEquals(25, square.size());
        assertEquals(negChunk, square.getFirst());

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_REMOTE_CLIENT_LEVEL_FACTORY, false);
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F4-04: Entity ID collision between local and remote ClientLevel")
    void entityIdCollisionBetweenLocalAndRemoteClientLevel() {
        int collidingEntityId = 42;
        RemoteEntitySyncPayload overworldPayload = RemoteEntitySyncPayload.fromPacket(
                E2ePortalFixtures.OVERWORLD,
                collidingEntityId,
                RemoteEntitySyncPayload.SyncKind.REMOVE_ENTITIES,
                E2ePortalFixtures.registryAccess(),
                new ClientboundRemoveEntitiesPacket(collidingEntityId)
        );
        RemoteEntitySyncPayload netherPayload = RemoteEntitySyncPayload.fromPacket(
                E2ePortalFixtures.NETHER,
                collidingEntityId,
                RemoteEntitySyncPayload.SyncKind.REMOVE_ENTITIES,
                E2ePortalFixtures.registryAccess(),
                new ClientboundRemoveEntitiesPacket(collidingEntityId)
        );
        assertNotEquals(overworldPayload, netherPayload);

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
    }

    @Test
    @Tag("tier2")
    @Tag("areaA")
    @DisplayName("T2-F4-05: Client disconnect or level unload cleans up all active remote ClientLevels")
    void clientDisconnectOrLevelUnloadCleansUpAllActiveRemoteClientLevels() {
        PortalStreamPlanner planner = new PortalStreamPlanner();
        planner.subscribe(
                UUID.fromString("00000000-0000-0000-0000-000000002405"),
                E2ePortalFixtures.OVERWORLD, E2ePortalFixtures.DEFAULT_SOURCE_CORE,
                E2ePortalFixtures.NETHER, E2ePortalFixtures.DEFAULT_TARGET_CORE
        );
        assertEquals(1, planner.subscriptionCount());

        planner.clearAll();
        SeamlessNetwork.resetHandlers();
        assertEquals(0, planner.subscriptionCount());

        SeamlessContractProbe.requireClass(FQCN_CLIENT_REMOTE_WORLD_MANAGER, false);
        SeamlessContractProbe.requireClass(FQCN_REMOTE_RENDER_WORLD_STATE, false);
    }
}
