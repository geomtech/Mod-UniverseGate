package fr.geomtech.universegate;

import fr.geomtech.universegate.net.PortalFieldCollapsePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mise en scène des portails, entièrement côté client (aucun paquet par tick, aucun coût serveur).
 *
 * <ul>
 *   <li><b>Accrochage de la Faille</b> (ouverture) : l'énergie part du Portal Core et remonte le cadre
 *   bloc par bloc, puis des fissures violettes se dessinent dans le vide, une par coordonnée accrochée.
 *   Le nombre de fissures dépend du trajet (même dimension, autre dimension, Faille).</li>
 *   <li><b>Membrane d'énergie noire</b> (portail ouvert) : des veines lumineuses tournent lentement
 *   autour d'un cœur sombre ; elles se tordent et projettent des étincelles quand quelqu'un traverse.
 *   Un portail instable grésille et se déchire par endroits.</li>
 *   <li><b>Effondrement</b> (fermeture) : 10 s avant la fin, les veines faiblissent et l'énergie
 *   redescend le cadre vers le Core ; à la fermeture, la membrane implose et se brise en éclats.</li>
 * </ul>
 *
 * <p>Tout est piloté par l'état synchronisé du Portal Core et par le temps de jeu, avec des tirages
 * déterministes pour la forme des fissures : chaque joueur voit la même chose au même moment.</p>
 */
public final class PortalCinematics {

    // --- Accrochage ---
    private static final long CHARGE_END_TICKS = 60L;
    private static final long LOCK_PHASE_END_TICKS = 140L;
    private static final int FISSURE_SEGMENTS = 7;
    private static final double FISSURE_POINT_SPACING = 0.18D;

    // --- Membrane ---
    private static final double HALF_U = PortalFrameDetector.INNER_WIDTH * 0.5D - 0.1D;
    private static final double HALF_V = PortalFrameDetector.INNER_HEIGHT * 0.5D - 0.1D;
    private static final double CENTER_V = 1.0D + PortalFrameDetector.INNER_HEIGHT * 0.5D;

    // --- Effondrement ---
    static final long COLLAPSE_WARNING_TICKS = 20L * 10L;

    private PortalCinematics() {}

    /** Géométrie d'un portail : repère (u horizontal dans le plan, v vertical depuis le Core). */
    record Frame(BlockPos core, Direction right, PortalFrameDetector.FrameMatch match, List<BlockPos> path) {
        double x(double u) { return core.getX() + 0.5D + right.getStepX() * u; }
        double y(double v) { return core.getY() + v; }
        double z(double u) { return core.getZ() + 0.5D + right.getStepZ() * u; }

        AABB interiorBox() {
            double minU = -PortalFrameDetector.INNER_WIDTH * 0.5D;
            double maxU = PortalFrameDetector.INNER_WIDTH * 0.5D;
            double ax = x(minU), bx = x(maxU), az = z(minU), bz = z(maxU);
            return new AABB(Math.min(ax, bx) - 0.35D, y(1.0D), Math.min(az, bz) - 0.35D,
                    Math.max(ax, bx) + 0.35D, y(1.0D + PortalFrameDetector.INNER_HEIGHT), Math.max(az, bz) + 0.35D);
        }

        /** Point de la face intérieure d'un bloc du cadre, tourné vers le centre du portail. */
        double[] innerFace(BlockPos framePos) {
            int du = (framePos.getX() - core.getX()) * right.getStepX() + (framePos.getZ() - core.getZ()) * right.getStepZ();
            int dv = framePos.getY() - core.getY();
            int halfWidth = PortalFrameDetector.INNER_WIDTH / 2 + 1;
            double u = Math.abs(du) == halfWidth ? du - Math.signum(du) * 0.5D : du;
            double v = dv + 0.5D - (dv == 0 ? -0.5D : (dv > PortalFrameDetector.INNER_HEIGHT ? 0.5D : 0.0D));
            return new double[]{x(u), y(v), z(u)};
        }
    }

    /** État client propre à un Portal Core (stocké sur l'entité de bloc côté client). */
    static final class State {
        Frame frame;
        long frameCheckedAt = Long.MIN_VALUE;
        int chargeStepsShown = 0;
        int fissuresShown = 0;
        long chargeForOpeningStart = Long.MIN_VALUE;
        long warnedForUntil = Long.MIN_VALUE;
        final Set<Integer> entitiesInside = new HashSet<>();
    }

    // =====================================================================
    // Entrée depuis PortalCoreBlockEntity#clientTick
    // =====================================================================

    static void clientTick(Level level, PortalCoreBlockEntity core, State state) {
        long now = level.getGameTime();
        if (state.frame == null || now - state.frameCheckedAt >= 20L || now < state.frameCheckedAt) {
            state.frameCheckedAt = now;
            BlockPos corePos = core.getBlockPos();
            state.frame = PortalFrameDetector.find(level, corePos)
                    .map(m -> new Frame(corePos, m.right(), m, PortalFrameHelper.collectFrameRotationPath(m, corePos)))
                    .orElse(null);
        }
        Frame frame = state.frame;
        if (frame == null) return;

        if (core.isOpening()) {
            tickOpening(level, core, state, frame, now);
        } else if (core.isActive()) {
            tickActive(level, core, state, frame, now);
        } else {
            state.entitiesInside.clear();
        }
    }

    // =====================================================================
    // Accrochage de la Faille
    // =====================================================================

    private static void tickOpening(Level level, PortalCoreBlockEntity core, State state, Frame frame, long now) {
        long start = core.getOpeningStartedGameTime();
        if (start <= 0L) return;
        if (state.chargeForOpeningStart != start) {
            state.chargeForOpeningStart = start;
            state.chargeStepsShown = 0;
            state.fissuresShown = 0;
        }
        long elapsed = now - start;
        if (elapsed < 0L) return;

        RandomSource random = level.random;
        List<BlockPos> path = frame.path();
        int steps = (path.size() + 1) / 2;

        // 1) L'énergie remonte le cadre depuis le Core, des deux côtés à la fois.
        int targetStep = (int) Math.min(steps, (elapsed * steps) / CHARGE_END_TICKS + 1);
        while (state.chargeStepsShown < targetStep) {
            int k = state.chargeStepsShown++;
            boolean lateJoin = elapsed > CHARGE_END_TICKS + 5L;
            for (BlockPos p : chargePair(path, k)) {
                double[] f = frame.innerFace(p);
                if (!lateJoin) {
                    for (int i = 0; i < 6; i++) {
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                                f[0] + (random.nextDouble() - 0.5D) * 0.5D, f[1] + (random.nextDouble() - 0.5D) * 0.5D,
                                f[2] + (random.nextDouble() - 0.5D) * 0.5D,
                                (random.nextDouble() - 0.5D) * 0.08D, (random.nextDouble() - 0.5D) * 0.08D,
                                (random.nextDouble() - 0.5D) * 0.08D);
                    }
                }
            }
            if (!lateJoin) {
                BlockPos sound = chargePair(path, k).get(0);
                level.playLocalSound(sound.getX() + 0.5D, sound.getY() + 0.5D, sound.getZ() + 0.5D,
                        SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.55F, 0.7F + k * 0.09F, false);
            }
        }
        // Les blocs déjà chargés crépitent doucement.
        for (int k = 0; k < state.chargeStepsShown; k++) {
            for (BlockPos p : chargePair(path, k)) {
                if (random.nextFloat() >= 0.12F) continue;
                double[] f = frame.innerFace(p);
                level.addParticle(ModParticles.SPARK, f[0], f[1], f[2],
                        (random.nextDouble() - 0.5D) * 0.01D, 0.004D, (random.nextDouble() - 0.5D) * 0.01D);
            }
        }

        // 2) Les fissures : une par coordonnée accrochée.
        int fissures = Math.max(1, core.getClientLinkSteps());
        long lockSpan = LOCK_PHASE_END_TICKS - CHARGE_END_TICKS;
        int targetFissures = elapsed < CHARGE_END_TICKS ? 0
                : (int) Math.min(fissures, ((elapsed - CHARGE_END_TICKS) * fissures) / lockSpan + 1);
        while (state.fissuresShown < targetFissures) {
            int i = state.fissuresShown++;
            if (elapsed - (CHARGE_END_TICKS + (i * lockSpan) / fissures) > 10L) continue; // arrivé en retard
            List<double[]> pts = fissure(frame, start, i);
            for (double[] p : pts) {
                level.addParticle(ParticleTypes.REVERSE_PORTAL, p[0], p[1], p[2], 0.0D, 0.0D, 0.0D);
            }
            double[] mid = pts.get(pts.size() / 2);
            for (int n = 0; n < 10; n++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, mid[0], mid[1], mid[2],
                        (random.nextDouble() - 0.5D) * 0.25D, (random.nextDouble() - 0.5D) * 0.25D,
                        (random.nextDouble() - 0.5D) * 0.25D);
            }
            level.playLocalSound(mid[0], mid[1], mid[2], SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                    0.9F, 1.1F + 0.6F * i / Math.max(1, fissures - 1), false);
        }
        // Les fissures déjà accrochées restent visibles jusqu'à l'ouverture.
        if (now % 4L == 0L) {
            for (int i = 0; i < state.fissuresShown; i++) {
                List<double[]> pts = fissure(frame, start, i);
                for (int n = 0; n < pts.size(); n += 2) {
                    double[] p = pts.get(n);
                    level.addParticle(ParticleTypes.REVERSE_PORTAL, p[0], p[1], p[2], 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    private static List<BlockPos> chargePair(List<BlockPos> path, int k) {
        int a = k;
        int b = path.size() - 1 - k;
        if (a >= b) return List.of(path.get(Math.min(a, path.size() - 1)));
        return List.of(path.get(a), path.get(b));
    }

    /** Fissure n° {@code index}, identique pour tous les joueurs (tirage déterministe). */
    private static List<double[]> fissure(Frame frame, long openingStart, int index) {
        RandomSource r = RandomSource.create(frame.core().asLong() * 31L + openingStart * 17L + index * 7919L);
        double u0 = -HALF_U, v0 = CENTER_V - HALF_V + r.nextDouble() * 2.0D * HALF_V;
        double u1 = HALF_U, v1 = CENTER_V - HALF_V + r.nextDouble() * 2.0D * HALF_V;
        if (r.nextBoolean()) { // fissure plutôt verticale
            u0 = -HALF_U + r.nextDouble() * 2.0D * HALF_U; v0 = CENTER_V - HALF_V;
            u1 = -HALF_U + r.nextDouble() * 2.0D * HALF_U; v1 = CENTER_V + HALF_V;
        }
        double[] us = new double[FISSURE_SEGMENTS + 1];
        double[] vs = new double[FISSURE_SEGMENTS + 1];
        for (int s = 0; s <= FISSURE_SEGMENTS; s++) {
            double t = (double) s / FISSURE_SEGMENTS;
            double jitter = (s == 0 || s == FISSURE_SEGMENTS) ? 0.0D : (r.nextDouble() - 0.5D) * 0.55D;
            us[s] = Mth.clamp(Mth.lerp(t, u0, u1) + jitter, -HALF_U, HALF_U);
            vs[s] = Mth.clamp(Mth.lerp(t, v0, v1) + jitter, CENTER_V - HALF_V, CENTER_V + HALF_V);
        }
        List<double[]> pts = new java.util.ArrayList<>();
        for (int s = 0; s < FISSURE_SEGMENTS; s++) {
            double du = us[s + 1] - us[s], dv = vs[s + 1] - vs[s];
            int n = Math.max(1, (int) (Math.sqrt(du * du + dv * dv) / FISSURE_POINT_SPACING));
            for (int k = 0; k < n; k++) {
                double t = (double) k / n;
                double u = us[s] + du * t, v = vs[s] + dv * t;
                pts.add(new double[]{frame.x(u), frame.y(v), frame.z(u)});
            }
        }
        return pts;
    }

    // =====================================================================
    // Membrane d'énergie noire + effondrement
    // =====================================================================

    private static void tickActive(Level level, PortalCoreBlockEntity core, State state, Frame frame, long now) {
        RandomSource random = level.random;
        boolean unstable = core.isClientAmbientUnstable();
        long until = core.getClientActiveUntil();
        long remaining = until > 0L ? until - now : Long.MAX_VALUE;
        double fade = remaining >= COLLAPSE_WARNING_TICKS ? 1.0D
                : Mth.clamp((double) remaining / COLLAPSE_WARNING_TICKS, 0.2D, 1.0D);

        // Veines lumineuses en spirale autour d'un cœur sombre.
        int arms = unstable ? 4 : 3;
        double spin = now * (unstable ? 0.11D : 0.045D);
        for (int arm = 0; arm < arms; arm++) {
            if (random.nextDouble() > fade) continue;
            double r = Math.sqrt(random.nextDouble());
            double angle = spin + arm * (Mth.TWO_PI / arms) + r * 2.4D;
            if (unstable) angle += (random.nextDouble() - 0.5D) * 0.6D;
            double u = Math.cos(angle) * r * HALF_U;
            double v = CENTER_V + Math.sin(angle) * r * HALF_V;
            double tu = -Math.sin(angle) * 0.012D, tv = Math.cos(angle) * 0.012D;
            level.addParticle(ModParticles.SPARK, frame.x(u), frame.y(v), frame.z(u),
                    frame.right().getStepX() * tu, tv, frame.right().getStepZ() * tu);
        }
        if (now % 3L == 0L && random.nextDouble() < fade) {
            double r = random.nextDouble() * 0.45D, a = random.nextDouble() * Mth.TWO_PI;
            double u = Math.cos(a) * r * HALF_U, v = CENTER_V + Math.sin(a) * r * HALF_V;
            level.addParticle(ParticleTypes.SQUID_INK, frame.x(u), frame.y(v), frame.z(u), 0.0D, 0.0D, 0.0D);
        }
        // Portail instable : la membrane grésille et se déchire par endroits.
        if (unstable && random.nextFloat() < 0.2F) {
            double a = random.nextDouble() * Mth.TWO_PI, r = 0.6D + random.nextDouble() * 0.4D;
            double u = Math.cos(a) * r * HALF_U, v = CENTER_V + Math.sin(a) * r * HALF_V;
            for (int n = 0; n < 4; n++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, frame.x(u), frame.y(v), frame.z(u),
                        (random.nextDouble() - 0.5D) * 0.2D, (random.nextDouble() - 0.5D) * 0.2D,
                        (random.nextDouble() - 0.5D) * 0.2D);
            }
        }

        tickCrossings(level, state, frame);

        // Effondrement : avertissement 10 s avant la fin, puis l'énergie redescend le cadre.
        if (until > 0L && remaining <= COLLAPSE_WARNING_TICKS && remaining > 0L) {
            if (state.warnedForUntil != until) {
                state.warnedForUntil = until;
                if (remaining > COLLAPSE_WARNING_TICKS - 10L) {
                    BlockPos c = frame.core();
                    level.playLocalSound(c.getX() + 0.5D, c.getY() + CENTER_V, c.getZ() + 0.5D,
                            SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.9F, 0.6F, false);
                }
            }
            List<BlockPos> path = frame.path();
            int steps = (path.size() + 1) / 2;
            if (now % 3L == 0L) {
                int k = steps - 1 - (int) (((COLLAPSE_WARNING_TICKS - remaining) / 3L) % steps);
                for (BlockPos p : chargePair(path, k)) {
                    double[] f = frame.innerFace(p);
                    level.addParticle(ModParticles.SPARK, f[0], f[1], f[2], 0.0D, -0.05D, 0.0D);
                }
            }
        }
    }

    /** Une onde d'étincelles part du point d'entrée de chaque entité qui traverse la membrane. */
    private static void tickCrossings(Level level, State state, Frame frame) {
        Set<Integer> now = new HashSet<>();
        for (Entity entity : level.getEntities((Entity) null, frame.interiorBox(), e -> !e.isSpectator())) {
            now.add(entity.getId());
            if (state.entitiesInside.contains(entity.getId())) continue;
            ripple(level, frame, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ());
        }
        state.entitiesInside.clear();
        state.entitiesInside.addAll(now);
    }

    private static void ripple(Level level, Frame frame, double x, double y, double z) {
        RandomSource random = level.random;
        // Projeté sur le plan du portail.
        double cx = frame.x(0.0D), cz = frame.z(0.0D);
        if (frame.right().getStepX() != 0) z = cz; else x = cx;
        for (int i = 0; i < 18; i++) {
            double a = i * (Mth.TWO_PI / 18.0D);
            double du = Math.cos(a) * 0.14D, dv = Math.sin(a) * 0.14D;
            level.addParticle(ModParticles.SPARK, x, y, z,
                    frame.right().getStepX() * du, dv, frame.right().getStepZ() * du);
        }
        for (int i = 0; i < 6; i++) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL,
                    x + (random.nextDouble() - 0.5D) * 0.4D, y + (random.nextDouble() - 0.5D) * 0.6D,
                    z + (random.nextDouble() - 0.5D) * 0.4D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Fermeture : implosion vers le centre, éclats de cristal et petite bouffée noire. */
    static void collapse(Level level, PortalFieldCollapsePayload payload) {
        if (payload.cells().isEmpty()) return;
        RandomSource random = level.random;
        double cx = 0, cy = 0, cz = 0;
        for (BlockPos p : payload.cells()) { cx += p.getX() + 0.5D; cy += p.getY() + 0.5D; cz += p.getZ() + 0.5D; }
        int n = payload.cells().size();
        cx /= n; cy /= n; cz /= n;
        boolean xAxis = payload.xAxis();

        for (int i = 0; i < 28; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double du = Math.cos(a) * HALF_U, dv = Math.sin(a) * HALF_V;
            double x = cx + (xAxis ? du : 0.0D), z = cz + (xAxis ? 0.0D : du), y = cy + dv;
            level.addParticle(ModParticles.SPARK, x, y, z, (cx - x) * 0.09D, (cy - y) * 0.09D, (cz - z) * 0.09D);
        }
        BlockParticleOption shard = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_CLUSTER.defaultBlockState());
        for (int i = 0; i < 14; i++) {
            level.addParticle(shard, cx + (random.nextDouble() - 0.5D) * 1.6D, cy + (random.nextDouble() - 0.5D) * 2.4D,
                    cz + (random.nextDouble() - 0.5D) * 1.6D,
                    (random.nextDouble() - 0.5D) * 0.2D, random.nextDouble() * 0.15D, (random.nextDouble() - 0.5D) * 0.2D);
        }
        for (int i = 0; i < 8; i++) {
            level.addParticle(ParticleTypes.SQUID_INK, cx, cy, cz,
                    (random.nextDouble() - 0.5D) * 0.12D, (random.nextDouble() - 0.5D) * 0.12D,
                    (random.nextDouble() - 0.5D) * 0.12D);
        }
        level.playLocalSound(cx, cy, cz, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F, false);
    }
}
