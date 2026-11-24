package fr.geomtech.universegate.e2e.harness;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;

/**
 * Thread-safe, idempotent headless Minecraft bootstrap helper for E2E tests
 * across Minecraft 1.21.1 and 1.21.11.
 */
public final class E2eBootstrap {

    private static final Object LOCK = new Object();
    private static volatile boolean bootstrapped = false;
    private static volatile RegistryAccess.Frozen cachedRegistryAccess;

    private E2eBootstrap() {}

    public static void ensureMinecraftBootstrapped() {
        if (bootstrapped) {
            return;
        }
        synchronized (LOCK) {
            if (bootstrapped) {
                return;
            }
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            cachedRegistryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            bootstrapped = true;
        }
    }

    public static boolean isBootstrapped() {
        return bootstrapped;
    }

    public static RegistryAccess.Frozen registryAccess() {
        ensureMinecraftBootstrapped();
        return cachedRegistryAccess;
    }

    public static RegistryFriendlyByteBuf newRegistryFriendlyByteBuf() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
    }
}
