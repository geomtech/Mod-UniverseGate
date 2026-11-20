package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Particules de la séquence d'ouverture d'un portail, générées côté client.
 *
 * <p>Le serveur ne fait que synchroniser l'état d'ouverture du Portal Core (début et fin, en game time).
 * Chaque client recalcule ici exactement ce que le serveur envoyait auparavant via
 * {@code ServerLevel#sendParticles} : même courbe de croissance, même passage au noir sur les
 * 2 dernières secondes, même dispersion gaussienne. On évite ainsi un paquet de particules
 * par case du portail et par tick.</p>
 */
public final class PortalOpeningParticles {

    static final long OPENING_BLACKOUT_TICKS = 20L * 2L;
    private static final double OPENING_PARTICLE_EXPONENT = 4.0D;
    private static final int OPENING_MAX_PARTICLES_PER_CELL = 7;

    private PortalOpeningParticles() {}

    static void clientTick(Level level, BlockPos corePos, PortalCoreBlockEntity core) {
        if (!level.isClientSide() || !core.isOpening()) return;

        Optional<PortalFrameDetector.FrameMatch> frame = PortalFrameDetector.find(level, corePos);
        if (frame.isEmpty()) return;

        long now = level.getGameTime();
        long openingStart = core.getOpeningStartedGameTime();
        long openingEnd = core.getOpeningCompleteGameTime();
        if (openingEnd <= openingStart) {
            openingStart = now;
            openingEnd = now + PortalConnectionManager.OPENING_DURATION_TICKS;
        }

        long duration = Math.max(1L, openingEnd - openingStart);
        double progress = Mth.clamp((double) (now - openingStart) / (double) duration, 0.0D, 1.0D);
        double growth = Math.expm1(progress * OPENING_PARTICLE_EXPONENT) / Math.expm1(OPENING_PARTICLE_EXPONENT);

        int baseCount = (int) Math.floor(growth * OPENING_MAX_PARTICLES_PER_CELL);
        long blackStart = openingEnd - OPENING_BLACKOUT_TICKS;
        double blackRatio = now <= blackStart
                ? 0.0D
                : Mth.clamp((double) (now - blackStart) / (double) Math.max(1L, OPENING_BLACKOUT_TICKS), 0.0D, 1.0D);

        PortalFrameDetector.FrameMatch match = frame.get();
        Direction.Axis axis = match.right() == Direction.EAST ? Direction.Axis.X : Direction.Axis.Z;
        RandomSource random = level.random;

        for (BlockPos p : match.interior()) {
            int count = baseCount;
            if (random.nextDouble() < growth) count++;
            if (count <= 0) continue;

            int blackCount = blackRatio <= 0.0D ? 0 : (int) Math.round(count * blackRatio);
            blackCount = Math.min(blackCount, count);
            int whiteCount = count - blackCount;

            if (whiteCount > 0) {
                emit(level, random, p, axis, ModParticles.SPARK, whiteCount, 0.002D);
            }
            if (blackCount > 0) {
                emit(level, random, p, axis, ParticleTypes.SQUID_INK, blackCount, 0.01D);
            }
        }
    }

    /** Même distribution que la réception d'un paquet {@code ClientboundLevelParticlesPacket} par le client vanilla. */
    private static void emit(Level level,
                             RandomSource random,
                             BlockPos pos,
                             Direction.Axis axis,
                             ParticleOptions particle,
                             int count,
                             double speed) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        double xSpread = axis == Direction.Axis.X ? 0.43D : 0.06D;
        double ySpread = 0.46D;
        double zSpread = axis == Direction.Axis.X ? 0.06D : 0.43D;

        for (int i = 0; i < count; i++) {
            double ox = random.nextGaussian() * xSpread;
            double oy = random.nextGaussian() * ySpread;
            double oz = random.nextGaussian() * zSpread;
            double vx = random.nextGaussian() * speed;
            double vy = random.nextGaussian() * speed;
            double vz = random.nextGaussian() * speed;
            level.addParticle(particle, x + ox, y + oy, z + oz, vx, vy, vz);
        }
    }
}
