package fr.geomtech.universegate.seamless.math;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure Java continuous portal crossing detection, anti-ping-pong cooldown/hysteresis,
 * half-through source-wall collision suppression, and continuous velocity/yaw preservation.
 */
public final class PortalCrossingMath {

    public static final double CROSSING_EPSILON = 0.02D;
    public static final double HYSTERESIS_DISTANCE = 0.65D;
    public static final long DEFAULT_COOLDOWN_TICKS = 10L;
    public static final double HALF_COLLISION_CORRIDOR_DEPTH = 1.75D;

    private PortalCrossingMath() {}

    /**
     * Returns true when an entity moved across the portal plane from the approach side
     * ({@code prevSignedDist <= CROSSING_EPSILON}) into or past the plane ({@code currSignedDist >= 0.0}),
     * or crossed the zero plane in either direction with non-zero displacement across the plane.
     */
    public static boolean hasCrossedPlane(double prevSignedDist, double currSignedDist) {
        return (prevSignedDist < 0.0D && currSignedDist >= 0.0D)
                || (prevSignedDist > 0.0D && currSignedDist <= 0.0D)
                || (Math.abs(prevSignedDist) <= CROSSING_EPSILON && currSignedDist > CROSSING_EPSILON);
    }

    /**
     * Returns true when {@code pos} lies within the portal's traversable opening (both the 3x4 inner
     * frame footprint including wired cells and the circular ring aperture).
     */
    public static boolean isInsideCrossingAperture(Vec3 pos, PortalTransform transform, double margin) {
        return transform.isWithinSourceAperture(pos, margin);
    }

    /**
     * Returns true if a block at {@code blockPos} sits strictly behind the source portal plane
     * (on the {@code +sourceNormal} far side) within the aperture corridor, so its collision shape
     * should be suppressed while an entity is halfway through an active outbound portal.
     */
    public static boolean shouldSuppressSourceBlock(BlockPos blockPos, PortalTransform transform) {
        Vec3 blockCenter = new Vec3(
                blockPos.getX() + 0.5D,
                blockPos.getY() + 0.5D,
                blockPos.getZ() + 0.5D
        );
        double signedDist = transform.signedDistanceToSourcePlane(blockCenter);
        if (signedDist <= 0.05D || signedDist > HALF_COLLISION_CORRIDOR_DEPTH) {
            return false;
        }
        return transform.isWithinSourceAperture(blockCenter, 0.35D);
    }

    /**
     * Returns true if {@code entityBox} overlaps the half-through corridor of the portal.
     */
    public static boolean overlapsPortalCorridor(AABB entityBox, PortalTransform transform) {
        Vec3 center = entityBox.getCenter();
        double signedDist = Math.abs(transform.signedDistanceToSourcePlane(center));
        double halfExtent = Math.max(entityBox.getXsize(), entityBox.getZsize()) * 0.5D;
        if (signedDist > halfExtent + 0.75D) {
            return false;
        }
        return transform.isWithinSourceAperture(center, halfExtent + 0.25D);
    }

    /**
     * Computes continuous exit position, velocity, and orientation through {@code transform}
     * without snapping look direction to the cardinal exit normal.
     */
    public static ExitKinematics computeContinuousExit(Vec3 entryPos,
                                                       Vec3 entryVelocity,
                                                       float entryYaw,
                                                       float entryPitch,
                                                       PortalTransform transform,
                                                       double minExitClearance,
                                                       boolean alignYawToVelocity) {
        Vec3 mappedPos = transform.transformPoint(entryPos);
        // Ensure entity clears the destination plane along +exitNormal to avoid immediate re-triggering
        double targetSigned = transform.signedDistanceToTargetPlane(mappedPos);
        if (targetSigned < minExitClearance) {
            double push = minExitClearance - targetSigned;
            mappedPos = mappedPos.add(
                    transform.exitNormal().getStepX() * push,
                    0.0D,
                    transform.exitNormal().getStepZ() * push
            );
        }

        Vec3 mappedVelocity = transform.transformVelocity(entryVelocity);
        float exitYaw = alignYawToVelocity
                ? yawFromVelocity(mappedVelocity, transform.transformYaw(entryYaw))
                : transform.transformYaw(entryYaw);
        float exitPitch = alignYawToVelocity
                ? pitchFromVelocity(mappedVelocity, entryPitch)
                : entryPitch;
        return new ExitKinematics(mappedPos, mappedVelocity, exitYaw, exitPitch);
    }

    public static float yawFromVelocity(Vec3 velocity, float fallbackYaw) {
        double horizontalLenSqr = velocity.x * velocity.x + velocity.z * velocity.z;
        if (horizontalLenSqr < 1.0E-7D) {
            return fallbackYaw;
        }
        return PortalTransform.wrapDegrees((float) (Math.atan2(velocity.z, velocity.x) * (180.0D / Math.PI)) - 90.0F);
    }

    public static float pitchFromVelocity(Vec3 velocity, float fallbackPitch) {
        double horizontalLen = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontalLen < 1.0E-7D && Math.abs(velocity.y) < 1.0E-7D) {
            return fallbackPitch;
        }
        return (float) (-(Math.atan2(velocity.y, horizontalLen) * (180.0D / Math.PI)));
    }

    public record ExitKinematics(Vec3 position, Vec3 velocity, float yRot, float xRot) {}

    /**
     * Pure stateful anti-ping-pong cooldown & hysteresis tracker keyed by entity UUID.
     */
    public static final class CrossingCooldownTracker {
        private final long cooldownTicks;
        private final double hysteresisDistance;
        private final Map<UUID, CooldownEntry> entries = new HashMap<>();

        public CrossingCooldownTracker() {
            this(DEFAULT_COOLDOWN_TICKS, HYSTERESIS_DISTANCE);
        }

        public CrossingCooldownTracker(long cooldownTicks, double hysteresisDistance) {
            this.cooldownTicks = Math.max(0L, cooldownTicks);
            this.hysteresisDistance = Math.max(0.0D, hysteresisDistance);
        }

        public void recordCrossing(UUID entityId, BlockPos exitCorePos, long gameTime) {
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(exitCorePos, "exitCorePos");
            entries.put(entityId, new CooldownEntry(exitCorePos.immutable(), gameTime + cooldownTicks));
        }

        /**
         * Returns true if the entity is still cooling down from a recent portal crossing.
         * Clears the entry once both the time cooldown has expired AND (if near the exit core)
         * the entity has moved beyond {@code hysteresisDistance} from the exit plane.
         */
        public boolean isCoolingDown(UUID entityId, BlockPos currentCorePos, double absPlaneDist, long gameTime) {
            CooldownEntry entry = entries.get(entityId);
            if (entry == null) {
                return false;
            }
            if (gameTime < entry.expiresAtTick()) {
                return true;
            }
            if (entry.exitCorePos().equals(currentCorePos) && absPlaneDist < hysteresisDistance) {
                return true;
            }
            entries.remove(entityId);
            return false;
        }

        public @org.jetbrains.annotations.Nullable BlockPos getCooldownCore(UUID entityId) {
            CooldownEntry entry = entries.get(entityId);
            return entry != null ? entry.exitCorePos() : null;
        }

        public boolean isTimeCoolingDown(UUID entityId, long gameTime) {
            CooldownEntry entry = entries.get(entityId);
            return entry != null && gameTime < entry.expiresAtTick();
        }

        public void clearCooldown(UUID entityId) {
            entries.remove(entityId);
        }

        public void clearEntity(UUID entityId) {
            entries.remove(entityId);
        }

        public void clearAll() {
            entries.clear();
        }

        public int size() {
            return entries.size();
        }

        private record CooldownEntry(BlockPos exitCorePos, long expiresAtTick) {}
    }
}
