package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;

import fr.geomtech.universegate.UniverseGateDimensions;
import fr.geomtech.universegate.PortalRiftHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PortalTeleportHandler {

    // --- réglages ---
    private static final int CORE_SEARCH_XZ = 3;
    private static final int CORE_SEARCH_Y = 5;

    private static final int KEYBOARD_RADIUS = 8;
    private static final int KEYBOARD_SEARCH_Y = 2;

    private static final long TELEPORT_COOLDOWN_TICKS = 40; // 2s anti ping-pong
    private static final long FUEL_CHARGE_COOLDOWN_TICKS = 10; // anti double facturation
    private static final long BLOCKED_DIRECTION_MESSAGE_COOLDOWN_TICKS = 20; // anti spam action bar
    private static final long COOLDOWN_MAP_RETENTION_TICKS = 20L * 60L * 10L;

    // --- état serveur ---
    private static final Map<UUID, Long> lastTeleportTick = new HashMap<>();
    private static final Map<UUID, Long> lastFuelChargeTick = new HashMap<>();
    private static final Map<UUID, Long> lastBlockedDirectionMessageTick = new HashMap<>();

    private PortalTeleportHandler() {}

    /** Appelé par PortalFieldBlock quand une entité touche le champ. */
    public static void tryTeleport(Entity entity, BlockPos fieldPos) {
        if (!(entity.level() instanceof ServerLevel sourceLevel)) return;
        if (!entity.isAlive()) return;
        if (entity.isPassenger()) return;

        long now = sourceLevel.getGameTime();
        if ((now & 127L) == 0L) {
            cleanupCooldownMaps(now);
        }

        // 1) anti ping-pong
        UUID entityId = entity.getUUID();
        Long lastTp = lastTeleportTick.get(entityId);
        if (lastTp != null && now - lastTp < TELEPORT_COOLDOWN_TICKS) return;

        // 2) trouver le core associé au champ
        BlockPos corePos = findCoreNear(sourceLevel, fieldPos);
        if (corePos == null) return;

        if (!(sourceLevel.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)) return;
        if (!core.isActive()) return;
        if (!core.isOutboundTravelEnabled()) {
            if (entity instanceof ServerPlayer player) {
                showBlockedDirectionMessage(player, now);
            }
            return;
        }

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

        boolean consumeFuel = !isRift && entity instanceof ServerPlayer;

        if (consumeFuel) {
            // 4) anti double facturation carburant (joueurs uniquement)
            Long lastFuel = lastFuelChargeTick.get(entityId);
            if (lastFuel != null && now - lastFuel < FUEL_CHARGE_COOLDOWN_TICKS) return;

            // 5) consommer 1 rift ash dans le keyboard source
            PortalKeyboardBlockEntity keyboard = findKeyboardNear(sourceLevel, corePos, KEYBOARD_RADIUS);
            if (keyboard == null || !keyboard.consumeOneFuel()) {
                // plus de carburant => fermeture immédiate
                PortalConnectionManager.forceCloseOneSide(sourceLevel, corePos);
                return;
            }
            lastFuelChargeTick.put(entityId, now);
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
        float yaw = entity.getYRot();

        var sourceMatch = PortalFrameDetector.find(sourceLevel, corePos);
        var targetMatch = PortalFrameDetector.find(targetLevel, targetEntry.pos());
        if (sourceMatch.isPresent() && targetMatch.isPresent()) {
            int sideSign = sideSignFromEntry(sourceMatch.get(), corePos, entity);
            PortalKeyboardBlockEntity targetKeyboard = findKeyboardNear(targetLevel, targetEntry.pos(), KEYBOARD_RADIUS);
            Direction exitNormal = targetKeyboard != null
                    ? normalFromKeyboard(targetMatch.get(), targetEntry.pos(), targetKeyboard.getBlockPos(), sideSign)
                    : normalFromMatch(targetMatch.get(), sideSign);
            x += exitNormal.getStepX() * 1.2;
            z += exitNormal.getStepZ() * 1.2;
            yaw = exitNormal.toYRot();
        }

        ModSounds.playAt(sourceLevel, corePos, ModSounds.PORTAL_ENTITY_GOING_THROUGH, 0.9F, 1.0F);
        boolean teleported;
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(targetLevel, x, y, z, yaw, player.getXRot());
            teleported = true;
        } else {
            teleported = entity.teleportTo(targetLevel, x, y, z, Set.<RelativeMovement>of(), yaw, entity.getXRot());
        }
        if (!teleported) return;

        ModSounds.playAt(targetLevel, targetEntry.pos(), ModSounds.PORTAL_ENTITY_GOING_THROUGH, 0.9F, 1.05F);
        lastTeleportTick.put(entityId, now);
        core.onEntityPassed(now);
        if (targetLevel.getBlockEntity(targetEntry.pos()) instanceof PortalCoreBlockEntity targetCore) {
            targetCore.onEntityPassed(targetLevel.getGameTime());
        }

        if (isRift) {
            PortalRiftHelper.handleRiftArrival(targetLevel, targetEntry.pos());
        }
    }

    private static void showBlockedDirectionMessage(ServerPlayer player, long now) {
        UUID pid = player.getUUID();
        Long lastMessageTick = lastBlockedDirectionMessageTick.get(pid);
        if (lastMessageTick != null && now - lastMessageTick < BLOCKED_DIRECTION_MESSAGE_COOLDOWN_TICKS) return;

        player.displayClientMessage(
                Component.translatable("message.universegate.blocked_direction").withStyle(ChatFormatting.RED),
                true
        );
        lastBlockedDirectionMessageTick.put(pid, now);
    }

    private static int sideSignFromEntry(PortalFrameDetector.FrameMatch match, BlockPos corePos, Entity entity) {
        double axisVelocity = match.right() == Direction.EAST
                ? entity.getDeltaMovement().z
                : entity.getDeltaMovement().x;

        if (Math.abs(axisVelocity) > 0.001D) {
            return axisVelocity >= 0 ? 1 : -1;
        }

        double centerX = corePos.getX() + 0.5;
        double centerZ = corePos.getZ() + 0.5;
        if (match.right() == Direction.EAST) {
            return entity.getZ() >= centerZ ? 1 : -1;
        }
        return entity.getX() >= centerX ? 1 : -1;
    }

    private static Direction normalFromMatch(PortalFrameDetector.FrameMatch match, int sideSign) {
        if (match.right() == Direction.EAST) {
            return sideSign >= 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return sideSign >= 0 ? Direction.EAST : Direction.WEST;
    }

    private static Direction normalFromKeyboard(PortalFrameDetector.FrameMatch match,
                                                BlockPos corePos,
                                                BlockPos keyboardPos,
                                                int fallbackSign) {
        if (match.right() == Direction.EAST) {
            int dz = Integer.compare(keyboardPos.getZ(), corePos.getZ());
            if (dz == 0) return normalFromMatch(match, fallbackSign);
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        int dx = Integer.compare(keyboardPos.getX(), corePos.getX());
        if (dx == 0) return normalFromMatch(match, fallbackSign);
        return dx > 0 ? Direction.EAST : Direction.WEST;
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

    private static void cleanupCooldownMaps(long now) {
        prune(lastTeleportTick, now);
        prune(lastFuelChargeTick, now);
        prune(lastBlockedDirectionMessageTick, now);
    }

    private static void prune(Map<UUID, Long> map, long now) {
        map.entrySet().removeIf(entry -> now - entry.getValue() > COOLDOWN_MAP_RETENTION_TICKS);
    }
}
