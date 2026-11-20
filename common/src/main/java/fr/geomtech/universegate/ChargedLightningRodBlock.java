package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Former portal lightning rod, replaced by the {@link LightningAntennaBlock}. Kept registered so worlds
 * that have one still load; it now behaves like a plain lightning rod and can be crafted into an antenna.
 */
public class ChargedLightningRodBlock extends LightningRodBlock implements EntityBlock {

    public static final int PORTAL_RADIUS = 12;

    public ChargedLightningRodBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargedLightningRodBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }
}
