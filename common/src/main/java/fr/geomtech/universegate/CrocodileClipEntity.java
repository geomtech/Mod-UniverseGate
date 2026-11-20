package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Render anchor. Cable routes and jaw positions are derived from the surrounding blocks. */
public final class CrocodileClipEntity extends BlockEntity {
    public CrocodileClipEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CROCODILE_CLIP, pos, state);
    }

    /** Older test builds saved clips as plain blocks. Repair only sections whose palette contains a clip. */
    public static void restoreMissing(LevelChunk chunk) {
        chunk.findBlocks(state -> state.is(ModBlocks.CROCODILE_CLIP), (pos, state) -> {
            if (chunk.getBlockEntity(pos) != null) return;
            chunk.addAndRegisterBlockEntity(new CrocodileClipEntity(pos.immutable(), state));
            //? if >=1.21.11 {
            /*chunk.markUnsaved();
            *///?} else {
            chunk.setUnsaved(true);
            //?}
        });
    }
}
