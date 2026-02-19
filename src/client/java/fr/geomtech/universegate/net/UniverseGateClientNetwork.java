package fr.geomtech.universegate.net;

import fr.geomtech.universegate.PortalCoreScreen;
import fr.geomtech.universegate.PortalKeyboardScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class UniverseGateClientNetwork {

    private UniverseGateClientNetwork() {}

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(PortalListPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof PortalKeyboardScreen screen) {
                    screen.setPortals(payload.portals());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PortalKeyboardStatusPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof PortalKeyboardScreen screen) {
                    if (screen.getKeyboardPos().equals(payload.keyboardPos())) {
                        screen.setPortalStatus(payload.active(), payload.disconnectAllowed());
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PortalCoreNamePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof PortalCoreScreen screen) {
                    if (screen.getCorePos().equals(payload.corePos())) {
                        screen.setPortalName(payload.name());
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PortalConnectionErrorPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof PortalKeyboardScreen screen) {
                    screen.showError(payload.errorMessage());
                }
            });
        });
    }
}
