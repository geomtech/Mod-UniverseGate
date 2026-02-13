package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class PortalFrameBlockEntity extends BlockEntity {

    public PortalFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PORTAL_FRAME, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortalFrameBlockEntity blockEntity) {
        if (level.isClientSide) return;
        if (!state.hasProperty(PortalFrameBlock.ACTIVE)
                || !state.hasProperty(PortalFrameBlock.UNSTABLE)
                || !state.hasProperty(PortalFrameBlock.BLINK_ON)) {
            return;
        }

        if (!state.getValue(PortalFrameBlock.ACTIVE)) {
            if (state.getValue(PortalFrameBlock.BLINK_ON)) {
                level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, false), 3);
            }
            return;
        }

        if (!hasAdjacentActiveFrame(level, pos)) {
            if (state.getValue(PortalFrameBlock.BLINK_ON)) {
                level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, false), 3);
            }
            return;
        }

        if (!state.getValue(PortalFrameBlock.UNSTABLE)) {
            BlockPos corePos = findCoreNear(level, pos, 4, 5);
            if (corePos == null) {
                if (state.getValue(PortalFrameBlock.BLINK_ON)) {
                    level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, false), 3);
                }
                return;
            }

            var match = PortalFrameDetector.find(level, corePos);
            if (match.isEmpty()) {
                if (state.getValue(PortalFrameBlock.BLINK_ON)) {
                    level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, false), 3);
                }
                return;
            }

            List<BlockPos> path = PortalFrameHelper.collectFrameRotationPath(match.get(), corePos);
            int idx = path.indexOf(pos);
            if (idx < 0 || path.isEmpty()) {
                if (state.getValue(PortalFrameBlock.BLINK_ON)) {
                    level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, false), 3);
                }
                return;
            }

            int head = (int) ((level.getGameTime() / 2L) % path.size());
            boolean blinkOn = idx == head;
            if (state.getValue(PortalFrameBlock.BLINK_ON) != blinkOn) {
                level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, blinkOn), 3);
            }
            return;
        }

        boolean blinkOn = ((level.getGameTime() / 6L) & 1L) == 0L;
        if (state.getValue(PortalFrameBlock.BLINK_ON) == blinkOn) return;

        level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, blinkOn), 3);
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
