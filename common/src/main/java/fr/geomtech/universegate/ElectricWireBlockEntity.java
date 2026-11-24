package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Render anchor for smooth 3D electric wire tubes; geometry is derived purely from the block state. */
public final class ElectricWireBlockEntity extends BlockEntity {

    public ElectricWireBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_WIRE, pos, state);
    }

    /** Older worlds saved electric wires as plain blocks. Repair only sections whose palette contains a wire. */
    public static void restoreMissing(LevelChunk chunk) {
        chunk.findBlocks(state -> state.is(ModBlocks.ELECTRIC_WIRE), (pos, state) -> {
            if (chunk.getBlockEntity(pos) != null) return;
            chunk.addAndRegisterBlockEntity(new ElectricWireBlockEntity(pos.immutable(), state));
            //? if >=1.21.11 {
            /*chunk.markUnsaved();
            *///?} else {
            chunk.setUnsaved(true);
            //?}
        });
    }
}
