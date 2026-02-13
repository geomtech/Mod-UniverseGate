package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class PortalFrameHelper {

    private PortalFrameHelper() {}

    public static List<BlockPos> collectFrame(PortalFrameDetector.FrameMatch match, BlockPos corePos) {
        List<BlockPos> result = new ArrayList<>();
        int halfWidth = PortalFrameDetector.INNER_WIDTH / 2 + 1; // outer half width
        int topY = PortalFrameDetector.INNER_HEIGHT + 1; // outer height

        for (int dy = 0; dy <= topY; dy++) {
            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                boolean isBorder = dy == 0 || dy == topY || dx == -halfWidth || dx == halfWidth;
                if (!isBorder) continue;
                if (dx == 0 && dy == 0) continue; // core position

                BlockPos p = corePos.offset(match.right().getStepX() * dx, dy, match.right().getStepZ() * dx);
                result.add(p);
            }
        }

        return result;
    }

    public static List<BlockPos> collectFrameRotationPath(PortalFrameDetector.FrameMatch match, BlockPos corePos) {
        List<BlockPos> result = new ArrayList<>();
        int halfWidth = PortalFrameDetector.INNER_WIDTH / 2 + 1;
        int topY = PortalFrameDetector.INNER_HEIGHT + 1;

        result.add(offset(corePos, match, -1, 0));
        result.add(offset(corePos, match, -halfWidth, 0));

        for (int dy = 1; dy <= topY; dy++) {
            result.add(offset(corePos, match, -halfWidth, dy));
        }

        for (int dx = -halfWidth + 1; dx <= halfWidth; dx++) {
            result.add(offset(corePos, match, dx, topY));
        }

        for (int dy = topY - 1; dy >= 1; dy--) {
            result.add(offset(corePos, match, halfWidth, dy));
        }

        result.add(offset(corePos, match, halfWidth, 0));
        result.add(offset(corePos, match, 1, 0));
        return result;
    }

    private static BlockPos offset(BlockPos corePos, PortalFrameDetector.FrameMatch match, int dx, int dy) {
        return corePos.offset(match.right().getStepX() * dx, dy, match.right().getStepZ() * dx);
    }
}
