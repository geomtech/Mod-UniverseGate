package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SolarPanelBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<PanelPart> PART = EnumProperty.create("part", PanelPart.class);

    private static final VoxelShape BASE_SHAPE_NORTH = Shapes.or(
            Block.box(6.0D, 0.0D, 6.0D, 10.0D, 2.0D, 10.0D),
            Block.box(6.9D, 2.0D, 6.9D, 9.1D, 16.0D, 9.1D),
            Block.box(6.4D, 6.0D, 8.8D, 9.6D, 9.8D, 11.6D),
            Block.box(6.8D, 6.5D, 11.6D, 9.2D, 9.3D, 16.0D)
    );

    private static final VoxelShape BASE_SHAPE_EAST = rotate90Y(BASE_SHAPE_NORTH);
    private static final VoxelShape BASE_SHAPE_SOUTH = rotate90Y(BASE_SHAPE_EAST);
    private static final VoxelShape BASE_SHAPE_WEST = rotate90Y(BASE_SHAPE_SOUTH);

    private static final VoxelShape COLUMN_SHAPE = Block.box(6.9D, 0.0D, 6.9D, 9.1D, 16.0D, 9.1D);
    private static final VoxelShape TOP_SHAPE = Shapes.or(
            Block.box(6.3D, 0.0D, 6.3D, 9.7D, 8.0D, 9.7D),
            Block.box(7.0D, 8.0D, 7.0D, 9.0D, 12.0D, 9.0D)
    );

    public SolarPanelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, PanelPart.BASE));
    }

    public static Direction getSocketDirection(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    public static boolean canConnectConduit(BlockState state, Direction directionFromConduitToPanel) {
        if (!state.hasProperty(PART) || state.getValue(PART) != PanelPart.BASE) {
            return false;
        }

        Direction sideSocketDirection = getSocketDirection(state);
        if (directionFromConduitToPanel.getOpposite() == sideSocketDirection) {
            return true;
        }
        return directionFromConduitToPanel == Direction.UP;
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
                .setValue(PART, PanelPart.BASE);
        return state.canSurvive(level, pos) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        PanelPart part = state.getValue(PART);

        BlockState below = level.getBlockState(pos.below());
        return switch (part) {
            case BASE -> below.isFaceSturdy(level, pos.below(), Direction.UP) || below.is(ModBlocks.ENERGY_CONDUIT);
            case LOWER -> isPartWithFacing(level.getBlockState(pos.below()), PanelPart.BASE, facing);
            case UPPER -> isPartWithFacing(level.getBlockState(pos.below()), PanelPart.LOWER, facing);
            case TOP -> isPartWithFacing(level.getBlockState(pos.below()), PanelPart.UPPER, facing);
        };
    }

    @Override
    public void setPlacedBy(Level level,
                            BlockPos pos,
                            BlockState state,
                            @Nullable LivingEntity placer,
                            ItemStack stack) {
        if (level.isClientSide || state.getValue(PART) != PanelPart.BASE) {
            super.setPlacedBy(level, pos, state, placer, stack);
            return;
        }

        Direction facing = state.getValue(FACING);
        level.setBlock(pos.above(), this.defaultBlockState().setValue(FACING, facing).setValue(PART, PanelPart.LOWER), 3);
        level.setBlock(pos.above(2), this.defaultBlockState().setValue(FACING, facing).setValue(PART, PanelPart.UPPER), 3);
        level.setBlock(pos.above(3), this.defaultBlockState().setValue(FACING, facing).setValue(PART, PanelPart.TOP), 3);
        super.setPlacedBy(level, pos, state, placer, stack);
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
    protected BlockState updateShape(BlockState state,
                                     Direction direction,
                                     BlockState neighborState,
                                     LevelAccessor level,
                                     BlockPos currentPos,
                                     BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
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
        PanelPart part = state.getValue(PART);
        if (part == PanelPart.LOWER || part == PanelPart.UPPER) {
            return COLUMN_SHAPE;
        }
        if (part == PanelPart.TOP) {
            return TOP_SHAPE;
        }

        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case NORTH -> BASE_SHAPE_NORTH;
            case EAST -> BASE_SHAPE_EAST;
            case WEST -> BASE_SHAPE_WEST;
            default -> BASE_SHAPE_SOUTH;
        };
    }

    private static boolean isPartWithFacing(BlockState state, PanelPart part, Direction facing) {
        return state.is(ModBlocks.SOLAR_PANEL)
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
        };
    }

    private static VoxelShape rotate90Y(VoxelShape shape) {
        VoxelShape[] out = new VoxelShape[] { Shapes.empty() };
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            out[0] = Shapes.or(out[0], Shapes.box(1.0D - z2, y1, x1, 1.0D - z1, y2, x2));
        });
        return out[0];
    }

    public enum PanelPart implements StringRepresentable {
        BASE("base"),
        LOWER("lower"),
        UPPER("upper"),
        TOP("top");

        private final String name;

        PanelPart(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
