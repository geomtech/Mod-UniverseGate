package fr.geomtech.universegate;

//? if >=1.21.11 {
/*import net.minecraft.world.entity.InsideBlockEffectApplier;
*///?}
//? if >=1.21.11 {
/*import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PortalFieldBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty UNSTABLE = BooleanProperty.create("unstable");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public PortalFieldBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(UNSTABLE, false)
                .setValue(WATERLOGGED, false));
    }

    private static final VoxelShape SHAPE_X = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    private static final VoxelShape SHAPE_Z = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalFieldBlockEntity(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        PortalFireGuard.clearFireAbove(level, pos);
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
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean intersects) {
    *///?} else {
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    //?}
        if (level.isClientSide()) return;

        PortalTeleportHandler.tryTeleport(entity, pos);
    }

    //? if >=1.21.11 {
    /*@Override
    public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
    *///?} else {
    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
    //?}
        if (!level.isClientSide() && level instanceof ServerLevel sl) {
            BlockPos corePos = findCoreNear(sl, pos, 4, 5);
            if (corePos != null && sl.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core && core.isActive()) {
                PortalConnectionManager.forceCloseOneSide(sl, corePos);
            }
        }
        super.wasExploded(level, pos, explosion);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // Survie: indestructible. Créatif: destructible.
        return player.getAbilities().instabuild ? 1.0F : 0.0F;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()
                && player.getAbilities().instabuild
                && level instanceof ServerLevel sl) {
            BlockPos corePos = findCoreNear(sl, pos, 4, 5);
            if (corePos != null && sl.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core && core.isActive()) {
                PortalConnectionManager.forceCloseOneSide(sl, corePos);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    //? if >=1.21.11 {
    /*@Override
    protected boolean propagatesSkylightDown(BlockState state) {
    *///?} else {
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
    //?}
        return true;
    }

    //? if >=1.21.11 {
    /*@Override
    protected int getLightBlock(BlockState state) {
    *///?} else {
    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
    //?}
        return 0;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    //? if >=1.21.11 {
    /*@Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess, BlockPos currentPos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
    *///?} else {
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
    //?}
        if (state.getValue(WATERLOGGED)) {
            //? if >=1.21.11 {
            /*tickAccess.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            *///?} else {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            //?}
        }
        //? if >=1.21.11 {
        /*return super.updateShape(state, level, tickAccess, currentPos, direction, neighborPos, neighborState, random);
        *///?} else {
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
        //?}
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // La membrane est dessinée par PortalMembraneLayer (anneau 3D du Core), pas par un modèle de bloc.
        return RenderShape.INVISIBLE;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.isClientSide() && fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager.isRemoteLevel(level)) {
            return;
        }
        Direction.Axis axis = state.getValue(AXIS);
        boolean unstable = state.getValue(UNSTABLE);
        if (!unstable) return;

        int count = random.nextFloat() < 0.70F ? 2 : 1;
        if (count == 0) return;
        // Portail rond : pas d'étincelles dans les coins du champ, cachés par l'anneau.
        PortalFieldBlinkRenderer.PortalCoords coords = PortalFieldBlinkRenderer.resolvePortalCoords(level, pos, axis);

        for (int i = 0; i < count; i++) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + 0.5;

            if (axis == Direction.Axis.X) {
                x += (random.nextDouble() - 0.5) * 0.92;
            } else {
                z += (random.nextDouble() - 0.5) * 0.92;
            }
            if (coords != null) {
                double u = coords.horizontalIndex() + (axis == Direction.Axis.X ? x - pos.getX() : z - pos.getZ())
                        - PortalFrameDetector.INNER_WIDTH * 0.5D;
                double v = coords.verticalIndex() + (y - pos.getY()) - PortalFrameDetector.INNER_HEIGHT * 0.5D;
                double r = PortalCinematics.MEMBRANE_RADIUS - 0.1D;
                if (u * u + v * v > r * r) continue;
            }

            double centerX = pos.getX() + 0.5;
            double centerZ = pos.getZ() + 0.5;
            double pullX = (centerX - x) * (unstable ? 0.10 : 0.06);
            double pullZ = (centerZ - z) * (unstable ? 0.10 : 0.06);

            double vx = pullX + (random.nextDouble() - 0.5) * (unstable ? 0.018 : 0.010);
            double vy = (random.nextDouble() - 0.5) * (unstable ? 0.035 : 0.018);
            double vz = pullZ + (random.nextDouble() - 0.5) * (unstable ? 0.018 : 0.010);

            double rodScale = unstable ? 0.34 : 0.20;
            double rodY = unstable ? 0.24 : 0.14;
            double normalOffset = unstable ? 0.46 : 0.43;
            double normalSpread = unstable ? 0.035 : 0.020;
            double normalSpeed = unstable ? 0.020 : 0.012;

            for (int side = -1; side <= 1; side += 2) {
                double px = x;
                double py = y;
                double pz = z;
                double pvx = vx * rodScale;
                double pvy = vy * rodY;
                double pvz = vz * rodScale;

                if (axis == Direction.Axis.X) {
                    pz += side * normalOffset + (random.nextDouble() - 0.5) * normalSpread;
                    pvz += side * normalSpeed;
                } else {
                    px += side * normalOffset + (random.nextDouble() - 0.5) * normalSpread;
                    pvx += side * normalSpeed;
                }

                level.addParticle(ModParticles.SPARK, px, py, pz, pvx, pvy, pvz);
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, UNSTABLE, WATERLOGGED);
    }

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
