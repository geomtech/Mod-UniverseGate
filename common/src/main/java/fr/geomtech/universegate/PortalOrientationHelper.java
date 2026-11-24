package fr.geomtech.universegate;

import fr.geomtech.universegate.seamless.net.PortalEndpointResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Single source of truth for locating a portal's controller (wired {@code PORTAL_KEYBOARD},
 * nearby {@code PORTAL_NATURAL_KEYBOARD}, or nearby {@code PORTAL_KEYBOARD}) and resolving
 * its front face normal on both client and server.
 */
public final class PortalOrientationHelper {

    public static final int KEYBOARD_SEARCH_RADIUS_XZ = 8;
    public static final int KEYBOARD_SEARCH_RADIUS_Y = 4;

    private PortalOrientationHelper() {}

    /**
     * Resolves the outward normal of the portal's front face (the side containing its controller).
     */
    public static Direction resolveFrontNormal(Level level, BlockPos corePos, Direction frameRight) {
        Direction normalizedRight = PortalEndpointResolver.normalizeFrameRight(frameRight);
        BlockPos controllerPos = findControllerPos(level, corePos, normalizedRight);
        return PortalEndpointResolver.resolveExitNormal(normalizedRight, corePos, controllerPos);
    }

    /**
     * Finds the best controller block for {@code corePos} that lies strictly off the portal plane
     * (so it unambiguously defines the portal's front side).
     *
     * <p>Priority:
     * <ol>
     *   <li>Closest off-plane keyboard wired to this portal via crocodile clips (disambiguates adjacent portals).</li>
     *   <li>Closest off-plane {@code PORTAL_NATURAL_KEYBOARD} within radius 8×4×8.</li>
     *   <li>Closest off-plane {@code PORTAL_KEYBOARD} within radius 8×4×8.</li>
     * </ol>
     * </p>
     */
    public static @Nullable BlockPos findControllerPos(Level level, BlockPos corePos, Direction frameRight) {
        Direction normalizedRight = PortalEndpointResolver.normalizeFrameRight(frameRight);

        // 1) Wired keyboards specifically connected to this portal
        BlockPos bestWired = pickClosestOffPlane(corePos, normalizedRight, WireNetwork.findWiredKeyboards(level, corePos));
        if (bestWired != null) {
            return bestWired;
        }

        // 2) Nearby natural keyboards, then nearby player keyboards
        BlockPos bestNatural = null;
        double bestNaturalDist = Double.MAX_VALUE;
        BlockPos bestKeyboard = null;
        double bestKeyboardDist = Double.MAX_VALUE;

        for (int dy = -KEYBOARD_SEARCH_RADIUS_Y; dy <= KEYBOARD_SEARCH_RADIUS_Y; dy++) {
            for (int dx = -KEYBOARD_SEARCH_RADIUS_XZ; dx <= KEYBOARD_SEARCH_RADIUS_XZ; dx++) {
                for (int dz = -KEYBOARD_SEARCH_RADIUS_XZ; dz <= KEYBOARD_SEARCH_RADIUS_XZ; dz++) {
                    if (!isOffPortalPlane(dx, dz, normalizedRight)) {
                        continue;
                    }
                    BlockPos p = corePos.offset(dx, dy, dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    var state = level.getBlockState(p);
                    if (state.is(ModBlocks.PORTAL_NATURAL_KEYBOARD)) {
                        double d = p.distSqr(corePos);
                        if (d < bestNaturalDist) {
                            bestNaturalDist = d;
                            bestNatural = p.immutable();
                        }
                    } else if (state.is(ModBlocks.PORTAL_KEYBOARD)) {
                        double d = p.distSqr(corePos);
                        if (d < bestKeyboardDist) {
                            bestKeyboardDist = d;
                            bestKeyboard = p.immutable();
                        }
                    }
                }
            }
        }
        return bestNatural != null ? bestNatural : bestKeyboard;
    }

    private static @Nullable BlockPos pickClosestOffPlane(BlockPos corePos,
                                                          Direction normalizedRight,
                                                          List<BlockPos> candidates) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            int dx = candidate.getX() - corePos.getX();
            int dz = candidate.getZ() - corePos.getZ();
            if (!isOffPortalPlane(dx, dz, normalizedRight)) {
                continue;
            }
            double dist = candidate.distSqr(corePos);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate.immutable();
            }
        }
        return best;
    }

    private static boolean isOffPortalPlane(int dx, int dz, Direction normalizedRight) {
        return normalizedRight == Direction.EAST ? dz != 0 : dx != 0;
    }
}
