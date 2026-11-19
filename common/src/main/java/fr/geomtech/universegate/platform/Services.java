package fr.geomtech.universegate.platform;

import fr.geomtech.universegate.UniverseGate;

import java.util.ServiceLoader;

/**
 * Loads the loader-specific (Fabric / NeoForge) implementations of the platform abstractions.
 */
public final class Services {

    public static final PlatformHelper PLATFORM = load(PlatformHelper.class);

    private Services() {}

    public static <T> T load(Class<T> clazz) {
        T service = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to load platform service for " + clazz.getName()));
        UniverseGate.LOGGER.debug("Loaded {} for service {}", service, clazz);
        return service;
    }
}
