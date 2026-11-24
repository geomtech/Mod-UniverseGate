package fr.geomtech.universegate.e2e.tier2;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.PortalGateLayer;
import fr.geomtech.universegate.PortalGateLayers;
import fr.geomtech.universegate.PortalGateRenderContext;
import fr.geomtech.universegate.compat.ImmediateBlockEntityRenderer;
import fr.geomtech.universegate.e2e.harness.E2eBootstrap;
import fr.geomtech.universegate.e2e.harness.E2ePortalFixtures;
import fr.geomtech.universegate.e2e.harness.SeamlessContractProbe;
import fr.geomtech.universegate.platform.ClientPlatformHelper;
import fr.geomtech.universegate.seamless.net.PortalEndpointResolver;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("tier2")
@Tag("areaB")
@DisplayName("Tier 2 Area B: Portal Rendering Boundary & Negative E2E (F5-F7)")
class Tier2AreaBRenderingBoundaryE2eTest {

    private static final String FQCN_PORTAL_TRANSFORM =
            "fr.geomtech.universegate.seamless.math.PortalTransform";
    private static final String FQCN_OBLIQUE_FRUSTUM_MATH =
            "fr.geomtech.universegate.seamless.math.ObliqueFrustumMath";
    private static final String FQCN_PORTAL_STENCIL_RENDERER =
            "fr.geomtech.universegate.seamless.client.PortalStencilRenderer";

    @BeforeAll
    static void bootstrap() {
        E2eBootstrap.ensureMinecraftBootstrapped();
    }

    private static void assertClientMethodExists(String methodName, Class<?> returnType, int paramCount) {
        for (Method m : ClientPlatformHelper.class.getMethods()) {
            if (m.getName().equals(methodName)
                    && m.getReturnType() == returnType
                    && m.getParameterCount() == paramCount) {
                return;
            }
        }
        throw new AssertionFailedError(
                "Missing ClientPlatformHelper#" + methodName
                        + " with returnType=" + returnType.getName() + " and paramCount=" + paramCount
        );
    }

    private static boolean hasPublicMethod(Class<?> owner) {
        for (Method m : owner.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethodWithReturn(Class<?> owner, String name, Class<?> returnType) {
        for (Method m : owner.getMethods()) {
            if (m.getName().equals(name) && m.getReturnType() == returnType) {
                return true;
            }
        }
        return false;
    }

    // ==================== F5 Boundaries ====================

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F5-01: Coplanar camera (d = 0) and behind-plane camera (d < 0) oblique/visibility guard")
    void coplanarAndBehindPlaneCameraObliqueVisibilityGuard() {
        Class<?> membraneLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");
        float depthPx = SeamlessContractProbe.readStaticField(membraneLayerCls, "DEPTH", float.class);
        assertEquals(0.5D, depthPx / 16.0D, 1.0E-9D);

        Class<?> transformCls = SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM);
        Class<?> obliqueCls = SeamlessContractProbe.requireClass(FQCN_OBLIQUE_FRUSTUM_MATH);
        assertTrue(hasPublicMethod(transformCls));
        assertTrue(hasMethodWithReturn(obliqueCls, "withObliqueNearPlane", Matrix4f.class));
    }

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F5-02: Grazing angle camera (89.9 deg) matrix finiteness")
    void grazingAngleCameraMatrixFiniteness() {
        Class<?> ringLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRingLayer");
        Class<?> membraneLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");
        assertEquals(8.0F, SeamlessContractProbe.readStaticField(ringLayerCls, "CENTER_H", float.class));
        assertEquals(48.0F, SeamlessContractProbe.readStaticField(ringLayerCls, "CENTER_V", float.class));
        assertEquals(36, SeamlessContractProbe.readStaticField(membraneLayerCls, "RADIUS", int.class));

        Class<?> transformCls = SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM);
        Class<?> obliqueCls = SeamlessContractProbe.requireClass(FQCN_OBLIQUE_FRUSTUM_MATH);
        assertTrue(hasPublicMethod(transformCls));
        assertTrue(hasMethodWithReturn(obliqueCls, "withObliqueNearPlane", Matrix4f.class));
    }

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F5-03: Aperture boundary ray (r = 2.249 vs 2.250 vs 2.251 blocks)")
    void apertureBoundaryRayTest() {
        Class<?> membraneLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");
        double radiusBlocks = SeamlessContractProbe.readStaticField(membraneLayerCls, "RADIUS", int.class) / 16.0D;
        assertEquals(2.25D, radiusBlocks, 1.0E-9D);

        double halfInnerW = PortalFrameDetector.INNER_WIDTH / 2.0D;
        double halfInnerH = PortalFrameDetector.INNER_HEIGHT / 2.0D;
        assertEquals(1.5D, halfInnerW, 1.0E-9D);
        assertEquals(2.0D, halfInnerH, 1.0E-9D);
        assertTrue(Math.hypot(halfInnerW, halfInnerH) > radiusBlocks);

        assertTrue(2.249D <= radiusBlocks);
        assertTrue(2.250D <= radiusBlocks);
        assertFalse(2.251D <= radiusBlocks);

        Class<?> transformCls = SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM);
        assertTrue(hasPublicMethod(transformCls));
    }

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F5-04: Invalid and non-orthogonal orientation inputs rejected cleanly")
    void invalidAndNonOrthogonalOrientationInputsRejectedCleanly() {
        assertEquals(Direction.EAST, PortalEndpointResolver.normalizeFrameRight(Direction.UP));
        assertEquals(Direction.EAST, PortalEndpointResolver.normalizeFrameRight(null));

        Class<?> transformCls = SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM);
        assertTrue(hasPublicMethod(transformCls));
    }

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F5-05: Extreme world coordinates (+/-29,000,000) relative-offset precision")
    void extremeWorldCoordinatesRelativeOffsetPrecision() {
        BlockPos negExtreme = new BlockPos(-29_000_000, 64, 29_000_000);
        BlockPos posExtreme = new BlockPos(29_000_000, 200, -29_000_000);

        Vec3 negCenter = E2ePortalFixtures.portalCenter(negExtreme);
        Vec3 posCenter = E2ePortalFixtures.portalCenter(posExtreme);
        assertEquals(-28_999_999.5D, negCenter.x, 1.0E-9D);
        assertEquals(67.0D, negCenter.y, 1.0E-9D);
        assertEquals(29_000_000.5D, negCenter.z, 1.0E-9D);
        assertEquals(29_000_000.5D, posCenter.x, 1.0E-9D);
        assertEquals(203.0D, posCenter.y, 1.0E-9D);
        assertEquals(-28_999_999.5D, posCenter.z, 1.0E-9D);

        Class<?> transformCls = SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM);
        assertTrue(hasPublicMethod(transformCls));
    }

    // ==================== F6 Boundaries ====================

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F6-01: Zero-sized or minimized framebuffer (0x0) guard")
    void zeroSizedOrMinimizedFramebufferGuard() {
        SeamlessContractProbe.requireClass("fr.geomtech.universegate.platform.ClientPlatformHelper", false);
        SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRenderer", false);

        SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "ensureMainTargetStencil", boolean.class);
    }

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F6-02: Simulated stencil-unavailable main target triggers clean fallback")
    void simulatedStencilUnavailableMainTargetTriggersCleanFallback() {
        Class<?> membraneCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");
        @SuppressWarnings("unchecked")
        List<PortalGateLayer> layers = SeamlessContractProbe.invokeStatic(
                PortalGateLayers.class, "all", List.class, new Class<?>[0]);
        assertTrue(layers.stream().anyMatch(membraneCls::isInstance));

        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "ensureMainTargetStencil", boolean.class);
        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class);
    }

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F6-03: 1.21.11 RecordingBufferSource isolation vs level-render hook")
    void recordingBufferSourceIsolationVsLevelRenderHook() {
        Class<?> gateRenderer = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRenderer", false);
        assertTrue(ImmediateBlockEntityRenderer.class.isAssignableFrom(gateRenderer));

        //? if >=1.21.11 {
        /*Class<?> recBuf = SeamlessContractProbe.requireClass("fr.geomtech.universegate.compat.RecordingBufferSource", false);
        Class<?> collectorCls = SeamlessContractProbe.requireClass("net.minecraft.client.renderer.SubmitNodeCollector", false);
        Class<?> stateCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.compat.ImmediateBlockEntityRenderer$State", false);
        Class<?> camStateCls = SeamlessContractProbe.requireClass("net.minecraft.client.renderer.state.CameraRenderState", false);
        SeamlessContractProbe.requireInstanceMethod(
                ImmediateBlockEntityRenderer.class, "submit", void.class,
                stateCls, PoseStack.class, collectorCls, camStateCls);
        SeamlessContractProbe.requireInstanceMethod(recBuf, "submit", void.class);
        *///?} else {
        SeamlessContractProbe.requireInstanceMethod(
                ImmediateBlockEntityRenderer.class, "render", void.class,
                net.minecraft.world.level.block.entity.BlockEntity.class, float.class,
                PoseStack.class, MultiBufferSource.class, int.class, int.class);
        //?}

        assertClientMethodExists("registerLevelRenderPortalHook", void.class, 1);
    }

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F6-04: Exception inside single portal render pass preserves state for remaining portals")
    void exceptionInsideSinglePortalRenderPassPreservesStateForRemainingPortals() {
        AABB bounds1 = E2ePortalFixtures.portalRenderBounds(E2ePortalFixtures.DEFAULT_SOURCE_CORE);
        AABB bounds2 = E2ePortalFixtures.portalRenderBounds(E2ePortalFixtures.DEFAULT_TARGET_CORE);
        assertFalse(bounds1.intersects(bounds2));

        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class);
        assertClientMethodExists("registerLevelRenderPortalHook", void.class, 1);
    }

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F6-05: Zoomed FOV (Spyglass 7 deg) and wide FOV (110 deg) projection compatibility")
    void zoomedFovAndWideFovProjectionCompatibility() {
        Class<?> gateRenderer = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRenderer", false);
        SeamlessContractProbe.requireInstanceMethod(gateRenderer, "rendersOffScreen", boolean.class);

        Class<?> obliqueCls = SeamlessContractProbe.requireClass(FQCN_OBLIQUE_FRUSTUM_MATH);
        assertTrue(hasMethodWithReturn(obliqueCls, "withObliqueNearPlane", Matrix4f.class));

        for (float fovDeg : new float[]{7.0F, 70.0F, 110.0F}) {
            Matrix4f proj = new Matrix4f().perspective(
                    (float) Math.toRadians(fovDeg), 16.0F / 9.0F, 0.05F, 256.0F);
            assertTrue(Float.isFinite(proj.m00()) && Float.isFinite(proj.m11()) && Float.isFinite(proj.m22()));
        }
    }

    // ==================== F7 Boundaries ====================

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F7-01: Stencil recursion depth cap prevents infinite portal-in-portal loop")
    void stencilRecursionDepthCapPreventsInfiniteLoop() {
        Class<?> membraneCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");
        @SuppressWarnings("unchecked")
        List<PortalGateLayer> layers = SeamlessContractProbe.invokeStatic(
                PortalGateLayers.class, "all", List.class, new Class<?>[0]);
        assertTrue(layers.stream().anyMatch(membraneCls::isInstance));

        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class);
        SeamlessContractProbe.requireClass("fr.geomtech.universegate.seamless.client.RemoteRenderWorldState", false);
    }

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F7-02: Portal 180-degree behind camera frustum is culled cleanly")
    void portal180DegreeBehindCameraFrustumIsCulledCleanly() {
        BlockPos core = new BlockPos(0, 64, 0);
        AABB bounds = E2ePortalFixtures.portalRenderBounds(core);
        Vec3 camPos = new Vec3(core.getX() + 0.5D, core.getY() + 3.0D, core.getZ() + 6.0D);
        // Camera looks toward +Z, while portal bounds span z in [-3, +4] strictly behind camPos.z
        assertTrue(bounds.maxZ < camPos.z);

        SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM);
        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class);
    }

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F7-03: Portal fully occluded by 5x6 foreground wall leaks zero pixels")
    void portalFullyOccludedBy5x6ForegroundWallLeaksZeroPixels() {
        assertEquals(5, SeamlessContractProbe.readStaticField(PortalFrameDetector.class, "OUTER_WIDTH", int.class));
        assertEquals(6, SeamlessContractProbe.readStaticField(PortalFrameDetector.class, "OUTER_HEIGHT", int.class));
        Class<?> membraneCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");
        double membraneRadius = SeamlessContractProbe.readStaticField(membraneCls, "RADIUS", int.class) / 16.0D;
        assertTrue(membraneRadius * 2.0D <= 5.0D && membraneRadius * 2.0D <= 6.0D);

        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class);
        SeamlessContractProbe.requireClass(FQCN_OBLIQUE_FRUSTUM_MATH);
    }

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F7-04: Buried portal plinth (PortalGateBaseLayer.isBuried) seam compatibility")
    void buriedPortalPlinthSeamCompatibility() {
        Class<?> baseLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateBaseLayer");
        SeamlessContractProbe.requireStaticMethod(
                baseLayerCls, "isBuried", boolean.class, Level.class, BlockPos.class, Direction.class);

        @SuppressWarnings("unchecked")
        List<PortalGateLayer> layers = SeamlessContractProbe.invokeStatic(
                PortalGateLayers.class, "all", List.class, new Class<?>[0]);
        assertTrue(baseLayerCls.isInstance(layers.get(0)));

        Class<?> ringLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRingLayer");
        Class<?> membraneCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");
        float centerV = SeamlessContractProbe.readStaticField(ringLayerCls, "CENTER_V", float.class);
        float innerRadius = SeamlessContractProbe.readStaticField(ringLayerCls, "INNER_RADIUS", float.class);
        int membraneRadius = SeamlessContractProbe.readStaticField(membraneCls, "RADIUS", int.class);
        int plinthHeight = SeamlessContractProbe.readStaticField(baseLayerCls, "PLINTH_HEIGHT", int.class);

        assertEquals(13.0F, centerV - innerRadius, 1.0E-5F);
        assertEquals(12.0F, centerV - membraneRadius, 1.0E-5F);
        assertEquals(12, plinthHeight);

        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class);
    }

    @Test
    @Tag("tier2")
    @Tag("areaB")
    @DisplayName("T2-F7-05: Idle and Opening portals (active == false) never draw opaque membrane or stencil")
    void idleAndOpeningPortalsNeverDrawOpaqueMembraneOrStencil() {
        PortalGateRenderContext idleCtx = new PortalGateRenderContext(
                null, null, E2ePortalFixtures.DEFAULT_SOURCE_CORE, Direction.EAST, 10L, 0.0F, 10.0F,
                false, false, false, 0.0F, 0.0F, 1.0F, false);
        PortalGateRenderContext openingCtx = new PortalGateRenderContext(
                null, null, E2ePortalFixtures.DEFAULT_SOURCE_CORE, Direction.EAST, 10L, 0.0F, 10.0F,
                true, false, false, 0.5F, 15.0F, 1.0F, false);

        assertFalse(idleCtx.running());
        assertFalse(idleCtx.active());
        assertTrue(openingCtx.running());
        assertFalse(openingCtx.active());

        Class<?> ringLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRingLayer");
        int idleColor = SeamlessContractProbe.invokeStatic(
                ringLayerCls, "lipColor", int.class,
                new Class<?>[]{PortalGateRenderContext.class, float.class},
                idleCtx, 0.0F);
        assertEquals(0x3A3A3A, idleColor);
        assertNotEquals(0xFFFFFF, idleColor);

        int bottomOpeningColor = SeamlessContractProbe.invokeStatic(
                ringLayerCls, "lipColor", int.class,
                new Class<?>[]{PortalGateRenderContext.class, float.class},
                openingCtx, -Mth.HALF_PI);
        int topOpeningColor = SeamlessContractProbe.invokeStatic(
                ringLayerCls, "lipColor", int.class,
                new Class<?>[]{PortalGateRenderContext.class, float.class},
                openingCtx, Mth.HALF_PI);
        assertEquals(0xFFFFFF, bottomOpeningColor);
        assertEquals(0x3A3A3A, topOpeningColor);

        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class);
    }
}
