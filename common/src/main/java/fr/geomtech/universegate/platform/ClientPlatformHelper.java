package fr.geomtech.universegate.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Consumer;

/**
 * Client-side networking abstraction.
 */
public interface ClientPlatformHelper {

    /** The handler may run on the network thread; hop to the client thread before touching game state. */
    <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type, Consumer<T> handler);

    void sendToServer(CustomPacketPayload payload);
}
