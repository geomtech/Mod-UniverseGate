package fr.geomtech.universegate;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CrystalCondenserBlock extends BaseEntityBlock {

    public static final MapCodec<CrystalCondenserBlock> CODEC = simpleCodec(CrystalCondenserBlock::new);

    public static final BooleanProperty HAS_CRYSTAL = BooleanProperty.create("has_crystal");

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 2.0D, 15.0D),
            Block.box(2.0D, 2.0D, 2.0D, 14.0D, 3.0D, 14.0D),
            Block.box(3.0D, 3.0D, 3.0D, 13.0D, 4.0D, 13.0D),
            Block.box(6.0D, 4.0D, 6.0D, 10.0D, 6.0D, 10.0D),
            Block.box(2.0D, 4.0D, 2.0D, 3.0D, 16.0D, 3.0D),
            Block.box(13.0D, 4.0D, 2.0D, 14.0D, 16.0D, 3.0D),
            Block.box(2.0D, 4.0D, 13.0D, 3.0D, 16.0D, 14.0D),
            Block.box(13.0D, 4.0D, 13.0D, 14.0D, 16.0D, 14.0D)
    );

    public CrystalCondenserBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HAS_CRYSTAL, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MeteorologicalCatalystBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_CRYSTAL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
                                           BlockState state,
                                           Level level,
                                           BlockPos pos,
                                           Player player,
                                           InteractionHand hand,
                                           BlockHitResult hit) {
        if (!state.getValue(HAS_CRYSTAL) && stack.is(ModItems.ENERGY_CRYSTAL)) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(HAS_CRYSTAL, true), 3);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.2F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

}
