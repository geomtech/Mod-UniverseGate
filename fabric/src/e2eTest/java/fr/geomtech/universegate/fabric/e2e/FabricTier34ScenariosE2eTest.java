package fr.geomtech.universegate.fabric.e2e;

import fr.geomtech.universegate.e2e.harness.SeamlessContractProbe;
import fr.geomtech.universegate.fabric.FabricClientPlatformHelper;
import fr.geomtech.universegate.fabric.FabricPlatformHelper;
import fr.geomtech.universegate.platform.ClientPlatformHelper;
import fr.geomtech.universegate.platform.ClientServices;
import fr.geomtech.universegate.platform.PlatformHelper;
import fr.geomtech.universegate.platform.Services;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("tier3")
@Tag("tier4")
@Tag("cross-loader")
@Tag("fabric")
@DisplayName("Fabric Tier 3/4 Seamless Loader Scenarios (T3-C18, T4-S07)")
class FabricTier34ScenariosE2eTest {

    private static final String FQCN_PORTAL_STENCIL_RENDERER =
            "fr.geomtech.universegate.seamless.client.PortalStencilRenderer";
    private static final String FQCN_SODIUM_IRIS_COMPAT =
            "fr.geomtech.universegate.seamless.client.SodiumIrisCompat";

    private static boolean hasMethodNamed(Class<?> owner, String methodName) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @Tag("areaB")
    @Tag("areaE")
    @DisplayName("T3-C18 (Fabric): Cross-version render hook, stencil & Sodium/Iris compat contracts")
    void t3C18FabricRenderHookStencilAndCompatContracts() {
        assertInstanceOf(FabricPlatformHelper.class, Services.PLATFORM);
        assertEquals("fabric", Services.PLATFORM.getLoaderName());
        assertFalse(Services.PLATFORM.autoRegistersPoiBlockStates());
        assertInstanceOf(FabricClientPlatformHelper.class, ClientServices.CLIENT);

        //? if >=1.21.11 {
        /*SeamlessContractProbe.requireClass("net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents", false);
        SeamlessContractProbe.requireClass("net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext", false);
        SeamlessContractProbe.requireClass("com.mojang.blaze3d.pipeline.RenderPipeline", false);
        SeamlessContractProbe.requireClass("fr.geomtech.universegate.compat.RecordingBufferSource", false);
        *///?} else {
        SeamlessContractProbe.requireClass("net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents", false);
        SeamlessContractProbe.requireClass("net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext", false);
        //?}

        SeamlessContractProbe.requireInstanceMethod(PlatformHelper.class, "isModLoaded", boolean.class, String.class);
        SeamlessContractProbe.requireInstanceMethod(FabricPlatformHelper.class, "isModLoaded", boolean.class, String.class);

        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "isIrisShaderPackActive", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "isIrisShadowPass", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "ensureMainTargetStencil", boolean.class);
        assertTrue(hasMethodNamed(ClientPlatformHelper.class, "registerLevelRenderPortalHook"),
                "ClientPlatformHelper must declare registerLevelRenderPortalHook");

        SeamlessContractProbe.requireInstanceMethod(FabricClientPlatformHelper.class, "isIrisShaderPackActive", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(FabricClientPlatformHelper.class, "isIrisShadowPass", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(FabricClientPlatformHelper.class, "ensureMainTargetStencil", boolean.class);
        assertTrue(hasMethodNamed(FabricClientPlatformHelper.class, "registerLevelRenderPortalHook"),
                "FabricClientPlatformHelper must implement registerLevelRenderPortalHook");
    }

    @Test
    @Tag("areaB")
    @Tag("areaD")
    @Tag("areaE")
    @DisplayName("T4-S07 (Fabric): Iris shaderpack fallback & shadow-pass suppression contracts")
    void t4S07FabricIrisShaderpackFallbackAndShadowPassContracts() {
        assertInstanceOf(FabricClientPlatformHelper.class, ClientServices.CLIENT);

        SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer", false);
        SeamlessContractProbe.requireClass(FQCN_SODIUM_IRIS_COMPAT, false);
        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class
        );

        SeamlessContractProbe.requireInstanceMethod(FabricClientPlatformHelper.class, "isIrisShaderPackActive", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(FabricClientPlatformHelper.class, "isIrisShadowPass", boolean.class);
    }
}
