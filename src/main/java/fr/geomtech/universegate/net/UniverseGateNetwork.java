package fr.geomtech.universegate.net;

import fr.geomtech.universegate.DarkEnergyNetworkHelper;
import fr.geomtech.universegate.EnergyNetworkHelper;
import fr.geomtech.universegate.ModBlocks;
import fr.geomtech.universegate.ModSounds;
import fr.geomtech.universegate.PortalConnectionManager;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.PortalInfo;
import fr.geomtech.universegate.PortalKeyboardBlockEntity;
import fr.geomtech.universegate.PortalKeyboardMenu;
import fr.geomtech.universegate.PortalMobileKeyboardMenu;
import fr.geomtech.universegate.PortalNaturalKeyboardBlockEntity;
import fr.geomtech.universegate.PortalNaturalKeyboardMenu;
import fr.geomtech.universegate.PortalRiftHelper;
import fr.geomtech.universegate.PortalRegistrySavedData;
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
    private static final int CORE_SEARCH_RADIUS_XZ = 8;
    private static final int CORE_SEARCH_RADIUS_Y = 4;

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

                PortalControllerAccess access = resolveControllerAccess(player, payload.keyboardPos());
                if (access == null) return;

                boolean isNaturalKeyboard = access.naturalKeyboard();
                BlockPos corePos = access.corePos();
                if (corePos == null) {
                    sendPortalConnectionError(player, "Aucun cœur de portail à proximité du clavier.");
                    return;
                }

                if (!(level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity coreBe)) return;

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
                sendPortalKeyboardStatusToPlayer(player, access.controllerPos());
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

                PortalControllerAccess access = resolveControllerAccess(player, payload.keyboardPos());
                if (access == null || access.corePos() == null) return;

                BlockPos corePos = access.corePos();

                if (level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core
                        && canDisconnectFromKeyboard(core)) {
                    PortalConnectionManager.forceCloseOneSide(level, corePos);
                }

                sendPortalKeyboardStatusToPlayer(player, access.controllerPos());
            });
        });
    }

    public static void sendPortalListToPlayer(net.minecraft.server.level.ServerPlayer player, BlockPos keyboardPos) {
        PortalControllerAccess access = resolveControllerAccess(player, keyboardPos);
        if (access == null) return;

        var reg = PortalRegistrySavedData.get(player.server);
        reg.pruneMissingPortals(player.server);
        ServerLevel level = player.serverLevel();
        boolean freeOpening = EnergyNetworkHelper.isRiftDimension(level) || access.naturalKeyboard();

        UUID selfPortalId = null;
        BlockPos corePos = access.corePos();
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

        ServerPlayNetworking.send(player, new PortalListPayload(access.controllerPos(), list));
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

        PortalControllerAccess access = resolveControllerAccess(player, keyboardPos);
        BlockPos corePos = access == null ? null : access.corePos();
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

    private record PortalControllerAccess(BlockPos controllerPos, BlockPos corePos, boolean naturalKeyboard) { }

    private static PortalControllerAccess resolveControllerAccess(net.minecraft.server.level.ServerPlayer player,
                                                                  BlockPos controllerPos) {
        ServerLevel level = player.serverLevel();

        if (player.containerMenu instanceof PortalNaturalKeyboardMenu naturalMenu
                && naturalMenu.getKeyboardPos().equals(controllerPos)) {
            BlockPos corePos = findCoreNear(level, controllerPos, CORE_SEARCH_RADIUS_XZ);
            return new PortalControllerAccess(controllerPos, corePos, true);
        }

        if (player.containerMenu instanceof PortalKeyboardMenu keyboardMenu
                && keyboardMenu.getKeyboardPos().equals(controllerPos)) {
            BlockPos corePos = findCoreNear(level, controllerPos, CORE_SEARCH_RADIUS_XZ);
            return new PortalControllerAccess(controllerPos, corePos, false);
        }

        if (player.containerMenu instanceof PortalMobileKeyboardMenu mobileMenu
                && mobileMenu.getKeyboardPos().equals(controllerPos)) {
            BlockPos corePos = findCoreNear(level, controllerPos, CORE_SEARCH_RADIUS_XZ);
            return new PortalControllerAccess(controllerPos, corePos, isNaturalKeyboardContext(level, corePos));
        }

        if (level.getBlockEntity(controllerPos) instanceof PortalNaturalKeyboardBlockEntity) {
            BlockPos corePos = findCoreNear(level, controllerPos, CORE_SEARCH_RADIUS_XZ);
            return new PortalControllerAccess(controllerPos, corePos, true);
        }

        if (level.getBlockEntity(controllerPos) instanceof PortalKeyboardBlockEntity) {
            BlockPos corePos = findCoreNear(level, controllerPos, CORE_SEARCH_RADIUS_XZ);
            return new PortalControllerAccess(controllerPos, corePos, false);
        }

        return null;
    }

    private static boolean isNaturalKeyboardContext(ServerLevel level, BlockPos corePos) {
        if (corePos == null) return false;
        return hasNaturalKeyboardNear(level, corePos, CORE_SEARCH_RADIUS_XZ, CORE_SEARCH_RADIUS_Y);
    }

    private static boolean hasNaturalKeyboardNear(ServerLevel level, BlockPos center, int radiusXZ, int radiusY) {
        for (int y = -radiusY; y <= radiusY; y++) {
            for (int x = -radiusXZ; x <= radiusXZ; x++) {
                for (int z = -radiusXZ; z <= radiusXZ; z++) {
                    BlockPos p = center.offset(x, y, z);
                    if (!level.hasChunkAt(p)) continue;
                    if (level.getBlockState(p).is(ModBlocks.PORTAL_NATURAL_KEYBOARD)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String shortId(java.util.UUID id) {
        String s = id.toString();
        return "Portal " + s.substring(0, 8);
    }

    private static BlockPos findCoreNear(ServerLevel level, BlockPos center, int r) {
        for (int y = -CORE_SEARCH_RADIUS_Y; y <= CORE_SEARCH_RADIUS_Y; y++) {
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
