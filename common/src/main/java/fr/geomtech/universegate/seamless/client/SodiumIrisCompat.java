package fr.geomtech.universegate.seamless.client;

import fr.geomtech.universegate.platform.Services;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Zero-hard-dependency runtime compatibility bridge for Sodium and Iris.
 *
 * <p>Detects Sodium/Iris via {@link fr.geomtech.universegate.platform.PlatformHelper#isModLoaded(String)}
 * and probes {@code net.irisshaders.iris.api.v0.IrisApi} via cached reflection so UniverseGate never
 * requires Sodium or Iris at compile time and cleanly falls back to {@code PortalMembraneLayer}
 * whenever shaderpacks, shadow passes, or incompatible render pipelines are active.</p>
 */
public final class SodiumIrisCompat {

    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    private static volatile boolean probed = false;
    private static volatile boolean sodiumLoaded = false;
    private static volatile boolean irisLoaded = false;
    private static volatile @Nullable Object irisApiInstance;
    private static volatile @Nullable Method isShaderPackInUseMethod;
    private static volatile @Nullable Method isRenderingShadowPassMethod;

    private SodiumIrisCompat() {}

    public static void ensureProbed() {
        if (probed) {
            return;
        }
        synchronized (SodiumIrisCompat.class) {
            if (probed) {
                return;
            }
            try {
                sodiumLoaded = Services.PLATFORM.isModLoaded("sodium")
                        || Services.PLATFORM.isModLoaded("embeddium");
                irisLoaded = Services.PLATFORM.isModLoaded("iris")
                        || Services.PLATFORM.isModLoaded("oculus");
                if (irisLoaded) {
                    probeIrisApi();
                }
            } catch (Throwable ignored) {
                sodiumLoaded = false;
                irisLoaded = false;
            }
            probed = true;
        }
    }

    private static void probeIrisApi() {
        try {
            Class<?> apiClass = Class.forName(IRIS_API_CLASS);
            Method getInstance = apiClass.getMethod("getInstance");
            Object instance = getInstance.invoke(null);
            if (instance != null) {
                irisApiInstance = instance;
                isShaderPackInUseMethod = apiClass.getMethod("isShaderPackInUse");
                isRenderingShadowPassMethod = apiClass.getMethod("isRenderingShadowPass");
            }
        } catch (Throwable ignored) {
            irisApiInstance = null;
            isShaderPackInUseMethod = null;
            isRenderingShadowPassMethod = null;
        }
    }

    public static boolean isSodiumLoaded() {
        ensureProbed();
        return sodiumLoaded;
    }

    public static boolean isIrisLoaded() {
        ensureProbed();
        return irisLoaded;
    }

    public static boolean isIrisShaderPackActive() {
        ensureProbed();
        Object instance = irisApiInstance;
        Method method = isShaderPackInUseMethod;
        if (instance == null || method == null) {
            return false;
        }
        try {
            Object res = method.invoke(instance);
            return Boolean.TRUE.equals(res);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isIrisShadowPass() {
        ensureProbed();
        Object instance = irisApiInstance;
        Method method = isRenderingShadowPassMethod;
        if (instance == null || method == null) {
            return false;
        }
        try {
            Object res = method.invoke(instance);
            return Boolean.TRUE.equals(res);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Evaluates pure fallback policy (testable without live Minecraft/OpenGL).
     */
    public static FallbackReason evaluateRenderPolicy(boolean stencilAvailable,
                                                      boolean sodiumActive,
                                                      boolean irisShadowPass,
                                                      boolean irisShaderPackActive) {
        if (irisShadowPass) {
            return FallbackReason.IRIS_SHADOW_PASS;
        }
        if (irisShaderPackActive) {
            return FallbackReason.IRIS_SHADERPACK_ACTIVE;
        }
        if (sodiumActive) {
            return FallbackReason.SODIUM_CHUNK_PIPELINE;
        }
        if (!stencilAvailable) {
            return FallbackReason.STENCIL_UNAVAILABLE;
        }
        return FallbackReason.NONE;
    }

    public static FallbackReason currentFallbackReason(boolean stencilAvailable) {
        return evaluateRenderPolicy(
                stencilAvailable,
                isSodiumLoaded(),
                isIrisShadowPass(),
                isIrisShaderPackActive()
        );
    }

    public static boolean canRenderSeamlessly(boolean stencilAvailable) {
        return currentFallbackReason(stencilAvailable) == FallbackReason.NONE;
    }

    public enum FallbackReason {
        NONE,
        STENCIL_UNAVAILABLE,
        IRIS_SHADOW_PASS,
        IRIS_SHADERPACK_ACTIVE,
        SODIUM_CHUNK_PIPELINE
    }
}
