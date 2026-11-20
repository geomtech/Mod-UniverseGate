package fr.geomtech.universegate;

//? if >=1.21.11 {
/*import net.minecraft.util.RandomSource;
*///?}
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Client-side particle for {@link ModParticles#SPARK}: a copy of the vanilla end rod particle.
 *
 * <p>AsyncParticles (GPU acceleration) only takes over particles whose class does not override
 * {@code render}. Its GPU path draws end rod sparkles as flat cyan squares, so this class overrides
 * {@code render} (behaviour unchanged) to keep UniverseGate's sparkles on the vanilla path.
 * Since 1.21.9 particles are drawn through particle groups and there is no {@code render} to override.
 */
public class UniverseGateSparkParticle extends SimpleAnimatedParticle {

    private static final int FADE_COLOR = 15916745; // same as vanilla end rod

    protected UniverseGateSparkParticle(ClientLevel level,
                                        double x, double y, double z,
                                        double xd, double yd, double zd,
                                        SpriteSet sprites) {
        super(level, x, y, z, sprites, 0.0125F);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.quadSize *= 0.75F;
        this.lifetime = 60 + this.random.nextInt(12);
        this.setFadeColor(FADE_COLOR);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void move(double x, double y, double z) {
        this.setBoundingBox(this.getBoundingBox().move(x, y, z));
        this.setLocationFromBoundingbox();
    }

    //? if <1.21.11 {
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        // Intentionally overridden: see class javadoc (AsyncParticles GPU compatibility).
        super.render(buffer, camera, partialTicks);
    }
    //?}

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        //? if >=1.21.11 {
        /*public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
        *///?} else {
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
        //?}
            return new UniverseGateSparkParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
