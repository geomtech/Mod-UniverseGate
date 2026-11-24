package fr.geomtech.universegate.seamless.net;

import fr.geomtech.universegate.compat.Compat;
import fr.geomtech.universegate.platform.ClientServices;
import fr.geomtech.universegate.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class SeamlessNetwork {

    private static final BiConsumer<PortalViewSubscriptionPayload, ServerPlayer> NOOP_SUBSCRIPTION_HANDLER =
            (payload, player) -> {};
    private static final Consumer<PortalRemoteEndpointPayload> NOOP_ENDPOINT_HANDLER = payload -> {};
    private static final Consumer<RemoteChunkDataPayload> NOOP_CHUNK_DATA_HANDLER = payload -> {};
    private static final Consumer<RemoteChunkForgetPayload> NOOP_CHUNK_FORGET_HANDLER = payload -> {};
    private static final Consumer<RemoteBlockDeltaPayload> NOOP_BLOCK_DELTA_HANDLER = payload -> {};
    private static final Consumer<RemoteEntitySyncPayload> NOOP_ENTITY_SYNC_HANDLER = payload -> {};
    private static final Consumer<SeamlessSwitchHintPayload> NOOP_SWITCH_HINT_HANDLER = payload -> {};

    private static volatile boolean payloadsRegistered = false;
    private static volatile boolean clientReceiversRegistered = false;

    private static volatile BiConsumer<PortalViewSubscriptionPayload, ServerPlayer> viewSubscriptionHandler =
            NOOP_SUBSCRIPTION_HANDLER;
    private static volatile Consumer<PortalRemoteEndpointPayload> remoteEndpointHandler =
            NOOP_ENDPOINT_HANDLER;
    private static volatile Consumer<RemoteChunkDataPayload> remoteChunkDataHandler =
            NOOP_CHUNK_DATA_HANDLER;
    private static volatile Consumer<RemoteChunkForgetPayload> remoteChunkForgetHandler =
            NOOP_CHUNK_FORGET_HANDLER;
    private static volatile Consumer<RemoteBlockDeltaPayload> remoteBlockDeltaHandler =
            NOOP_BLOCK_DELTA_HANDLER;
    private static volatile Consumer<RemoteEntitySyncPayload> remoteEntitySyncHandler =
            NOOP_ENTITY_SYNC_HANDLER;
    private static volatile Consumer<SeamlessSwitchHintPayload> switchHintHandler =
            NOOP_SWITCH_HINT_HANDLER;

    private SeamlessNetwork() {}

    public static synchronized void registerPayloads() {
        if (payloadsRegistered) {
            return;
        }
        payloadsRegistered = true;

        Services.PLATFORM.registerClientboundPayload(
                PortalRemoteEndpointPayload.TYPE,
                PortalRemoteEndpointPayload.STREAM_CODEC
        );
        Services.PLATFORM.registerClientboundPayload(
                RemoteChunkDataPayload.TYPE,
                RemoteChunkDataPayload.STREAM_CODEC
        );
        Services.PLATFORM.registerClientboundPayload(
                RemoteChunkForgetPayload.TYPE,
                RemoteChunkForgetPayload.STREAM_CODEC
        );
        Services.PLATFORM.registerClientboundPayload(
                RemoteBlockDeltaPayload.TYPE,
                RemoteBlockDeltaPayload.STREAM_CODEC
        );
        Services.PLATFORM.registerClientboundPayload(
                RemoteEntitySyncPayload.TYPE,
                RemoteEntitySyncPayload.STREAM_CODEC
        );
        Services.PLATFORM.registerClientboundPayload(
                SeamlessSwitchHintPayload.TYPE,
                SeamlessSwitchHintPayload.STREAM_CODEC
        );

        Services.PLATFORM.registerServerboundPayload(
                PortalViewSubscriptionPayload.TYPE,
                PortalViewSubscriptionPayload.STREAM_CODEC,
                (payload, player) -> Compat.server(player).execute(() -> viewSubscriptionHandler.accept(payload, player))
        );
    }

    public static synchronized void registerClientReceivers() {
        if (clientReceiversRegistered) {
            return;
        }
        clientReceiversRegistered = true;
        ClientDispatcher.register();
    }

    public static void setViewSubscriptionHandler(@Nullable BiConsumer<PortalViewSubscriptionPayload, ServerPlayer> handler) {
        viewSubscriptionHandler = handler != null ? handler : NOOP_SUBSCRIPTION_HANDLER;
    }

    public static void setRemoteEndpointHandler(@Nullable Consumer<PortalRemoteEndpointPayload> handler) {
        remoteEndpointHandler = handler != null ? handler : NOOP_ENDPOINT_HANDLER;
    }

    public static void setRemoteChunkDataHandler(@Nullable Consumer<RemoteChunkDataPayload> handler) {
        remoteChunkDataHandler = handler != null ? handler : NOOP_CHUNK_DATA_HANDLER;
    }

    public static void setRemoteChunkForgetHandler(@Nullable Consumer<RemoteChunkForgetPayload> handler) {
        remoteChunkForgetHandler = handler != null ? handler : NOOP_CHUNK_FORGET_HANDLER;
    }

    public static void setRemoteBlockDeltaHandler(@Nullable Consumer<RemoteBlockDeltaPayload> handler) {
        remoteBlockDeltaHandler = handler != null ? handler : NOOP_BLOCK_DELTA_HANDLER;
    }

    public static void setRemoteEntitySyncHandler(@Nullable Consumer<RemoteEntitySyncPayload> handler) {
        remoteEntitySyncHandler = handler != null ? handler : NOOP_ENTITY_SYNC_HANDLER;
    }

    public static void setSwitchHintHandler(@Nullable Consumer<SeamlessSwitchHintPayload> handler) {
        switchHintHandler = handler != null ? handler : NOOP_SWITCH_HINT_HANDLER;
    }

    public static void resetHandlers() {
        viewSubscriptionHandler = NOOP_SUBSCRIPTION_HANDLER;
        remoteEndpointHandler = NOOP_ENDPOINT_HANDLER;
        remoteChunkDataHandler = NOOP_CHUNK_DATA_HANDLER;
        remoteChunkForgetHandler = NOOP_CHUNK_FORGET_HANDLER;
        remoteBlockDeltaHandler = NOOP_BLOCK_DELTA_HANDLER;
        remoteEntitySyncHandler = NOOP_ENTITY_SYNC_HANDLER;
        switchHintHandler = NOOP_SWITCH_HINT_HANDLER;
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(payload, "payload");
        Services.PLATFORM.sendToPlayer(player, payload);
    }

    public static void sendSubscriptionToServer(BlockPos sourceCorePos, boolean subscribe) {
        Objects.requireNonNull(sourceCorePos, "sourceCorePos");
        ClientDispatcher.sendSubscription(new PortalViewSubscriptionPayload(sourceCorePos, subscribe));
    }

    private static final class ClientDispatcher {
        private static void register() {
            ClientServices.CLIENT.registerClientReceiver(
                    PortalRemoteEndpointPayload.TYPE,
                    payload -> Minecraft.getInstance().execute(() -> remoteEndpointHandler.accept(payload))
            );
            ClientServices.CLIENT.registerClientReceiver(
                    RemoteChunkDataPayload.TYPE,
                    payload -> Minecraft.getInstance().execute(() -> remoteChunkDataHandler.accept(payload))
            );
            ClientServices.CLIENT.registerClientReceiver(
                    RemoteChunkForgetPayload.TYPE,
                    payload -> Minecraft.getInstance().execute(() -> remoteChunkForgetHandler.accept(payload))
            );
            ClientServices.CLIENT.registerClientReceiver(
                    RemoteBlockDeltaPayload.TYPE,
                    payload -> Minecraft.getInstance().execute(() -> remoteBlockDeltaHandler.accept(payload))
            );
            ClientServices.CLIENT.registerClientReceiver(
                    RemoteEntitySyncPayload.TYPE,
                    payload -> Minecraft.getInstance().execute(() -> remoteEntitySyncHandler.accept(payload))
            );
            // Switch hint only updates a volatile slot in SeamlessSwitchCoordinator and MUST run
            // synchronously before ClientboundRespawnPacket executes on the main thread; wrapping
            // in Minecraft.execute() on NeoForge re-enqueues it *behind* handleRespawn because
            // ReentrantBlockableEventLoop.scheduleExecutables() returns true during runAllTasks().
            ClientServices.CLIENT.registerClientReceiver(
                    SeamlessSwitchHintPayload.TYPE,
                    payload -> switchHintHandler.accept(payload)
            );
        }

        private static void sendSubscription(PortalViewSubscriptionPayload payload) {
            ClientServices.CLIENT.sendToServer(payload);
        }
    }
}
