package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MeteorologicalCatalystBlockEntity extends BlockEntity {

    public MeteorologicalCatalystBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.METEOROLOGICAL_CATALYST, pos, state);
    }
}
