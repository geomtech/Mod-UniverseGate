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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A pair of blue and red electric wires lying on a floor, a wall or a ceiling.
 *
 * <p>{@link #FACING} is the side of the block the wire lies against (DOWN = floor, UP = ceiling).
 * Each side property tells whether the wire runs toward that direction: {@code side} when it goes
 * flat to the edge of the block, {@code up} when it also climbs the face of the next block (inner
 * corner or step), like redstone dust. Wires link anything that conducts: other wires, crocodile
 * clips and the base of the lightning antenna (see {@link WireNetwork}).
 */
public class ElectricWireBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<RedstoneSide> NORTH = EnumProperty.create("north", RedstoneSide.class);
    public static final EnumProperty<RedstoneSide> SOUTH = EnumProperty.create("south", RedstoneSide.class);
    public static final EnumProperty<RedstoneSide> EAST = EnumProperty.create("east", RedstoneSide.class);
    public static final EnumProperty<RedstoneSide> WEST = EnumProperty.create("west", RedstoneSide.class);
    public static final EnumProperty<RedstoneSide> UP = EnumProperty.create("up", RedstoneSide.class);
    public static final EnumProperty<RedstoneSide> DOWN = EnumProperty.create("down", RedstoneSide.class);

    private static final Map<Direction, EnumProperty<RedstoneSide>> SIDES = new EnumMap<>(Map.of(
            Direction.NORTH, NORTH,
            Direction.SOUTH, SOUTH,
            Direction.EAST, EAST,
            Direction.WEST, WEST,
            Direction.UP, UP,
            Direction.DOWN, DOWN
    ));

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.DOWN, Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D),
            Direction.UP, Block.box(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D),
            Direction.NORTH, Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1.0D),
            Direction.SOUTH, Block.box(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D),
            Direction.WEST, Block.box(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D),
            Direction.EAST, Block.box(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D)
    ));

    /** Where a wire leads in one direction: how it is drawn, and the block it reaches. */
    public record Link(RedstoneSide side, BlockPos target) {}

    public ElectricWireBlock(Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any().setValue(FACING, Direction.DOWN);
        for (EnumProperty<RedstoneSide> side : SIDES.values()) {
            state = state.setValue(side, RedstoneSide.NONE);
        }
        this.registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    public static EnumProperty<RedstoneSide> sideProperty(Direction direction) {
        return SIDES.get(direction);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // Lie on the clicked face first, then on whatever the player looks at.
        Direction clicked = context.getClickedFace().getOpposite();
        BlockState state = this.defaultBlockState().setValue(FACING, clicked);
        if (canSurvive(state, level, pos)) {
            return withConnections(level, pos, state);
        }
        for (Direction direction : context.getNearestLookingDirections()) {
            state = this.defaultBlockState().setValue(FACING, direction);
            if (canSurvive(state, level, pos)) {
                return withConnections(level, pos, state);
            }
        }
        return null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction attach = state.getValue(FACING);
        BlockPos supportPos = pos.relative(attach);
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, attach.getOpposite());
    }

    //? if >=1.21.11 {
    /*@Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess, BlockPos currentPos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
    *///?} else {
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
    //?}
        if (!canSurvive(state, level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return withConnections(level, currentPos, state);
    }

    /**
     * Wires also link across edges (inner corners, outer corners, steps), so a wire placed or removed
     * diagonally from this one does not reach it through {@code updateShape}: refresh those here, as
     * redstone dust does.
     */
    @Override
    protected void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, int flags, int recursionLeft) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int nonZero = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
                    if (nonZero != 2) continue;
                    cursor.setWithOffset(pos, dx, dy, dz);
                    BlockState neighbor = level.getBlockState(cursor);
                    if (!neighbor.is(this)) continue;
                    BlockState updated = withConnections(level, cursor, neighbor);
                    if (updated != neighbor) {
                        level.setBlock(cursor.immutable(), updated, flags, recursionLeft);
                    }
                }
            }
        }
    }

    public static BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        Direction attach = state.getValue(FACING);
        for (Direction direction : Direction.values()) {
            RedstoneSide side = RedstoneSide.NONE;
            if (direction.getAxis() != attach.getAxis()) {
                Link link = link(level, pos, attach, direction);
                if (link != null) side = link.side();
            }
            state = state.setValue(SIDES.get(direction), side);
        }
        return state;
    }

    /**
     * Where the wire at {@code pos}, lying against {@code attach}, leads toward {@code direction}
     * (perpendicular to {@code attach}), or null when nothing conducting is there.
     */
    public static @Nullable Link link(BlockGetter level, BlockPos pos, Direction attach, Direction direction) {
        BlockPos next = pos.relative(direction);
        BlockState nextState = level.getBlockState(next);

        // Same surface, or the wire continues on the face this one runs into (inner corner, upper side).
        if (isWire(nextState)) {
            Direction nextAttach = nextState.getValue(FACING);
            if (nextAttach == attach || nextAttach == direction) return new Link(RedstoneSide.SIDE, next);
        }
        if (isTerminal(nextState)) return new Link(RedstoneSide.SIDE, next);

        // Inner corner, lower side: the wire climbs the face in front of it, inside this block.
        BlockPos inside = pos.relative(attach.getOpposite());
        BlockState insideState = level.getBlockState(inside);
        if (isWire(insideState) && insideState.getValue(FACING) == direction) {
            return new Link(RedstoneSide.UP, inside);
        }

        if (nextState.isRedstoneConductor(level, next)) {
            // Step up: the wire climbs the step and goes on on top of it.
            BlockPos above = next.relative(attach.getOpposite());
            BlockState aboveState = level.getBlockState(above);
            if (isWire(aboveState) && aboveState.getValue(FACING) == attach
                    && !insideState.isRedstoneConductor(level, inside)) {
                return new Link(RedstoneSide.UP, above);
            }
        } else {
            // Outer corner (the wire wraps around the edge) or step down.
            BlockPos below = next.relative(attach);
            BlockState belowState = level.getBlockState(below);
            if (isWire(belowState)) {
                Direction belowAttach = belowState.getValue(FACING);
                if (belowAttach == attach || belowAttach == direction.getOpposite()) {
                    return new Link(RedstoneSide.SIDE, below);
                }
            }
        }
        return null;
    }

    public static boolean isWire(BlockState state) {
        return state.is(ModBlocks.ELECTRIC_WIRE);
    }

    /** Blocks a wire plugs into directly: crocodile clips and the base of the lightning antenna. */
    public static boolean isTerminal(BlockState state) {
        return state.is(ModBlocks.CROCODILE_CLIP) || LightningAntennaBlock.isBase(state);
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
        BlockState rotated = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            rotated = rotated.setValue(SIDES.get(rotation.rotate(direction)), state.getValue(SIDES.get(direction)));
        }
        return rotated;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            mirrored = mirrored.setValue(SIDES.get(mirror.mirror(direction)), state.getValue(SIDES.get(direction)));
        }
        return mirrored;
    }
}
