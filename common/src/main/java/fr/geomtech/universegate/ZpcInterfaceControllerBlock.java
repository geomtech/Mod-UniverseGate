package fr.geomtech.universegate;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ZpcInterfaceControllerBlock extends BaseEntityBlock {

    public static final MapCodec<ZpcInterfaceControllerBlock> CODEC = simpleCodec(ZpcInterfaceControllerBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty HAS_ZPC = BooleanProperty.create("has_zpc");
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    public static final IntegerProperty INSERT_STAGE = IntegerProperty.create("insert_stage", 0, 4);

    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 2.5D, 15.0D),
            Block.box(2.0D, 2.5D, 2.0D, 14.0D, 8.5D, 14.0D),
            Block.box(5.0D, 8.5D, 5.0D, 11.0D, 10.5D, 11.0D),
            Block.box(0.0D, 8.5D, 8.0D, 5.0D, 11.5D, 14.0D),
            Block.box(11.0D, 8.5D, 8.0D, 16.0D, 11.5D, 14.0D),
            Block.box(5.0D, 8.5D, 0.0D, 11.0D, 11.5D, 5.0D)
    );
    private static final VoxelShape SHAPE_EAST = rotate90Y(SHAPE_NORTH);
    private static final VoxelShape SHAPE_SOUTH = rotate90Y(SHAPE_EAST);
    private static final VoxelShape SHAPE_WEST = rotate90Y(SHAPE_SOUTH);

    public ZpcInterfaceControllerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HAS_ZPC, false)
                .setValue(ACTIVE, false)
                .setValue(INSERT_STAGE, 0));
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(ACTIVE) ? 14 : 0;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZpcInterfaceControllerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_ZPC, ACTIVE, INSERT_STAGE);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_SOUTH;
        };
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
                                           BlockState state,
                                           Level level,
                                           BlockPos pos,
                                           Player player,
                                           net.minecraft.world.InteractionHand hand,
                                           BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ZpcInterfaceControllerBlockEntity controller)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }

        if (stack.is(ModItems.ZPC)) {
            if (!level.isClientSide) {
                controller.tryInsertZpc(player, stack);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide && player.isShiftKeyDown() && controller.hasZpcInstalled()) {
            if (controller.hasUnengagedZpc()) {
                controller.startEngageAnimation();
            } else {
                controller.ejectZpc(player);
            }
            return ItemInteractionResult.sidedSuccess(false);
        }

        if (!level.isClientSide && !player.isShiftKeyDown() && player instanceof ServerPlayer sp) {
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
        if (!(level.getBlockEntity(pos) instanceof ZpcInterfaceControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                if (controller.hasZpcInstalled()) {
                    if (controller.hasUnengagedZpc()) {
                        controller.startEngageAnimation();
                    } else {
                        controller.ejectZpc(player);
                    }
                }
            } else if (player instanceof ServerPlayer sp) {
                sp.openMenu(controller);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ZpcInterfaceControllerBlockEntity controller) {
                controller.dropInstalledZpc();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                             BlockState state,
                                                                             BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.ZPC_INTERFACE_CONTROLLER,
                (lvl, pos, blockState, be) -> be.serverTick());
    }

    private static VoxelShape rotate90Y(VoxelShape shape) {
        VoxelShape[] out = new VoxelShape[] { Shapes.empty() };
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            out[0] = Shapes.or(out[0], Shapes.box(1.0D - z2, y1, x1, 1.0D - z1, y2, x2));
        });
        return out[0];
    }
}
