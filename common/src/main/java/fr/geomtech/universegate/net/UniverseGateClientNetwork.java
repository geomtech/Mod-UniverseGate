package fr.geomtech.universegate.net;

import fr.geomtech.universegate.PortalCoreScreen;
import fr.geomtech.universegate.PortalKeyboardScreen;
import fr.geomtech.universegate.platform.ClientServices;
import fr.geomtech.universegate.seamless.net.SeamlessNetwork;
import net.minecraft.client.Minecraft;

public final class UniverseGateClientNetwork {

    private UniverseGateClientNetwork() {}

    public static void registerClient() {
        ClientServices.CLIENT.registerClientReceiver(PortalListPayload.TYPE, payload -> {
            Minecraft.getInstance().execute(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.screen instanceof PortalKeyboardScreen screen) {
                    screen.setPortals(payload.portals());
                }
            });
        });

        ClientServices.CLIENT.registerClientReceiver(PortalKeyboardStatusPayload.TYPE, payload -> {
            Minecraft.getInstance().execute(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.screen instanceof PortalKeyboardScreen screen) {
                    if (screen.getKeyboardPos().equals(payload.keyboardPos())) {
                        screen.setPortalStatus(payload.active(), payload.disconnectAllowed());
                    }
                }
            });
        });

        ClientServices.CLIENT.registerClientReceiver(PortalCoreNamePayload.TYPE, payload -> {
            Minecraft.getInstance().execute(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.screen instanceof PortalCoreScreen screen) {
                    if (screen.getCorePos().equals(payload.corePos())) {
                        screen.setPortalName(payload.name());
                    }
                }
            });
        });

        ClientServices.CLIENT.registerClientReceiver(PortalFieldCollapsePayload.TYPE, payload -> {
            Minecraft.getInstance().execute(() -> {
                var level = Minecraft.getInstance().level;
                if (level != null) {
                    fr.geomtech.universegate.PortalFieldCollapseParticles.spawn(level, payload);
                }
            });
        });
        ClientServices.CLIENT.registerClientReceiver(PortalConnectionErrorPayload.TYPE, payload -> {
            Minecraft.getInstance().execute(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.screen instanceof PortalKeyboardScreen screen) {
                    screen.showError(payload.errorMessage());
                }
            });
        });

        SeamlessNetwork.registerClientReceivers();
        fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager.initClient();
        fr.geomtech.universegate.seamless.client.SeamlessSwitchCoordinator.initClient();
    }
}
