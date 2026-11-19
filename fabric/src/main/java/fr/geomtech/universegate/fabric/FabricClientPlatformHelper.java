package fr.geomtech.universegate.fabric;

import fr.geomtech.universegate.platform.ClientPlatformHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Consumer;

public final class FabricClientPlatformHelper implements ClientPlatformHelper {

    @Override
    public <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
