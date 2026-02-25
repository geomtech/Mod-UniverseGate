package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalPursuitTracker {

    private static final long MOB_PORTAL_PURSUIT_GRACE_TICKS = 20L * 15L;
    private static final long MOB_PORTAL_NUDGE_INTERVAL_TICKS = 10L;
    private static final double MOB_PORTAL_NUDGE_SPEED = 1.15D;

    private record PursuitState(ResourceKey<Level> sourceDimension,
                                BlockPos portalFieldPos,
                                UUID playerId,
                                long expiresAtGameTime) {}

    private static final Map<UUID, PursuitState> TRACKED = new ConcurrentHashMap<>();

    private PortalPursuitTracker() {}

    public static void trackMobTowardPortalField(ServerLevel level,
                                                 Mob mob,
                                                 BlockPos portalFieldPos,
                                                 UUID playerId) {
        long expiresAt = level.getGameTime() + MOB_PORTAL_PURSUIT_GRACE_TICKS;
        BlockPos immutableFieldPos = portalFieldPos.immutable();

        TRACKED.merge(
                mob.getUUID(),
                new PursuitState(level.dimension(), immutableFieldPos, playerId, expiresAt),
                (oldState, newState) -> oldState.expiresAtGameTime() >= newState.expiresAtGameTime()
                        ? oldState
                        : newState
        );
    }

    public static void onMobTeleported(ServerLevel destinationLevel, Mob mob) {
        PursuitState state = TRACKED.get(mob.getUUID());
        if (state == null) return;

        long now = destinationLevel.getGameTime();
        if (now >= state.expiresAtGameTime()) {
            TRACKED.remove(mob.getUUID(), state);
            return;
        }

        ServerPlayer player = destinationLevel.getServer().getPlayerList().getPlayer(state.playerId());
        if (player == null || player.level() != destinationLevel) return;

        if (mob.hasEffect(MobEffects.INVISIBILITY)) {
            mob.removeEffect(MobEffects.INVISIBILITY);
        }
        if (mob.isInvisible()) {
            mob.setInvisible(false);
        }

        mob.setTarget(player);
        refreshMobTracking(destinationLevel, mob);
        mob.hasImpulse = true;
        mob.hurtMarked = true;
        TRACKED.remove(mob.getUUID(), state);
    }

    private static void refreshMobTracking(ServerLevel level, Mob mob) {
        level.getChunkSource().removeEntity(mob);
        level.getChunkSource().addEntity(mob);
    }

    public static boolean isProtectedFromNaturalDespawn(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return false;

        PursuitState state = TRACKED.get(mob.getUUID());
        if (state == null) return false;

        long now = level.getGameTime();
        if (now >= state.expiresAtGameTime()) {
            TRACKED.remove(mob.getUUID(), state);
            return false;
        }
        return true;
    }

    public static void tickWorld(ServerLevel level) {
        long now = level.getGameTime();
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, PursuitState> entry : TRACKED.entrySet()) {
            UUID mobId = entry.getKey();
            PursuitState state = entry.getValue();

            if (!state.sourceDimension().equals(level.dimension())) continue;

            if (now >= state.expiresAtGameTime()) {
                toRemove.add(mobId);
                continue;
            }

            Entity entity = level.getEntity(mobId);
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                toRemove.add(mobId);
                continue;
            }

            LivingEntity currentTarget = mob.getTarget();
            if (currentTarget != null && currentTarget.level() == level) {
                continue;
            }
            if (currentTarget != null) {
                mob.setTarget(null);
            }

            long phase = Math.floorMod(mobId.getLeastSignificantBits(), MOB_PORTAL_NUDGE_INTERVAL_TICKS);
            if (Math.floorMod(now, MOB_PORTAL_NUDGE_INTERVAL_TICKS) != phase) continue;

            BlockPos portalFieldPos = state.portalFieldPos();
            double targetX = portalFieldPos.getX() + 0.5D;
            double targetY = portalFieldPos.getY() + 0.5D;
            double targetZ = portalFieldPos.getZ() + 0.5D;

            mob.getNavigation().moveTo(targetX, targetY, targetZ, MOB_PORTAL_NUDGE_SPEED);
            mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, MOB_PORTAL_NUDGE_SPEED);
        }

        if (toRemove.isEmpty()) return;
        for (UUID mobId : toRemove) {
            TRACKED.remove(mobId);
        }
    }
}
