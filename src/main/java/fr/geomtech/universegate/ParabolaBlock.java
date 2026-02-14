package fr.geomtech.universegate;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Block.box(6.5D, 0.0D, 6.5D, 9.5D, 1.0D, 9.5D),
            Block.box(7.2D, 1.0D, 7.2D, 8.8D, 12.0D, 8.8D),
            Block.box(6.2D, 11.0D, 6.2D, 9.8D, 12.2D, 9.8D),
            Block.box(3.0D, 11.2D, 1.5D, 13.0D, 15.5D, 10.5D),
            Block.box(7.6D, 12.0D, 2.0D, 8.4D, 14.0D, 5.0D)
    );
    private static final VoxelShape SHAPE_EAST = rotate90Y(SHAPE_NORTH);
    private static final VoxelShape SHAPE_SOUTH = rotate90Y(SHAPE_EAST);
    private static final VoxelShape SHAPE_WEST = rotate90Y(SHAPE_SOUTH);

    public ParabolaBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.BASE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ParabolaBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(PART, Part.BASE);
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
        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case NORTH -> SHAPE_NORTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_SOUTH;
        };
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
        ROTOR("rotor");

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
