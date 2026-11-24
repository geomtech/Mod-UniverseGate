package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PortalFrameBlockEntity extends BlockEntity {

    public PortalFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PORTAL_FRAME, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortalFrameBlockEntity blockEntity) {
        // The 3D ring handles its own visual animation; crystal light is set once when the portal forms.
    }
}
