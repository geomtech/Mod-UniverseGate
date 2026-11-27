package fr.geomtech.universegate.seamless.physics;

import fr.geomtech.universegate.ModBlocks;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.PortalTeleportHandler;
import fr.geomtech.universegate.compat.Compat;
import fr.geomtech.universegate.seamless.math.PortalCrossingMath;
import fr.geomtech.universegate.seamless.math.PortalTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side continuous plane-crossing tracker replacing abrupt edge-touch teleportation with
 * signed-distance plane crossing + anti-ping-pong cooldown/hysteresis.
 */
public final class PortalCrossingTracker {

    private static final PortalCrossingMath.CrossingCooldownTracker COOLDOWNS =
            new PortalCrossingMath.CrossingCooldownTracker();
    private static final Map<UUID, LastSample> LAST_SAMPLES = new HashMap<>();
    private static final Map<UUID, ApproachDirection> LAST_APPROACH = new HashMap<>();
    private static final Map<UUID, PlayerPosSample> PLAYER_PREV_POS = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> COOLDOWN_DIMS = new HashMap<>();
    private static final Map<UUID, DeferredPlayerCrossing> DEFERRED_PLAYER_CROSSINGS = new HashMap<>();
    private static final long STALE_SAMPLE_TICKS = 40L;

    private PortalCrossingTracker() {}

    public static void deferPlayerCrossing(UUID playerId, ResourceKey<Level> dim, BlockPos fieldPos) {
        DEFERRED_PLAYER_CROSSINGS.put(playerId, new DeferredPlayerCrossing(dim, fieldPos.immutable()));
    }

    static @org.jetbrains.annotations.Nullable BlockPos takeDeferredPlayerCrossing(UUID playerId, ResourceKey<Level> dim) {
        DeferredPlayerCrossing crossing = DEFERRED_PLAYER_CROSSINGS.remove(playerId);
        return crossing != null && crossing.dim().equals(dim) ? crossing.fieldPos() : null;
    }

    /**
     * Runs once at the end of every server tick (AFTER {@code ServerConnectionListener#tick} and
     * {@code ServerPlayer#doTick}):
     * <ol>
     *   <li>Processes field contacts after movement packets have finalized the player's position and look.</li>
     *   <li>Evicts post-crossing hysteresis cooldowns as soon as the player steps away from the exit
     *       portal, so sprinting back in later can never hit a stale cooldown.</li>
     *   <li>Performs a lightweight swept segment check along {@code [prevPos, currPos]} to catch fast
     *       sprinting, sprint-jumping, elytra, or multi-packet lag spikes that might tunnel past the
     *       thin {@code PortalFieldBlock} without firing {@code entityInside}.</li>
     *   <li>Snapshots the player's true end-of-tick position for the next tick's plane-crossing check.</li>
     * </ol>
     */
    public static void tickServerPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || player.isRemoved() || !player.isAlive()) {
                if (player != null) {
                    DEFERRED_PLAYER_CROSSINGS.remove(player.getUUID());
                }
                continue;
            }
            UUID playerId = player.getUUID();
            BlockPos deferredField = takeDeferredPlayerCrossing(playerId, player.level().dimension());
            if (deferredField != null && player.level().getBlockState(deferredField).is(ModBlocks.PORTAL_FIELD)) {
                PortalTeleportHandler.tryTeleport(player, deferredField);
            }
            ServerLevel level = Compat.serverLevel(player);
            ResourceKey<Level> dim = level.dimension();
            long now = level.getGameTime();

            // 1) Proactively clear exit hysteresis cooldown once the player moves away from the exit core
            BlockPos cooldownCore = COOLDOWNS.getCooldownCore(playerId);
            if (cooldownCore != null) {
                ResourceKey<Level> cooldownDim = COOLDOWN_DIMS.get(playerId);
                double horizDist;
                if (cooldownDim != null && !cooldownDim.equals(dim)) {
                    horizDist = Double.MAX_VALUE;
                } else {
                    Vec3 exitCenter = PortalTransform.portalCenter(cooldownCore);
                    horizDist = Math.hypot(player.getX() - exitCenter.x, player.getZ() - exitCenter.z);
                }
                if (!COOLDOWNS.isCoolingDown(playerId, cooldownCore, horizDist, now)) {
                    COOLDOWN_DIMS.remove(playerId);
                }
            }

            // 2) Swept anti-tunneling check if the player moved significantly this tick
            PlayerPosSample prevSample = PLAYER_PREV_POS.get(playerId);
            Vec3 currPos = player.position();
            if (prevSample != null
                    && prevSample.dim().equals(dim)
                    && (now - prevSample.gameTime()) <= STALE_SAMPLE_TICKS) {
                double distSqr = prevSample.pos().distanceToSqr(currPos);
                if (distSqr > 0.04D && distSqr < 100.0D) {
                    double sampleYOffset = Math.min(0.6D, player.getBbHeight() * 0.4D);
                    BlockPos sweptField = BlockGetter.traverseBlocks(
                            prevSample.pos().add(0.0D, sampleYOffset, 0.0D),
                            currPos.add(0.0D, sampleYOffset, 0.0D),
                            level,
                            (world, pos) -> world.isLoaded(pos) && world.getBlockState(pos).is(ModBlocks.PORTAL_FIELD)
                                    ? pos.immutable() : null,
                            world -> null
                    );
                    if (sweptField != null) {
                        PortalTeleportHandler.tryTeleport(player, sweptField);
                    }
                }
            }

            // 3) Snapshot post-tick position (re-read level/pos in case swept check just teleported the player)
            if (!player.isRemoved() && player.isAlive()) {
                ServerLevel afterLevel = Compat.serverLevel(player);
                PLAYER_PREV_POS.put(
                        playerId,
                        new PlayerPosSample(afterLevel.dimension(), player.position(), afterLevel.getGameTime())
                );
            }
        }
    }

    /**
     * Evaluates whether {@code entity} has crossed the center plane of {@code core} on this tick.
     * Fast projectiles, minecarts, and dropped items crossing through the thin field block are
     * allowed to cross immediately without waiting an extra tick so they never tunnel or stall.
     */
    public static boolean shouldCrossPortal(ServerLevel level, Entity entity, PortalCoreBlockEntity core) {
        long now = level.getGameTime();
        BlockPos corePos = core.getBlockPos();
        UUID entityId = entity.getUUID();

        Optional<PortalFrameDetector.FrameMatch> matchOpt = PortalFrameDetector.find(level, corePos);
        if (matchOpt.isEmpty()) {
            return false;
        }
        Direction right = matchOpt.get().right();
        Direction positiveNormal = right.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;

        Vec3 center = PortalTransform.portalCenter(corePos);
        Vec3 currPos = entity.position();
        double currSigned = (currPos.x - center.x) * positiveNormal.getStepX()
                + (currPos.z - center.z) * positiveNormal.getStepZ();

        // Resolve true previous-tick position: prefer PLAYER_PREV_POS for ServerPlayer (whose xo/zo
        // is already overwritten to x/z during handleMovePlayer), then LAST_SAMPLES, then xo/zo.
        double prevX = entity.xo;
        double prevZ = entity.zo;
        boolean hasTrustedPrev = false;
        if (entity instanceof ServerPlayer) {
            PlayerPosSample playerSample = PLAYER_PREV_POS.get(entityId);
            if (playerSample != null
                    && playerSample.dim().equals(level.dimension())
                    && (now - playerSample.gameTime()) <= STALE_SAMPLE_TICKS) {
                prevX = playerSample.pos().x;
                prevZ = playerSample.pos().z;
                hasTrustedPrev = true;
            }
        }
        double prevSigned = (prevX - center.x) * positiveNormal.getStepX()
                + (prevZ - center.z) * positiveNormal.getStepZ();

        if (!hasTrustedPrev) {
            LastSample prevSample = LAST_SAMPLES.get(entityId);
            if (prevSample != null && prevSample.corePos().equals(corePos) && (now - prevSample.gameTime()) <= STALE_SAMPLE_TICKS) {
                prevSigned = prevSample.signedDist();
            }
        }

        if (COOLDOWNS.isCoolingDown(entityId, corePos, Math.abs(currSigned), now)) {
            // If the time cooldown already expired and the entity deliberately crossed the zero plane
            // (e.g., exited at 0.52 blocks, waited >0.5s without stepping to 0.65, then walked back in),
            // release hysteresis and allow the crossing.
            boolean deliberateReentry = !COOLDOWNS.isTimeCoolingDown(entityId, now)
                    && Math.abs(prevSigned) > 0.08D
                    && PortalCrossingMath.hasCrossedPlane(prevSigned, currSigned);
            if (deliberateReentry) {
                COOLDOWNS.clearCooldown(entityId);
                COOLDOWN_DIMS.remove(entityId);
            } else {
                LAST_SAMPLES.put(entityId, new LastSample(corePos, currSigned, now));
                return false;
            }
        } else {
            COOLDOWN_DIMS.remove(entityId);
        }

        // Fast projectiles, minecarts & dropped items may traverse the 0.25-block portal plane within one tick
        if (entity instanceof Projectile || entity instanceof AbstractMinecart || entity instanceof ItemEntity) {
            recordApproach(entityId, corePos, positiveNormal, prevSigned, currSigned, entity.getDeltaMovement(), now);
            return true;
        }

        LAST_SAMPLES.put(entityId, new LastSample(corePos, currSigned, now));

        // Crossed the exact plane OR moving into the portal center plane (within 0.16 blocks)
        boolean crossed = PortalCrossingMath.hasCrossedPlane(prevSigned, currSigned);
        boolean movingIntoCenter = Math.abs(currSigned) <= 0.16D
                && (Math.abs(prevSigned) <= 0.16D || Math.abs(currSigned) < Math.abs(prevSigned));
        if (crossed || movingIntoCenter) {
            recordApproach(entityId, corePos, positiveNormal, prevSigned, currSigned, entity.getDeltaMovement(), now);
            return true;
        }
        return false;
    }

    /**
     * Returns the resolved inward crossing normal recorded when {@link #shouldCrossPortal}
     * accepted the crossing, or {@code null} if unavailable.
     */
    public static @org.jetbrains.annotations.Nullable Direction getLastInwardNormal(UUID entityId, BlockPos corePos) {
        ApproachDirection approach = LAST_APPROACH.get(entityId);
        if (approach == null || !approach.corePos().equals(corePos)) {
            return null;
        }
        return approach.inwardNormal();
    }

    private static void recordApproach(UUID entityId,
                                       BlockPos corePos,
                                       Direction positiveNormal,
                                       double prevSigned,
                                       double currSigned,
                                       Vec3 velocity,
                                       long now) {
        Direction inward = null;
        // 1) If we have a clear previous-side sample, entity approached from that side into the portal
        if (Math.abs(prevSigned) > 0.02D) {
            inward = prevSigned > 0.0D ? positiveNormal.getOpposite() : positiveNormal;
        } else {
            // 2) Check actual tick displacement along the portal normal
            double delta = currSigned - prevSigned;
            if (Math.abs(delta) > 0.001D) {
                inward = delta > 0.0D ? positiveNormal : positiveNormal.getOpposite();
            } else {
                // 3) Check entity velocity along the portal normal (projectiles, minecarts, mobs)
                double vel = velocity.x * positiveNormal.getStepX() + velocity.z * positiveNormal.getStepZ();
                if (Math.abs(vel) > 0.001D) {
                    inward = vel > 0.0D ? positiveNormal : positiveNormal.getOpposite();
                }
            }
        }
        if (inward != null) {
            LAST_APPROACH.put(entityId, new ApproachDirection(corePos.immutable(), inward, now));
        }
    }

    public static void recordCrossingCompleted(UUID entityId, BlockPos destinationCorePos, long destinationGameTime) {
        recordCrossingCompleted(entityId, null, destinationCorePos, destinationGameTime);
    }

    public static void recordCrossingCompleted(UUID entityId,
                                               @org.jetbrains.annotations.Nullable ResourceKey<Level> destinationDim,
                                               BlockPos destinationCorePos,
                                               long destinationGameTime) {
        COOLDOWNS.recordCrossing(entityId, destinationCorePos, destinationGameTime);
        if (destinationDim != null) {
            COOLDOWN_DIMS.put(entityId, destinationDim);
        } else {
            COOLDOWN_DIMS.remove(entityId);
        }
        LAST_SAMPLES.remove(entityId);
        LAST_APPROACH.remove(entityId);
        PLAYER_PREV_POS.remove(entityId);
    }

    public static void onEntityRemoved(UUID entityId) {
        DEFERRED_PLAYER_CROSSINGS.remove(entityId);
        COOLDOWNS.clearEntity(entityId);
        COOLDOWN_DIMS.remove(entityId);
        LAST_SAMPLES.remove(entityId);
        LAST_APPROACH.remove(entityId);
        PLAYER_PREV_POS.remove(entityId);
    }

    public static void clearAll() {
        DEFERRED_PLAYER_CROSSINGS.clear();
        COOLDOWNS.clearAll();
        COOLDOWN_DIMS.clear();
        LAST_SAMPLES.clear();
        LAST_APPROACH.clear();
        PLAYER_PREV_POS.clear();
    }

    private record LastSample(BlockPos corePos, double signedDist, long gameTime) {}

    private record ApproachDirection(BlockPos corePos, Direction inwardNormal, long gameTime) {}

    private record PlayerPosSample(ResourceKey<Level> dim, Vec3 pos, long gameTime) {}

    private record DeferredPlayerCrossing(ResourceKey<Level> dim, BlockPos fieldPos) {}
}
