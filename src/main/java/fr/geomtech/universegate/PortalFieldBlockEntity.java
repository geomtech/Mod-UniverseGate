package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PortalFieldBlockEntity extends BlockEntity {

    public PortalFieldBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PORTAL_FIELD, pos, state);
    }
}
