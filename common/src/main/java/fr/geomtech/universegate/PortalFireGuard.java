package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;

final class PortalFireGuard {

    private PortalFireGuard() {}

    static void clearFireAbove(Level level, BlockPos pos) {
        if (level.isClientSide) return;

        BlockPos above = pos.above();
        if (level.getBlockState(above).getBlock() instanceof BaseFireBlock) {
            level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
        }
    }
}
