package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
//? if >=1.21.11 {
/*import net.minecraft.util.RandomSource;
*///?}

/** Dark opening ink with its own renderer so particle accelerators keep the sprite colour and alpha. */
public final class PortalInkParticle extends SimpleAnimatedParticle {
    private PortalInkParticle(ClientLevel level, double x, double y, double z,
                              double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, sprites, 0.0F);
        friction = .92F;
        quadSize = .5F;
        lifetime = (int) (6F / (.2F + random.nextFloat() * .8F));
        hasPhysics = false;
        xd = vx;
        yd = vy;
        zd = vz;
        setColor(.04F, .04F, .04F);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed && level.getBlockState(BlockPos.containing(x, y, z)).isAir()) yd -= .0074;
    }

    //? if <1.21.11 {
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        super.render(buffer, camera, partialTicks);
    }
    //?}

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        //? if >=1.21.11 {
        /*public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
        *///?} else {
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
        //?}
            return new PortalInkParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
