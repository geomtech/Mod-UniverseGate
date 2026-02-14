package fr.geomtech.universegate.net;

import fr.geomtech.universegate.ModBlocks;
import fr.geomtech.universegate.ModSounds;
import fr.geomtech.universegate.PortalConnectionManager;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.PortalKeyboardBlockEntity;
import fr.geomtech.universegate.PortalRegistrySavedData;
import fr.geomtech.universegate.PortalInfo;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class UniverseGateNetwork {

    private UniverseGateNetwork() {}

    public static void registerCommon() {
        // types
        PayloadTypeRegistry.playS2C().register(PortalListPayload.TYPE, PortalListPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PortalCoreNamePayload.TYPE, PortalCoreNamePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PortalKeyboardStatusPayload.TYPE, PortalKeyboardStatusPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ConnectPortalPayload.TYPE, ConnectPortalPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RenamePortalPayload.TYPE, RenamePortalPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DisconnectPortalPayload.TYPE, DisconnectPortalPayload.STREAM_CODEC);

        // handler C2S (connect)
        ServerPlayNetworking.registerGlobalReceiver(ConnectPortalPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                ServerLevel level = player.serverLevel();

                // Keyboard à la position indiquée
                if (!(level.getBlockEntity(payload.keyboardPos()) instanceof PortalKeyboardBlockEntity kb)) return;

                // Trouver un core proche du keyboard (rayon 8)
                BlockPos corePos = findCoreNear(level, payload.keyboardPos(), 8);
                if (corePos == null) return;

                // Ouvrir Stargate A<->B
                if (kb.fuelCount() <= 0) {
                    ModSounds.playAt(level, payload.keyboardPos(), ModSounds.PORTAL_ERROR, 0.9F, 1.0F);
                    return;
                }
                boolean ok = PortalConnectionManager.openBothSides(level, corePos, payload.targetPortalId());
                if (ok) {
                    kb.consumeOneFuel();
                } else {
                    ModSounds.playAt(level, corePos, ModSounds.PORTAL_ERROR, 0.9F, 1.0F);
                }
                // (Optionnel: feedback joueur)
                // player.displayClientMessage(Component.literal(ok ? "§aConnexion établie" : "§cConnexion impossible"), true);
                sendPortalKeyboardStatusToPlayer(player, payload.keyboardPos());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RenamePortalPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                ServerLevel level = player.serverLevel();

                if (!(level.getBlockEntity(payload.corePos()) instanceof PortalCoreBlockEntity core)) return;

                core.renamePortal(payload.name());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DisconnectPortalPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                ServerLevel level = player.serverLevel();

                BlockPos corePos = findCoreNear(level, payload.keyboardPos(), 8);
                if (corePos == null) return;

                if (level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core
                        && core.isActiveOrOpening()
                        && core.isOutboundTravelEnabled()) {
                    PortalConnectionManager.forceCloseOneSide(level, corePos);
                }

                sendPortalKeyboardStatusToPlayer(player, payload.keyboardPos());
            });
        });
    }

    public static void sendPortalListToPlayer(net.minecraft.server.level.ServerPlayer player, BlockPos keyboardPos) {
        var reg = PortalRegistrySavedData.get(player.server);

        UUID selfPortalId = null;
        BlockPos corePos = findCoreNear(player.serverLevel(), keyboardPos, 8);
        if (corePos != null && player.serverLevel().getBlockEntity(corePos) instanceof PortalCoreBlockEntity core) {
            selfPortalId = core.getPortalId();
        }

        UUID excludedId = selfPortalId;
        List<PortalInfo> list = reg.listVisible().stream()
                .filter(e -> excludedId == null || !excludedId.equals(e.id()))
                .map(e -> new PortalInfo(e.id(), e.name().isEmpty() ? shortId(e.id()) : e.name(), e.dim().location(), e.pos()))
                .collect(Collectors.toList());

        ServerPlayNetworking.send(player, new PortalListPayload(keyboardPos, list));
    }

    public static void sendPortalKeyboardStatusToPlayer(net.minecraft.server.level.ServerPlayer player, BlockPos keyboardPos) {
        boolean active = false;
        boolean disconnectAllowed = false;
        BlockPos corePos = findCoreNear(player.serverLevel(), keyboardPos, 8);
        if (corePos != null && player.serverLevel().getBlockEntity(corePos) instanceof PortalCoreBlockEntity core) {
            active = core.isActiveOrOpening();
            disconnectAllowed = active && core.isOutboundTravelEnabled();
        }
        ServerPlayNetworking.send(player, new PortalKeyboardStatusPayload(keyboardPos, active, disconnectAllowed));
    }

    public static void sendPortalCoreNameToPlayer(net.minecraft.server.level.ServerPlayer player, BlockPos corePos) {
        if (!(player.serverLevel().getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)) return;
        ServerPlayNetworking.send(player, new PortalCoreNamePayload(corePos, core.getPortalName()));
    }

    private static String shortId(java.util.UUID id) {
        String s = id.toString();
        return "Portal " + s.substring(0, 8);
    }

    private static BlockPos findCoreNear(ServerLevel level, BlockPos center, int r) {
        for (int y = -4; y <= 4; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = center.offset(x, y, z);
                    if (level.getBlockState(p).is(ModBlocks.PORTAL_CORE)) return p;
                }
            }
        }
        return null;
    }
}
