package fr.geomtech.universegate.neoforge;

import fr.geomtech.universegate.platform.ClientPlatformHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

public final class NeoForgeClientPlatformHelper implements ClientPlatformHelper {

    @Override
    public <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        NeoForgePlatformHelper.CLIENT_RECEIVERS.put(type, handler);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
