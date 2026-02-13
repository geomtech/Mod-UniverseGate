package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

public class PortalFrameBlock extends Block implements EntityBlock {

    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    public static final BooleanProperty UNSTABLE = BooleanProperty.create("unstable");
    public static final BooleanProperty BLINK_ON = BooleanProperty.create("blink_on");

    public PortalFrameBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ACTIVE, false)
                .setValue(UNSTABLE, false)
                .setValue(BLINK_ON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, UNSTABLE, BLINK_ON);
    }

    public static int lightLevel(BlockState state) {
        if (!state.getValue(ACTIVE)) return 0;
        if (state.getValue(UNSTABLE) && !state.getValue(BLINK_ON)) return 0;
        return 12;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalFrameBlockEntity(pos, state);
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
        boolean unstable = state.getValue(UNSTABLE);
        if (unstable && !state.getValue(BLINK_ON)) return;

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
                level.addParticle(ParticleTypes.END_ROD, x, y, z, vx, vy, vz);
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
        level.addParticle(ParticleTypes.END_ROD, x, y, z, vx, vy, vz);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockPos corePos = findCoreNear((ServerLevel) level, pos, 4, 5);
            if (corePos != null && level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core && core.isActive()) {
                PortalConnectionManager.forceCloseOneSide((ServerLevel) level, corePos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
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
