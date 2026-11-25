package fr.geomtech.universegate.net;

import fr.geomtech.universegate.compat.Compat;
import fr.geomtech.universegate.EnergyNetworkHelper;
import fr.geomtech.universegate.ModBlocks;
import fr.geomtech.universegate.ModSounds;
import fr.geomtech.universegate.PortalActivatorBlockEntity;
import fr.geomtech.universegate.PortalDialResult;
import fr.geomtech.universegate.PortalDialService;
import fr.geomtech.universegate.PortalConnectionManager;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.PortalInfo;
import fr.geomtech.universegate.PortalKeyboardBlockEntity;
import fr.geomtech.universegate.PortalKeyboardMenu;
import fr.geomtech.universegate.PortalMobileKeyboardMenu;
import fr.geomtech.universegate.PortalNaturalKeyboardBlockEntity;
import fr.geomtech.universegate.PortalNaturalKeyboardMenu;
import fr.geomtech.universegate.PortalRegistrySavedData;
import fr.geomtech.universegate.WireNetwork;
import fr.geomtech.universegate.platform.Services;
import fr.geomtech.universegate.seamless.net.SeamlessNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class UniverseGateNetwork {

    private UniverseGateNetwork() {}

    public static final UUID DARK_DIMENSION_ID = PortalDialService.DARK_DIMENSION_ID;
    private static final int CORE_SEARCH_RADIUS_XZ = 8;
    private static final int CORE_SEARCH_RADIUS_Y = 4;

    public static void registerCommon() {
        // types
        Services.PLATFORM.registerClientboundPayload(PortalListPayload.TYPE, PortalListPayload.STREAM_CODEC);
        Services.PLATFORM.registerClientboundPayload(PortalCoreNamePayload.TYPE, PortalCoreNamePayload.STREAM_CODEC);
        Services.PLATFORM.registerClientboundPayload(PortalKeyboardStatusPayload.TYPE, PortalKeyboardStatusPayload.STREAM_CODEC);
        Services.PLATFORM.registerClientboundPayload(PortalActivatorTargetPayload.TYPE, PortalActivatorTargetPayload.STREAM_CODEC);
        Services.PLATFORM.registerClientboundPayload(PortalConnectionErrorPayload.TYPE, PortalConnectionErrorPayload.STREAM_CODEC);
        Services.PLATFORM.registerClientboundPayload(PortalFieldCollapsePayload.TYPE, PortalFieldCollapsePayload.STREAM_CODEC);
        SeamlessNetwork.registerPayloads();
                        
        // handler C2S (connect)
        Services.PLATFORM.registerServerboundPayload(ConnectPortalPayload.TYPE, ConnectPortalPayload.STREAM_CODEC, (payload, player) -> {
            Compat.server(player).execute(() -> {
                ServerLevel level = Compat.serverLevel(player);

                PortalControllerAccess access = resolveControllerAccess(player, payload.keyboardPos());
                if (access == null) return;

                boolean isNaturalKeyboard = access.naturalKeyboard();
                BlockPos corePos = access.corePos();
                if (level.getBlockEntity(access.controllerPos()) instanceof PortalActivatorBlockEntity activator) {
                    PortalRegistrySavedData registry = PortalRegistrySavedData.get(Compat.server(player));
                    UUID selectedId = payload.targetPortalId();
                    boolean validDestination = DARK_DIMENSION_ID.equals(selectedId)
                            || registry.get(selectedId) != null;
                    boolean selfDestination = corePos != null
                            && level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity sourceCore
                            && selectedId.equals(sourceCore.getPortalId());
                    if (!validDestination || selfDestination) {
                        ModSounds.playAt(level, payload.keyboardPos(), ModSounds.PORTAL_ERROR, 0.9F, 1.0F);
                        sendPortalConnectionError(player, "Destination de portail invalide.");
                        return;
                    }
                    activator.setTargetPortalId(selectedId);
                    sendPortalActivatorTargetToPlayer(player, access.controllerPos());
                    sendPortalKeyboardStatusToPlayer(player, access.controllerPos());
                    return;
                }

                if (corePos == null) {
                    if (!isNaturalKeyboard && level.getBlockState(access.controllerPos()).is(ModBlocks.PORTAL_KEYBOARD)) {
                        ModSounds.playAt(level, payload.keyboardPos(), ModSounds.PORTAL_ERROR, 0.9F, 1.0F);
                        sendPortalConnectionError(player, WireNetwork.hasClip(level, access.controllerPos())
                                ? "Clavier non relié : les fils électriques de ses pinces crocodiles n'atteignent aucun portail."
                                : "Clavier non câblé : pose des pinces crocodiles sur le clavier et relie-les au portail avec des fils électriques.");
                        return;
                    }
                    sendPortalConnectionError(player, "Aucun cœur de portail à proximité du clavier.");
                    return;
                }

                if (!(level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity)) return;

                PortalDialResult result = PortalDialService.dial(
                        level, corePos, payload.targetPortalId(), isNaturalKeyboard);
                if (!result.success()) {
                    ModSounds.playAt(level, corePos, ModSounds.PORTAL_ERROR, 0.9F, 1.0F);
                    sendPortalConnectionError(player, result.errorMessage());
                }
                sendPortalKeyboardStatusToPlayer(player, access.controllerPos());
            });
        });

        Services.PLATFORM.registerServerboundPayload(RenamePortalPayload.TYPE, RenamePortalPayload.STREAM_CODEC, (payload, player) -> {
            Compat.server(player).execute(() -> {
                ServerLevel level = Compat.serverLevel(player);

                if (!(level.getBlockEntity(payload.corePos()) instanceof PortalCoreBlockEntity core)) return;

                core.renamePortal(payload.name());
            });
        });

        Services.PLATFORM.registerServerboundPayload(DisconnectPortalPayload.TYPE, DisconnectPortalPayload.STREAM_CODEC, (payload, player) -> {
            Compat.server(player).execute(() -> {
                ServerLevel level = Compat.serverLevel(player);

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

        var reg = PortalRegistrySavedData.get(Compat.server(player));
        reg.pruneMissingPortals(Compat.server(player));
        ServerLevel level = Compat.serverLevel(player);
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

        Services.PLATFORM.sendToPlayer(player, new PortalListPayload(access.controllerPos(), list));
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
        if (corePos != null && Compat.serverLevel(player).getBlockEntity(corePos) instanceof PortalCoreBlockEntity core) {
            active = core.isActiveOrOpening();
            disconnectAllowed = canDisconnectFromKeyboard(core);
        }
        Services.PLATFORM.sendToPlayer(player, new PortalKeyboardStatusPayload(keyboardPos, active, disconnectAllowed));
    }

    public static void sendPortalActivatorTargetToPlayer(net.minecraft.server.level.ServerPlayer player,
                                                         BlockPos activatorPos) {
        if (!(Compat.serverLevel(player).getBlockEntity(activatorPos)
                instanceof PortalActivatorBlockEntity activator)) return;
        Services.PLATFORM.sendToPlayer(
                player,
                new PortalActivatorTargetPayload(activatorPos, activator.getTargetPortalId())
        );
    }

    private static boolean canDisconnectFromKeyboard(PortalCoreBlockEntity core) {
        if (!core.isActiveOrOpening()) return false;
        if (core.isRiftLightningLink()) return false;
        return core.isOutboundTravelEnabled();
    }

    public static void sendPortalCoreNameToPlayer(net.minecraft.server.level.ServerPlayer player, BlockPos corePos) {
        if (!(Compat.serverLevel(player).getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)) return;
        Services.PLATFORM.sendToPlayer(player, new PortalCoreNamePayload(corePos, core.getPortalName()));
    }

    public static void sendPortalConnectionError(net.minecraft.server.level.ServerPlayer player, String errorMessage) {
        Services.PLATFORM.sendToPlayer(player, new PortalConnectionErrorPayload(errorMessage));
    }

    private record PortalControllerAccess(BlockPos controllerPos, BlockPos corePos, boolean naturalKeyboard) { }

    private static PortalControllerAccess resolveControllerAccess(net.minecraft.server.level.ServerPlayer player,
                                                                  BlockPos controllerPos) {
        ServerLevel level = Compat.serverLevel(player);

        if (player.containerMenu instanceof PortalNaturalKeyboardMenu naturalMenu
                && naturalMenu.getKeyboardPos().equals(controllerPos)) {
            BlockPos corePos = findCoreNear(level, controllerPos, CORE_SEARCH_RADIUS_XZ);
            return new PortalControllerAccess(controllerPos, corePos, true);
        }

        if (player.containerMenu instanceof PortalKeyboardMenu keyboardMenu
                && keyboardMenu.getKeyboardPos().equals(controllerPos)) {
            BlockPos corePos = WireNetwork.findWiredCore(level, controllerPos);
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
            // A player-built keyboard only reaches the portal its crocodile clips are wired to.
            BlockPos corePos = WireNetwork.findWiredCore(level, controllerPos);
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
