package fr.geomtech.universegate.net;

import fr.geomtech.universegate.DarkEnergyNetworkHelper;
import fr.geomtech.universegate.ModBlocks;
import fr.geomtech.universegate.EnergyNetworkHelper;
import fr.geomtech.universegate.ModSounds;
import fr.geomtech.universegate.PortalConnectionManager;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.PortalKeyboardBlockEntity;
import fr.geomtech.universegate.PortalNaturalKeyboardBlockEntity;
import fr.geomtech.universegate.PortalRiftHelper;
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

    public static final UUID DARK_DIMENSION_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    public static void registerCommon() {
        // types
        PayloadTypeRegistry.playS2C().register(PortalListPayload.TYPE, PortalListPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PortalCoreNamePayload.TYPE, PortalCoreNamePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PortalKeyboardStatusPayload.TYPE, PortalKeyboardStatusPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PortalConnectionErrorPayload.TYPE, PortalConnectionErrorPayload.STREAM_CODEC);
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

                // Check if it's a natural keyboard (no energy required)
                boolean isNaturalKeyboard = kb instanceof PortalNaturalKeyboardBlockEntity;

                // Trouver un core proche du keyboard (rayon 8)
                BlockPos corePos = findCoreNear(level, payload.keyboardPos(), 8);
                if (corePos == null) {
                    sendPortalConnectionError(player, "Aucun cœur de portail à proximité du clavier.");
                    return;
                }
                
                PortalCoreBlockEntity coreBe = null;
                if (level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity be) {
                    coreBe = be;
                }
                if (coreBe == null) return;

                // Ouvrir Stargate A<->B
                boolean isFromRift = EnergyNetworkHelper.isRiftDimension(level);
                UUID targetPortalId = payload.targetPortalId();

                // Logic for Dark Dimension
                if (payload.targetPortalId().equals(DARK_DIMENSION_ID)) {
                    boolean charged = coreBe.isDarkEnergyComplete();
                    boolean linked = DarkEnergyNetworkHelper.isPortalPowered(level, corePos);
                    if (!charged || !linked) {
                        ModSounds.playAt(level, payload.keyboardPos(), ModSounds.PORTAL_ERROR, 0.9F, 1.0F);
                        if (!charged) {
                            sendPortalConnectionError(player, "Énergie Noire insuffisante pour la Dark Dimension.");
                        } else {
                            sendPortalConnectionError(player, "La destination Dark est verrouillée: générateur noir non relié.");
                        }
                        return;
                    }

                    PortalRegistrySavedData.PortalEntry darkTarget = PortalRiftHelper.ensureDarkDimensionTarget(level);
                    if (darkTarget == null) {
                        ModSounds.playAt(level, payload.keyboardPos(), ModSounds.PORTAL_ERROR, 0.9F, 1.0F);
                        sendPortalConnectionError(player, "Aucun portail Dark disponible pour le moment.");
                        return;
                    }
                    targetPortalId = darkTarget.id();
                }

                // Cost logic: free from Rift/natural keyboard, otherwise dynamic
                boolean requiresOpenEnergy = !isFromRift && !isNaturalKeyboard;
                int openEnergyCost = requiresOpenEnergy
                        ? EnergyNetworkHelper.getPortalOpenEnergyCost(level, corePos, targetPortalId)
                        : 0;

                boolean ok = PortalConnectionManager.openBothSides(
                        level,
                        corePos,
                        targetPortalId,
                        false,
                        isNaturalKeyboard
                );
                if (ok) {
                    if (requiresOpenEnergy) {
                        boolean consumed = EnergyNetworkHelper.consumePortalEnergy(level, corePos, openEnergyCost);
                        if (!consumed) {
                            PortalConnectionManager.forceCloseOneSide(level, corePos);
                            ModSounds.playAt(level, payload.keyboardPos(), ModSounds.PORTAL_ERROR, 0.9F, 1.0F);
                            sendPortalConnectionError(player, "Énergie insuffisante pour l'ouverture du portail (" + openEnergyCost + " EU requis).");
                        }
                    }
                } else {
                    ModSounds.playAt(level, corePos, ModSounds.PORTAL_ERROR, 0.9F, 1.0F);
                    sendPortalConnectionError(player, "Le portail cible est introuvable ou déjà actif.");
                }
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

                if (!(level.getBlockEntity(payload.keyboardPos()) instanceof PortalKeyboardBlockEntity)) return;

                BlockPos corePos = findCoreNear(level, payload.keyboardPos(), 8);
                if (corePos == null) return;

                if (level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core
                        && canDisconnectFromKeyboard(core)) {
                    PortalConnectionManager.forceCloseOneSide(level, corePos);
                }

                sendPortalKeyboardStatusToPlayer(player, payload.keyboardPos());
            });
        });
    }

    public static void sendPortalListToPlayer(net.minecraft.server.level.ServerPlayer player, BlockPos keyboardPos) {
        var reg = PortalRegistrySavedData.get(player.server);
        reg.pruneMissingPortals(player.server);
        ServerLevel level = player.serverLevel();
        boolean freeOpening = EnergyNetworkHelper.isRiftDimension(level)
                || level.getBlockEntity(keyboardPos) instanceof PortalNaturalKeyboardBlockEntity;

        UUID selfPortalId = null;
        BlockPos corePos = findCoreNear(level, keyboardPos, 8);
        if (corePos != null && level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core) {
            selfPortalId = core.getPortalId();
        }

        UUID excludedId = selfPortalId;
        List<PortalInfo> list = reg.listVisible().stream()
                .filter(e -> excludedId == null || !excludedId.equals(e.id()))
                .map(e -> new PortalInfo(
                        e.id(),
                        e.name().isEmpty() ? shortId(e.id()) : e.name(),
                        e.dim().location(),
                        e.pos(),
                        computePortalOpenCostForUi(level, corePos, e, freeOpening)
                ))
                .collect(Collectors.toList());

        // Inject Dark Dimension
        PortalRegistrySavedData.PortalEntry darkEntry = reg.get(DARK_DIMENSION_ID);
        int darkOpenCost = computePortalOpenCostForUi(level, corePos, darkEntry, freeOpening);
        list.add(new PortalInfo(
                DARK_DIMENSION_ID,
                "§cDark Dimension",
                net.minecraft.resources.ResourceLocation.parse("universegate:rift"),
                BlockPos.ZERO,
                darkOpenCost
        ));

        ServerPlayNetworking.send(player, new PortalListPayload(keyboardPos, list));
    }

    private static int computePortalOpenCostForUi(ServerLevel sourceLevel,
                                                  BlockPos sourceCorePos,
                                                  PortalRegistrySavedData.PortalEntry targetEntry,
                                                  boolean freeOpening) {
        if (freeOpening) return 0;
        if (sourceCorePos == null || targetEntry == null) {
            return EnergyNetworkHelper.PORTAL_OPEN_BASE_ENERGY_COST;
        }
        return EnergyNetworkHelper.getPortalOpenEnergyCost(sourceLevel, sourceCorePos, targetEntry);
    }

    public static void sendPortalKeyboardStatusToPlayer(net.minecraft.server.level.ServerPlayer player, BlockPos keyboardPos) {
        boolean active = false;
        boolean disconnectAllowed = false;

        BlockPos corePos = findCoreNear(player.serverLevel(), keyboardPos, 8);
        if (corePos != null && player.serverLevel().getBlockEntity(corePos) instanceof PortalCoreBlockEntity core) {
            active = core.isActiveOrOpening();
            disconnectAllowed = canDisconnectFromKeyboard(core);
        }
        ServerPlayNetworking.send(player, new PortalKeyboardStatusPayload(keyboardPos, active, disconnectAllowed));
    }

    private static boolean canDisconnectFromKeyboard(PortalCoreBlockEntity core) {
        if (!core.isActiveOrOpening()) return false;
        if (core.isRiftLightningLink()) return false;
        return core.isOutboundTravelEnabled();
    }

    public static void sendPortalCoreNameToPlayer(net.minecraft.server.level.ServerPlayer player, BlockPos corePos) {
        if (!(player.serverLevel().getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)) return;
        ServerPlayNetworking.send(player, new PortalCoreNamePayload(corePos, core.getPortalName()));
    }

    public static void sendPortalConnectionError(net.minecraft.server.level.ServerPlayer player, String errorMessage) {
        ServerPlayNetworking.send(player, new PortalConnectionErrorPayload(errorMessage));
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
                    if (!level.hasChunkAt(p)) continue;
                    if (level.getBlockState(p).is(ModBlocks.PORTAL_CORE)) return p;
                }
            }
        }
        return null;
    }
}
