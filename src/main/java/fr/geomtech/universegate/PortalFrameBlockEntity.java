package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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

        if (!state.getValue(PortalFrameBlock.ACTIVE) || !state.getValue(PortalFrameBlock.UNSTABLE)) {
            if (state.getValue(PortalFrameBlock.BLINK_ON)) {
                level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, false), 3);
            }
            return;
        }

        boolean blinkOn = ((level.getGameTime() / 6L) & 1L) == 0L;
        if (state.getValue(PortalFrameBlock.BLINK_ON) == blinkOn) return;

        level.setBlock(pos, state.setValue(PortalFrameBlock.BLINK_ON, blinkOn), 3);
    }
}
