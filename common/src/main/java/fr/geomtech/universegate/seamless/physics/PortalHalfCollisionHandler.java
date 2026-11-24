package fr.geomtech.universegate.seamless.physics;

import fr.geomtech.universegate.ModBlocks;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager;
import fr.geomtech.universegate.seamless.math.PortalCrossingMath;
import fr.geomtech.universegate.seamless.math.PortalTransform;
import fr.geomtech.universegate.seamless.net.PortalRemoteEndpointPayload;
import fr.geomtech.universegate.seamless.net.ResolvedPortalEndpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles half-through portal collision shaping (both server and client) and third-person camera
 * clip protection when an entity or camera ray overlaps an active outbound portal aperture.
 */
public final class PortalHalfCollisionHandler {

    private static final int SEARCH_XZ = 3;
    private static final int SEARCH_Y = 5;

    private PortalHalfCollisionHandler() {}

    /**
     * Lithium-safe entrypoint invoked at {@code HEAD} of {@link Entity#collideBoundingBox}.
     * Checks portal proximity FIRST so non-straddling entities return {@code null} immediately
     * (letting Lithium / vanilla handle collision at full speed) without calling private
     * {@code Entity#collectColliders}.
     */
    public static @Nullable List<VoxelShape> resolveHalfThroughColliders(Entity entity,
                                                                         Level level,
                                                                         AABB sweptBox,
                                                                         List<VoxelShape> initialCollisions) {
        if (entity == null || level == null || entity.isRemoved()) {
            return null;
        }
        PortalOverlap overlap = findOverlappingOutboundPortal(level, entity.position(), sweptBox);
        if (overlap == null) {
            return null;
        }

        List<VoxelShape> vanillaShapes = new ArrayList<>(initialCollisions.size() + 16);
        if (!initialCollisions.isEmpty()) {
            vanillaShapes.addAll(initialCollisions);
        }
        var worldBorder = level.getWorldBorder();
        if (worldBorder.isInsideCloseToBorder(entity, sweptBox)) {
            vanillaShapes.add(worldBorder.getCollisionShape());
        }
        for (VoxelShape blockShape : level.getBlockCollisions(entity, sweptBox)) {
            vanillaShapes.add(blockShape);
        }
        return filterAndAugmentShapes(entity, sweptBox, vanillaShapes, overlap);
    }

    /**
     * Checks if {@code entity} with swept bounding box {@code sweptBox} currently overlaps an active
     * outbound portal. If so, returns an adjusted collision list where blocks strictly behind the
     * source portal plane inside the aperture are suppressed and destination-side collision shapes
     * are transformed back into source space. Returns {@code null} when no portal is overlapped
     * (zero overhead on the fast path).
     */
    public static @Nullable List<VoxelShape> buildHalfThroughCollisionShapes(Entity entity,
                                                                             Level level,
                                                                             AABB sweptBox,
                                                                             List<VoxelShape> vanillaBlockShapes) {
        if (entity == null || level == null) {
            return null;
        }
        PortalOverlap overlap = findOverlappingOutboundPortal(level, entity.position(), sweptBox);
        if (overlap == null) {
            return null;
        }
        return filterAndAugmentShapes(entity, sweptBox, vanillaBlockShapes, overlap);
    }

    private static List<VoxelShape> filterAndAugmentShapes(Entity entity,
                                                           AABB sweptBox,
                                                           List<VoxelShape> vanillaBlockShapes,
                                                           PortalOverlap overlap) {
        PortalTransform transform = overlap.transform();
        List<VoxelShape> filtered = new ArrayList<>(vanillaBlockShapes.size() + 8);
        for (VoxelShape shape : vanillaBlockShapes) {
            if (shape.isEmpty()) {
                continue;
            }
            AABB bounds = shape.bounds();
            BlockPos samplePos = BlockPos.containing(bounds.getCenter());
            if (PortalCrossingMath.shouldSuppressSourceBlock(samplePos, transform)) {
                continue;
            }
            filtered.add(shape);
        }

        // Sample destination-side collisions and map them back into source space
        Level destLevel = overlap.destinationLevel();
        if (destLevel != null) {
            AABB destBox = transform.transformAABB(sweptBox);
            PortalTransform inverse = transform.inverse();
            for (VoxelShape destShape : destLevel.getBlockCollisions(entity, destBox)) {
                if (destShape.isEmpty()) {
                    continue;
                }
                for (AABB subBox : destShape.toAabbs()) {
                    if (transform.signedDistanceToTargetPlane(subBox.getCenter()) < -0.05D) {
                        continue;
                    }
                    filtered.add(Shapes.create(inverse.transformAABB(subBox)));
                }
            }
        }
        return filtered;
    }

    /**
     * Returns true if a third-person camera segment from {@code anchor} to {@code target} passes
     * through an active portal's aperture corridor, preventing blocks directly behind the source
     * portal ring from prematurely pushing the camera forward.
     */
    public static boolean isCameraRayThroughActivePortal(Level level, Vec3 anchor, Vec3 cameraPoint) {
        if (level == null || !level.isClientSide()) {
            return false;
        }
        AABB rayBox = new AABB(anchor, cameraPoint).inflate(0.5D);
        return findOverlappingOutboundPortal(level, anchor, rayBox) != null;
    }

    private static @Nullable PortalOverlap findOverlappingOutboundPortal(Level level, Vec3 referencePos, AABB box) {
        BlockPos center = BlockPos.containing(box.getCenter());
        for (int dy = -SEARCH_Y; dy <= SEARCH_Y; dy++) {
            for (int dx = -SEARCH_XZ; dx <= SEARCH_XZ; dx++) {
                for (int dz = -SEARCH_XZ; dz <= SEARCH_XZ; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(p)) continue;
                    if (!level.getBlockState(p).is(ModBlocks.PORTAL_CORE)) continue;
                    if (!(level.getBlockEntity(p) instanceof PortalCoreBlockEntity core)) continue;
                    if (!core.isActive()) continue;

                    PortalOverlap overlap = resolveOverlapForCore(level, core, referencePos, box);
                    if (overlap != null) {
                        return overlap;
                    }
                }
            }
        }
        return null;
    }

    private static @Nullable PortalOverlap resolveOverlapForCore(Level level,
                                                                 PortalCoreBlockEntity core,
                                                                 Vec3 referencePos,
                                                                 AABB box) {
        BlockPos corePos = core.getBlockPos();
        Optional<PortalFrameDetector.FrameMatch> frameOpt = PortalFrameDetector.find(level, corePos);
        if (frameOpt.isEmpty()) {
            return null;
        }
        Direction sourceRight = frameOpt.get().right();
        Direction frontNormal = fr.geomtech.universegate.PortalOrientationHelper.resolveFrontNormal(level, corePos, sourceRight);
        Vec3 portalCenter = PortalTransform.portalCenter(corePos);
        double signedFrontDist = (referencePos.x - portalCenter.x) * frontNormal.getStepX()
                + (referencePos.z - portalCenter.z) * frontNormal.getStepZ();
        if (signedFrontDist <= 0.0D) {
            return null;
        }
        Direction sourceNormal = frontNormal.getOpposite();

        if (level instanceof ServerLevel sl) {
            Optional<ResolvedPortalEndpoint> resolvedOpt = core.resolveRemoteEndpoint();
            if (resolvedOpt.isEmpty()) {
                return null;
            }
            ResolvedPortalEndpoint endpoint = resolvedOpt.get();
            PortalTransform transform = new PortalTransform(
                    corePos, sourceNormal, endpoint.targetCorePos(), endpoint.exitNormal());
            if (!PortalCrossingMath.overlapsPortalCorridor(box, transform)) {
                return null;
            }
            ServerLevel destLevel = sl.getServer().getLevel(endpoint.targetDim());
            return new PortalOverlap(transform, destLevel);
        }

        // Client side: look up synced remote endpoint & remote ClientLevel
        PortalRemoteEndpointPayload endpoint = ClientRemoteWorldManager.getInstance().getEndpoint(corePos);
        if (endpoint == null || !endpoint.isActive()) {
            return null;
        }
        PortalTransform transform = new PortalTransform(
                corePos, sourceNormal, endpoint.targetCorePos(), endpoint.exitNormal());
        if (!PortalCrossingMath.overlapsPortalCorridor(box, transform)) {
            return null;
        }
        Level destLevel = endpoint.targetDim().equals(level.dimension())
                ? level
                : ClientRemoteWorldManager.getInstance().getRemoteLevel(endpoint.targetDim());
        return new PortalOverlap(transform, destLevel);
    }

    private record PortalOverlap(PortalTransform transform, @Nullable Level destinationLevel) {}
}
