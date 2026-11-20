package fr.geomtech.universegate.neoforge;

import fr.geomtech.universegate.platform.ClientPlatformHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? if >=1.21.11 {
/*import net.neoforged.neoforge.client.network.ClientPacketDistributor;
*///?} else {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import java.util.function.Consumer;

public final class NeoForgeClientPlatformHelper implements ClientPlatformHelper {

    @Override
    public <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        NeoForgePlatformHelper.CLIENT_RECEIVERS.put(type, handler);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        //? if >=1.21.11 {
        /*ClientPacketDistributor.sendToServer(payload);
        *///?} else {
        PacketDistributor.sendToServer(payload);
        //?}
    }
}
