package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

public class EnergyConduitBlock extends Block {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = Map.of(
            Direction.NORTH, NORTH,
            Direction.EAST, EAST,
            Direction.SOUTH, SOUTH,
            Direction.WEST, WEST,
            Direction.UP, UP,
            Direction.DOWN, DOWN
    );

    private static final VoxelShape CENTER_SHAPE = Block.box(6.0D, 6.0D, 6.0D, 10.0D, 10.0D, 10.0D);
    private static final VoxelShape NORTH_SHAPE = Block.box(6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 6.0D);
    private static final VoxelShape SOUTH_SHAPE = Block.box(6.0D, 6.0D, 10.0D, 10.0D, 10.0D, 16.0D);
    private static final VoxelShape WEST_SHAPE = Block.box(0.0D, 6.0D, 6.0D, 6.0D, 10.0D, 10.0D);
    private static final VoxelShape EAST_SHAPE = Block.box(10.0D, 6.0D, 6.0D, 16.0D, 10.0D, 10.0D);
    private static final VoxelShape UP_SHAPE = Block.box(6.0D, 10.0D, 6.0D, 10.0D, 16.0D, 10.0D);
    private static final VoxelShape DOWN_SHAPE = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 6.0D, 10.0D);
    private static final VoxelShape[] SHAPE_CACHE = buildShapeCache();

    public EnergyConduitBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return updateConnections(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state,
                                     Direction direction,
                                     BlockState neighborState,
                                     LevelAccessor level,
                                     BlockPos currentPos,
                                     BlockPos neighborPos) {
        BooleanProperty property = PROPERTY_BY_DIRECTION.get(direction);
        if (property == null) return state;
        return state.setValue(property, canConnectTo(level, neighborPos));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_CACHE[shapeIndex(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_CACHE[shapeIndex(state)];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    private BlockState updateConnections(BlockGetter level, BlockPos pos, BlockState state) {
        return state
                .setValue(NORTH, canConnectTo(level, pos.north()))
                .setValue(EAST, canConnectTo(level, pos.east()))
                .setValue(SOUTH, canConnectTo(level, pos.south()))
                .setValue(WEST, canConnectTo(level, pos.west()))
                .setValue(UP, canConnectTo(level, pos.above()))
                .setValue(DOWN, canConnectTo(level, pos.below()));
    }

    private boolean canConnectTo(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.ENERGY_CONDUIT)
                || state.is(ModBlocks.ENERGY_CONDENSER)
                || state.is(ModBlocks.PORTAL_CORE)
                || state.is(ModBlocks.PORTAL_FRAME)
                || state.is(ModBlocks.PORTAL_FIELD)
                || state.is(ModBlocks.PORTAL_KEYBOARD)
                || state.is(ModBlocks.CHARGED_LIGHTNING_ROD)
                || state.is(ModBlocks.METEOROLOGICAL_CONTROLLER);
    }

    private static VoxelShape[] buildShapeCache() {
        VoxelShape[] shapes = new VoxelShape[64];
        for (int i = 0; i < shapes.length; i++) {
            VoxelShape shape = CENTER_SHAPE;
            if ((i & 1) != 0) shape = Shapes.or(shape, NORTH_SHAPE);
            if ((i & 2) != 0) shape = Shapes.or(shape, EAST_SHAPE);
            if ((i & 4) != 0) shape = Shapes.or(shape, SOUTH_SHAPE);
            if ((i & 8) != 0) shape = Shapes.or(shape, WEST_SHAPE);
            if ((i & 16) != 0) shape = Shapes.or(shape, UP_SHAPE);
            if ((i & 32) != 0) shape = Shapes.or(shape, DOWN_SHAPE);
            shapes[i] = shape;
        }
        return shapes;
    }

    private static int shapeIndex(BlockState state) {
        int index = 0;
        if (state.getValue(NORTH)) index |= 1;
        if (state.getValue(EAST)) index |= 2;
        if (state.getValue(SOUTH)) index |= 4;
        if (state.getValue(WEST)) index |= 8;
        if (state.getValue(UP)) index |= 16;
        if (state.getValue(DOWN)) index |= 32;
        return index;
    }
}
