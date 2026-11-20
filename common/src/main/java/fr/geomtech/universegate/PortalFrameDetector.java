package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PortalFrameDetector {

    // Intérieur
    public static final int INNER_WIDTH = 3;
    public static final int INNER_HEIGHT = 4;

    // Extérieur
    private static final int OUTER_WIDTH = INNER_WIDTH + 2;   // 5
    private static final int OUTER_HEIGHT = INNER_HEIGHT + 2; // 6

    // Core au bas-centre => demi-largeur extérieure = 2 (positions -2..+2)
    private static final int HALF_OUTER_WIDTH = OUTER_WIDTH / 2; // 2
    private static final int TOP_Y = OUTER_HEIGHT - 1; // 5

    private PortalFrameDetector() {}

    public record FrameMatch(Direction right, List<BlockPos> interior) {}

    public static Optional<FrameMatch> find(Level level, BlockPos corePos) {
        // Deux orientations : largeur sur X (EAST) ou sur Z (SOUTH)
        Optional<FrameMatch> x = tryMatch(level, corePos, Direction.EAST);
        if (x.isPresent()) return x;

        Optional<FrameMatch> z = tryMatch(level, corePos, Direction.SOUTH);
        if (z.isPresent()) return z;

        return Optional.empty();
    }

    /** Finds a loaded, complete frame whose border actually contains the clipped block. */
    static Optional<BlockPos> findCoreOfFrame(Level level, BlockPos framePos) {
        for (Direction right : List.of(Direction.EAST, Direction.SOUTH)) {
            for (int dy = 0; dy <= TOP_Y; dy++) {
                for (int dx = -HALF_OUTER_WIDTH; dx <= HALF_OUTER_WIDTH; dx++) {
                    if (dy != 0 && dy != TOP_Y && Math.abs(dx) != HALF_OUTER_WIDTH) continue;
                    BlockPos core = offset(framePos, right, -dx, -dy);
                    if (!level.isLoaded(core) || !level.getBlockState(core).is(ModBlocks.PORTAL_CORE)) continue;
                    // This five-block row spans at most two chunks; every row is directly above it.
                    if (!level.isLoaded(core.relative(right, -HALF_OUTER_WIDTH))
                            || !level.isLoaded(core.relative(right, HALF_OUTER_WIDTH))) continue;
                    if (tryMatch(level, core, right).isPresent()) return Optional.of(core);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<FrameMatch> tryMatch(Level level, BlockPos corePos, Direction right) {
        List<BlockPos> interior = new ArrayList<>();

        for (int dy = 0; dy <= TOP_Y; dy++) {
            for (int dx = -HALF_OUTER_WIDTH; dx <= HALF_OUTER_WIDTH; dx++) {

                BlockPos p = offset(corePos, right, dx, dy);
                boolean isBorder = (dy == 0 || dy == TOP_Y || dx == -HALF_OUTER_WIDTH || dx == HALF_OUTER_WIDTH);

                BlockState state = level.getBlockState(p);

                if (isBorder) {
                    // Bas-centre (dx=0, dy=0) : doit être le PORTAL_CORE
                    if (dx == 0 && dy == 0) {
                        if (!state.is(ModBlocks.PORTAL_CORE)) return Optional.empty();
                        continue;
                    }

                    // Tout le reste du contour : PORTAL_FRAME obligatoire
                    if (!state.is(ModBlocks.PORTAL_FRAME)) return Optional.empty();

                } else {
                    // Intérieur : on accepte "vide" (air) ou blocs remplaçables si tu veux.
                    // Pour l'instant : on exige juste que ce ne soit pas la frame/core.
                    if (state.is(ModBlocks.PORTAL_FRAME) || state.is(ModBlocks.PORTAL_CORE)) {
                        return Optional.empty();
                    }
                    interior.add(p);
                }
            }
        }

        return Optional.of(new FrameMatch(right, interior));
    }

    private static BlockPos offset(BlockPos origin, Direction right, int dx, int dy) {
        return origin.offset(right.getStepX() * dx, dy, right.getStepZ() * dx);
    }
}
