package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.List;

public class KeloDeadTreeFeature extends Feature<NoneFeatureConfiguration> {

    public KeloDeadTreeFeature(com.mojang.serialization.Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();

        BlockPos base = context.origin();
        if (level.isEmptyBlock(base)) base = base.below();
        if (!level.getBlockState(base).is(ModBlocks.VOID_BLOCK)) return false;

        BlockPos trunkStart = base.above();
        if (isReservedPortalColumn(trunkStart)) return false;
        int height = 4 + random.nextInt(6); // 4..9

        for (int y = 0; y < height; y++) {
            BlockPos p = trunkStart.above(y);
            if (isReservedPortalColumn(p)) return false;
            if (!level.isEmptyBlock(p)) return false;
        }

        BlockState vertical = ModBlocks.KELO_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int y = 0; y < height; y++) {
            setBlock(level, trunkStart.above(y), vertical);
        }

        int branchCount = 1 + random.nextInt(3);
        List<Direction> dirs = List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
        for (int i = 0; i < branchCount; i++) {
            Direction d = dirs.get(random.nextInt(dirs.size()));
            int len = 1 + random.nextInt(3);
            int y = height - 2 - random.nextInt(2);
            BlockPos branchStart = trunkStart.above(Math.max(1, y));

            BlockState horizontal = ModBlocks.KELO_LOG.defaultBlockState().setValue(
                    RotatedPillarBlock.AXIS,
                    (d.getAxis() == Direction.Axis.X) ? Direction.Axis.X : Direction.Axis.Z
            );

            for (int l = 1; l <= len; l++) {
                BlockPos p = branchStart.relative(d, l);
                if (isReservedPortalColumn(p)) break;
                if (!level.isEmptyBlock(p)) break;
                setBlock(level, p, horizontal);
            }
        }

        return true;
    }

    private static boolean isReservedPortalColumn(BlockPos pos) {
        return pos.getX() == 0 && pos.getZ() == 0;
    }
}
