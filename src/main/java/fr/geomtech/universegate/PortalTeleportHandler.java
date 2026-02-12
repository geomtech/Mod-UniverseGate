package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import fr.geomtech.universegate.UniverseGateDimensions;
import fr.geomtech.universegate.PortalRiftHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PortalTeleportHandler {

    // --- réglages ---
    private static final int CORE_SEARCH_XZ = 3;
    private static final int CORE_SEARCH_Y = 5;

    private static final int KEYBOARD_RADIUS = 8;
    private static final int KEYBOARD_SEARCH_Y = 2;

    private static final long TELEPORT_COOLDOWN_TICKS = 40; // 2s anti ping-pong
    private static final long FUEL_CHARGE_COOLDOWN_TICKS = 10; // anti double facturation

    // --- état serveur ---
    private static final Map<UUID, Long> lastTeleportTick = new HashMap<>();
    private static final Map<UUID, Long> lastFuelChargeTick = new HashMap<>();

    private PortalTeleportHandler() {}

    /** Appelé par PortalFieldBlock quand un joueur touche le champ. */
    public static void tryTeleport(ServerPlayer player, BlockPos fieldPos) {
        ServerLevel sourceLevel = player.serverLevel();
        long now = sourceLevel.getGameTime();

        // 1) anti ping-pong
        UUID pid = player.getUUID();
        Long lastTp = lastTeleportTick.get(pid);
        if (lastTp != null && now - lastTp < TELEPORT_COOLDOWN_TICKS) return;

        // 2) trouver le core associé au champ
        BlockPos corePos = findCoreNear(sourceLevel, fieldPos);
        if (corePos == null) return;

        if (!(sourceLevel.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)) return;
        if (!core.isActive()) return;

        UUID targetId = core.getTargetPortalId();
        if (targetId == null) return;

        // 3) resolve destination (cross-dimension)
        PortalRegistrySavedData registry = PortalRegistrySavedData.get(sourceLevel.getServer());
        PortalRegistrySavedData.PortalEntry targetEntry = registry.get(targetId);
        if (targetEntry == null) {
            // destination inexistante => fermeture
            PortalConnectionManager.forceCloseOneSide(sourceLevel, corePos);
            return;
        }

        boolean isRift = targetEntry.dim().equals(UniverseGateDimensions.RIFT);

        // 4) anti double facturation carburant
        Long lastFuel = lastFuelChargeTick.get(pid);
        if (lastFuel != null && now - lastFuel < FUEL_CHARGE_COOLDOWN_TICKS) return;

        if (!isRift) {
            // 5) consommer 1 catalyst dans le keyboard source
            PortalKeyboardBlockEntity keyboard = findKeyboardNear(sourceLevel, corePos, KEYBOARD_RADIUS);
            if (keyboard == null || !keyboard.consumeOneCatalyst()) {
                // plus de carburant => fermeture immédiate
                PortalConnectionManager.forceCloseOneSide(sourceLevel, corePos);
                return;
            }
            lastFuelChargeTick.put(pid, now);
        }

        ServerLevel targetLevel = sourceLevel.getServer().getLevel(targetEntry.dim());
        if (targetLevel == null) {
            PortalConnectionManager.forceCloseOneSide(sourceLevel, corePos);
            return;
        }

        // charger chunk destination (core)
        targetLevel.getChunk(targetEntry.pos());

        // 6) point d’arrivée (orienté selon le portail)
        double x = targetEntry.pos().getX() + 0.5;
        double y = targetEntry.pos().getY() + 1.0;
        double z = targetEntry.pos().getZ() + 0.5;
        float yaw = player.getYRot();

        var sourceMatch = PortalFrameDetector.find(sourceLevel, corePos);
        var targetMatch = PortalFrameDetector.find(targetLevel, targetEntry.pos());
        if (sourceMatch.isPresent() && targetMatch.isPresent()) {
            int sideSign = sideSignFromEntry(sourceMatch.get(), corePos, player);
            Direction exitNormal = normalFromMatch(targetMatch.get(), sideSign);
            x += exitNormal.getStepX() * 1.2;
            z += exitNormal.getStepZ() * 1.2;
            yaw = exitNormal.toYRot();
        }

        player.teleportTo(targetLevel, x, y, z, yaw, player.getXRot());
        lastTeleportTick.put(pid, now);

        if (isRift) {
            PortalRiftHelper.handleRiftArrival(targetLevel, targetEntry.pos());
        }
    }

    private static int sideSignFromEntry(PortalFrameDetector.FrameMatch match, BlockPos corePos, ServerPlayer player) {
        double axisVelocity = match.right() == Direction.EAST
                ? player.getDeltaMovement().z
                : player.getDeltaMovement().x;

        if (Math.abs(axisVelocity) > 0.001D) {
            return axisVelocity >= 0 ? 1 : -1;
        }

        double centerX = corePos.getX() + 0.5;
        double centerZ = corePos.getZ() + 0.5;
        if (match.right() == Direction.EAST) {
            return player.getZ() >= centerZ ? 1 : -1;
        }
        return player.getX() >= centerX ? 1 : -1;
    }

    private static Direction normalFromMatch(PortalFrameDetector.FrameMatch match, int sideSign) {
        if (match.right() == Direction.EAST) {
            return sideSign >= 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return sideSign >= 0 ? Direction.EAST : Direction.WEST;
    }

    private static BlockPos findCoreNear(ServerLevel level, BlockPos pos) {
        for (int dy = -CORE_SEARCH_Y; dy <= CORE_SEARCH_Y; dy++) {
            for (int dx = -CORE_SEARCH_XZ; dx <= CORE_SEARCH_XZ; dx++) {
                for (int dz = -CORE_SEARCH_XZ; dz <= CORE_SEARCH_XZ; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(ModBlocks.PORTAL_CORE)) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    private static PortalKeyboardBlockEntity findKeyboardNear(ServerLevel level, BlockPos corePos, int r) {
        for (int dy = -KEYBOARD_SEARCH_Y; dy <= KEYBOARD_SEARCH_Y; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = corePos.offset(dx, dy, dz);
                    if (!level.getBlockState(p).is(ModBlocks.PORTAL_KEYBOARD)) continue;
                    if (level.getBlockEntity(p) instanceof PortalKeyboardBlockEntity kb) return kb;
                }
            }
        }
        return null;
    }
}
