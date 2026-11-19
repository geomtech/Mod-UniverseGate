package fr.geomtech.universegate;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class MobClonerControllerBlock extends BaseEntityBlock {

    public static final MapCodec<MobClonerControllerBlock> CODEC = simpleCodec(MobClonerControllerBlock::new);

    public MobClonerControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobClonerControllerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
                                           BlockState state,
                                           Level level,
                                           BlockPos pos,
                                           Player player,
                                           net.minecraft.world.InteractionHand hand,
                                           BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MobClonerControllerBlockEntity controller)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }

        if (stack.is(ModItems.DNA)) {
            if (!level.isClientSide) {
                if (controller.tryInsertDna(player, stack)) {
                    return ItemInteractionResult.CONSUME;
                }
                if (controller.isChargeActive()) {
                    player.displayClientMessage(Component.translatable("message.universegate.mob_cloner_controller_charge_running"), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.universegate.mob_cloner_controller_slot_occupied"), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            sp.openMenu(controller);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MobClonerControllerBlockEntity controller && player instanceof ServerPlayer sp) {
            sp.openMenu(controller);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MobClonerControllerBlockEntity controller) {
                controller.dropContents();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                             BlockState state,
                                                                             BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.MOB_CLONER_CONTROLLER, MobClonerControllerBlockEntity::tick);
    }
}
