package fr.geomtech.universegate;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ParabolaBlock extends BaseEntityBlock {

    public static final MapCodec<ParabolaBlock> CODEC = simpleCodec(ParabolaBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final VoxelShape BASE_SHAPE = Shapes.or(
            Block.box(2.0D, 0.0D, 2.0D, 14.0D, 2.0D, 14.0D),
            Block.box(4.0D, 2.0D, 4.0D, 12.0D, 4.0D, 12.0D),
            Block.box(6.0D, 4.0D, 6.0D, 10.0D, 16.0D, 10.0D)
    );

    private static final VoxelShape COLUMN_SHAPE = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);

    private static final VoxelShape TOP_SHAPE_NORTH = Shapes.or(
            Block.box(6.0D, 0.0D, 6.0D, 10.0D, 8.0D, 10.0D), // support
            Block.box(-16.0D, 8.0D, -8.0D, 32.0D, 24.0D, 32.0D) // dish approximate box
    );

    private static final VoxelShape TOP_SHAPE_EAST = rotate90Y(TOP_SHAPE_NORTH);
    private static final VoxelShape TOP_SHAPE_SOUTH = rotate90Y(TOP_SHAPE_EAST);
    private static final VoxelShape TOP_SHAPE_WEST = rotate90Y(TOP_SHAPE_SOUTH);

    public ParabolaBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.BASE)
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.BASE ? new ParabolaBlockEntity(pos, state) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();

        for (int i = 1; i <= 3; i++) {
            BlockPos checkPos = pos.above(i);
            BlockState checkState = level.getBlockState(checkPos);
            if (!checkState.isAir() && !checkState.canBeReplaced()) {
                return null;
            }
        }

        BlockState state = this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PART, Part.BASE);
        
        return state.canSurvive(level, pos) ? updateConnections(level, pos, state) : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || state.getValue(PART) != Part.BASE) {
            super.setPlacedBy(level, pos, state, placer, stack);
            return;
        }

        Direction facing = state.getValue(FACING);
        level.setBlock(pos.above(), this.defaultBlockState().setValue(FACING, facing).setValue(PART, Part.LOWER), 3);
        level.setBlock(pos.above(2), this.defaultBlockState().setValue(FACING, facing).setValue(PART, Part.UPPER), 3);
        level.setBlock(pos.above(3), this.defaultBlockState().setValue(FACING, facing).setValue(PART, Part.TOP), 3);
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(PART) != Part.BASE) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.PARABOLA, ParabolaBlockEntity::serverTick);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, movedByPiston);
            return;
        }

        if (!level.isClientSide) {
            BlockPos basePos = getBasePos(pos, state);
            for (int i = 0; i < 4; i++) {
                BlockPos partPos = basePos.above(i);
                if (partPos.equals(pos)) continue;
                if (level.getBlockState(partPos).is(this)) {
                    level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (state.getValue(PART) == Part.BASE) {
            return updateConnections(level, currentPos, state);
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Part part = state.getValue(PART);

        BlockState below = level.getBlockState(pos.below());
        return switch (part) {
            case BASE -> below.isFaceSturdy(level, pos.below(), Direction.UP) || (below.is(ModBlocks.ENERGY_CONDUIT));
            case LOWER -> isPartWithFacing(level.getBlockState(pos.below()), Part.BASE, facing);
            case UPPER -> isPartWithFacing(level.getBlockState(pos.below()), Part.LOWER, facing);
            case TOP, DISH -> isPartWithFacing(level.getBlockState(pos.below()), Part.UPPER, facing);
        };
    }

    private static boolean isPartWithFacing(BlockState state, Part part, Direction facing) {
        return state.is(ModBlocks.PARABOLA_BLOCK)
                && state.hasProperty(PART)
                && state.hasProperty(FACING)
                && state.getValue(PART) == part
                && state.getValue(FACING) == facing;
    }

    private static BlockPos getBasePos(BlockPos pos, BlockState state) {
        return switch (state.getValue(PART)) {
            case BASE -> pos;
            case LOWER -> pos.below();
            case UPPER -> pos.below(2);
            case TOP -> pos.below(3);
            default -> pos;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, NORTH, EAST, SOUTH, WEST);
    }

    private BlockState updateConnections(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getValue(PART) != Part.BASE) return state;
        return state
                .setValue(NORTH, canConnectTo(level, pos.north()))
                .setValue(EAST, canConnectTo(level, pos.east()))
                .setValue(SOUTH, canConnectTo(level, pos.south()))
                .setValue(WEST, canConnectTo(level, pos.west()));
    }

    private boolean canConnectTo(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.ENERGY_CONDUIT);
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
        Part part = state.getValue(PART);
        if (part == Part.LOWER || part == Part.UPPER) {
            return COLUMN_SHAPE;
        }
        if (part == Part.TOP || part == Part.DISH) {
            Direction facing = state.getValue(FACING);
            return switch (facing) {
                case NORTH -> TOP_SHAPE_NORTH;
                case EAST -> TOP_SHAPE_EAST;
                case WEST -> TOP_SHAPE_WEST;
                default -> TOP_SHAPE_SOUTH;
            };
        }

        return BASE_SHAPE;
    }

    private static VoxelShape rotate90Y(VoxelShape shape) {
        VoxelShape[] out = new VoxelShape[] { Shapes.empty() };
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            out[0] = Shapes.or(out[0], Shapes.box(1.0D - z2, y1, x1, 1.0D - z1, y2, x2));
        });
        return out[0];
    }

    public enum Part implements StringRepresentable {
        BASE("base"),
        LOWER("lower"),
        UPPER("upper"),
        TOP("top"),
        DISH("dish");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
