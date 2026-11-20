package fr.geomtech.universegate;

//? if >=1.21.11 {
/*import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A pair of crocodile clips (one red, one blue) biting the block in {@link #FACING}: a portal frame,
 * the portal core, or the lightning antenna. Electric wires plug into it from any side.
 */
public class CrocodileClipBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.DOWN, Block.box(2.0D, 0.0D, 2.0D, 14.0D, 4.0D, 14.0D),
            Direction.UP, Block.box(2.0D, 12.0D, 2.0D, 14.0D, 16.0D, 14.0D),
            Direction.NORTH, Block.box(2.0D, 2.0D, 0.0D, 14.0D, 14.0D, 4.0D),
            Direction.SOUTH, Block.box(2.0D, 2.0D, 12.0D, 14.0D, 14.0D, 16.0D),
            Direction.WEST, Block.box(0.0D, 2.0D, 2.0D, 4.0D, 14.0D, 14.0D),
            Direction.EAST, Block.box(12.0D, 2.0D, 2.0D, 16.0D, 14.0D, 14.0D)
    ));

    public CrocodileClipBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    public CrocodileClipEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrocodileClipEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos gripped = pos.relative(facing);
        BlockState grippedState = level.getBlockState(gripped);
        return canGrip(grippedState) || grippedState.isFaceSturdy(level, gripped, facing.getOpposite());
    }

    /** Blocks a clip holds on to even where their face is not a full one. */
    private static boolean canGrip(BlockState state) {
        return state.is(ModBlocks.PORTAL_FRAME)
                || state.is(ModBlocks.PORTAL_CORE)
                || state.is(ModBlocks.LIGHTNING_ANTENNA);
    }

    //? if >=1.21.11 {
    /*@Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess, BlockPos currentPos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
    *///?} else {
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
    //?}
        if (direction == state.getValue(FACING) && !canSurvive(state, level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
