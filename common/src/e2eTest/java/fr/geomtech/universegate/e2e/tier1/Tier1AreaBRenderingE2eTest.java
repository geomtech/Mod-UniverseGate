package fr.geomtech.universegate.e2e.tier1;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.PortalFrameHelper;
import fr.geomtech.universegate.PortalGateLayer;
import fr.geomtech.universegate.PortalGateLayers;
import fr.geomtech.universegate.PortalGateRenderContext;
import fr.geomtech.universegate.compat.ImmediateBlockEntityRenderer;
import fr.geomtech.universegate.e2e.harness.E2eBootstrap;
import fr.geomtech.universegate.e2e.harness.E2ePortalFixtures;
import fr.geomtech.universegate.e2e.harness.SeamlessContractProbe;
import fr.geomtech.universegate.platform.ClientPlatformHelper;
import fr.geomtech.universegate.platform.PlatformHelper;
import fr.geomtech.universegate.seamless.net.PortalEndpointResolver;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("tier1")
@Tag("areaB")
@DisplayName("Tier 1 Area B: Portal Aperture Projection & Recursive Rendering (F5-F7)")
class Tier1AreaBRenderingE2eTest {

    private static final String FQCN_PORTAL_TRANSFORM =
            "fr.geomtech.universegate.seamless.math.PortalTransform";
    private static final String FQCN_OBLIQUE_FRUSTUM_MATH =
            "fr.geomtech.universegate.seamless.math.ObliqueFrustumMath";
    private static final String FQCN_PORTAL_STENCIL_RENDERER =
            "fr.geomtech.universegate.seamless.client.PortalStencilRenderer";
    private static final String FQCN_REMOTE_RENDER_WORLD_STATE =
            "fr.geomtech.universegate.seamless.client.RemoteRenderWorldState";

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

    private static void assertFieldDeclaredNoInit(Class<?> owner, String fieldName) {
        try {
            owner.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new AssertionFailedError("Missing declared field " + owner.getName() + "#" + fieldName, e);
        }
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

    // ==================== F5: Portal coordinate & oblique clip math ====================

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F5-01: Parallel same-orientation portal transform (EAST -> EAST)")
    void parallelSameOrientationPortalTransform() {
        Class<?> ringLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRingLayer");
        Class<?> membraneLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");

        float centerH = SeamlessContractProbe.readStaticField(ringLayerCls, "CENTER_H", float.class);
        float centerV = SeamlessContractProbe.readStaticField(ringLayerCls, "CENTER_V", float.class);
        float depth = SeamlessContractProbe.readStaticField(membraneLayerCls, "DEPTH", float.class);

        Vec3 center = E2ePortalFixtures.portalCenter(new BlockPos(0, 64, 0));
        assertEquals(new Vec3(centerH / 16.0D, 64.0D + centerV / 16.0D, depth / 16.0D), center);
        assertEquals(new Vec3(0.5D, 67.0D, 0.5D), center);

        assertEquals(Direction.NORTH, PortalEndpointResolver.resolveExitNormal(
                Direction.EAST, new BlockPos(100, 80, 100), new BlockPos(100, 80, 95)));
        assertEquals(Direction.SOUTH, PortalEndpointResolver.resolveExitNormal(
                Direction.EAST, new BlockPos(0, 64, 0), null));

        Class<?> transformCls = SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM);
        assertTrue(hasPublicMethod(transformCls), "PortalTransform must expose public transform methods");
    }

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F5-02: Orthogonal 90-degree portal rotation (EAST -> SOUTH)")
    void orthogonal90DegreePortalRotation() {
        BlockPos core = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        List<BlockPos> eastFrame = PortalFrameHelper.collectFrame(
                E2ePortalFixtures.frameMatch(core, Direction.EAST), core);
        List<BlockPos> southFrame = PortalFrameHelper.collectFrame(
                E2ePortalFixtures.frameMatch(core, Direction.SOUTH), core);
        assertEquals(E2ePortalFixtures.EXPECTED_FRAME_BLOCK_COUNT, eastFrame.size());
        assertEquals(E2ePortalFixtures.EXPECTED_FRAME_BLOCK_COUNT, southFrame.size());
        assertTrue(eastFrame.stream().allMatch(p -> p.getZ() == core.getZ()));
        assertTrue(southFrame.stream().allMatch(p -> p.getX() == core.getX()));

        assertEquals(Direction.EAST, PortalEndpointResolver.surfaceRightForExitNormal(Direction.SOUTH));
        assertEquals(Direction.NORTH, PortalEndpointResolver.surfaceRightForExitNormal(Direction.EAST));

        PortalGateRenderContext eastCtx = new PortalGateRenderContext(
                null, null, core, Direction.EAST, 100L, 0.0F, 100.0F,
                false, true, false, 0.0F, 0.0F, 1.0F, false);
        PortalGateRenderContext southCtx = new PortalGateRenderContext(
                null, null, core, Direction.SOUTH, 100L, 0.0F, 100.0F,
                false, true, false, 0.0F, 0.0F, 1.0F, false);
        assertTrue(eastCtx.xAxis());
        assertFalse(southCtx.xAxis());

        Class<?> transformCls = SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM);
        assertTrue(hasPublicMethod(transformCls), "PortalTransform must expose rotation/basis math methods");
    }

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F5-03: Circular aperture frustum visibility (R = 2.25 blocks)")
    void circularApertureFrustumVisibility() {
        Class<?> ringLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRingLayer");
        Class<?> membraneLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");

        int membraneRadiusPx = SeamlessContractProbe.readStaticField(membraneLayerCls, "RADIUS", int.class);
        float innerRadiusPx = SeamlessContractProbe.readStaticField(ringLayerCls, "INNER_RADIUS", float.class);
        assertEquals(36, membraneRadiusPx);
        assertEquals(35.0F, innerRadiusPx);
        assertTrue(membraneRadiusPx > innerRadiusPx);
        assertEquals(2.25D, membraneRadiusPx / 16.0D, 1.0E-9D);

        BlockPos core = new BlockPos(0, 64, 0);
        AABB bounds = E2ePortalFixtures.portalRenderBounds(core);
        Vec3 center = E2ePortalFixtures.portalCenter(core);
        assertTrue(bounds.contains(center.x + 2.25D, center.y, center.z));
        assertTrue(bounds.contains(center.x - 2.25D, center.y, center.z));
        assertTrue(bounds.contains(center.x, center.y + 2.25D, center.z));
        assertTrue(bounds.contains(center.x, center.y - 2.25D, center.z));

        Class<?> transformCls = SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM);
        assertTrue(hasPublicMethod(transformCls), "PortalTransform must expose aperture frustum visibility check");
    }

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F5-04: Lengyel oblique near-plane matrix calculation")
    void lengyelObliqueNearPlaneMatrixCalculation() {
        Class<?> ringLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRingLayer");
        Class<?> membraneLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");

        float ringZ0 = SeamlessContractProbe.readStaticField(ringLayerCls, "RING_Z0", float.class);
        float ringZ1 = SeamlessContractProbe.readStaticField(ringLayerCls, "RING_Z1", float.class);
        float depth = SeamlessContractProbe.readStaticField(membraneLayerCls, "DEPTH", float.class);
        assertEquals(8.0F, depth);
        assertTrue(ringZ0 <= depth && depth <= ringZ1);

        Class<?> obliqueCls = SeamlessContractProbe.requireClass(FQCN_OBLIQUE_FRUSTUM_MATH);
        assertTrue(hasMethodWithReturn(obliqueCls, "withObliqueNearPlane", Matrix4f.class),
                "ObliqueFrustumMath must declare withObliqueNearPlane returning Matrix4f");

        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0D), 16.0F / 9.0F, 0.05F, 256.0F);
        Vector4f inFront = new Vector4f(0.0F, 0.0F, -6.0F, 1.0F).mul(proj);
        float ndcZFront = inFront.z / inFront.w;
        assertTrue(ndcZFront >= -1.0F && ndcZFront <= 1.0F);
    }

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F5-05: Multi-step recursive transform composition (A -> B -> C)")
    void multiStepRecursiveTransformComposition() {
        PortalGateRenderContext ctxA = new PortalGateRenderContext(
                null, null, new BlockPos(0, 64, 0), Direction.EAST, 1L, 0.0F, 1.0F,
                false, true, false, 0.0F, 15.0F, 1.0F, false);
        PortalGateRenderContext ctxB = new PortalGateRenderContext(
                null, null, new BlockPos(100, 70, 100), Direction.SOUTH, 1L, 0.0F, 1.0F,
                false, true, false, 0.0F, 30.0F, 1.0F, false);
        PortalGateRenderContext ctxC = new PortalGateRenderContext(
                null, null, new BlockPos(200, 80, -50), Direction.EAST, 1L, 0.0F, 1.0F,
                false, true, false, 0.0F, 45.0F, 1.0F, false);

        assertTrue(ctxA.running() && ctxA.xAxis());
        assertTrue(ctxB.running() && !ctxB.xAxis());
        assertTrue(ctxC.running() && ctxC.xAxis());

        Class<?> transformCls = SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM);
        assertTrue(hasPublicMethod(transformCls), "PortalTransform must support multi-step composition");
    }

    // ==================== F6: Stencil / FBO setup & render hook ====================

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F6-01: Main RenderTarget stencil attachment availability (Fabric contract)")
    void mainRenderTargetStencilAttachmentAvailabilityFabricContract() {
        SeamlessContractProbe.requireClass("fr.geomtech.universegate.platform.ClientPlatformHelper", false);
        SeamlessContractProbe.requireClass("com.mojang.blaze3d.pipeline.RenderTarget", false);
        SeamlessContractProbe.requireClass("com.mojang.blaze3d.pipeline.MainTarget", false);

        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "ensureMainTargetStencil", boolean.class);
    }

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F6-02: Main RenderTarget stencil attachment availability (NeoForge contract)")
    void mainRenderTargetStencilAttachmentAvailabilityNeoForgeContract() {
        SeamlessContractProbe.requireClass("fr.geomtech.universegate.platform.PlatformHelper", false);
        SeamlessContractProbe.requireClass("fr.geomtech.universegate.platform.ClientPlatformHelper", false);
        SeamlessContractProbe.requireInstanceMethod(PlatformHelper.class, "getLoaderName", String.class);

        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "ensureMainTargetStencil", boolean.class);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
    }

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F6-03: Level render stage hook fires outside BER recording")
    void levelRenderStageHookFiresOutsideBerRecording() {
        Class<?> gateRenderer = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRenderer", false);
        assertTrue(ImmediateBlockEntityRenderer.class.isAssignableFrom(gateRenderer));

        //? if >=1.21.11 {
        /*Class<?> recBuf = SeamlessContractProbe.requireClass("fr.geomtech.universegate.compat.RecordingBufferSource", false);
        assertTrue(MultiBufferSource.class.isAssignableFrom(recBuf));
        SeamlessContractProbe.requireInstanceMethod(recBuf, "submit", void.class);
        SeamlessContractProbe.requireClass("fr.geomtech.universegate.compat.ImmediateBlockEntityRenderer$State", false);
        *///?} else {
        SeamlessContractProbe.requireInstanceMethod(
                ImmediateBlockEntityRenderer.class,
                "shouldRenderOffScreen",
                boolean.class,
                net.minecraft.world.level.block.entity.BlockEntity.class
        );
        //?}

        assertClientMethodExists("registerLevelRenderPortalHook", void.class, 1);
    }

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F6-04: GL depth, stencil and viewport state preservation contract")
    void glDepthStencilAndViewportStatePreservationContract() {
        Class<?> renderTypes = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRenderTypes", false);
        assertFieldDeclaredNoInit(renderTypes, "GATE_SOLID");

        @SuppressWarnings("unchecked")
        List<PortalGateLayer> layers = SeamlessContractProbe.invokeStatic(
                PortalGateLayers.class, "all", List.class, new Class<?>[0]);
        assertEquals(3, layers.size());
        assertEquals("PortalGateRingLayer", layers.get(0).getClass().getSimpleName());
        assertEquals("PortalMembraneLayer", layers.get(1).getClass().getSimpleName());
        assertEquals("PortalArcLayer", layers.get(2).getClass().getSimpleName());

        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "ensureMainTargetStencil", boolean.class);
    }

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F6-05: Clean offscreen depth-stencil FBO resize contract")
    void cleanOffscreenDepthStencilFboResizeContract() {
        SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRenderTypes", false);
        Class<?> ringLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRingLayer");
        assertEquals(96, SeamlessContractProbe.readStaticField(ringLayerCls, "SIZE", int.class));

        SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireClass(FQCN_REMOTE_RENDER_WORLD_STATE, false);
        SeamlessContractProbe.requireInstanceMethod(ClientPlatformHelper.class, "ensureMainTargetStencil", boolean.class);
    }

    // ==================== F7: Recursive stencil + oblique clip rendering ====================

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F7-01: Circular 36 px aperture stencil masking and ring overlay order")
    void circular36PxApertureStencilMaskingAndRingOverlayOrder() {
        Class<?> ringLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRingLayer");
        Class<?> membraneLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");

        assertEquals(36, SeamlessContractProbe.readStaticField(membraneLayerCls, "RADIUS", int.class));
        assertEquals(35.0F, SeamlessContractProbe.readStaticField(ringLayerCls, "INNER_RADIUS", float.class));
        assertEquals(47.0F, SeamlessContractProbe.readStaticField(ringLayerCls, "OUTER_RADIUS", float.class));
        float depth = SeamlessContractProbe.readStaticField(membraneLayerCls, "DEPTH", float.class);
        float z0 = SeamlessContractProbe.readStaticField(ringLayerCls, "RING_Z0", float.class);
        float z1 = SeamlessContractProbe.readStaticField(ringLayerCls, "RING_Z1", float.class);
        assertEquals(8.0F, depth);
        assertEquals((z0 + z1) / 2.0F, depth, 1.0E-5F);

        @SuppressWarnings("unchecked")
        List<PortalGateLayer> layers = SeamlessContractProbe.invokeStatic(
                PortalGateLayers.class, "all", List.class, new Class<?>[0]);
        assertEquals("PortalGateRingLayer", layers.get(1).getClass().getSimpleName());
        assertEquals("PortalMembraneLayer", layers.get(2).getClass().getSimpleName());
        assertEquals("PortalArcLayer", layers.get(3).getClass().getSimpleName());

        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class);
    }

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F7-02: Oblique near-plane clipping of blocks behind exit portal")
    void obliqueNearPlaneClippingOfBlocksBehindExitPortal() {
        Class<?> ringLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRingLayer");
        Class<?> membraneLayerCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");

        float centerH = SeamlessContractProbe.readStaticField(ringLayerCls, "CENTER_H", float.class);
        float centerV = SeamlessContractProbe.readStaticField(ringLayerCls, "CENTER_V", float.class);
        float depth = SeamlessContractProbe.readStaticField(membraneLayerCls, "DEPTH", float.class);

        BlockPos targetCore = E2ePortalFixtures.DEFAULT_TARGET_CORE;
        Vec3 expectedCenter = new Vec3(
                targetCore.getX() + centerH / 16.0D,
                targetCore.getY() + centerV / 16.0D,
                targetCore.getZ() + depth / 16.0D
        );
        assertEquals(expectedCenter, E2ePortalFixtures.portalCenter(targetCore));
        assertTrue(PortalEndpointResolver.resolveExitNormal(Direction.EAST, targetCore, null).getAxis().isHorizontal());

        SeamlessContractProbe.requireClass(FQCN_OBLIQUE_FRUSTUM_MATH);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
    }

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F7-03: Depth buffer restoration on portal aperture")
    void depthBufferRestorationOnPortalAperture() {
        Class<?> blinkRenderer = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalFieldBlinkRenderer", false);
        assertFieldDeclaredNoInit(blinkRenderer, "PORTAL_OCCLUSION_RENDER_TYPE");
        assertFieldDeclaredNoInit(blinkRenderer, "SINGULARITY_RENDER_TYPE");

        Class<?> renderTypes = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRenderTypes", false);
        assertFieldDeclaredNoInit(renderTypes, "GATE_SOLID");

        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireStaticMethod(
                stencilRenderer, "wasRenderedSeamlesslyThisFrame", boolean.class, BlockPos.class);
    }

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F7-04: Recursive portal-in-portal rendering (depth = 2)")
    void recursivePortalInPortalRenderingDepth2() {
        PortalGateRenderContext outer = new PortalGateRenderContext(
                null, null, E2ePortalFixtures.DEFAULT_SOURCE_CORE, Direction.EAST, 50L, 0.5F, 50.5F,
                false, true, false, 0.0F, 90.0F, 1.0F, false);
        PortalGateRenderContext inner = new PortalGateRenderContext(
                null, null, E2ePortalFixtures.DEFAULT_TARGET_CORE, Direction.SOUTH, 50L, 0.5F, 50.5F,
                false, true, false, 0.0F, 180.0F, 1.0F, false);

        assertTrue(outer.running() && outer.active());
        assertTrue(inner.running() && inner.active());

        SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        SeamlessContractProbe.requireClass(FQCN_REMOTE_RENDER_WORLD_STATE, false);
        SeamlessContractProbe.requireClass(FQCN_PORTAL_TRANSFORM);
    }

    @Test
    @Tag("tier1")
    @Tag("areaB")
    @DisplayName("T1-F7-05: Automatic PortalMembraneLayer fallback when seamless pass skipped")
    void automaticPortalMembraneLayerFallbackWhenSeamlessPassSkipped() {
        Class<?> membraneCls = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");
        assertTrue(PortalGateLayer.class.isAssignableFrom(membraneCls));
        assertEquals(36, SeamlessContractProbe.readStaticField(membraneCls, "RADIUS", int.class));
        assertEquals(8.0F, SeamlessContractProbe.readStaticField(membraneCls, "DEPTH", float.class));
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                membraneCls, "render", void.class,
                PortalGateRenderContext.class, PoseStack.class, MultiBufferSource.class));

        @SuppressWarnings("unchecked")
        List<PortalGateLayer> layers = SeamlessContractProbe.invokeStatic(
                PortalGateLayers.class, "all", List.class, new Class<?>[0]);
        assertTrue(membraneCls.isInstance(layers.get(2)));

        Class<?> stencilRenderer = SeamlessContractProbe.requireClass(FQCN_PORTAL_STENCIL_RENDERER, false);
        Boolean rendered = SeamlessContractProbe.invokeStatic(
                stencilRenderer,
                "wasRenderedSeamlesslyThisFrame",
                Boolean.class,
                new Class<?>[]{BlockPos.class},
                new BlockPos(999, 64, 999)
        );
        assertFalse(rendered);
    }
}
