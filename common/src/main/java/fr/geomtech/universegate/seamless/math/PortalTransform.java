package fr.geomtech.universegate.seamless.math;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Objects;

/**
 * Pure Euclidean portal transform between an oriented source portal plane and its destination portal plane.
 *
 * <p>Uses a proper Y-axis rotation ({@code det = +1}) so view-space handedness, backface culling,
 * player left/right intuition, and entity velocity all remain seamlessly continuous.</p>
 *
 * <p>Grounded in {@code PortalGateRingLayer} / {@code PortalMembraneLayer} geometry:
 * center at {@code corePos + (0.5, 3.0, 0.5)}, aperture radius {@code 36/16 = 2.25} blocks.</p>
 */
public final class PortalTransform {

    public static final double CENTER_OFFSET_X = 0.5D;
    public static final double CENTER_OFFSET_Y = 3.0D;
    public static final double CENTER_OFFSET_Z = 0.5D;
    public static final double APERTURE_RADIUS = 36.0D / 16.0D; // 2.25 blocks
    public static final double RING_INNER_RADIUS = 35.0D / 16.0D; // 2.1875 blocks

    private final BlockPos sourceCorePos;
    private final BlockPos targetCorePos;
    private final Vec3 sourceCenter;
    private final Vec3 targetCenter;
    private final Direction sourceNormal; // unit horizontal normal pointing INTO source portal
    private final Direction sourceRight;  // viewer's right-hand axis (sourceNormal.getClockWise())
    private final Direction exitNormal;   // unit horizontal normal pointing OUT OF target portal
    private final Direction targetRight;  // viewer's right-hand axis (exitNormal.getClockWise())
    private final float deltaYawDegrees;

    public PortalTransform(BlockPos sourceCorePos,
                           Direction sourceNormal,
                           BlockPos targetCorePos,
                           Direction exitNormal) {
        this.sourceCorePos = Objects.requireNonNull(sourceCorePos, "sourceCorePos").immutable();
        this.targetCorePos = Objects.requireNonNull(targetCorePos, "targetCorePos").immutable();
        this.sourceNormal = requireHorizontal(sourceNormal, "sourceNormal");
        this.exitNormal = requireHorizontal(exitNormal, "exitNormal");
        this.sourceRight = this.sourceNormal.getClockWise();
        this.targetRight = this.exitNormal.getClockWise();
        this.sourceCenter = portalCenter(this.sourceCorePos);
        this.targetCenter = portalCenter(this.targetCorePos);
        this.deltaYawDegrees = wrapDegrees(this.exitNormal.toYRot() - this.sourceNormal.toYRot());
    }

    public static Vec3 portalCenter(BlockPos corePos) {
        return new Vec3(
                corePos.getX() + CENTER_OFFSET_X,
                corePos.getY() + CENTER_OFFSET_Y,
                corePos.getZ() + CENTER_OFFSET_Z
        );
    }

    /**
     * Resolves the horizontal inward normal facing away from a viewer/entity at {@code viewerPos}
     * through a portal whose horizontal frame axis is {@code portalRight} ({@code EAST} or {@code SOUTH}).
     */
    public static Direction resolveInwardNormal(BlockPos corePos, Direction portalRight, Vec3 viewerPos) {
        Vec3 center = portalCenter(corePos);
        if (portalRight.getAxis() == Direction.Axis.X) {
            // Frame spans X, normal is NORTH (-Z) or SOUTH (+Z)
            return viewerPos.z >= center.z ? Direction.NORTH : Direction.SOUTH;
        }
        // Frame spans Z, normal is WEST (-X) or EAST (+X)
        return viewerPos.x >= center.x ? Direction.WEST : Direction.EAST;
    }

    public BlockPos sourceCorePos() {
        return sourceCorePos;
    }

    public BlockPos targetCorePos() {
        return targetCorePos;
    }

    public Vec3 sourceCenter() {
        return sourceCenter;
    }

    public Vec3 targetCenter() {
        return targetCenter;
    }

    public Direction sourceNormal() {
        return sourceNormal;
    }

    public Direction sourceRight() {
        return sourceRight;
    }

    public Direction exitNormal() {
        return exitNormal;
    }

    public Direction targetRight() {
        return targetRight;
    }

    public float deltaYawDegrees() {
        return deltaYawDegrees;
    }

    /**
     * Maps a world-space point from source portal space into destination portal space.
     */
    public Vec3 transformPoint(Vec3 point) {
        double dx = point.x - sourceCenter.x;
        double dy = point.y - sourceCenter.y;
        double dz = point.z - sourceCenter.z;

        double u = dx * sourceRight.getStepX() + dz * sourceRight.getStepZ();
        double w = dx * sourceNormal.getStepX() + dz * sourceNormal.getStepZ();

        double outX = targetCenter.x + u * targetRight.getStepX() + w * exitNormal.getStepX();
        double outY = targetCenter.y + dy;
        double outZ = targetCenter.z + u * targetRight.getStepZ() + w * exitNormal.getStepZ();
        return new Vec3(outX, outY, outZ);
    }

    /**
     * Maps a direction or velocity vector from source portal space into destination portal space
     * (pure Y-axis rotation, preserving magnitude and vertical component).
     */
    public Vec3 transformDirection(Vec3 vector) {
        double u = vector.x * sourceRight.getStepX() + vector.z * sourceRight.getStepZ();
        double w = vector.x * sourceNormal.getStepX() + vector.z * sourceNormal.getStepZ();

        double outX = u * targetRight.getStepX() + w * exitNormal.getStepX();
        double outZ = u * targetRight.getStepZ() + w * exitNormal.getStepZ();
        return new Vec3(outX, vector.y, outZ);
    }

    public Vec3 transformVelocity(Vec3 velocity) {
        return transformDirection(velocity);
    }

    /**
     * Maps a Minecraft yaw angle (degrees) continuously across the portal.
     */
    public float transformYaw(float sourceYawDegrees) {
        return wrapDegrees(sourceYawDegrees + deltaYawDegrees);
    }

    /**
     * Transforms an axis-aligned bounding box by transforming its 8 corners and taking their AABB envelope.
     */
    public AABB transformAABB(AABB box) {
        Vec3 c0 = transformPoint(new Vec3(box.minX, box.minY, box.minZ));
        Vec3 c1 = transformPoint(new Vec3(box.maxX, box.maxY, box.maxZ));
        return new AABB(
                Math.min(c0.x, c1.x),
                Math.min(c0.y, c1.y),
                Math.min(c0.z, c1.z),
                Math.max(c0.x, c1.x),
                Math.max(c0.y, c1.y),
                Math.max(c0.z, c1.z)
        );
    }

    /**
     * Returns the signed distance from {@code point} to the source portal plane along {@link #sourceNormal}.
     * Negative values are on the viewer's approach side; positive values are past the portal plane.
     */
    public double signedDistanceToSourcePlane(Vec3 point) {
        return (point.x - sourceCenter.x) * sourceNormal.getStepX()
                + (point.z - sourceCenter.z) * sourceNormal.getStepZ();
    }

    /**
     * Returns the signed distance from {@code point} to the target portal plane along {@link #exitNormal}.
     * Positive values are in front of the destination portal (the visible / kept half-space).
     */
    public double signedDistanceToTargetPlane(Vec3 point) {
        return (point.x - targetCenter.x) * exitNormal.getStepX()
                + (point.z - targetCenter.z) * exitNormal.getStepZ();
    }

    /**
     * Returns true if the projection of {@code point} onto the source portal plane lies within
     * the circular aperture of radius {@code APERTURE_RADIUS + margin}.
     */
    public boolean isWithinSourceAperture(Vec3 point, double margin) {
        double dx = point.x - sourceCenter.x;
        double dy = point.y - sourceCenter.y;
        double dz = point.z - sourceCenter.z;
        double u = dx * sourceRight.getStepX() + dz * sourceRight.getStepZ();
        double r = APERTURE_RADIUS + margin;
        return (u * u + dy * dy) <= (r * r);
    }

    /**
     * Returns the destination clipping plane in world space relative to the transformed camera position
     * {@code targetCameraPos}, oriented so {@code C . P >= 0} on the visible side ({@code +exitNormal})
     * of the destination portal.
     */
    public Vector4f targetClipPlaneCameraRelative(Vec3 targetCameraPos, float clipOffset) {
        float nx = exitNormal.getStepX();
        float ny = 0.0F;
        float nz = exitNormal.getStepZ();
        // Plane passes through targetCenter + exitNormal * clipOffset
        double px = (targetCenter.x + nx * clipOffset) - targetCameraPos.x;
        double py = targetCenter.y - targetCameraPos.y;
        double pz = (targetCenter.z + nz * clipOffset) - targetCameraPos.z;
        float d = (float) -(nx * px + ny * py + nz * pz);
        return new Vector4f(nx, ny, nz, d);
    }

    /**
     * Builds the 4x4 pure rotation matrix (determinant +1) around the Y axis corresponding to
     * {@link #transformDirection(Vec3)}.
     *
     * <p>Note JOML column-major naming: {@code m{col}{row}}, so column 0 is {@code (m00, m01, m02)}
     * and column 2 is {@code (m20, m21, m22)}.</p>
     */
    public Matrix4f rotationMatrix() {
        Vec3 col0 = transformDirection(new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 col2 = transformDirection(new Vec3(0.0D, 0.0D, 1.0D));
        Matrix4f mat = new Matrix4f().identity();
        mat.m00((float) col0.x);
        mat.m02((float) col0.z);
        mat.m20((float) col2.x);
        mat.m22((float) col2.z);
        return mat;
    }

    /**
     * Returns the exact inverse transform mapping destination portal space back to source portal space.
     */
    public PortalTransform inverse() {
        return new PortalTransform(targetCorePos, exitNormal, sourceCorePos, sourceNormal);
    }

    /**
     * Quick frustum/sphere test: returns true if any part of the circular portal aperture could be
     * visible to a viewer at {@code cameraPos} looking along {@code cameraLook} within {@code maxDistance}.
     */
    public boolean isAperturePotentiallyVisible(Vec3 cameraPos, Vec3 cameraLook, double maxDistance) {
        Vec3 toPortal = sourceCenter.subtract(cameraPos);
        double distSqr = toPortal.lengthSqr();
        double maxWithRadius = maxDistance + APERTURE_RADIUS;
        if (distSqr > maxWithRadius * maxWithRadius) {
            return false;
        }
        // Check camera is in front of or within aperture thickness of the source plane
        double signedDist = signedDistanceToSourcePlane(cameraPos);
        if (signedDist > 0.25D) {
            return false;
        }
        // If camera is inside the bounding sphere of the aperture, always consider visible
        if (distSqr <= (APERTURE_RADIUS + 0.5D) * (APERTURE_RADIUS + 0.5D)) {
            return true;
        }
        double dist = Math.sqrt(distSqr);
        double dot = toPortal.dot(cameraLook) / dist;
        // Conservative cone check (covers wide FOV up to ~130 deg + aperture angular radius)
        double angularRadius = Math.min(1.0D, APERTURE_RADIUS / dist);
        return dot >= -0.35D - angularRadius;
    }

    private static Direction requireHorizontal(Direction dir, String name) {
        Objects.requireNonNull(dir, name);
        if (dir.getAxis().isVertical()) {
            throw new IllegalArgumentException(name + " must be horizontal, got " + dir);
        }
        return dir;
    }

    public static float wrapDegrees(float deg) {
        float wrapped = deg % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }
}
