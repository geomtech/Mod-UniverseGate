package fr.geomtech.universegate.seamless.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalStreamPlannerTest {

    private static final ResourceKey<Level> DIM_OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> DIM_NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
    private static final ResourceKey<Level> DIM_END =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_end"));

    @Test
    void portalCenterMatchesRingAndMembraneGeometry() {
        BlockPos corePos = new BlockPos(-16, 64, 32);
        Vec3 center = PortalStreamPlanner.portalCenter(corePos);

        assertEquals(-15.5D, center.x, 1.0e-9);
        assertEquals(67.0D, center.y, 1.0e-9);
        assertEquals(32.5D, center.z, 1.0e-9);
        assertEquals(0.0D, PortalStreamPlanner.distanceSquaredToPortalCenter(corePos, -15.5D, 67.0D, 32.5D), 1.0e-9);
    }

    @Test
    void subscriptionAndEntityRangeValidationEnforcesExactBoundaries() {
        PortalStreamPlanner planner = new PortalStreamPlanner(2, 64.0D, 32.0D, 4);
        BlockPos core = new BlockPos(10, 70, -20);
        Vec3 center = PortalStreamPlanner.portalCenter(core);

        assertTrue(planner.isWithinSubscriptionRange(core, center.x + 64.0D, center.y, center.z));
        assertFalse(planner.isWithinSubscriptionRange(core, center.x + 64.001D, center.y, center.z));

        assertTrue(planner.isEntityInStreamRange(core, center.x, center.y, center.z - 32.0D));
        assertFalse(planner.isEntityInStreamRange(core, center.x, center.y, center.z - 32.001D));
    }

    @Test
    void collectChunkSquareOrdersClosestFirstAndDiffsCleanly() {
        List<ChunkPos> square = PortalStreamPlanner.collectChunkSquare(5, -3, 2);
        assertEquals(25, square.size());
        assertEquals(new ChunkPos(5, -3), square.getFirst());

        int prevDistSq = -1;
        for (ChunkPos pos : square) {
            int dx = pos.x - 5;
            int dz = pos.z + 3;
            int distSq = dx * dx + dz * dz;
            assertTrue(distSq >= prevDistSq, "Chunks must be ordered by non-decreasing squared distance");
            prevDistSq = distSq;
        }

        ChunkPos staleChunk = new ChunkPos(99, 99);
        Set<Long> sent = Set.of(new ChunkPos(5, -3).toLong(), new ChunkPos(6, -3).toLong(), staleChunk.toLong());
        PortalStreamPlanner.ChunkDiff diff = PortalStreamPlanner.diffChunks(sent, square);

        assertEquals(23, diff.toSend().size());
        assertEquals(List.of(staleChunk), diff.toForget());
        assertFalse(diff.toSend().contains(new ChunkPos(5, -3)));
        assertFalse(diff.toSend().contains(new ChunkPos(6, -3)));
        assertEquals(new ChunkPos(5, -4), diff.toSend().getFirst());
    }

    @Test
    void overlappingPortalsForSamePlayerDoNotPrematurelyForgetSharedChunks() {
        PortalStreamPlanner planner = new PortalStreamPlanner(1, 128.0D, 40.0D, 4);
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000001");
        BlockPos portalA = new BlockPos(0, 64, 0);
        BlockPos portalB = new BlockPos(20, 64, 0);
        BlockPos sharedTarget = new BlockPos(160, 70, 160);

        assertTrue(planner.subscribe(player, DIM_OVERWORLD, portalA, DIM_NETHER, sharedTarget).isEmpty());
        assertTrue(planner.subscribe(player, DIM_OVERWORLD, portalB, DIM_NETHER, sharedTarget).isEmpty());

        long sharedChunk = new ChunkPos(sharedTarget).toLong();
        planner.markChunkSent(new PortalStreamPlanner.SubscriberKey(player, DIM_OVERWORLD, portalA), sharedChunk);
        planner.markChunkSent(new PortalStreamPlanner.SubscriberKey(player, DIM_OVERWORLD, portalB), sharedChunk);

        List<PortalStreamPlanner.ChunkForgetBatch> afterFirstClose = planner.closePortal(DIM_OVERWORLD, portalA);
        assertTrue(afterFirstClose.isEmpty(), "Shared chunk must remain loaded while portalB is still open");
        assertTrue(planner.isChunkSentToPlayer(player, DIM_NETHER, sharedChunk));

        List<PortalStreamPlanner.ChunkForgetBatch> afterSecondClose = planner.closePortal(DIM_OVERWORLD, portalB);
        assertEquals(1, afterSecondClose.size());
        assertEquals(DIM_NETHER, afterSecondClose.getFirst().dim());
        assertEquals(List.of(new ChunkPos(sharedTarget)), afterSecondClose.getFirst().chunks());
        assertFalse(planner.isChunkSentToPlayer(player, DIM_NETHER, sharedChunk));
    }

    @Test
    void endpointRetargetUnloadAndPlayerRemovalEmitExpectedForgets() {
        PortalStreamPlanner planner = new PortalStreamPlanner(1, 128.0D, 40.0D, 4);
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID p2 = UUID.fromString("00000000-0000-0000-0000-000000000022");
        BlockPos sourceCore = new BlockPos(0, 64, 0);
        BlockPos target1 = new BlockPos(0, 64, 0);
        BlockPos target2 = new BlockPos(500, 64, 500);

        planner.subscribe(p1, DIM_OVERWORLD, sourceCore, DIM_NETHER, target1);
        planner.subscribe(p2, DIM_OVERWORLD, sourceCore, DIM_NETHER, target1);

        long target1Chunk = new ChunkPos(target1).toLong();
        var subKey1 = new PortalStreamPlanner.SubscriberKey(p1, DIM_OVERWORLD, sourceCore);
        var subKey2 = new PortalStreamPlanner.SubscriberKey(p2, DIM_OVERWORLD, sourceCore);
        planner.markChunkSent(subKey1, target1Chunk);
        planner.markChunkSent(subKey2, target1Chunk);

        // Same endpoint is a no-op
        assertTrue(planner.onEndpointUpdated(DIM_OVERWORLD, sourceCore, DIM_NETHER, target1).isEmpty());
        assertTrue(planner.isChunkSentToPlayer(p1, DIM_NETHER, target1Chunk));

        // Retarget forgets old chunk and updates ticket target
        List<PortalStreamPlanner.ChunkForgetBatch> retargetForgets =
                planner.onEndpointUpdated(DIM_OVERWORLD, sourceCore, DIM_END, target2);
        assertEquals(2, retargetForgets.size());
        assertEquals(9, planner.desiredTicketKeysForPortal(new PortalStreamPlanner.PortalKey(DIM_OVERWORLD, sourceCore)).size());
        assertEquals(DIM_END, planner.subscription(subKey1).target().targetDim());
        assertTrue(planner.subscription(subKey1).sentChunks().isEmpty());

        // Chunk unload forgets sent chunk cleanly
        long target2Chunk = new ChunkPos(target2).toLong();
        planner.markChunkSent(subKey1, target2Chunk);
        List<PortalStreamPlanner.ChunkForgetBatch> unloadForgets = planner.onChunkUnloaded(DIM_END, target2Chunk);
        assertEquals(1, unloadForgets.size());
        assertEquals(p1, unloadForgets.getFirst().playerId());
        assertFalse(planner.isChunkSentToPlayer(p1, DIM_END, target2Chunk));

        // Player removal returns affected portals and forget batches
        planner.markChunkSent(subKey1, target2Chunk);
        Set<PortalStreamPlanner.PortalKey> affected = new HashSet<>();
        List<PortalStreamPlanner.ChunkForgetBatch> playerForgets = planner.removePlayer(p1, affected);
        assertEquals(Set.of(new PortalStreamPlanner.PortalKey(DIM_OVERWORLD, sourceCore)), affected);
        assertEquals(1, playerForgets.size());
        assertEquals(1, planner.subscriptionCount());
    }

    @Test
    void blockDeltaCoalescesDuplicatesAndGatesOnSentChunks() {
        PortalStreamPlanner planner = new PortalStreamPlanner(1, 128.0D, 40.0D, 4);
        UUID sentPlayer = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID pendingPlayer = UUID.fromString("00000000-0000-0000-0000-000000000102");
        BlockPos sourceCore = new BlockPos(0, 64, 0);
        BlockPos targetCore = new BlockPos(8, 64, 8);

        planner.subscribe(sentPlayer, DIM_OVERWORLD, sourceCore, DIM_NETHER, targetCore);
        planner.subscribe(pendingPlayer, DIM_OVERWORLD, sourceCore, DIM_NETHER, targetCore);

        BlockPos changedPosA = new BlockPos(5, 65, 5);
        BlockPos changedPosB = new BlockPos(6, 65, 5);
        long chunkKey = ChunkPos.asLong(changedPosA);

        // Before any subscriber has received the chunk, block deltas are ignored
        assertFalse(planner.queueBlockDelta(DIM_NETHER, changedPosA));
        assertTrue(planner.drainDirtyBlocks().isEmpty());

        planner.markChunkSent(new PortalStreamPlanner.SubscriberKey(sentPlayer, DIM_OVERWORLD, sourceCore), chunkKey);

        assertTrue(planner.queueBlockDelta(DIM_NETHER, changedPosA));
        assertTrue(planner.queueBlockDelta(DIM_NETHER, changedPosA));
        assertTrue(planner.queueBlockDelta(DIM_NETHER, changedPosB));

        var drained = planner.drainDirtyBlocks();
        assertEquals(Set.of(changedPosA, changedPosB), drained.get(DIM_NETHER).get(chunkKey));
        assertTrue(planner.drainDirtyBlocks().isEmpty());

        // Only sentPlayer receives deltas; pendingPlayer has not received the chunk yet
        assertEquals(Set.of(sentPlayer), planner.subscribersForSentChunk(DIM_NETHER, chunkKey));
    }

    @Test
    void entitySyncKindFilteringAndVisibilityChecksGateOnSentChunkAndRange() {
        PortalStreamPlanner planner = new PortalStreamPlanner(2, 128.0D, 20.0D, 4);
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000202");
        BlockPos sourceCore = new BlockPos(0, 64, 0);
        BlockPos targetCore = new BlockPos(8, 64, 8);

        assertTrue(PortalStreamPlanner.filterEntitySyncKind(
                new ClientboundMoveEntityPacket.Pos(1, (short) 1, (short) 2, (short) 3, true)
        ).isPresent());
        assertTrue(PortalStreamPlanner.filterEntitySyncKind(
                new ClientboundSetEntityMotionPacket(1, Vec3.ZERO)
        ).isPresent());
        assertTrue(PortalStreamPlanner.filterEntitySyncKind(
                new ClientboundRemoveEntitiesPacket(1)
        ).isPresent());
        assertTrue(PortalStreamPlanner.filterEntitySyncKind(
                new ClientboundSetTimePacket(10L, 20L, true)
        ).isEmpty());
        assertTrue(PortalStreamPlanner.filterEntitySyncKind(
                new ClientboundSetHealthPacket(20.0F, 20, 5.0F)
        ).isEmpty());

        planner.subscribe(player, DIM_OVERWORLD, sourceCore, DIM_NETHER, targetCore);
        long targetChunk = new ChunkPos(targetCore).toLong();

        // Chunk not sent yet -> invisible
        assertTrue(planner.subscribersForEntityInRange(DIM_NETHER, targetChunk, 8.5D, 67.0D, 8.5D).isEmpty());
        assertFalse(planner.isEntityStillVisibleToPlayer(player, DIM_NETHER, targetChunk, 8.5D, 67.0D, 8.5D));

        planner.markChunkSent(new PortalStreamPlanner.SubscriberKey(player, DIM_OVERWORLD, sourceCore), targetChunk);

        // In sent chunk and within 20 blocks -> visible
        assertEquals(Set.of(player), planner.subscribersForEntityInRange(DIM_NETHER, targetChunk, 12.0D, 67.0D, 8.5D));
        assertTrue(planner.isEntityStillVisibleToPlayer(player, DIM_NETHER, targetChunk, 12.0D, 67.0D, 8.5D));

        // Out of 20-block range even in sent chunk -> excluded
        assertTrue(planner.subscribersForEntityInRange(DIM_NETHER, targetChunk, 8.5D, 100.0D, 8.5D).isEmpty());
        assertFalse(planner.isEntityStillVisibleToPlayer(player, DIM_NETHER, targetChunk, 8.5D, 100.0D, 8.5D));
    }
}
