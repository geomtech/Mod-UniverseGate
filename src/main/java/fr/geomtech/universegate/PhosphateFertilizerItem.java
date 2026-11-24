package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

public class PhosphateFertilizerItem extends Item {

    private static final int GROWTH_ATTEMPTS = 4;

    public PhosphateFertilizerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BonemealableBlock plant)
                || !plant.isValidBonemealTarget(level, pos, state)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.FAIL;

        boolean fertilized = false;
        for (int attempt = 0; attempt < GROWTH_ATTEMPTS; attempt++) {
            BlockState currentState = serverLevel.getBlockState(pos);
            if (!(currentState.getBlock() instanceof BonemealableBlock currentPlant)
                    || !currentPlant.isValidBonemealTarget(serverLevel, pos, currentState)) {
                break;
            }
            if (currentPlant.isBonemealSuccess(serverLevel, serverLevel.getRandom(), pos, currentState)) {
                currentPlant.performBonemeal(serverLevel, serverLevel.getRandom(), pos, currentState);
                fertilized = true;
            }
        }

        if (!fertilized) return InteractionResult.FAIL;
        if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(false);
    }
}
