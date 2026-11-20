package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LightBeamEmitterBlockEntity extends BlockEntity {

    private static final int BEAM_HEIGHT = 8;

    public LightBeamEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIGHT_BEAM_EMITTER, pos, state);
    }

    /**
     * Purely visual beam. It used to be emitted from the server with ~40 particle packets per tick
     * per emitter (sent to every nearby player); it is now spawned locally on each client.
     */
    public void clientTick() {
        Level level = this.level;
        if (level == null || !level.isClientSide()) return;

        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 1.0;
        double z = worldPosition.getZ() + 0.5;
        long t = level.getGameTime();
        RandomSource random = level.random;

        int heightReached = 0;

        for (int i = 0; i < BEAM_HEIGHT; i++) {
            BlockPos p = worldPosition.above(i + 1);
            if (!level.getBlockState(p).isAir()) break;

            double py = y + i + 0.3;

            // Dense luminous core
            burst(level, random, x, py, z, 3, 0.02, 0.16, 0.02);

            // Soft volume around the core
            burst(level, random, x, py, z, 2, 0.18, 0.10, 0.18);

            // Two rotating strands to make the column feel alive
            double angle = t * 0.22 + i * 0.65;
            spawnStrandParticle(level, random, x, py, z, angle, 0.22);
            spawnStrandParticle(level, random, x, py, z, angle + Math.PI, 0.22);

            // Occasional sparkle pulses
            if (random.nextFloat() < 0.22F) {
                burst(level, random, x, py, z, 2, 0.10, 0.05, 0.10);
            }

            heightReached = i + 1;
        }

        // Bright cap at the top of the beam
        if (heightReached > 0) {
            double topY = y + heightReached - 0.1;
            burst(level, random, x, topY, z, 8, 0.18, 0.05, 0.18);
            burst(level, random, x, topY, z, 3, 0.12, 0.04, 0.12);
        }
    }

    /** Same spread as {@code ServerLevel#sendParticles(type, x, y, z, count, dx, dy, dz, 0)}. */
    private static void burst(Level level, RandomSource random, double x, double y, double z,
                              int count, double dx, double dy, double dz) {
        for (int n = 0; n < count; n++) {
            level.addParticle(ModParticles.SPARK,
                    x + random.nextGaussian() * dx,
                    y + random.nextGaussian() * dy,
                    z + random.nextGaussian() * dz,
                    0.0, 0.0, 0.0);
        }
    }

    private static void spawnStrandParticle(Level level, RandomSource random, double x, double y, double z,
                                            double angle, double radius) {
        double px = x + Math.cos(angle) * radius;
        double pz = z + Math.sin(angle) * radius;
        burst(level, random, px, y, pz, 1, 0.0, 0.02, 0.0);
    }
}
