package fr.geomtech.universegate.seamless.client;

import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.mixin.client.ClientPacketListenerAccessor;
import fr.geomtech.universegate.seamless.net.PortalRemoteEndpointPayload;
import fr.geomtech.universegate.seamless.net.RemoteBlockDeltaPayload;
import fr.geomtech.universegate.seamless.net.RemoteChunkDataPayload;
import fr.geomtech.universegate.seamless.net.RemoteChunkForgetPayload;
import fr.geomtech.universegate.seamless.net.RemoteEntitySyncPayload;
import fr.geomtech.universegate.seamless.net.SeamlessNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
//? if >=1.21.11 {
/*import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
*///?}
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages isolated remote {@link ClientLevel} instances on the client for real-time cross-portal
 * rendering and loading-screen-free dimension swaps.
 */
public final class ClientRemoteWorldManager {

    private static final ClientRemoteWorldManager INSTANCE = new ClientRemoteWorldManager();

    private final RemoteWorldBudgetTracker budgetTracker = new RemoteWorldBudgetTracker();
    private final Map<ResourceKey<Level>, Map<BlockPos, PortalRemoteEndpointPayload>> endpointsByDim = new HashMap<>();
    private final Map<ResourceKey<Level>, RemoteWorldEntry> remoteWorlds = new HashMap<>();
    private @Nullable ResourceKey<Level> lastActiveDimension;
    private boolean initialized = false;

    private ClientRemoteWorldManager() {}

    public static ClientRemoteWorldManager getInstance() {
        return INSTANCE;
    }

    public static void initClient() {
        INSTANCE.registerHandlers();
    }

    public synchronized void registerHandlers() {
        if (initialized) {
            return;
        }
        initialized = true;
        SeamlessNetwork.setRemoteEndpointHandler(this::handleRemoteEndpoint);
        SeamlessNetwork.setRemoteChunkDataHandler(this::handleRemoteChunkData);
        SeamlessNetwork.setRemoteChunkForgetHandler(this::handleRemoteChunkForget);
        SeamlessNetwork.setRemoteBlockDeltaHandler(this::handleRemoteBlockDelta);
        SeamlessNetwork.setRemoteEntitySyncHandler(this::handleRemoteEntitySync);
    }

    /**
     * Returns true if {@code level} is one of our remote preview worlds rather than the player's
     * active {@link Minecraft#level}. Used to strictly gate ambient sounds and particles.
     */
    public static boolean isRemoteLevel(@Nullable Level level) {
        if (level == null || !level.isClientSide()) {
            return false;
        }
        return INSTANCE.containsLevelReference(level);
    }

    private boolean containsLevelReference(Level level) {
        for (RemoteWorldEntry entry : remoteWorlds.values()) {
            if (entry.level == level) {
                return true;
            }
        }
        return false;
    }

    // ---------- Incoming S2C Handlers (Client Thread) ----------

    public void handleRemoteEndpoint(PortalRemoteEndpointPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ResourceKey<Level> activeDim = mc.level != null ? mc.level.dimension() : lastActiveDimension;
        if (activeDim == null) {
            return;
        }
        BlockPos corePos = payload.corePos().immutable();
        Map<BlockPos, PortalRemoteEndpointPayload> dimMap =
                endpointsByDim.computeIfAbsent(activeDim, k -> new HashMap<>());

        if (payload.isClosed() || (!payload.isActive() && !payload.isOpening())) {
            PortalRemoteEndpointPayload removed = dimMap.remove(corePos);
            if (removed != null) {
                pruneUnusedRemoteWorld(removed.targetDim());
            }
            return;
        }
        dimMap.put(corePos, payload);
        UniverseGate.LOGGER.info("[SeamlessPortal] Endpoint synced: dim={} core={} -> dim={} target={} exit={} flags={}",
                activeDim.location(), corePos, payload.targetDim().location(), payload.targetCorePos(), payload.exitNormal(), payload.stateFlags());

        if (!payload.targetDim().equals(activeDim)) {
            ChunkPos targetCenter = new ChunkPos(payload.targetCorePos());
            getOrCreateRemoteWorld(mc, payload.targetDim(), targetCenter);
        }
    }

    public void handleRemoteChunkData(RemoteChunkDataPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.level == null) {
            return;
        }

        // Same-dimension portals already have chunks in mc.level; remote worlds only hold other dimensions
        // or far-away chunks not loaded in mc.level.
        if (payload.dim().equals(mc.level.dimension())) {
            if (mc.level.getChunkSource().hasChunk(payload.chunkX(), payload.chunkZ())) {
                return;
            }
        }

        ChunkPos chunkPos = new ChunkPos(payload.chunkX(), payload.chunkZ());
        RemoteWorldEntry entry = getOrCreateRemoteWorld(mc, payload.dim(), chunkPos);
        if (entry == null) {
            return;
        }

        if (!RemoteWorldBudgetTracker.isWithinRingBuffer(
                entry.viewCenter.x, entry.viewCenter.z, entry.viewRadius, payload.chunkX(), payload.chunkZ())) {
            entry.viewCenter = chunkPos;
            entry.level.getChunkSource().updateViewCenter(chunkPos.x, chunkPos.z);
        }

        try {
            ClientboundLevelChunkWithLightPacket vanillaPacket = payload.decodePacket(connection.registryAccess());
            runWithRemoteConnectionLevel(connection, entry.level, () -> {
                ClientPacketListenerAccessor accessor = (ClientPacketListenerAccessor) connection;
                accessor.universegate$updateLevelChunk(vanillaPacket.getX(), vanillaPacket.getZ(), vanillaPacket.getChunkData());
                // Enable section status in LevelLightEngine (mirrors ClientPacketListener#enableChunkLight
                // without dirtying the active world's LevelRenderer) so block light (torches, lamps, lava)
                // and sky light data from applyLightData are actually stored and sampled!
                net.minecraft.world.level.chunk.LevelChunk chunk =
                        entry.level.getChunkSource().getChunk(vanillaPacket.getX(), vanillaPacket.getZ(), false);
                if (chunk != null) {
                    var lightEngine = entry.level.getChunkSource().getLightEngine();
                    var sections = chunk.getSections();
                    for (int i = 0; i < sections.length; i++) {
                        int sy = entry.level.getSectionYFromSectionIndex(i);
                        lightEngine.updateSectionStatus(SectionPos.of(chunkPos, sy), sections[i].hasOnlyAir());
                    }
                    lightEngine.setLightEnabled(chunkPos, true);
                }
                //? if >=1.21.11 {
                /*accessor.universegate$applyLightData(vanillaPacket.getX(), vanillaPacket.getZ(), vanillaPacket.getLightData(), false);
                *///?} else {
                accessor.universegate$applyLightData(vanillaPacket.getX(), vanillaPacket.getZ(), vanillaPacket.getLightData());
                //?}
            });
            entry.level.pollLightUpdates();
            entry.level.getChunkSource().getLightEngine().runLightUpdates();
            entry.level.updateSkyBrightness();
            entry.loadedChunks.add(chunkPos.toLong());
            entry.cachedChunkPayloads.put(chunkPos.toLong(), payload);
            if (entry.renderState != null) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        entry.renderState.markChunkDirty(payload.chunkX() + dx, payload.chunkZ() + dz);
                    }
                }
            }
        } catch (Throwable t) {
            UniverseGate.LOGGER.debug("Failed to apply remote chunk ({}, {}) in {}", payload.chunkX(), payload.chunkZ(), payload.dim(), t);
        }
    }

    public void handleRemoteChunkForget(RemoteChunkForgetPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        RemoteWorldEntry entry = remoteWorlds.get(payload.dim());
        if (connection == null || entry == null) {
            return;
        }

        ChunkPos chunkPos = new ChunkPos(payload.chunkX(), payload.chunkZ());
        entry.loadedChunks.remove(chunkPos.toLong());
        entry.cachedChunkPayloads.remove(chunkPos.toLong());
        try {
            ClientboundForgetLevelChunkPacket forgetPacket = new ClientboundForgetLevelChunkPacket(chunkPos);
            runWithRemoteConnectionLevel(connection, entry.level, () -> {
                entry.level.getChunkSource().drop(chunkPos);
                ((ClientPacketListenerAccessor) connection).universegate$queueLightRemoval(forgetPacket);
            });
        } catch (Throwable t) {
            UniverseGate.LOGGER.debug("Failed to forget remote chunk ({}, {}) in {}", payload.chunkX(), payload.chunkZ(), payload.dim(), t);
        }
    }

    public void handleRemoteBlockDelta(RemoteBlockDeltaPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        RemoteWorldEntry entry = remoteWorlds.get(payload.dim());
        if (connection == null || entry == null) {
            return;
        }

        BlockPos pos = payload.pos();
        try {
            entry.level.setServerVerifiedBlockState(pos, payload.state(), Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
            var lightEngine = entry.level.getChunkSource().getLightEngine();
            lightEngine.checkBlock(pos);
            lightEngine.runLightUpdates();
            if (payload.blockEntityTag().isPresent()) {
                BlockEntity be = entry.level.getBlockEntity(pos);
                if (be != null) {
                    CompoundTag tag = payload.blockEntityTag().get();
                    //? if >=1.21.11 {
                    /*be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, connection.registryAccess(), tag));
                    *///?} else {
                    be.loadWithComponents(tag, connection.registryAccess());
                    //?}
                }
            }
            if (entry.renderState != null) {
                entry.renderState.markSectionDirty(
                        SectionPos.blockToSectionCoord(pos.getX()),
                        SectionPos.blockToSectionCoord(pos.getY()),
                        SectionPos.blockToSectionCoord(pos.getZ()),
                        true
                );
            }
        } catch (Throwable t) {
            UniverseGate.LOGGER.debug("Failed to apply remote block delta at {} in {}", pos, payload.dim(), t);
        }
    }

    public void handleRemoteEntitySync(RemoteEntitySyncPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        RemoteWorldEntry entry = remoteWorlds.get(payload.dim());
        if (connection == null || entry == null) {
            return;
        }

        try {
            Packet<?> packet = payload.decodeVanillaPacket(connection.registryAccess());
            runWithRemoteConnectionLevel(connection, entry.level, () -> dispatchEntityPacket(connection, packet));
        } catch (Throwable t) {
            UniverseGate.LOGGER.debug("Failed to apply remote entity sync {} for entity {} in {}",
                    payload.kind(), payload.entityId(), payload.dim(), t);
        }
    }

    private static void dispatchEntityPacket(ClientPacketListener connection, Packet<?> packet) {
        if (packet instanceof ClientboundAddEntityPacket p) {
            connection.handleAddEntity(p);
        } else if (packet instanceof ClientboundSetEntityDataPacket p) {
            connection.handleSetEntityData(p);
        } else if (packet instanceof ClientboundUpdateAttributesPacket p) {
            connection.handleUpdateAttributes(p);
        } else if (packet instanceof ClientboundSetEquipmentPacket p) {
            connection.handleSetEquipment(p);
        } else if (packet instanceof ClientboundMoveEntityPacket p) {
            connection.handleMoveEntity(p);
        } else if (packet instanceof ClientboundTeleportEntityPacket p) {
            connection.handleTeleportEntity(p);
        } else if (packet instanceof ClientboundSetEntityMotionPacket p) {
            connection.handleSetEntityMotion(p);
        } else if (packet instanceof ClientboundRotateHeadPacket p) {
            connection.handleRotateMob(p);
        } else if (packet instanceof ClientboundRemoveEntitiesPacket p) {
            connection.handleRemoveEntities(p);
        } else if (packet instanceof ClientboundSetPassengersPacket p) {
            connection.handleSetEntityPassengersPacket(p);
        } else if (packet instanceof ClientboundSetEntityLinkPacket p) {
            connection.handleEntityLinkPacket(p);
        } else if (packet instanceof ClientboundProjectilePowerPacket p) {
            connection.handleProjectilePowerPacket(p);
        }
        //? if >=1.21.11 {
        /*else if (packet instanceof ClientboundEntityPositionSyncPacket p) {
            connection.handleEntityPositionSync(p);
        } else if (packet instanceof ClientboundMoveMinecartPacket p) {
            connection.handleMinecartAlongTrack(p);
        }
        *///?}
    }

    // ---------- Client Tick & Subscriptions ----------

    public void tickClient(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            if (!remoteWorlds.isEmpty() || !endpointsByDim.isEmpty()) {
                onClientDisconnect();
            }
            return;
        }

        ResourceKey<Level> currentDim = mc.level.dimension();
        if (lastActiveDimension != null && !lastActiveDimension.equals(currentDim)) {
            endpointsByDim.remove(lastActiveDimension);
            budgetTracker.clear();
        }
        lastActiveDimension = currentDim;

        Map<BlockPos, PortalRemoteEndpointPayload> currentEndpoints =
                endpointsByDim.getOrDefault(currentDim, Collections.emptyMap());

        // Compute C2S view subscriptions with hysteresis
        RemoteWorldBudgetTracker.SubscriptionDiff diff = budgetTracker.computeSubscriptionDiff(
                currentEndpoints.keySet(),
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ()
        );
        for (BlockPos unsub : diff.toUnsubscribe()) {
            SeamlessNetwork.sendSubscriptionToServer(unsub, false);
        }
        for (BlockPos sub : diff.toSubscribe()) {
            SeamlessNetwork.sendSubscriptionToServer(sub, true);
        }

        // Advance time, lighting, and entity interpolation on remote ClientLevels
        long activeGameTime = mc.level.getGameTime();
        long activeDayTime = mc.level.getDayTime();
        for (RemoteWorldEntry entry : remoteWorlds.values()) {
            if (entry.level == mc.level) {
                continue;
            }
            try {
                //? if >=1.21.11 {
                /*entry.level.setTimeFromServer(activeGameTime, activeDayTime, false);
                *///?} else {
                entry.level.setGameTime(activeGameTime);
                entry.level.setDayTime(activeDayTime);
                //?}
                entry.level.updateSkyBrightness();
                entry.level.pollLightUpdates();
                entry.level.getChunkSource().getLightEngine().runLightUpdates();
                entry.level.tickEntities();
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------- Queries & Seamless Dimension Swap ----------

    public Collection<PortalRemoteEndpointPayload> getActiveEndpoints() {
        Minecraft mc = Minecraft.getInstance();
        ResourceKey<Level> activeDim = mc.level != null ? mc.level.dimension() : lastActiveDimension;
        if (activeDim == null) {
            return List.of();
        }
        Map<BlockPos, PortalRemoteEndpointPayload> map = endpointsByDim.get(activeDim);
        return map != null ? Collections.unmodifiableCollection(map.values()) : List.of();
    }

    public @Nullable PortalRemoteEndpointPayload getEndpoint(BlockPos corePos) {
        Minecraft mc = Minecraft.getInstance();
        ResourceKey<Level> activeDim = mc.level != null ? mc.level.dimension() : lastActiveDimension;
        if (activeDim == null) {
            return null;
        }
        Map<BlockPos, PortalRemoteEndpointPayload> map = endpointsByDim.get(activeDim);
        return map != null ? map.get(corePos) : null;
    }

    public @Nullable ClientLevel getRemoteLevel(ResourceKey<Level> dim) {
        RemoteWorldEntry entry = remoteWorlds.get(dim);
        return entry != null ? entry.level : null;
    }

    public @Nullable RemoteWorldEntry getRemoteWorldEntry(ResourceKey<Level> dim) {
        return remoteWorlds.get(dim);
    }

    public boolean hasLoadedChunk(ResourceKey<Level> dim, BlockPos pos) {
        RemoteWorldEntry entry = remoteWorlds.get(dim);
        if (entry == null) {
            return false;
        }
        ChunkPos cp = new ChunkPos(pos);
        return entry.loadedChunks.contains(cp.toLong())
                || entry.level.getChunkSource().hasChunk(cp.x, cp.z);
    }

    /**
     * Extracts cached chunk payloads from the preloaded remote world for {@code targetDim} and
     * disposes the temporary remote world cleanly before vanilla's {@code handleRespawn} installs
     * a fresh authentic {@link ClientLevel}. Never demotes {@code previousActiveLevel} into the
     * remote pool so old active entities/state never pollute subsequent remote views.
     */
    public List<RemoteChunkDataPayload> takePreloadedChunksAndDispose(ResourceKey<Level> targetDim) {
        RemoteWorldEntry removed = remoteWorlds.remove(targetDim);
        budgetTracker.removeWorld(targetDim);
        if (removed == null) {
            return List.of();
        }
        List<RemoteChunkDataPayload> payloads = new ArrayList<>(removed.cachedChunkPayloads.values());
        removed.dispose();
        return payloads;
    }

    /**
     * Pre-seeds an authentic vanilla {@link ClientLevel} with cached {@link RemoteChunkDataPayload}s
     * immediately after a seamless dimension switch so frame 0 renders with zero blank terrain flash
     * while preserving vanilla's exact biome seed, lighting engine, and block prediction state.
     */
    public void preseedActiveLevel(ClientPacketListener connection,
                                   ClientLevel activeLevel,
                                   BlockPos centerPos,
                                   List<RemoteChunkDataPayload> payloads) {
        if (connection == null || activeLevel == null || payloads.isEmpty()) {
            return;
        }
        ChunkPos centerChunk = new ChunkPos(centerPos);
        activeLevel.getChunkSource().updateViewCenter(centerChunk.x, centerChunk.z);
        ClientPacketListenerAccessor accessor = (ClientPacketListenerAccessor) connection;
        for (RemoteChunkDataPayload payload : payloads) {
            try {
                ClientboundLevelChunkWithLightPacket vanillaPacket = payload.decodePacket(connection.registryAccess());
                int cx = vanillaPacket.getX();
                int cz = vanillaPacket.getZ();
                accessor.universegate$updateLevelChunk(cx, cz, vanillaPacket.getChunkData());
                net.minecraft.world.level.chunk.LevelChunk chunk = activeLevel.getChunkSource().getChunk(cx, cz, false);
                if (chunk != null) {
                    var lightEngine = activeLevel.getChunkSource().getLightEngine();
                    ChunkPos cp = new ChunkPos(cx, cz);
                    var sections = chunk.getSections();
                    for (int i = 0; i < sections.length; i++) {
                        int sy = activeLevel.getSectionYFromSectionIndex(i);
                        lightEngine.updateSectionStatus(SectionPos.of(cp, sy), sections[i].hasOnlyAir());
                    }
                    lightEngine.setLightEnabled(cp, true);
                }
                //? if >=1.21.11 {
                /*accessor.universegate$applyLightData(cx, cz, vanillaPacket.getLightData(), false);
                *///?} else {
                accessor.universegate$applyLightData(cx, cz, vanillaPacket.getLightData());
                //?}
            } catch (Throwable ignored) {
            }
        }
        activeLevel.pollLightUpdates();
        activeLevel.getChunkSource().getLightEngine().runLightUpdates();
        activeLevel.updateSkyBrightness();
    }

    /**
     * Promotes a preloaded remote {@link ClientLevel} into the active world slot during a seamless
     * dimension switch, demoting the previous active {@link ClientLevel} back into the remote pool
     * so looking backward through the portal immediately renders without flicker.
     */
    public @Nullable RemoteWorldEntry promoteRemoteWorld(ResourceKey<Level> targetDim,
                                                         @Nullable ClientLevel previousActiveLevel,
                                                         @Nullable BlockPos previousPortalCenter) {
        RemoteWorldEntry promoted = remoteWorlds.remove(targetDim);
        budgetTracker.removeWorld(targetDim);
        return promoted;
    }

    public void onClientDisconnect() {
        List<RemoteWorldEntry> entries = new ArrayList<>(remoteWorlds.values());
        remoteWorlds.clear();
        endpointsByDim.clear();
        budgetTracker.clear();
        lastActiveDimension = null;
        for (RemoteWorldEntry entry : entries) {
            entry.dispose();
        }
    }

    // ---------- Internal Helpers ----------

    private @Nullable RemoteWorldEntry getOrCreateRemoteWorld(Minecraft mc,
                                                              ResourceKey<Level> targetDim,
                                                              ChunkPos centerChunk) {
        RemoteWorldEntry existing = remoteWorlds.get(targetDim);
        if (existing != null) {
            evictOverBudget(targetDim);
            return existing;
        }

        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            return null;
        }

        int viewRadius = RemoteWorldBudgetTracker.DEFAULT_REMOTE_CHUNK_RADIUS;
        ClientLevel created = RemoteClientLevelFactory.create(mc, connection, targetDim, centerChunk, viewRadius);
        if (created == null) {
            return null;
        }

        RemoteWorldEntry entry = new RemoteWorldEntry(created, centerChunk, viewRadius);
        remoteWorlds.put(targetDim, entry);
        evictOverBudget(targetDim);
        return entry;
    }

    private void evictOverBudget(ResourceKey<Level> touchedDim) {
        List<ResourceKey<Level>> evicted = budgetTracker.touchAndCollectEvictions(touchedDim, System.currentTimeMillis());
        for (ResourceKey<Level> dim : evicted) {
            RemoteWorldEntry removed = remoteWorlds.remove(dim);
            if (removed != null) {
                removed.dispose();
            }
        }
    }

    private void pruneUnusedRemoteWorld(ResourceKey<Level> dim) {
        for (Map<BlockPos, PortalRemoteEndpointPayload> map : endpointsByDim.values()) {
            for (PortalRemoteEndpointPayload remaining : map.values()) {
                if (remaining.targetDim().equals(dim)) {
                    return;
                }
            }
        }
        // Keep recently visited remote worlds cached up to LRU budget so fast re-opening is instant,
        // unless budget overflows (handled by evictOverBudget).
    }

    private static void runWithRemoteConnectionLevel(ClientPacketListener connection,
                                                     ClientLevel remoteLevel,
                                                     Runnable action) {
        ClientPacketListenerAccessor accessor = (ClientPacketListenerAccessor) connection;
        ClientLevel prevLevel = accessor.universegate$getLevel();
        ClientLevel.ClientLevelData prevData = accessor.universegate$getLevelData();
        try {
            accessor.universegate$setLevel(remoteLevel);
            if (remoteLevel.getLevelData() instanceof ClientLevel.ClientLevelData remoteData) {
                accessor.universegate$setLevelData(remoteData);
            }
            action.run();
        } finally {
            accessor.universegate$setLevel(prevLevel);
            accessor.universegate$setLevelData(prevData);
        }
    }

    public static final class RemoteWorldEntry {
        public final ClientLevel level;
        public ChunkPos viewCenter;
        public final int viewRadius;
        public final Set<Long> loadedChunks = new HashSet<>();
        public final Map<Long, RemoteChunkDataPayload> cachedChunkPayloads = new HashMap<>();
        public @Nullable RemoteRenderWorldState renderState;

        public RemoteWorldEntry(ClientLevel level, ChunkPos viewCenter, int viewRadius) {
            this.level = level;
            this.viewCenter = viewCenter;
            this.viewRadius = viewRadius;
        }

        public RemoteRenderWorldState getOrCreateRenderState(Minecraft minecraft) {
            if (renderState == null || renderState.isDisposed()) {
                renderState = new RemoteRenderWorldState(level, viewRadius);
            }
            renderState.ensureInitialized(minecraft);
            return renderState;
        }

        public void dispose() {
            loadedChunks.clear();
            cachedChunkPayloads.clear();
            if (renderState != null) {
                renderState.dispose();
                renderState = null;
            }
        }
    }
}
