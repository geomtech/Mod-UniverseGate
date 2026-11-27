package fr.geomtech.universegate;

//? if >=1.21.11 {
/*import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.ScheduledTickAccess;
import org.jetbrains.annotations.Nullable;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

public class PortalFrameBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {

    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    public static final BooleanProperty UNSTABLE = BooleanProperty.create("unstable");
    public static final BooleanProperty BLINK_ON = BooleanProperty.create("blink_on");
    /** Ce bloc appartient au modèle 3D dessiné par le Core. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    /** 0 : bloc de cadre classique ; 1..17 : ancrage invisible d'un segment d'anneau (voir PortalGateGeometry). */
    public static final IntegerProperty PIECE = IntegerProperty.create("piece", 0, PortalGateGeometry.PIECE_COUNT);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public PortalFrameBlock(Properties properties) {
        super(properties.dynamicShape());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ACTIVE, false)
                .setValue(UNSTABLE, false)
                .setValue(BLINK_ON, false)
                .setValue(FORMED, false)
                .setValue(PIECE, 0)
                .setValue(AXIS, Direction.Axis.X)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, UNSTABLE, BLINK_ON, FORMED, PIECE, AXIS, WATERLOGGED);
    }

    static BlockState withPlacementWater(BlockState state, BlockGetter level, BlockPos pos) {
        return state.setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withPlacementWater(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    //? if >=1.21.11 {
    /*@Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }
    *///?} else {
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
    //?}

    public static boolean isSegmentAnchor(BlockState state) {
        return state.is(ModBlocks.PORTAL_FRAME) && state.getValue(PIECE) != 0;
    }

    private static boolean hidden(BlockState state) {
        return state.getValue(FORMED) || state.getValue(PIECE) != 0;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return hidden(state) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int piece = state.getValue(PIECE);
        VoxelShape localShape = piece != 0 ? PortalGateGeometry.segmentShape(piece, state.getValue(AXIS)) : Shapes.block();
        return state.getValue(FORMED) ? PortalGateGeometry.shape(state, level, pos, localShape) : localShape;
    }

    // Bloc masqué : il ne doit plus cacher les faces de ses voisins.
    //? if >=1.21.11 {
    /*@Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return hidden(state) ? Shapes.empty() : Shapes.block();
    }
    *///?} else {
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return hidden(state) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        // The lighting thread must not query neighboring chunks to resolve the ring shape.
        return hidden(state) && !state.getValue(WATERLOGGED);
    }
    //?}

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(PIECE) != 0
                && (player.isCreative() || !player.hasCorrectToolForDrops(state))) {
            PortalRingSegments.suppressNextDrop(pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    //? if >=1.21.11 {
    /*@Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return state.getValue(PIECE) != 0 ? new ItemStack(ModItems.PORTAL_RING_SEGMENT)
                : super.getCloneItemStack(level, pos, state, includeData);
    }
    *///?} else {
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(PIECE) != 0 ? new ItemStack(ModItems.PORTAL_RING_SEGMENT)
                : super.getCloneItemStack(level, pos, state);
    }
    //?}

    public static int lightLevel(BlockState state) {
        return state.getValue(ACTIVE) ? 10 : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(ACTIVE) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalFrameBlockEntity(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        PortalFireGuard.clearFireAbove(level, pos);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                             BlockState state,
                                                                             BlockEntityType<T> type) {
        if (type != ModBlockEntities.PORTAL_FRAME) return null;
        return (lvl, pos, st, be) -> PortalFrameBlockEntity.tick(lvl, pos, st, (PortalFrameBlockEntity) be);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) return;
        if (state.getValue(FORMED)) return; // l'anneau 3D gère ses propres effets
        boolean unstable = state.getValue(UNSTABLE);
        if (!state.getValue(BLINK_ON)) return;

        if (unstable) {
            if (random.nextFloat() > 0.62F) return;
            int count = 2 + random.nextInt(4);
            for (int i = 0; i < count; i++) {
                double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.65;
                double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.65;
                double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.65;
                double vx = (random.nextDouble() - 0.5) * 0.03;
                double vy = (random.nextDouble() - 0.5) * 0.03;
                double vz = (random.nextDouble() - 0.5) * 0.03;
                level.addParticle(ModParticles.SPARK, x, y, z, vx, vy, vz);
            }
            return;
        }

        if (random.nextFloat() > 0.07F) return;
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
        double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
        double vx = (random.nextDouble() - 0.5) * 0.01;
        double vy = 0.005 + random.nextDouble() * 0.01;
        double vz = (random.nextDouble() - 0.5) * 0.01;
        level.addParticle(ModParticles.SPARK, x, y, z, vx, vy, vz);
    }

    //? if >=1.21.11 {
    /*@Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
    *///?} else {
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
    //?}
        PortalFireGuard.clearFireAbove(level, pos);
        //? if >=1.21.11 {
        /*super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        *///?} else {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        //?}
    }

    //? if >=1.21.11 {
    /*@Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (level.getBlockState(pos).is(this)) {
            // Simple changement d'état (FORMED, BLINK_ON...) : le cadre est toujours là.
            super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
            return;
        }
        if (state.getValue(PIECE) != 0) PortalRingSegments.onAnchorRemoved(level, pos, state);
        BlockPos corePos = findCoreNear(level, pos, 4, 5);
        if (corePos != null && level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core && core.isActiveOrOpening()) {
            PortalConnectionManager.forceCloseOneSide(level, corePos);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
    *///?} else {
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            if (state.getValue(PIECE) != 0) PortalRingSegments.onAnchorRemoved((ServerLevel) level, pos, state);
            BlockPos corePos = findCoreNear((ServerLevel) level, pos, 4, 5);
            if (corePos != null && level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core && core.isActiveOrOpening()) {
                PortalConnectionManager.forceCloseOneSide((ServerLevel) level, corePos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    //?}

    private static BlockPos findCoreNear(ServerLevel level, BlockPos center, int rXZ, int rY) {
        for (int dy = -rY; dy <= rY; dy++) {
            for (int dx = -rXZ; dx <= rXZ; dx++) {
                for (int dz = -rXZ; dz <= rXZ; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(ModBlocks.PORTAL_CORE)) return p;
                }
            }
        }
        return null;
    }
}
