package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PortalFieldBlock extends Block implements EntityBlock {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty UNSTABLE = BooleanProperty.create("unstable");

    public PortalFieldBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(UNSTABLE, false));
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

    @Override
    protected void neighborChanged(BlockState state,
                                   Level level,
                                   BlockPos pos,
                                   Block neighborBlock,
                                   BlockPos neighborPos,
                                   boolean movedByPiston) {
        PortalFireGuard.clearFireAbove(level, pos);
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;

        PortalTeleportHandler.tryTeleport(entity, pos);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide && level instanceof ServerLevel sl) {
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
        if (!level.isClientSide
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

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(UNSTABLE)) return;

        Direction.Axis axis = state.getValue(AXIS);
        int count = 2 + random.nextInt(4);
        for (int i = 0; i < count; i++) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + 0.5;

            if (axis == Direction.Axis.X) {
                x += (random.nextDouble() - 0.5) * 0.9;
                z += (random.nextDouble() - 0.5) * 0.1;
            } else {
                x += (random.nextDouble() - 0.5) * 0.1;
                z += (random.nextDouble() - 0.5) * 0.9;
            }

            double vx = (random.nextDouble() - 0.5) * 0.03;
            double vy = (random.nextDouble() - 0.5) * 0.02;
            double vz = (random.nextDouble() - 0.5) * 0.03;
            level.addParticle(ParticleTypes.END_ROD, x, y, z, vx, vy, vz);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, UNSTABLE);
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
