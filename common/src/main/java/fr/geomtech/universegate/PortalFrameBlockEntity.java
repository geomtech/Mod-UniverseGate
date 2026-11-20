package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class PortalFrameBlockEntity extends BlockEntity {

    private static final long PATH_CACHE_TICKS = 40L;
    // BLINK_ON is purely visual: send it to clients without waking every neighbour up.
    private static final int BLINK_UPDATE_FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS;

    private long pathCacheExpiresAt = 0L;
    private int cachedPathIndex = -1;
    private int cachedPathSize = 0;

    public PortalFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PORTAL_FRAME, pos, state);
    }

    private void refreshPathCache(Level level, BlockPos pos) {
        cachedPathIndex = -1;
        cachedPathSize = 0;

        BlockPos corePos = findCoreNear(level, pos, 4, 5);
        if (corePos == null) return;

        var match = PortalFrameDetector.find(level, corePos);
        if (match.isEmpty()) return;

        List<BlockPos> path = PortalFrameHelper.collectFrameRotationPath(match.get(), corePos);
        if (path.isEmpty()) return;

        cachedPathIndex = path.indexOf(pos);
        cachedPathSize = path.size();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortalFrameBlockEntity blockEntity) {
        if (level.isClientSide) return;
        if (!state.hasProperty(PortalFrameBlock.ACTIVE)
                || !state.hasProperty(PortalFrameBlock.UNSTABLE)
                || !state.hasProperty(PortalFrameBlock.BLINK_ON)) {
            return;
        }

        if (!state.getValue(PortalFrameBlock.ACTIVE)) {
            blockEntity.pathCacheExpiresAt = 0L;
            if (state.getValue(PortalFrameBlock.BLINK_ON)) {
                level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, false), BLINK_UPDATE_FLAGS);
            }
            return;
        }

        if (!hasAdjacentActiveFrame(level, pos)) {
            if (state.getValue(PortalFrameBlock.BLINK_ON)) {
                level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, false), BLINK_UPDATE_FLAGS);
            }
            return;
        }

        if (!state.getValue(PortalFrameBlock.UNSTABLE)) {
            // Locating the core and re-detecting the whole frame is costly, and every frame block of
            // an active portal did it every tick. Cache where this block sits on the rotation path.
            long now = level.getGameTime();
            if (now >= blockEntity.pathCacheExpiresAt) {
                blockEntity.refreshPathCache(level, pos);
                blockEntity.pathCacheExpiresAt = now + PATH_CACHE_TICKS;
            }

            int idx = blockEntity.cachedPathIndex;
            int size = blockEntity.cachedPathSize;
            if (idx < 0 || size <= 0) {
                if (state.getValue(PortalFrameBlock.BLINK_ON)) {
                    level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, false), BLINK_UPDATE_FLAGS);
                }
                return;
            }

            int head = (int) ((now / 2L) % size);
            boolean blinkOn = idx == head;
            if (state.getValue(PortalFrameBlock.BLINK_ON) != blinkOn) {
                level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, blinkOn), BLINK_UPDATE_FLAGS);
            }
            return;
        }

        blockEntity.pathCacheExpiresAt = 0L;
        boolean blinkOn = ((level.getGameTime() / 6L) & 1L) == 0L;
        if (state.getValue(PortalFrameBlock.BLINK_ON) == blinkOn) return;

        level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, blinkOn), BLINK_UPDATE_FLAGS);
    }

    private static boolean hasAdjacentActiveFrame(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(dir));
            if (neighbor.is(ModBlocks.PORTAL_FRAME)
                    && neighbor.hasProperty(PortalFrameBlock.ACTIVE)
                    && neighbor.getValue(PortalFrameBlock.ACTIVE)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos findCoreNear(Level level, BlockPos center, int rXZ, int rY) {
        for (int dy = -rY; dy <= rY; dy++) {
            for (int dx = -rXZ; dx <= rXZ; dx++) {
                for (int dz = -rXZ; dz <= rXZ; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(ModBlocks.PORTAL_CORE)) return p;
                }
            }
        }
        return null;
    }
}
