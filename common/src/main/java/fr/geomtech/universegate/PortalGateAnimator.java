package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Animation de l'anneau 3D d'un portail construit, entièrement côté client.
 *
 * <p>L'anneau tourne sur lui-même : il accélère pendant l'ouverture, garde une rotation lente tant que le
 * portail est ouvert (plus nerveuse s'il est instable). À la fermeture, il repart en sens inverse jusqu'à sa
 * position d'origine, puis se refroidit : jets de vapeur et fumée par ses évents, avec un « pssshhh ».</p>
 *
 * <p>Les couches de rendu ({@link PortalGateLayer}) lisent l'angle ici, ce qui permet d'ajouter d'autres
 * effets sans toucher à l'animation.</p>
 */
public final class PortalGateAnimator {

    private static final float OPENING_SPEED = 7.5F;   // degrés par tick
    private static final float ACTIVE_SPEED = 1.2F;
    private static final float UNSTABLE_SPEED = 3.5F;
    private static final int VENT_TICKS = 60;
    private static final float REWIND_MAX_SPEED = 6.0F;

    private float angle;
    private float speed;
    private boolean wasRunning;
    private boolean rewinding;
    private int ventTicks;
    private Direction right;
    private final PortalGateArcs arcs = new PortalGateArcs();

    void tick(Level level, PortalCoreBlockEntity core) {
        right = resolveRight(level, core.getBlockPos());
        boolean opening = core.isOpening();
        boolean active = core.isActive();
        boolean unstable = active && core.isClientAmbientUnstable();

        boolean running = opening || active;
        if (wasRunning && !running) {
            rewinding = true; // fermeture : retour en arrière vers la position d'origine, puis vapeur
        }
        if (running) {
            rewinding = false;
        }
        wasRunning = running;

        if (rewinding) {
            tickRewind();
        } else {
            float target = opening ? OPENING_SPEED : unstable ? UNSTABLE_SPEED : active ? ACTIVE_SPEED : 0.0F;
            // Montée en régime lente à l'ouverture.
            speed += (target - speed) * (target > speed ? 0.04F : 0.025F);
            if (unstable) speed += (level.random.nextFloat() - 0.5F) * 0.8F;
            if (Math.abs(speed) < 0.01F && target == 0.0F) speed = 0.0F;
            angle = wrap(angle + speed);
        }

        arcs.tick(level, core.getBlockPos(), right, angle, opening, active, unstable);

        if (ventTicks > 0) {
            tickVent(level, core.getBlockPos(), VENT_TICKS - ventTicks);
            ventTicks--;
        }
    }

    /** Recul jusqu'à 0° : vite au début, en douceur à l'arrivée, puis déclenche la vapeur. */
    private void tickRewind() {
        float remaining = angle; // l'anneau recule : il doit parcourir « angle » degrés vers 0
        float step = Math.max(0.5F, Math.min(REWIND_MAX_SPEED, remaining * 0.06F));
        speed = -step;
        if (angle + speed <= 0.0F) {
            angle = 0.0F;
            speed = 0.0F;
            rewinding = false;
            ventTicks = VENT_TICKS;
            return;
        }
        angle += speed;
    }

    private static float wrap(float degrees) {
        float a = degrees % 360.0F;
        return a < 0.0F ? a + 360.0F : a;
    }

    /** Angle de l'anneau en degrés, interpolé entre deux ticks. */
    public float angle(float partialTick) {
        return angle + speed * partialTick;
    }

    /** Arcs électriques en cours entre les cristaux de l'anneau et les blocs voisins. */
    public PortalGateArcs arcs() {
        return arcs;
    }

    public float speed() {
        return speed;
    }

    /** Vrai pendant la désactivation (retour arrière de l'anneau + jets de vapeur « pssshhh »). */
    public boolean isCooldown() {
        return rewinding || ventTicks > 0;
    }

    /** Refroidissement : deux jets de vapeur, puis quelques volutes de vapeur qui s'élèvent (pas de fumée). */
    private void tickVent(Level level, BlockPos core, int age) {
        if (right == null) return;
        RandomSource random = level.random;
        if (age == 0 || age == 18) {
            float volume = age == 0 ? 1.0F : 0.6F;
            double[] c = toWorld(core, 0.5D, 1.0D, 0.5D);
            level.playLocalSound(c[0], c[1], c[2], ModSounds.PORTAL_VENT, SoundSource.BLOCKS,
                    volume, 0.85F + random.nextFloat() * 0.2F, false);
        }

        // Évents : de chaque côté du socle, et sur les flancs bas de l'anneau.
        double[][] vents = {
                {-2.2D, 0.55D, -1.0D}, {3.2D, 0.55D, 1.0D},
                {-1.55D, 1.25D, -1.0D}, {2.55D, 1.25D, 1.0D}
        };
        boolean jet = age < 14 || (age >= 18 && age < 26);
        for (double[] vent : vents) {
            double[] p = toWorld(core, vent[0], vent[1], 0.5D + (random.nextDouble() - 0.5D) * 0.5D);
            double out = vent[2];
            if (jet) {
                // En diagonale, un peu plus vers le haut que vers l'extérieur.
                double speed = 0.10D + random.nextDouble() * 0.05D;
                double[] v = toVelocity(out * speed * 0.8D, speed);
                level.addParticle(ParticleTypes.CLOUD, p[0], p[1], p[2], v[0], v[1], v[2]);
            } else if (random.nextInt(4) == 0) {
                double[] v = toVelocity(out * 0.02D, 0.05D + random.nextDouble() * 0.03D);
                level.addParticle(ParticleTypes.CLOUD, p[0], p[1], p[2], v[0], v[1], v[2]);
            }
        }
    }

    /** Coordonnées locales au portail (horizontal, vertical, profondeur en blocs depuis le coin du Core). */
    private double[] toWorld(BlockPos core, double h, double v, double d) {
        boolean xAxis = right.getAxis() == Direction.Axis.X;
        return new double[]{
                core.getX() + (xAxis ? h : d),
                core.getY() + v,
                core.getZ() + (xAxis ? d : h)
        };
    }

    private double[] toVelocity(double h, double v) {
        boolean xAxis = right.getAxis() == Direction.Axis.X;
        return new double[]{xAxis ? h : 0.0D, v, xAxis ? 0.0D : h};
    }

    /** Orientation du portail, déduite des blocs de cadre masqués à côté du Core. */
    static Direction resolveRight(Level level, BlockPos core) {
        BlockState east = level.getBlockState(core.east());
        if (east.is(ModBlocks.PORTAL_FRAME) && east.getValue(PortalFrameBlock.FORMED)) return Direction.EAST;
        BlockState south = level.getBlockState(core.south());
        if (south.is(ModBlocks.PORTAL_FRAME) && south.getValue(PortalFrameBlock.FORMED)) return Direction.SOUTH;
        return null;
    }
}
