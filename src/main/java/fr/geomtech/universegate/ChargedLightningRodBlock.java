package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ChargedLightningRodBlock extends LightningRodBlock implements EntityBlock {

    public static final int PORTAL_RADIUS = 12;

    public ChargedLightningRodBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onLightningStrike(BlockState state, Level level, BlockPos pos) {
        super.onLightningStrike(state, level, pos);
        if (level.isClientSide) return;
        if (!(level.getBlockEntity(pos) instanceof ChargedLightningRodBlockEntity be)) return;

        be.addCharge(1);

        if (level instanceof ServerLevel sl) {
            PortalRiftHelper.tryOpenRiftFromRod(sl, pos, PORTAL_RADIUS);
        }
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
