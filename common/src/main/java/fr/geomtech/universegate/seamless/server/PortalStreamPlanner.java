package fr.geomtech.universegate.seamless.server;

import fr.geomtech.universegate.seamless.net.RemoteEntitySyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pure-Java state and planning engine for server-side portal streaming.
 *
 * <p>Decoupled from {@code ServerLevel} and {@code MinecraftServer} so geometry,
 * closest-first chunk footprints, chunk diffing, subscription lifecycle, delta coalescing,
 * and entity filtering can be exhaustively verified in JUnit 5 tests.
 */
public final class PortalStreamPlanner {

    public static final int DEFAULT_STREAM_CHUNK_RADIUS = 2;
    public static final int TICKET_RADIUS_PER_STREAMED_CHUNK = 2;
    public static final double DEFAULT_MAX_SUBSCRIPTION_DISTANCE = 128.0D;
    public static final double DEFAULT_MAX_ENTITY_STREAM_RANGE = 40.0D;
    public static final int DEFAULT_CHUNKS_PER_TICK_BUDGET = 4;

    public record PortalKey(ResourceKey<Level> sourceDim, BlockPos sourceCorePos) {
        public PortalKey {
            Objects.requireNonNull(sourceDim, "sourceDim");
            Objects.requireNonNull(sourceCorePos, "sourceCorePos");
            sourceCorePos = sourceCorePos.immutable();
        }
    }

    public record SubscriberKey(UUID playerId, ResourceKey<Level> sourceDim, BlockPos sourceCorePos) {
        public SubscriberKey {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(sourceDim, "sourceDim");
            Objects.requireNonNull(sourceCorePos, "sourceCorePos");
            sourceCorePos = sourceCorePos.immutable();
        }

        public PortalKey portalKey() {
            return new PortalKey(sourceDim, sourceCorePos);
        }
    }

    public record TargetKey(ResourceKey<Level> targetDim, BlockPos targetCorePos) {
        public TargetKey {
            Objects.requireNonNull(targetDim, "targetDim");
            Objects.requireNonNull(targetCorePos, "targetCorePos");
            targetCorePos = targetCorePos.immutable();
        }
    }

    public record ChunkDiff(List<ChunkPos> toSend, List<ChunkPos> toForget) {
        public ChunkDiff {
            toSend = List.copyOf(toSend);
            toForget = List.copyOf(toForget);
        }
    }

    public record ChunkForgetBatch(UUID playerId, ResourceKey<Level> dim, List<ChunkPos> chunks) {
        public ChunkForgetBatch {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(dim, "dim");
            chunks = List.copyOf(chunks);
        }
    }

    public static final class SubscriptionState {
        private final SubscriberKey key;
        private TargetKey target;
        private List<ChunkPos> desiredChunks;
        private Set<Long> desiredChunkSet;
        private final Set<Long> sentChunks = new LinkedHashSet<>();
        private final Set<Long> pendingLightChunks = new HashSet<>();

        private SubscriptionState(SubscriberKey key, TargetKey target, List<ChunkPos> desiredChunks) {
            this.key = key;
            this.target = target;
            setDesiredChunks(desiredChunks);
        }

        private void setDesiredChunks(List<ChunkPos> desired) {
            this.desiredChunks = List.copyOf(desired);
            Set<Long> set = new HashSet<>(desired.size() * 2);
            for (ChunkPos pos : desired) {
                set.add(pos.toLong());
            }
            this.desiredChunkSet = Collections.unmodifiableSet(set);
        }

        public SubscriberKey key() {
            return key;
        }

        public TargetKey target() {
            return target;
        }

        public List<ChunkPos> desiredChunks() {
            return desiredChunks;
        }

        public Set<Long> desiredChunkSet() {
            return desiredChunkSet;
        }

        public Set<Long> sentChunks() {
            return Collections.unmodifiableSet(sentChunks);
        }

        public Set<Long> pendingLightChunks() {
            return Collections.unmodifiableSet(pendingLightChunks);
        }
    }

    private final int streamChunkRadius;
    private final double maxSubscriptionDistanceSq;
    private final double maxEntityStreamRangeSq;
    private final int chunksPerTickBudget;

    private final Map<SubscriberKey, SubscriptionState> subscriptions = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> dirtyBlocksByDimAndChunk = new HashMap<>();

    public PortalStreamPlanner() {
        this(
                DEFAULT_STREAM_CHUNK_RADIUS,
                DEFAULT_MAX_SUBSCRIPTION_DISTANCE,
                DEFAULT_MAX_ENTITY_STREAM_RANGE,
                DEFAULT_CHUNKS_PER_TICK_BUDGET
        );
    }

    public PortalStreamPlanner(int streamChunkRadius,
                               double maxSubscriptionDistance,
                               double maxEntityStreamRange,
                               int chunksPerTickBudget) {
        if (streamChunkRadius < 0) {
            throw new IllegalArgumentException("streamChunkRadius must be >= 0, got " + streamChunkRadius);
        }
        if (maxSubscriptionDistance <= 0.0D || !Double.isFinite(maxSubscriptionDistance)) {
            throw new IllegalArgumentException("maxSubscriptionDistance must be finite > 0");
        }
        if (maxEntityStreamRange <= 0.0D || !Double.isFinite(maxEntityStreamRange)) {
            throw new IllegalArgumentException("maxEntityStreamRange must be finite > 0");
        }
        if (chunksPerTickBudget <= 0) {
            throw new IllegalArgumentException("chunksPerTickBudget must be > 0");
        }
        this.streamChunkRadius = streamChunkRadius;
        this.maxSubscriptionDistanceSq = maxSubscriptionDistance * maxSubscriptionDistance;
        this.maxEntityStreamRangeSq = maxEntityStreamRange * maxEntityStreamRange;
        this.chunksPerTickBudget = chunksPerTickBudget;
    }

    public int streamChunkRadius() {
        return streamChunkRadius;
    }

    public int chunksPerTickBudget() {
        return chunksPerTickBudget;
    }

    public static Vec3 portalCenter(BlockPos corePos) {
        Objects.requireNonNull(corePos, "corePos");
        return new Vec3(corePos.getX() + 0.5D, corePos.getY() + 3.0D, corePos.getZ() + 0.5D);
    }

    public static double distanceSquaredToPortalCenter(BlockPos corePos, double x, double y, double z) {
        Objects.requireNonNull(corePos, "corePos");
        double cx = corePos.getX() + 0.5D;
        double cy = corePos.getY() + 3.0D;
        double cz = corePos.getZ() + 0.5D;
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean isWithinSubscriptionRange(BlockPos sourceCorePos, double playerX, double playerY, double playerZ) {
        return distanceSquaredToPortalCenter(sourceCorePos, playerX, playerY, playerZ) <= maxSubscriptionDistanceSq;
    }

    public boolean isEntityInStreamRange(BlockPos targetCorePos, double entityX, double entityY, double entityZ) {
        return distanceSquaredToPortalCenter(targetCorePos, entityX, entityY, entityZ) <= maxEntityStreamRangeSq;
    }

    public static Optional<RemoteEntitySyncPayload.SyncKind> filterEntitySyncKind(Packet<?> packet) {
        Objects.requireNonNull(packet, "packet");
        return RemoteEntitySyncPayload.kindForPacket(packet);
    }

    /**
     * Collects a {@code (2*radius + 1)^2} square of {@link ChunkPos} around
     * {@code (centerChunkX, centerChunkZ)} ordered closest-first.
     */
    public static List<ChunkPos> collectChunkSquare(int centerChunkX, int centerChunkZ, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0, got " + radius);
        }
        int side = 2 * radius + 1;
        List<ChunkPos> result = new ArrayList<>(side * side);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                result.add(new ChunkPos(centerChunkX + dx, centerChunkZ + dz));
            }
        }
        result.sort(Comparator
                .comparingInt((ChunkPos p) -> {
                    int dx = p.x - centerChunkX;
                    int dz = p.z - centerChunkZ;
                    return dx * dx + dz * dz;
                })
                .thenComparingInt(p -> Math.max(Math.abs(p.x - centerChunkX), Math.abs(p.z - centerChunkZ)))
                .thenComparingInt(p -> p.z)
                .thenComparingInt(p -> p.x));
        return List.copyOf(result);
    }

    public static ChunkDiff diffChunks(Set<Long> currentlySentChunks, List<ChunkPos> desiredOrderedChunks) {
        Objects.requireNonNull(currentlySentChunks, "currentlySentChunks");
        Objects.requireNonNull(desiredOrderedChunks, "desiredOrderedChunks");

        Set<Long> desiredSet = new HashSet<>(desiredOrderedChunks.size() * 2);
        List<ChunkPos> toSend = new ArrayList<>();
        for (ChunkPos pos : desiredOrderedChunks) {
            long key = pos.toLong();
            desiredSet.add(key);
            if (!currentlySentChunks.contains(key)) {
                toSend.add(pos);
            }
        }

        List<ChunkPos> toForget = new ArrayList<>();
        for (long sentKey : currentlySentChunks) {
            if (!desiredSet.contains(sentKey)) {
                toForget.add(new ChunkPos(sentKey));
            }
        }
        toForget.sort(Comparator.comparingInt((ChunkPos p) -> p.z).thenComparingInt(p -> p.x));

        return new ChunkDiff(toSend, toForget);
    }

    public ChunkDiff computeDiff(SubscriptionState state) {
        Objects.requireNonNull(state, "state");
        return diffChunks(state.sentChunks, state.desiredChunks);
    }

    public List<ChunkPos> desiredChunksForTarget(BlockPos targetCorePos) {
        Objects.requireNonNull(targetCorePos, "targetCorePos");
        ChunkPos center = new ChunkPos(targetCorePos);
        return collectChunkSquare(center.x, center.z, streamChunkRadius);
    }

    public List<RemoteChunkTicketController.TicketKey> desiredTicketKeysForPortal(PortalKey portalKey) {
        Objects.requireNonNull(portalKey, "portalKey");
        TargetKey target = activeTargetForPortal(portalKey);
        if (target == null) {
            return List.of();
        }
        List<ChunkPos> chunks = desiredChunksForTarget(target.targetCorePos());
        List<RemoteChunkTicketController.TicketKey> ticketKeys = new ArrayList<>(chunks.size());
        for (ChunkPos chunk : chunks) {
            ticketKeys.add(new RemoteChunkTicketController.TicketKey(
                    target.targetDim(),
                    chunk.x,
                    chunk.z,
                    TICKET_RADIUS_PER_STREAMED_CHUNK
            ));
        }
        return List.copyOf(ticketKeys);
    }

    public List<ChunkForgetBatch> subscribe(UUID playerId,
                                            ResourceKey<Level> sourceDim,
                                            BlockPos sourceCorePos,
                                            ResourceKey<Level> targetDim,
                                            BlockPos targetCorePos) {
        SubscriberKey key = new SubscriberKey(playerId, sourceDim, sourceCorePos);
        TargetKey target = new TargetKey(targetDim, targetCorePos);

        SubscriptionState existing = subscriptions.get(key);
        if (existing != null) {
            if (existing.target.equals(target)) {
                return List.of();
            }
            List<ChunkForgetBatch> forgets = forgetChunksNotHeldElsewhere(
                    playerId,
                    existing.target.targetDim(),
                    existing.sentChunks,
                    key
            );
            existing.target = target;
            existing.sentChunks.clear();
            existing.pendingLightChunks.clear();
            existing.setDesiredChunks(desiredChunksForTarget(target.targetCorePos()));
            return forgets;
        }

        SubscriptionState created = new SubscriptionState(key, target, desiredChunksForTarget(target.targetCorePos()));
        subscriptions.put(key, created);
        return List.of();
    }

    public List<ChunkForgetBatch> unsubscribe(UUID playerId,
                                              ResourceKey<Level> sourceDim,
                                              BlockPos sourceCorePos) {
        SubscriberKey key = new SubscriberKey(playerId, sourceDim, sourceCorePos);
        SubscriptionState removed = subscriptions.remove(key);
        if (removed == null || removed.sentChunks.isEmpty()) {
            return List.of();
        }
        return forgetChunksNotHeldElsewhere(playerId, removed.target.targetDim(), removed.sentChunks, null);
    }

    public List<ChunkForgetBatch> onEndpointUpdated(ResourceKey<Level> sourceDim,
                                                    BlockPos sourceCorePos,
                                                    ResourceKey<Level> newTargetDim,
                                                    BlockPos newTargetCorePos) {
        PortalKey portalKey = new PortalKey(sourceDim, sourceCorePos);
        TargetKey nextTarget = new TargetKey(newTargetDim, newTargetCorePos);
        List<ChunkPos> nextDesired = desiredChunksForTarget(newTargetCorePos);

        List<ChunkForgetBatch> forgets = new ArrayList<>();
        for (SubscriptionState state : List.copyOf(subscriptions.values())) {
            if (!state.key.portalKey().equals(portalKey)) {
                continue;
            }
            if (state.target.equals(nextTarget)) {
                continue;
            }
            ResourceKey<Level> oldDim = state.target.targetDim();
            Set<Long> oldSent = Set.copyOf(state.sentChunks);
            state.target = nextTarget;
            state.sentChunks.clear();
            state.pendingLightChunks.clear();
            state.setDesiredChunks(nextDesired);

            if (!oldSent.isEmpty()) {
                forgets.addAll(forgetChunksNotHeldElsewhere(state.key.playerId(), oldDim, oldSent, state.key));
            }
        }
        return forgets;
    }

    public List<ChunkForgetBatch> closePortal(ResourceKey<Level> sourceDim, BlockPos sourceCorePos) {
        PortalKey portalKey = new PortalKey(sourceDim, sourceCorePos);
        List<SubscriptionState> removedStates = new ArrayList<>();
        for (SubscriberKey key : List.copyOf(subscriptions.keySet())) {
            if (key.portalKey().equals(portalKey)) {
                SubscriptionState removed = subscriptions.remove(key);
                if (removed != null) {
                    removedStates.add(removed);
                }
            }
        }
        if (removedStates.isEmpty()) {
            return List.of();
        }

        List<ChunkForgetBatch> forgets = new ArrayList<>();
        for (SubscriptionState removed : removedStates) {
            if (!removed.sentChunks.isEmpty()) {
                forgets.addAll(forgetChunksNotHeldElsewhere(
                        removed.key.playerId(),
                        removed.target.targetDim(),
                        removed.sentChunks,
                        null
                ));
            }
        }
        return forgets;
    }

    public List<ChunkForgetBatch> removePlayer(UUID playerId, Set<PortalKey> affectedPortalsOut) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(affectedPortalsOut, "affectedPortalsOut");

        Map<ResourceKey<Level>, Set<Long>> sentByDim = new HashMap<>();
        for (SubscriberKey key : List.copyOf(subscriptions.keySet())) {
            if (key.playerId().equals(playerId)) {
                affectedPortalsOut.add(key.portalKey());
                SubscriptionState removed = subscriptions.remove(key);
                if (removed != null && !removed.sentChunks.isEmpty()) {
                    sentByDim.computeIfAbsent(removed.target.targetDim(), ignored -> new LinkedHashSet<>())
                            .addAll(removed.sentChunks);
                }
            }
        }

        if (sentByDim.isEmpty()) {
            return List.of();
        }
        List<ChunkForgetBatch> forgets = new ArrayList<>(sentByDim.size());
        for (Map.Entry<ResourceKey<Level>, Set<Long>> entry : sentByDim.entrySet()) {
            forgets.add(new ChunkForgetBatch(playerId, entry.getKey(), toSortedChunkList(entry.getValue())));
        }
        return forgets;
    }

    public List<ChunkForgetBatch> onChunkUnloaded(ResourceKey<Level> targetDim, long chunkPosLong) {
        Objects.requireNonNull(targetDim, "targetDim");
        Set<UUID> playersToForget = new LinkedHashSet<>();

        for (SubscriptionState state : subscriptions.values()) {
            if (!state.target.targetDim().equals(targetDim)) {
                continue;
            }
            state.pendingLightChunks.remove(chunkPosLong);
            if (state.sentChunks.remove(chunkPosLong)) {
                playersToForget.add(state.key.playerId());
            }
        }

        if (playersToForget.isEmpty()) {
            return List.of();
        }
        ChunkPos chunkPos = new ChunkPos(chunkPosLong);
        List<ChunkForgetBatch> forgets = new ArrayList<>(playersToForget.size());
        for (UUID playerId : playersToForget) {
            if (!isChunkSentToPlayer(playerId, targetDim, chunkPosLong)) {
                forgets.add(new ChunkForgetBatch(playerId, targetDim, List.of(chunkPos)));
            }
        }
        return forgets;
    }

    public void markLightWaitRequested(SubscriberKey key, long chunkPosLong) {
        SubscriptionState state = subscriptions.get(key);
        if (state != null) {
            state.pendingLightChunks.add(chunkPosLong);
        }
    }

    public void markChunkSent(SubscriberKey key, long chunkPosLong) {
        SubscriptionState state = subscriptions.get(key);
        if (state != null) {
            state.pendingLightChunks.remove(chunkPosLong);
            state.sentChunks.add(chunkPosLong);
        }
    }

    public boolean markChunkForgotten(SubscriberKey key, long chunkPosLong) {
        SubscriptionState state = subscriptions.get(key);
        if (state == null) {
            return false;
        }
        state.pendingLightChunks.remove(chunkPosLong);
        return state.sentChunks.remove(chunkPosLong);
    }

    public boolean queueBlockDelta(ResourceKey<Level> dim, BlockPos pos) {
        Objects.requireNonNull(dim, "dim");
        Objects.requireNonNull(pos, "pos");
        long chunkKey = ChunkPos.asLong(pos);
        if (!hasAnySentChunk(dim, chunkKey)) {
            return false;
        }
        dirtyBlocksByDimAndChunk
                .computeIfAbsent(dim, ignored -> new HashMap<>())
                .computeIfAbsent(chunkKey, ignored -> new LinkedHashSet<>())
                .add(pos.immutable());
        return true;
    }

    public Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> drainDirtyBlocks() {
        if (dirtyBlocksByDimAndChunk.isEmpty()) {
            return Map.of();
        }
        Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> snapshot = new HashMap<>(dirtyBlocksByDimAndChunk.size());
        for (Map.Entry<ResourceKey<Level>, Map<Long, Set<BlockPos>>> dimEntry : dirtyBlocksByDimAndChunk.entrySet()) {
            Map<Long, Set<BlockPos>> chunksCopy = new HashMap<>(dimEntry.getValue().size());
            for (Map.Entry<Long, Set<BlockPos>> chunkEntry : dimEntry.getValue().entrySet()) {
                chunksCopy.put(chunkEntry.getKey(), Set.copyOf(chunkEntry.getValue()));
            }
            snapshot.put(dimEntry.getKey(), Collections.unmodifiableMap(chunksCopy));
        }
        dirtyBlocksByDimAndChunk.clear();
        return Collections.unmodifiableMap(snapshot);
    }

    public Set<UUID> subscribersForSentChunk(ResourceKey<Level> targetDim, long chunkPosLong) {
        Objects.requireNonNull(targetDim, "targetDim");
        Set<UUID> result = new LinkedHashSet<>();
        for (SubscriptionState state : subscriptions.values()) {
            if (state.target.targetDim().equals(targetDim) && state.sentChunks.contains(chunkPosLong)) {
                result.add(state.key.playerId());
            }
        }
        return result;
    }

    public Set<UUID> subscribersForEntityInRange(ResourceKey<Level> targetDim,
                                                 long entityChunkLong,
                                                 double entityX,
                                                 double entityY,
                                                 double entityZ) {
        Objects.requireNonNull(targetDim, "targetDim");
        Set<UUID> result = new LinkedHashSet<>();
        for (SubscriptionState state : subscriptions.values()) {
            if (!state.target.targetDim().equals(targetDim)) {
                continue;
            }
            if (!state.sentChunks.contains(entityChunkLong)) {
                continue;
            }
            if (isEntityInStreamRange(state.target.targetCorePos(), entityX, entityY, entityZ)) {
                result.add(state.key.playerId());
            }
        }
        return result;
    }

    public boolean isEntityStillVisibleToPlayer(UUID playerId,
                                                ResourceKey<Level> targetDim,
                                                long entityChunkLong,
                                                double entityX,
                                                double entityY,
                                                double entityZ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(targetDim, "targetDim");
        for (SubscriptionState state : subscriptions.values()) {
            if (!state.key.playerId().equals(playerId)) {
                continue;
            }
            if (!state.target.targetDim().equals(targetDim)) {
                continue;
            }
            if (!state.sentChunks.contains(entityChunkLong)) {
                continue;
            }
            if (isEntityInStreamRange(state.target.targetCorePos(), entityX, entityY, entityZ)) {
                return true;
            }
        }
        return false;
    }

    public boolean isChunkSentToPlayer(UUID playerId, ResourceKey<Level> targetDim, long chunkPosLong) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(targetDim, "targetDim");
        for (SubscriptionState state : subscriptions.values()) {
            if (state.key.playerId().equals(playerId)
                    && state.target.targetDim().equals(targetDim)
                    && state.sentChunks.contains(chunkPosLong)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnySentChunk(ResourceKey<Level> targetDim, long chunkPosLong) {
        for (SubscriptionState state : subscriptions.values()) {
            if (state.target.targetDim().equals(targetDim) && state.sentChunks.contains(chunkPosLong)) {
                return true;
            }
        }
        return false;
    }

    public TargetKey activeTargetForPortal(PortalKey portalKey) {
        for (SubscriptionState state : subscriptions.values()) {
            if (state.key.portalKey().equals(portalKey)) {
                return state.target;
            }
        }
        return null;
    }

    public boolean hasSubscribers(PortalKey portalKey) {
        return activeTargetForPortal(portalKey) != null;
    }

    public Collection<SubscriptionState> subscriptions() {
        return List.copyOf(subscriptions.values());
    }

    public SubscriptionState subscription(SubscriberKey key) {
        return subscriptions.get(key);
    }

    public int subscriptionCount() {
        return subscriptions.size();
    }

    public void clearAll() {
        subscriptions.clear();
        dirtyBlocksByDimAndChunk.clear();
    }

    private List<ChunkForgetBatch> forgetChunksNotHeldElsewhere(UUID playerId,
                                                                ResourceKey<Level> targetDim,
                                                                Set<Long> candidateChunks,
                                                                SubscriberKey excludedKey) {
        if (candidateChunks.isEmpty()) {
            return List.of();
        }
        Set<Long> toForget = new LinkedHashSet<>();
        for (long chunkKey : candidateChunks) {
            if (!isChunkHeldByOtherSubscription(playerId, targetDim, chunkKey, excludedKey)) {
                toForget.add(chunkKey);
            }
        }
        if (toForget.isEmpty()) {
            return List.of();
        }
        return List.of(new ChunkForgetBatch(playerId, targetDim, toSortedChunkList(toForget)));
    }

    private boolean isChunkHeldByOtherSubscription(UUID playerId,
                                                   ResourceKey<Level> targetDim,
                                                   long chunkKey,
                                                   SubscriberKey excludedKey) {
        for (SubscriptionState state : subscriptions.values()) {
            if (excludedKey != null && state.key.equals(excludedKey)) {
                continue;
            }
            if (state.key.playerId().equals(playerId)
                    && state.target.targetDim().equals(targetDim)
                    && state.sentChunks.contains(chunkKey)) {
                return true;
            }
        }
        return false;
    }

    private static List<ChunkPos> toSortedChunkList(Set<Long> packedChunks) {
        List<ChunkPos> chunks = new ArrayList<>(packedChunks.size());
        for (long key : packedChunks) {
            chunks.add(new ChunkPos(key));
        }
        chunks.sort(Comparator.comparingInt((ChunkPos p) -> p.z).thenComparingInt(p -> p.x));
        return List.copyOf(chunks);
    }
}
