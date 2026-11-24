package fr.geomtech.universegate;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class AcidNeonLampBlock extends BaseEntityBlock {

    public static final MapCodec<AcidNeonLampBlock> CODEC = simpleCodec(AcidNeonLampBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public AcidNeonLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(LIT) ? 15 : 0;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AcidNeonLampBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
                                           BlockState state,
                                           Level level,
                                           BlockPos pos,
                                           Player player,
                                           net.minecraft.world.InteractionHand hand,
                                           BlockHitResult hit) {
        if (!stack.is(ModItems.ACID_BATTERY)) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!(level.getBlockEntity(pos) instanceof AcidNeonLampBlockEntity lamp)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        int charge = AcidBatteryItem.getStoredEnergy(stack);
        if (charge <= 0 || lamp.getStoredEnergy() >= AcidNeonLampBlockEntity.CAPACITY) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        int accepted = lamp.addEnergy(charge);
        if (accepted <= 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!player.getAbilities().instabuild) {
            if (charge == accepted) stack.shrink(1);
            else AcidBatteryItem.setStoredEnergy(stack, charge - accepted);
        }
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AcidNeonLampBlockEntity machine) {
            player.displayClientMessage(Component.translatable("message.universegate.machine_charge",
                    machine.getStoredEnergy(), AcidNeonLampBlockEntity.CAPACITY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof AcidNeonLampBlockEntity lamp
                && lamp.getStoredEnergy() > 0) {
            int remaining = lamp.getStoredEnergy();
            while (remaining > 0) {
                ItemStack battery = AcidBatteryItem.createChargedStack();
                int charge = Math.min(remaining, AcidBatteryItem.CAPACITY);
                AcidBatteryItem.setStoredEnergy(battery, charge);
                net.minecraft.world.Containers.dropItemStack(level,
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, battery);
                remaining -= charge;
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                             BlockState state,
                                                                             BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.ACID_NEON_LAMP, AcidNeonLampBlockEntity::tick);
    }
}
