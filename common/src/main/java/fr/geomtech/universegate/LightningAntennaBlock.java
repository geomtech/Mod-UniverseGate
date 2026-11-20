package fr.geomtech.universegate;

//? if >=1.21.11 {
/*import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
*///?}
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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

/**
 * The lightning antenna: a 4-block mast that catches lightning (its top is a lightning rod) and
 * sends the charge through electric wires and crocodile clips to a portal, to open the Rift.
 * Only the base holds a block entity; the other parts follow it.
 */
public class LightningAntennaBlock extends BaseEntityBlock {

    public static final MapCodec<LightningAntennaBlock> CODEC = simpleCodec(LightningAntennaBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");
    public static final int HEIGHT = 4;

    private static final VoxelShape BASE_SHAPE = Shapes.or(
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 2.0D, 15.0D),
            Block.box(4.0D, 2.0D, 4.0D, 12.0D, 10.0D, 12.0D),
            Block.box(6.0D, 10.0D, 6.0D, 10.0D, 16.0D, 10.0D)
    );
    private static final VoxelShape LOWER_SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);
    private static final VoxelShape UPPER_SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);
    private static final VoxelShape TOP_SHAPE = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 14.0D, 10.0D);

    public LightningAntennaBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.BASE)
                .setValue(CHARGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public static boolean isBase(BlockState state) {
        return state.is(ModBlocks.LIGHTNING_ANTENNA) && state.getValue(PART) == Part.BASE;
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(CHARGED) && state.getValue(PART) == Part.TOP ? 10 : 0;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.BASE ? new LightningAntennaBlockEntity(pos, state) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(PART) != Part.BASE) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.LIGHTNING_ANTENNA, LightningAntennaBlockEntity::serverTick);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        for (int i = 1; i < HEIGHT; i++) {
            BlockPos checkPos = pos.above(i);
            if (checkPos.getY() >= level.getMaxBuildHeight()) return null;
            if (!level.getBlockState(checkPos).canBeReplaced(context)) return null;
        }
        BlockState state = this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(PART, Part.BASE);
        return state.canSurvive(level, pos) ? state : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide() && state.getValue(PART) == Part.BASE) {
            Direction facing = state.getValue(FACING);
            Part[] parts = Part.values();
            for (int i = 1; i < HEIGHT; i++) {
                level.setBlock(pos.above(i), this.defaultBlockState().setValue(FACING, facing).setValue(PART, parts[i]), 3);
            }
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    /** Only the base drops the antenna: breaking a higher part breaks the base, which takes the rest with it. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative() && state.getValue(PART) != Part.BASE) {
            BlockPos base = basePos(pos, state);
            if (isBase(level.getBlockState(base))) {
                level.destroyBlock(base, true, player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Part part = state.getValue(PART);
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        if (part == Part.BASE) {
            return below.isFaceSturdy(level, belowPos, Direction.UP);
        }
        return below.is(this) && below.getValue(PART).ordinal() == part.ordinal() - 1;
    }

    //? if >=1.21.11 {
    /*@Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess, BlockPos currentPos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
    *///?} else {
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
    //?}
        Part part = state.getValue(PART);
        // Like a door: a part whose neighbour in the mast is gone goes too, so the whole antenna breaks at once.
        if (direction == Direction.DOWN && !canSurvive(state, level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (direction == Direction.UP && part != Part.TOP) {
            boolean partAbove = neighborState.is(this) && neighborState.getValue(PART).ordinal() == part.ordinal() + 1;
            if (!partAbove) return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, CHARGED);
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
        return switch (state.getValue(PART)) {
            case BASE -> BASE_SHAPE;
            case LOWER -> LOWER_SHAPE;
            case UPPER -> UPPER_SHAPE;
            case TOP -> TOP_SHAPE;
        };
    }

    public static BlockPos basePos(BlockPos pos, BlockState state) {
        return pos.below(state.getValue(PART).ordinal());
    }

    /** Sets the charged look on every part of the antenna standing on {@code basePos}. */
    public static void setCharged(Level level, BlockPos basePos, boolean charged) {
        for (int i = 0; i < HEIGHT; i++) {
            BlockPos partPos = basePos.above(i);
            BlockState partState = level.getBlockState(partPos);
            if (partState.is(ModBlocks.LIGHTNING_ANTENNA) && partState.getValue(CHARGED) != charged) {
                level.setBlock(partPos, partState.setValue(CHARGED, charged), Block.UPDATE_CLIENTS);
            }
        }
    }

    public enum Part implements StringRepresentable {
        BASE("base"),
        LOWER("lower"),
        UPPER("upper"),
        TOP("top");

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
