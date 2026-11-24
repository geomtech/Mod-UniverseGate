package fr.geomtech.universegate.neoforge.e2e;

import fr.geomtech.universegate.e2e.harness.SeamlessContractProbe;
import fr.geomtech.universegate.neoforge.NeoForgeClientPlatformHelper;
import fr.geomtech.universegate.neoforge.NeoForgePlatformHelper;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("tier3")
@Tag("tier4")
@Tag("cross-loader")
@Tag("neoforge")
@DisplayName("NeoForge Tier 3/4 Seamless Loader Scenarios (T3-C18, T4-S07)")
class NeoForgeTier34ScenariosE2eTest {

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
    @DisplayName("T3-C18 (NeoForge): NETWORK_VERSION=2, render/stencil hooks & Sodium/Iris compat contracts")
    void t3C18NeoForgeNetworkVersionRenderHookStencilAndCompatContracts() {
        assertInstanceOf(NeoForgePlatformHelper.class, Services.PLATFORM);
        assertEquals("neoforge", Services.PLATFORM.getLoaderName());
        assertTrue(Services.PLATFORM.autoRegistersPoiBlockStates());
        assertInstanceOf(NeoForgeClientPlatformHelper.class, ClientServices.CLIENT);

        String networkVersion = SeamlessContractProbe.readStaticField(
                NeoForgePlatformHelper.class, "NETWORK_VERSION", String.class
        );
        assertEquals("2", networkVersion);

        //? if >=1.21.11 {
        /*SeamlessContractProbe.requireClass("net.neoforged.neoforge.client.event.ConfigureMainRenderTargetEvent", false);
        SeamlessContractProbe.requireClass("net.neoforged.neoforge.client.event.RenderLevelStageEvent$AfterTranslucentBlocks", false);
        SeamlessContractProbe.requireClass("net.neoforged.neoforge.client.event.RenderLevelStageEvent$AfterEntities", false);
        SeamlessContractProbe.requireClass("net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent", false);
        SeamlessContractProbe.requireClass("fr.geomtech.universegate.compat.RecordingBufferSource", false);
        *///?} else {
        SeamlessContractProbe.requireClass("net.neoforged.neoforge.client.event.RenderLevelStageEvent", false);
        SeamlessContractProbe.requireClass("net.neoforged.neoforge.client.event.RenderLevelStageEvent$Stage", false);
        SeamlessContractProbe.requireClass("net.neoforged.neoforge.client.StencilManager", false);
        //?}

        SeamlessContractProbe.requireInstanceMethod(PlatformHelper.class, "isModLoaded", boolean.class, String.class);
        SeamlessContractProbe.requireInstanceMethod(NeoForgePlatformHelper.class, "isModLoaded", boolean.class, String.class);

        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "isIrisShaderPackActive", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "isIrisShadowPass", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "ensureMainTargetStencil", boolean.class);
        assertTrue(hasMethodNamed(ClientPlatformHelper.class, "registerLevelRenderPortalHook"),
                "ClientPlatformHelper must declare registerLevelRenderPortalHook");

        SeamlessContractProbe.requireInstanceMethod(NeoForgeClientPlatformHelper.class, "isIrisShaderPackActive", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(NeoForgeClientPlatformHelper.class, "isIrisShadowPass", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(NeoForgeClientPlatformHelper.class, "ensureMainTargetStencil", boolean.class);
        assertTrue(hasMethodNamed(NeoForgeClientPlatformHelper.class, "registerLevelRenderPortalHook"),
                "NeoForgeClientPlatformHelper must implement registerLevelRenderPortalHook");
    }

    @Test
    @Tag("areaB")
    @Tag("areaD")
    @Tag("areaE")
    @DisplayName("T4-S07 (NeoForge): Iris shaderpack fallback & shadow-pass suppression contracts")
    void t4S07NeoForgeIrisShaderpackFallbackAndShadowPassContracts() {
        assertInstanceOf(NeoForgeClientPlatformHelper.class, ClientServices.CLIENT);

        SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer", false);
        SeamlessContractProbe.requireClass(FQCN_SODIUM_IRIS_COMPAT, false);
        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class
        );

        SeamlessContractProbe.requireInstanceMethod(NeoForgeClientPlatformHelper.class, "isIrisShaderPackActive", boolean.class);
        SeamlessContractProbe.requireInstanceMethod(NeoForgeClientPlatformHelper.class, "isIrisShadowPass", boolean.class);
    }
}
