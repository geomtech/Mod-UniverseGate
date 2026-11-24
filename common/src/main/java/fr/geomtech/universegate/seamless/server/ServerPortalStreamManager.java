package fr.geomtech.universegate.seamless.server;

import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.compat.Compat;
import fr.geomtech.universegate.platform.Services;
import fr.geomtech.universegate.mixin.ChunkMapAccessor;
import fr.geomtech.universegate.mixin.TrackedEntityAccessor;
import fr.geomtech.universegate.seamless.net.PortalEndpointListener;
import fr.geomtech.universegate.seamless.net.PortalViewSubscriptionPayload;
import fr.geomtech.universegate.seamless.net.RemoteBlockDeltaPayload;
import fr.geomtech.universegate.seamless.net.RemoteChunkDataPayload;
import fr.geomtech.universegate.seamless.net.RemoteChunkForgetPayload;
import fr.geomtech.universegate.seamless.net.RemoteEntitySyncPayload;
import fr.geomtech.universegate.seamless.net.ResolvedPortalEndpoint;
import fr.geomtech.universegate.seamless.net.SeamlessNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Coordinates server-side chunk tickets, remote chunk/light streaming, coalesced block/BE deltas,
 * and tracked entity pairing/live sync for subscribed portal observers.
 */
public final class ServerPortalStreamManager implements PortalEndpointListener {

    private static volatile @Nullable ServerPortalStreamManager instance;

    private final MinecraftServer server;
    private final PortalStreamPlanner planner;
    private final RemoteChunkTicketController ticketController;
    private final Map<UUID, Map<ResourceKey<Level>, Set<Integer>>> pairedEntitiesByPlayerAndDim = new HashMap<>();

    private ServerPortalStreamManager(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.planner = new PortalStreamPlanner(
                3,
                PortalStreamPlanner.DEFAULT_MAX_SUBSCRIPTION_DISTANCE,
                48.0D,
                8
        );
        this.ticketController = new RemoteChunkTicketController(
                RemoteChunkTicketController.createMinecraftBackend(() -> this.server)
        );
    }

    public static void onServerStarting(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        resetState();
        ServerPortalStreamManager manager = new ServerPortalStreamManager(server);
        instance = manager;
        PortalCoreBlockEntity.setEndpointListener(manager);
        SeamlessNetwork.setViewSubscriptionHandler(manager::handleViewSubscription);
    }

    public static void onServerStopping(MinecraftServer server) {
        ServerPortalStreamManager current = instance;
        if (current != null) {
            current.teardownAll(true);
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        resetState();
        PortalCoreBlockEntity.setEndpointListener(null);
        // Only clear the server-side C2S handler; never call SeamlessNetwork.resetHandlers() here
        // because in singleplayer the integrated server shares the JVM with the client and resetting
        // client S2C handlers to NOOP breaks seamless rendering on subsequent world loads!
        SeamlessNetwork.setViewSubscriptionHandler(null);
    }

    private static void resetState() {
        ServerPortalStreamManager previous = instance;
        instance = null;
        if (previous != null) {
            previous.teardownAll(false);
        }
    }

    public static void onServerTickEnd(MinecraftServer server) {
        ServerPortalStreamManager current = instance;
        if (current != null && current.server == server) {
            current.tick();
        }
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        ServerPortalStreamManager current = instance;
        if (current != null && player != null) {
            current.handlePlayerDisconnect(player.getUUID());
        }
    }

    public static void onPlayerChangedDimension(ServerPlayer player,
                                                ResourceKey<Level> fromDim,
                                                ResourceKey<Level> toDim) {
        ServerPortalStreamManager current = instance;
        if (current != null && player != null) {
            current.handlePlayerChangedDimension(player);
        }
    }

    public static void onBlockChanged(@Nullable ServerLevel level, @Nullable BlockPos pos) {
        ServerPortalStreamManager current = instance;
        if (current != null && level != null && pos != null) {
            current.planner.queueBlockDelta(level.dimension(), pos);
        }
    }

    public static void onChunkUnloaded(@Nullable ResourceKey<Level> dim, long chunkPosLong) {
        ServerPortalStreamManager current = instance;
        if (current != null && dim != null) {
            current.handleChunkUnloaded(dim, chunkPosLong);
        }
    }

    public static void onTrackedEntityAdded(@Nullable ServerLevel level, @Nullable Entity entity) {
        ServerPortalStreamManager current = instance;
        if (current != null && level != null && entity != null) {
            current.handleTrackedEntityAdded(level, entity);
        }
    }

    public static void onTrackedEntityRemoved(@Nullable ServerLevel level, @Nullable Entity entity) {
        ServerPortalStreamManager current = instance;
        if (current != null && level != null && entity != null) {
            current.handleTrackedEntityRemoved(level, entity);
        }
    }

    public static void onTrackedEntityPacket(@Nullable ServerLevel level,
                                             @Nullable Entity entity,
                                             @Nullable ServerEntity serverEntity,
                                             @Nullable Packet<?> rawPacket) {
        ServerPortalStreamManager current = instance;
        if (current != null && level != null && entity != null && serverEntity != null && rawPacket != null) {
            current.handleTrackedEntityPacket(level, entity, serverEntity, rawPacket);
        }
    }

    public static void unwrapPackets(Packet<?> packet, Consumer<Packet<?>> consumer) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(consumer, "consumer");
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> subPacket : bundle.subPackets()) {
                unwrapPackets(subPacket, consumer);
            }
            return;
        }
        consumer.accept(packet);
    }

    public static Optional<RemoteEntitySyncPayload> wrapEntityPacket(ResourceKey<Level> dim,
                                                                     int entityId,
                                                                     RegistryAccess registryAccess,
                                                                     Packet<?> packet) {
        Objects.requireNonNull(dim, "dim");
        Objects.requireNonNull(registryAccess, "registryAccess");
        Objects.requireNonNull(packet, "packet");
        return PortalStreamPlanner.filterEntitySyncKind(packet)
                .map(kind -> RemoteEntitySyncPayload.fromPacket(dim, entityId, kind, registryAccess, packet));
    }

    // ---------- PortalEndpointListener ----------

    @Override
    public void onEndpointUpdated(ServerLevel sourceLevel,
                                  BlockPos sourceCorePos,
                                  PortalCoreBlockEntity core,
                                  ResolvedPortalEndpoint endpoint) {
        PortalStreamPlanner.PortalKey portalKey = new PortalStreamPlanner.PortalKey(sourceLevel.dimension(), sourceCorePos);
        List<PortalStreamPlanner.ChunkForgetBatch> forgets = planner.onEndpointUpdated(
                sourceLevel.dimension(),
                sourceCorePos,
                endpoint.targetDim(),
                endpoint.targetCorePos()
        );
        sendChunkForgets(forgets, true);
        syncPortalTicketClaims(portalKey);
    }

    @Override
    public void onEndpointClosed(ServerLevel sourceLevel,
                                 BlockPos sourceCorePos,
                                 PortalCoreBlockEntity core,
                                 @Nullable ResolvedPortalEndpoint previousEndpoint) {
        teardownPortal(sourceLevel.dimension(), sourceCorePos);
    }

    @Override
    public void onCoreUnloaded(ServerLevel sourceLevel,
                               BlockPos sourceCorePos,
                               PortalCoreBlockEntity core) {
        teardownPortal(sourceLevel.dimension(), sourceCorePos);
    }

    @Override
    public void onObserverUntracked(ServerLevel sourceLevel,
                                    BlockPos sourceCorePos,
                                    UUID playerId) {
        List<PortalStreamPlanner.ChunkForgetBatch> forgets = planner.unsubscribe(
                playerId,
                sourceLevel.dimension(),
                sourceCorePos
        );
        sendChunkForgets(forgets, true);
        syncPortalTicketClaims(new PortalStreamPlanner.PortalKey(sourceLevel.dimension(), sourceCorePos));
    }

    // ---------- C2S View Subscription ----------

    private void handleViewSubscription(PortalViewSubscriptionPayload payload, ServerPlayer player) {
        if (player == null || player.isRemoved()) {
            return;
        }
        ServerLevel sourceLevel = Compat.serverLevel(player);
        BlockPos sourceCorePos = payload.sourceCorePos();
        PortalStreamPlanner.PortalKey portalKey = new PortalStreamPlanner.PortalKey(sourceLevel.dimension(), sourceCorePos);

        if (!payload.subscribe()) {
            List<PortalStreamPlanner.ChunkForgetBatch> forgets = planner.unsubscribe(
                    player.getUUID(),
                    sourceLevel.dimension(),
                    sourceCorePos
            );
            sendChunkForgets(forgets, true);
            syncPortalTicketClaims(portalKey);
            return;
        }

        if (!sourceLevel.hasChunkAt(sourceCorePos)) {
            return;
        }
        if (!(sourceLevel.getBlockEntity(sourceCorePos) instanceof PortalCoreBlockEntity core)) {
            return;
        }
        if (!core.isActiveOrOpening()) {
            return;
        }
        if (!planner.isWithinSubscriptionRange(sourceCorePos, player.getX(), player.getY(), player.getZ())) {
            return;
        }

        Optional<ResolvedPortalEndpoint> resolved = core.resolveRemoteEndpoint();
        if (resolved.isEmpty()) {
            return;
        }
        ResolvedPortalEndpoint endpoint = resolved.get();

        List<PortalStreamPlanner.ChunkForgetBatch> forgets = planner.subscribe(
                player.getUUID(),
                sourceLevel.dimension(),
                sourceCorePos,
                endpoint.targetDim(),
                endpoint.targetCorePos()
        );
        sendChunkForgets(forgets, true);
        syncPortalTicketClaims(portalKey);
    }

    // ---------- Player & Portal Lifecycle ----------

    private void teardownPortal(ResourceKey<Level> sourceDim, BlockPos sourceCorePos) {
        PortalStreamPlanner.PortalKey portalKey = new PortalStreamPlanner.PortalKey(sourceDim, sourceCorePos);
        List<PortalStreamPlanner.ChunkForgetBatch> forgets = planner.closePortal(sourceDim, sourceCorePos);
        sendChunkForgets(forgets, true);
        ticketController.releaseOwner(portalKey);
    }

    private void handlePlayerDisconnect(UUID playerId) {
        Set<PortalStreamPlanner.PortalKey> affectedPortals = new HashSet<>();
        planner.removePlayer(playerId, affectedPortals);
        pairedEntitiesByPlayerAndDim.remove(playerId);
        for (PortalStreamPlanner.PortalKey portalKey : affectedPortals) {
            syncPortalTicketClaims(portalKey);
        }
    }

    private void handlePlayerChangedDimension(ServerPlayer player) {
        Set<PortalStreamPlanner.PortalKey> affectedPortals = new HashSet<>();
        List<PortalStreamPlanner.ChunkForgetBatch> forgets = planner.removePlayer(player.getUUID(), affectedPortals);
        sendChunkForgets(forgets, true);
        pairedEntitiesByPlayerAndDim.remove(player.getUUID());
        for (PortalStreamPlanner.PortalKey portalKey : affectedPortals) {
            syncPortalTicketClaims(portalKey);
        }
    }

    private void teardownAll(boolean notifyClients) {
        if (notifyClients) {
            for (PortalStreamPlanner.SubscriptionState sub : planner.subscriptions()) {
                ServerPlayer player = server.getPlayerList().getPlayer(sub.key().playerId());
                if (player != null && !player.isRemoved()) {
                    for (long chunkKey : sub.sentChunks()) {
                        ChunkPos chunkPos = new ChunkPos(chunkKey);
                        SeamlessNetwork.sendToPlayer(
                                player,
                                new RemoteChunkForgetPayload(sub.target().targetDim(), chunkPos.x, chunkPos.z)
                        );
                    }
                }
            }
            for (Map.Entry<UUID, Map<ResourceKey<Level>, Set<Integer>>> playerEntry : pairedEntitiesByPlayerAndDim.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerEntry.getKey());
                if (player == null || player.isRemoved()) {
                    continue;
                }
                for (Map.Entry<ResourceKey<Level>, Set<Integer>> dimEntry : playerEntry.getValue().entrySet()) {
                    ServerLevel targetLevel = server.getLevel(dimEntry.getKey());
                    if (targetLevel != null && !dimEntry.getValue().isEmpty()) {
                        sendEntityRemoval(player, targetLevel, dimEntry.getValue());
                    }
                }
            }
        }
        planner.clearAll();
        pairedEntitiesByPlayerAndDim.clear();
        ticketController.clearAll();
    }

    private void syncPortalTicketClaims(PortalStreamPlanner.PortalKey portalKey) {
        List<RemoteChunkTicketController.TicketKey> desired = planner.desiredTicketKeysForPortal(portalKey);
        if (desired.isEmpty()) {
            ticketController.releaseOwner(portalKey);
        } else {
            ticketController.setClaims(portalKey, desired);
        }
    }

    // ---------- Server Tick ----------

    private void tick() {
        pruneInvalidSubscriptions();
        keepActiveDimensionsTicking();
        streamReadyChunks();
        reconcileTrackedEntities();
        flushDirtyBlockDeltas();
    }

    private void pruneInvalidSubscriptions() {
        List<PortalStreamPlanner.SubscriptionState> invalid = new ArrayList<>();
        for (PortalStreamPlanner.SubscriptionState sub : planner.subscriptions()) {
            UUID playerId = sub.key().playerId();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || player.isRemoved()) {
                invalid.add(sub);
                continue;
            }
            if (!Compat.serverLevel(player).dimension().equals(sub.key().sourceDim())) {
                invalid.add(sub);
                continue;
            }
            if (!planner.isWithinSubscriptionRange(
                    sub.key().sourceCorePos(),
                    player.getX(),
                    player.getY(),
                    player.getZ()
            )) {
                invalid.add(sub);
                continue;
            }
            ServerLevel sourceLevel = server.getLevel(sub.key().sourceDim());
            BlockPos corePos = sub.key().sourceCorePos();
            if (sourceLevel == null
                    || !sourceLevel.hasChunkAt(corePos)
                    || !(sourceLevel.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)
                    || !core.isActiveOrOpening()) {
                invalid.add(sub);
            }
        }

        if (invalid.isEmpty()) {
            return;
        }
        Set<PortalStreamPlanner.PortalKey> affectedPortals = new HashSet<>();
        for (PortalStreamPlanner.SubscriptionState sub : invalid) {
            affectedPortals.add(sub.key().portalKey());
            List<PortalStreamPlanner.ChunkForgetBatch> forgets = planner.unsubscribe(
                    sub.key().playerId(),
                    sub.key().sourceDim(),
                    sub.key().sourceCorePos()
            );
            sendChunkForgets(forgets, true);
        }
        for (PortalStreamPlanner.PortalKey portalKey : affectedPortals) {
            syncPortalTicketClaims(portalKey);
        }
    }

    private void keepActiveDimensionsTicking() {
        for (ResourceKey<Level> dim : ticketController.activeDimensions()) {
            ServerLevel targetLevel = server.getLevel(dim);
            if (targetLevel != null) {
                targetLevel.resetEmptyTime();
            }
        }
    }

    private void streamReadyChunks() {
        for (PortalStreamPlanner.SubscriptionState sub : planner.subscriptions()) {
            ServerPlayer player = server.getPlayerList().getPlayer(sub.key().playerId());
            if (player == null || player.isRemoved()) {
                continue;
            }
            ServerLevel targetLevel = server.getLevel(sub.target().targetDim());
            if (targetLevel == null) {
                continue;
            }

            PortalStreamPlanner.ChunkDiff diff = planner.computeDiff(sub);
            if (!diff.toForget().isEmpty()) {
                List<ChunkPos> forgetNow = new ArrayList<>();
                for (ChunkPos chunkPos : diff.toForget()) {
                    long chunkKey = chunkPos.toLong();
                    planner.markChunkForgotten(sub.key(), chunkKey);
                    if (!planner.isChunkSentToPlayer(player.getUUID(), targetLevel.dimension(), chunkKey)) {
                        forgetNow.add(chunkPos);
                    }
                }
                if (!forgetNow.isEmpty()) {
                    sendChunkForgets(
                            List.of(new PortalStreamPlanner.ChunkForgetBatch(
                                    player.getUUID(),
                                    targetLevel.dimension(),
                                    forgetNow
                            )),
                            true
                    );
                }
            }

            if (diff.toSend().isEmpty()) {
                continue;
            }

            ChunkMap chunkMap = targetLevel.getChunkSource().chunkMap;
            int sentThisTick = 0;
            for (ChunkPos chunkPos : diff.toSend()) {
                if (sentThisTick >= planner.chunksPerTickBudget()) {
                    break;
                }
                long chunkKey = chunkPos.toLong();
                if (!sub.pendingLightChunks().contains(chunkKey)
                        && targetLevel.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) != null) {
                    chunkMap.waitForLightBeforeSending(chunkPos, 0);
                    planner.markLightWaitRequested(sub.key(), chunkKey);
                }

                LevelChunk readyChunk = chunkMap.getChunkToSend(chunkKey);
                if (readyChunk == null || !readyChunk.isLightCorrect()) {
                    continue;
                }

                ClientboundLevelChunkWithLightPacket vanillaPacket = new ClientboundLevelChunkWithLightPacket(
                        readyChunk,
                        targetLevel.getLightEngine(),
                        null,
                        null
                );
                RemoteChunkDataPayload payload = RemoteChunkDataPayload.fromPacket(
                        targetLevel.dimension(),
                        targetLevel.registryAccess(),
                        vanillaPacket
                );
                SeamlessNetwork.sendToPlayer(player, payload);
                planner.markChunkSent(sub.key(), chunkKey);
                sentThisTick++;

                pairEntitiesInChunk(player, targetLevel, sub.target().targetCorePos(), chunkKey);
            }
        }
    }

    private void reconcileTrackedEntities() {
        // 1. Remove paired entities that left streamed chunks/range or died.
        for (UUID playerId : List.copyOf(pairedEntitiesByPlayerAndDim.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || player.isRemoved()) {
                pairedEntitiesByPlayerAndDim.remove(playerId);
                continue;
            }
            Map<ResourceKey<Level>, Set<Integer>> dims = pairedEntitiesByPlayerAndDim.get(playerId);
            if (dims == null) {
                continue;
            }
            for (ResourceKey<Level> dim : List.copyOf(dims.keySet())) {
                Set<Integer> entityIds = dims.get(dim);
                if (entityIds == null || entityIds.isEmpty()) {
                    dims.remove(dim);
                    continue;
                }
                ServerLevel targetLevel = server.getLevel(dim);
                if (targetLevel == null) {
                    dims.remove(dim);
                    continue;
                }
                List<Integer> toRemove = new ArrayList<>();
                for (Integer entityId : List.copyOf(entityIds)) {
                    Entity entity = targetLevel.getEntity(entityId);
                    if (entity == null
                            || entity.isRemoved()
                            || !planner.isEntityStillVisibleToPlayer(
                                    playerId,
                                    dim,
                                    entity.chunkPosition().toLong(),
                                    entity.getX(),
                                    entity.getY(),
                                    entity.getZ()
                            )) {
                        entityIds.remove(entityId);
                        toRemove.add(entityId);
                    }
                }
                if (!toRemove.isEmpty()) {
                    sendEntityRemoval(player, targetLevel, toRemove);
                }
                if (entityIds.isEmpty()) {
                    dims.remove(dim);
                }
            }
            if (dims.isEmpty()) {
                pairedEntitiesByPlayerAndDim.remove(playerId);
            }
        }

        // 2. Pair entities that entered streamed chunks or moved into range.
        Set<ResourceKey<Level>> activeTargetDims = new HashSet<>();
        for (PortalStreamPlanner.SubscriptionState sub : planner.subscriptions()) {
            if (!sub.sentChunks().isEmpty()) {
                activeTargetDims.add(sub.target().targetDim());
            }
        }
        for (ResourceKey<Level> dim : activeTargetDims) {
            ServerLevel targetLevel = server.getLevel(dim);
            if (targetLevel == null) {
                continue;
            }
            ChunkMap chunkMap = targetLevel.getChunkSource().chunkMap;
            for (Object trackedObj : ((ChunkMapAccessor) chunkMap).universegate$getEntityMap().values()) {
                TrackedEntityAccessor accessor = (TrackedEntityAccessor) trackedObj;
                Entity entity = accessor.universegate$getEntity();
                if (entity == null || entity.isRemoved()) {
                    continue;
                }
                long chunkKey = entity.chunkPosition().toLong();
                if (!planner.hasAnySentChunk(dim, chunkKey)) {
                    continue;
                }
                Set<UUID> interestedPlayers = planner.subscribersForEntityInRange(
                        dim,
                        chunkKey,
                        entity.getX(),
                        entity.getY(),
                        entity.getZ()
                );
                if (interestedPlayers.isEmpty()) {
                    continue;
                }
                ServerEntity serverEntity = accessor.universegate$getServerEntity();
                for (UUID playerId : interestedPlayers) {
                    if (!isEntityPaired(playerId, dim, entity.getId())) {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null && !player.isRemoved()) {
                            pairEntityIfNeeded(player, targetLevel, entity, serverEntity);
                        }
                    }
                }
            }
        }
    }

    private void flushDirtyBlockDeltas() {
        Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> drained = planner.drainDirtyBlocks();
        if (drained.isEmpty()) {
            return;
        }
        for (Map.Entry<ResourceKey<Level>, Map<Long, Set<BlockPos>>> dimEntry : drained.entrySet()) {
            ResourceKey<Level> dim = dimEntry.getKey();
            ServerLevel targetLevel = server.getLevel(dim);
            if (targetLevel == null) {
                continue;
            }
            for (Map.Entry<Long, Set<BlockPos>> chunkEntry : dimEntry.getValue().entrySet()) {
                long chunkKey = chunkEntry.getKey();
                Set<UUID> receiverIds = planner.subscribersForSentChunk(dim, chunkKey);
                if (receiverIds.isEmpty()) {
                    continue;
                }
                if (!targetLevel.hasChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey))) {
                    continue;
                }
                List<ServerPlayer> receivers = new ArrayList<>(receiverIds.size());
                for (UUID playerId : receiverIds) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null && !player.isRemoved()) {
                        receivers.add(player);
                    }
                }
                if (receivers.isEmpty()) {
                    continue;
                }

                for (BlockPos pos : chunkEntry.getValue()) {
                    BlockState state = targetLevel.getBlockState(pos);
                    BlockEntity be = state.hasBlockEntity() ? targetLevel.getBlockEntity(pos) : null;
                    Optional<CompoundTag> beTag = (be != null && be.getUpdatePacket() != null)
                            ? Optional.of(be.getUpdateTag(targetLevel.registryAccess()))
                            : Optional.empty();
                    RemoteBlockDeltaPayload payload = new RemoteBlockDeltaPayload(dim, pos, state, beTag);
                    for (ServerPlayer receiver : receivers) {
                        SeamlessNetwork.sendToPlayer(receiver, payload);
                    }
                }
            }
        }
    }

    // ---------- Mixin Event Handlers ----------

    private void handleChunkUnloaded(ResourceKey<Level> dim, long chunkPosLong) {
        List<PortalStreamPlanner.ChunkForgetBatch> forgets = planner.onChunkUnloaded(dim, chunkPosLong);
        sendChunkForgets(forgets, true);
    }

    private void handleTrackedEntityAdded(ServerLevel level, Entity entity) {
        if (entity.isRemoved()) {
            return;
        }
        long chunkKey = entity.chunkPosition().toLong();
        if (!planner.hasAnySentChunk(level.dimension(), chunkKey)) {
            return;
        }
        Object trackedObj = ((ChunkMapAccessor) level.getChunkSource().chunkMap)
                .universegate$getEntityMap()
                .get(entity.getId());
        if (trackedObj == null) {
            return;
        }
        Set<UUID> receivers = planner.subscribersForEntityInRange(
                level.dimension(),
                chunkKey,
                entity.getX(),
                entity.getY(),
                entity.getZ()
        );
        if (receivers.isEmpty()) {
            return;
        }
        ServerEntity serverEntity = ((TrackedEntityAccessor) trackedObj).universegate$getServerEntity();
        for (UUID playerId : receivers) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && !player.isRemoved()) {
                pairEntityIfNeeded(player, level, entity, serverEntity);
            }
        }
    }

    private void handleTrackedEntityRemoved(ServerLevel level, Entity entity) {
        ResourceKey<Level> dim = level.dimension();
        int entityId = entity.getId();
        for (UUID playerId : List.copyOf(pairedEntitiesByPlayerAndDim.keySet())) {
            Map<ResourceKey<Level>, Set<Integer>> dims = pairedEntitiesByPlayerAndDim.get(playerId);
            if (dims == null) {
                continue;
            }
            Set<Integer> paired = dims.get(dim);
            if (paired == null || !paired.remove(entityId)) {
                continue;
            }
            if (paired.isEmpty()) {
                dims.remove(dim);
            }
            if (dims.isEmpty()) {
                pairedEntitiesByPlayerAndDim.remove(playerId);
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && !player.isRemoved()) {
                sendEntityRemoval(player, level, List.of(entityId));
            }
        }
    }

    private void handleTrackedEntityPacket(ServerLevel level,
                                           Entity entity,
                                           ServerEntity serverEntity,
                                           Packet<?> rawPacket) {
        ResourceKey<Level> dim = level.dimension();
        long chunkKey = entity.chunkPosition().toLong();
        if (!planner.hasAnySentChunk(dim, chunkKey)) {
            return;
        }

        Set<UUID> receivers = planner.subscribersForEntityInRange(
                dim,
                chunkKey,
                entity.getX(),
                entity.getY(),
                entity.getZ()
        );
        if (receivers.isEmpty()) {
            return;
        }

        unwrapPackets(rawPacket, packet -> {
            Optional<RemoteEntitySyncPayload.SyncKind> kindOpt = PortalStreamPlanner.filterEntitySyncKind(packet);
            if (kindOpt.isEmpty()) {
                return;
            }
            RemoteEntitySyncPayload.SyncKind kind = kindOpt.get();
            RemoteEntitySyncPayload lazyPayload = null;

            for (UUID playerId : receivers) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || player.isRemoved()) {
                    continue;
                }
                if (kind == RemoteEntitySyncPayload.SyncKind.REMOVE_ENTITIES) {
                    if (unmarkEntityPaired(playerId, dim, entity.getId())) {
                        if (lazyPayload == null) {
                            lazyPayload = RemoteEntitySyncPayload.fromPacket(
                                    dim,
                                    entity.getId(),
                                    kind,
                                    level.registryAccess(),
                                    packet
                            );
                        }
                        SeamlessNetwork.sendToPlayer(player, lazyPayload);
                    }
                    continue;
                }

                if (!isEntityPaired(playerId, dim, entity.getId())) {
                    pairEntityIfNeeded(player, level, entity, serverEntity);
                    continue;
                }

                if (lazyPayload == null) {
                    lazyPayload = RemoteEntitySyncPayload.fromPacket(
                            dim,
                            entity.getId(),
                            kind,
                            level.registryAccess(),
                            packet
                    );
                }
                SeamlessNetwork.sendToPlayer(player, lazyPayload);
            }
        });
    }

    // ---------- Helpers ----------

    private void pairEntitiesInChunk(ServerPlayer player,
                                     ServerLevel targetLevel,
                                     BlockPos targetCorePos,
                                     long chunkKey) {
        ChunkMap chunkMap = targetLevel.getChunkSource().chunkMap;
        for (Object trackedObj : ((ChunkMapAccessor) chunkMap).universegate$getEntityMap().values()) {
            TrackedEntityAccessor accessor = (TrackedEntityAccessor) trackedObj;
            Entity entity = accessor.universegate$getEntity();
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            if (entity.chunkPosition().toLong() != chunkKey) {
                continue;
            }
            if (!planner.isEntityInStreamRange(targetCorePos, entity.getX(), entity.getY(), entity.getZ())) {
                continue;
            }
            pairEntityIfNeeded(player, targetLevel, entity, accessor.universegate$getServerEntity());
        }
    }

    private void pairEntityIfNeeded(ServerPlayer player,
                                    ServerLevel targetLevel,
                                    Entity entity,
                                    ServerEntity serverEntity) {
        Set<Integer> paired = pairedEntitiesByPlayerAndDim
                .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
                .computeIfAbsent(targetLevel.dimension(), ignored -> new LinkedHashSet<>());
        if (!paired.add(entity.getId())) {
            return;
        }
        Services.PLATFORM.sendEntityPairingData(serverEntity, player, rawPacket ->
                unwrapPackets(rawPacket, packet ->
                        wrapEntityPacket(targetLevel.dimension(), entity.getId(), targetLevel.registryAccess(), packet)
                                .ifPresent(payload -> SeamlessNetwork.sendToPlayer(player, payload))
                )
        );
    }

    private boolean isEntityPaired(UUID playerId, ResourceKey<Level> dim, int entityId) {
        Map<ResourceKey<Level>, Set<Integer>> dims = pairedEntitiesByPlayerAndDim.get(playerId);
        if (dims == null) {
            return false;
        }
        Set<Integer> ids = dims.get(dim);
        return ids != null && ids.contains(entityId);
    }

    private boolean unmarkEntityPaired(UUID playerId, ResourceKey<Level> dim, int entityId) {
        Map<ResourceKey<Level>, Set<Integer>> dims = pairedEntitiesByPlayerAndDim.get(playerId);
        if (dims == null) {
            return false;
        }
        Set<Integer> ids = dims.get(dim);
        if (ids == null || !ids.remove(entityId)) {
            return false;
        }
        if (ids.isEmpty()) {
            dims.remove(dim);
        }
        if (dims.isEmpty()) {
            pairedEntitiesByPlayerAndDim.remove(playerId);
        }
        return true;
    }

    private void sendChunkForgets(List<PortalStreamPlanner.ChunkForgetBatch> batches, boolean removeEntities) {
        if (batches.isEmpty()) {
            return;
        }
        for (PortalStreamPlanner.ChunkForgetBatch batch : batches) {
            ServerPlayer player = server.getPlayerList().getPlayer(batch.playerId());
            if (player != null && !player.isRemoved()) {
                for (ChunkPos chunk : batch.chunks()) {
                    SeamlessNetwork.sendToPlayer(player, new RemoteChunkForgetPayload(batch.dim(), chunk.x, chunk.z));
                }
            }
            if (removeEntities) {
                reconcilePlayerDimEntityRemovals(batch.playerId(), batch.dim(), player);
            }
        }
    }

    private void reconcilePlayerDimEntityRemovals(UUID playerId,
                                                  ResourceKey<Level> dim,
                                                  @Nullable ServerPlayer player) {
        Map<ResourceKey<Level>, Set<Integer>> dims = pairedEntitiesByPlayerAndDim.get(playerId);
        if (dims == null) {
            return;
        }
        Set<Integer> entityIds = dims.get(dim);
        if (entityIds == null || entityIds.isEmpty()) {
            dims.remove(dim);
            return;
        }
        ServerLevel targetLevel = server.getLevel(dim);
        List<Integer> toRemove = new ArrayList<>();
        for (Integer entityId : List.copyOf(entityIds)) {
            Entity entity = targetLevel != null ? targetLevel.getEntity(entityId) : null;
            if (entity == null
                    || entity.isRemoved()
                    || !planner.isEntityStillVisibleToPlayer(
                            playerId,
                            dim,
                            entity.chunkPosition().toLong(),
                            entity.getX(),
                            entity.getY(),
                            entity.getZ()
                    )) {
                entityIds.remove(entityId);
                toRemove.add(entityId);
            }
        }
        if (entityIds.isEmpty()) {
            dims.remove(dim);
        }
        if (dims.isEmpty()) {
            pairedEntitiesByPlayerAndDim.remove(playerId);
        }
        if (!toRemove.isEmpty() && player != null && !player.isRemoved() && targetLevel != null) {
            sendEntityRemoval(player, targetLevel, toRemove);
        }
    }

    private static void sendEntityRemoval(ServerPlayer player, ServerLevel targetLevel, Collection<Integer> entityIds) {
        if (entityIds.isEmpty()) {
            return;
        }
        int[] ids = entityIds.stream().mapToInt(Integer::intValue).toArray();
        ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(ids);
        RemoteEntitySyncPayload payload = RemoteEntitySyncPayload.fromPacket(
                targetLevel.dimension(),
                ids[0],
                RemoteEntitySyncPayload.SyncKind.REMOVE_ENTITIES,
                targetLevel.registryAccess(),
                removePacket
        );
        SeamlessNetwork.sendToPlayer(player, payload);
    }
}
