package fr.geomtech.universegate;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class ModParticles {

    /**
     * Looks exactly like the vanilla end rod sparkle (same sprites, colours and motion), but uses
     * UniverseGate's own particle class so that particle renderers replacing the vanilla pipeline
     * (e.g. AsyncParticles' GPU acceleration) fall back to the vanilla rendering path for it.
     */
    public static final SimpleParticleType SPARK = register("spark");

    private static SimpleParticleType register(String id) {
        // The SimpleParticleType constructor is protected in vanilla: use an anonymous subclass.
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, id),
                new SimpleParticleType(false) {});
    }

    public static void register() {}

    private ModParticles() {}
}
