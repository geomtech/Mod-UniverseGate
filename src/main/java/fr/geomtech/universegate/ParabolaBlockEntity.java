package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ParabolaBlockEntity extends BlockEntity {

    public ParabolaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARABOLA, pos, state);
    }
}
