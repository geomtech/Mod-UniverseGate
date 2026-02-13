package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LightBeamEmitterBlockEntity extends BlockEntity {

    private static final int BEAM_HEIGHT = 8;

    public LightBeamEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIGHT_BEAM_EMITTER, pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (sl.getGameTime() % 1L != 0L) return;

        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 1.0;
        double z = worldPosition.getZ() + 0.5;
        long t = sl.getGameTime();

        int heightReached = 0;

        for (int i = 0; i < BEAM_HEIGHT; i++) {
            BlockPos p = worldPosition.above(i + 1);
            if (!sl.getBlockState(p).isAir()) break;

            double py = y + i + 0.3;

            // Dense luminous core
            sl.sendParticles(ParticleTypes.END_ROD, x, py, z, 3, 0.02, 0.16, 0.02, 0.0);

            // Soft volume around the core (still END_ROD-based)
            sl.sendParticles(ParticleTypes.END_ROD, x, py, z, 2, 0.18, 0.10, 0.18, 0.0);

            // Two rotating strands to make the column feel alive
            double angle = t * 0.22 + i * 0.65;
            spawnStrandParticle(sl, x, py, z, angle, 0.22);
            spawnStrandParticle(sl, x, py, z, angle + Math.PI, 0.22);

            // Occasional sparkle pulses
            if (sl.random.nextFloat() < 0.22F) {
                sl.sendParticles(ParticleTypes.END_ROD, x, py, z, 2, 0.10, 0.05, 0.10, 0.0);
            }

            heightReached = i + 1;
        }

        // Bright cap at the top of the beam
        if (heightReached > 0) {
            double topY = y + heightReached - 0.1;
            sl.sendParticles(ParticleTypes.END_ROD, x, topY, z, 8, 0.18, 0.05, 0.18, 0.0);
            sl.sendParticles(ParticleTypes.END_ROD, x, topY, z, 3, 0.12, 0.04, 0.12, 0.0);
        }
    }

    private static void spawnStrandParticle(ServerLevel level, double x, double y, double z, double angle, double radius) {
        double px = x + Math.cos(angle) * radius;
        double pz = z + Math.sin(angle) * radius;
        level.sendParticles(ParticleTypes.END_ROD, px, y, pz, 1, 0.0, 0.02, 0.0, 0.0);
    }
}
