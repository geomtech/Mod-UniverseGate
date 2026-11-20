package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Arcs électriques du portail 3D (côté client) : tant que le portail s'ouvre ou est ouvert, de brefs arcs
 * blancs jaillissent des cristaux de l'anneau vers le sol et les blocs voisins. Plus fréquents s'il est instable.
 *
 * <p>Positions en blocs depuis le coin du Core (x, y, z du monde), dessinées par {@link PortalArcLayer}.</p>
 */
public final class PortalGateArcs {

    static final int POINTS = 10;
    private static final float[] CRYSTAL_ANGLES = {90.0F, 210.0F, 330.0F};
    private static final float CRYSTAL_RADIUS = 44.0F; // px, bord extérieur des cristaux
    private static final int MAX_ARCS = 3;
    private static final double MIN_REACH = 0.8D;
    private static final double MAX_REACH = 4.5D;

    public static final class Arc {
        final int crystal;
        final Vec3 target;
        final int life;
        int age;
        final float[] jitter = new float[POINTS * 3];

        Arc(int crystal, Vec3 target, int life) {
            this.crystal = crystal;
            this.target = target;
            this.life = life;
        }
    }

    private final List<Arc> arcs = new ArrayList<>();
    private final List<Vec3> candidates = new ArrayList<>();
    private long nextScan;
    private Direction scannedRight;

    List<Arc> active() {
        return arcs;
    }

    void tick(Level level, BlockPos core, Direction right, float ringAngle,
              boolean opening, boolean active, boolean unstable) {
        if (right == null || !(opening || active)) {
            arcs.clear();
            return;
        }
        RandomSource random = level.random;
        arcs.removeIf(arc -> ++arc.age >= arc.life);
        for (Arc arc : arcs) {
            if (arc.age % 2 == 0) jitter(arc, random);
        }

        long now = level.getGameTime();
        if (now >= nextScan || scannedRight != right) {
            scan(level, core, right);
            nextScan = now + 40L;
            scannedRight = right;
        }

        float chance = unstable ? 0.35F : active ? 0.10F : 0.18F;
        if (arcs.size() < MAX_ARCS && random.nextFloat() < chance) {
            spawn(level, core, right, ringAngle, random);
        }
    }

    private void spawn(Level level, BlockPos core, Direction right, float ringAngle, RandomSource random) {
        int crystal = random.nextInt(CRYSTAL_ANGLES.length);
        Vec3 start = crystalPoint(right, crystal, ringAngle);
        List<Vec3> reachable = new ArrayList<>();
        for (Vec3 c : candidates) {
            double d = c.distanceTo(start);
            if (d >= MIN_REACH && d <= MAX_REACH) reachable.add(c);
        }
        if (reachable.isEmpty()) return;
        Vec3 target = reachable.get(random.nextInt(reachable.size()));
        Arc arc = new Arc(crystal, target, 3 + random.nextInt(4));
        jitter(arc, random);
        arcs.add(arc);

        // Impact : quelques étincelles du mod là où l'arc touche.
        for (int i = 0; i < 3; i++) {
            level.addParticle(ModParticles.SPARK,
                    core.getX() + target.x, core.getY() + target.y, core.getZ() + target.z,
                    (random.nextDouble() - 0.5D) * 0.08D, random.nextDouble() * 0.06D, (random.nextDouble() - 0.5D) * 0.08D);
        }
    }

    private static void jitter(Arc arc, RandomSource random) {
        for (int i = 0; i < arc.jitter.length; i++) {
            arc.jitter[i] = random.nextFloat() * 2.0F - 1.0F;
        }
    }

    /** Point de départ d'un arc : bord du cristal, qui tourne avec l'anneau. */
    static Vec3 crystalPoint(Direction right, int crystal, float ringAngle) {
        float a = (CRYSTAL_ANGLES[crystal] + ringAngle) * Mth.DEG_TO_RAD;
        double h = (PortalGateRingLayer.CENTER_H + Mth.cos(a) * CRYSTAL_RADIUS) / 16.0D;
        double v = (PortalGateRingLayer.CENTER_V + Mth.sin(a) * CRYSTAL_RADIUS) / 16.0D;
        return right.getAxis() == Direction.Axis.X ? new Vec3(h, v, 0.5D) : new Vec3(0.5D, v, h);
    }

    /** Blocs pleins voisins (sol compris) avec une face à l'air libre : les cibles possibles des arcs. */
    private void scan(Level level, BlockPos core, Direction right) {
        candidates.clear();
        Direction depth = right.getClockWise();
        Vec3 center = right.getAxis() == Direction.Axis.X
                ? new Vec3(PortalGateRingLayer.CENTER_H / 16.0D, PortalGateRingLayer.CENTER_V / 16.0D, 0.5D)
                : new Vec3(0.5D, PortalGateRingLayer.CENTER_V / 16.0D, PortalGateRingLayer.CENTER_H / 16.0D);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dh = -6; dh <= 7; dh++) {
            for (int dv = -2; dv <= 8; dv++) {
                for (int dd = -4; dd <= 4; dd++) {
                    pos.set(core.getX() + right.getStepX() * dh + depth.getStepX() * dd,
                            core.getY() + dv,
                            core.getZ() + right.getStepZ() * dh + depth.getStepZ() * dd);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || isPortalBlock(state)) continue;
                    if (!state.isCollisionShapeFullBlock(level, pos)) continue;
                    if (!hasOpenFace(level, pos)) continue;
                    Vec3 block = new Vec3(pos.getX() - core.getX() + 0.5D, pos.getY() - core.getY() + 0.5D,
                            pos.getZ() - core.getZ() + 0.5D);
                    // Point d'impact : sur la surface du bloc, côté anneau.
                    Vec3 toRing = center.subtract(block);
                    double ax = Math.abs(toRing.x), ay = Math.abs(toRing.y), az = Math.abs(toRing.z);
                    Vec3 face = ax >= ay && ax >= az ? new Vec3(Math.signum(toRing.x) * 0.5D, 0, 0)
                            : ay >= az ? new Vec3(0, Math.signum(toRing.y) * 0.5D, 0)
                            : new Vec3(0, 0, Math.signum(toRing.z) * 0.5D);
                    candidates.add(block.add(face));
                }
            }
        }
    }

    private static boolean isPortalBlock(BlockState state) {
        return state.is(ModBlocks.PORTAL_FRAME) || state.is(ModBlocks.PORTAL_CORE) || state.is(ModBlocks.PORTAL_FIELD);
    }

    private static boolean hasOpenFace(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            BlockState ns = level.getBlockState(n);
            if (ns.isAir()) return true;
        }
        return false;
    }
}
