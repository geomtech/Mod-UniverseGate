package fr.geomtech.universegate.seamless.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
//? if <1.21.11 {
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexSorting;
import fr.geomtech.universegate.PortalBuriedFloor;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.PortalGateFootRenderer;
import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.mixin.client.CameraAccessor;
import fr.geomtech.universegate.mixin.client.LevelRendererAccessor;
import fr.geomtech.universegate.mixin.client.LightTextureAccessor;
import fr.geomtech.universegate.mixin.client.SectionRenderDispatcherAccessor;
import fr.geomtech.universegate.seamless.math.ObliqueFrustumMath;
import fr.geomtech.universegate.seamless.math.PortalTransform;
import fr.geomtech.universegate.seamless.net.PortalRemoteEndpointPayload;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
//?}

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Recursive stencil-masked + oblique-near-plane portal renderer.
 *
 * <p>Executes outside {@code PortalGateRenderer} (so MC 1.21.11's {@code RecordingBufferSource}
 * is never blocked) and coordinates with {@code PortalMembraneLayer} via
 * {@link #replacesMembrane(BlockPos)}. Iris and Sodium composite after the main world render.
 * Shadow passes, missing stencil bits and opening blackout keep the membrane fallback.</p>
 */
public final class PortalStencilRenderer {

    public static final int MAX_PORTALS_PER_FRAME = 4;
    public static final int MAX_RECURSION_DEPTH = 2;
    public static final double MAX_SEAMLESS_DISTANCE = 48.0D;
    private static final int APERTURE_SEGMENTS = 64;
    private static final int MAX_SYNC_SECTION_BUILDS_PER_FRAME = 4;

    private static final Set<BlockPos> RENDERED_SEAMLESSLY_THIS_FRAME = new HashSet<>();
    //? if <1.21.11 {
    private static final double VEIL_FADE_START = 24.0D;
    private static final double VEIL_OPAQUE_DISTANCE = 34.0D;
    private static final double TERRAIN_RADIUS = 56.0D;
    private static final double DETAIL_RADIUS_SQR = 20.0D * 20.0D;
    private static final Set<BlockPos> RENDERED_SURFACES_THIS_FRAME = new HashSet<>();
    private static final Map<BlockPos, CachedFrontNormal> FRONT_NORMAL_CACHE = new HashMap<>();
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> frontNormalCacheDim = null;
    private static RemoteRenderWorldState localCompositeWorld;
    private static List<PortalCandidate> pendingComposite = List.of();
    private static final Map<PortalRemoteEndpointPayload, Long> VIEW_RETRY_AT = new HashMap<>();
    //?}
    private static int currentRecursionDepth = 0;
    private static String lastDiagnosticStatus = "";

    private PortalStencilRenderer() {}

    public static void beginFrame() {
        if (currentRecursionDepth == 0) {
            RENDERED_SEAMLESSLY_THIS_FRAME.clear();
            //? if <1.21.11 {
            RENDERED_SURFACES_THIS_FRAME.clear();
            pendingComposite = List.of();
            //?}
        }
    }

    public static boolean isRenderingPortalPass() {
        return currentRecursionDepth > 0;
    }

    public static int getCurrentRecursionDepth() {
        return currentRecursionDepth;
    }

    /** Whether this frame includes a live view of the destination through the given portal. */
    public static boolean wasRenderedSeamlesslyThisFrame(BlockPos corePos) {
        return corePos != null && RENDERED_SEAMLESSLY_THIS_FRAME.contains(corePos);
    }

    /** Whether the compositor owns the vortex surface, including opaque surfaces. */
    public static boolean replacesMembrane(BlockPos corePos) {
        if (isRenderingPortalPass()) return false;
        //? if <1.21.11 {
        if (RENDERED_SURFACES_THIS_FRAME.contains(corePos)) return true;
        for (PortalCandidate candidate : pendingComposite) {
            if (candidate.corePos().equals(corePos)) return true;
        }
        //?}
        return false;
    }

    public static void onClientDisconnect() {
        RENDERED_SEAMLESSLY_THIS_FRAME.clear();
        //? if <1.21.11 {
        RENDERED_SURFACES_THIS_FRAME.clear();
        FRONT_NORMAL_CACHE.clear();
        frontNormalCacheDim = null;
        if (localCompositeWorld != null) localCompositeWorld.dispose();
        localCompositeWorld = null;
        pendingComposite = List.of();
        VIEW_RETRY_AT.clear();
        //?}
        currentRecursionDepth = 0;
        lastDiagnosticStatus = "";
        PortalRenderTargetPool.releaseAll();
    }

    /**
     * Renders the visible vortex surfaces and, when enabled, the live view through them.
     */
    public static void renderActivePortals(LevelRenderer levelRenderer,
                                           Camera mainCamera,
                                           float partialTick,
                                           Matrix4f frustumMatrix,
                                           Matrix4f projectionMatrix) {
        //? if <1.21.11 {
        if (SodiumIrisCompat.isIrisShadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ClientRemoteWorldManager.getInstance().getActiveEndpoints().isEmpty()) return;
        var candidates = collectCandidates(mc.level, mainCamera.getPosition(), new Vec3(mainCamera.getLookVector()),
                new Matrix4f(projectionMatrix).mul(frustumMatrix));
        if (usesLateComposite()) {
            var target = mc.getMainRenderTarget();
            if (candidates.isEmpty() || !PortalRenderTargetPool.hasStencilBuffer(target.frameBufferId, target.width, target.height)) return;
            // Leave the aperture clear while Iris assembles the main world. Its depth must come
            // from actual foreground geometry rather than from the fallback membrane.
            pendingComposite = candidates.subList(0, Math.min(candidates.size(), MAX_PORTALS_PER_FRAME));
        } else {
            renderPortals(levelRenderer, mainCamera, partialTick, frustumMatrix, projectionMatrix, candidates);
        }
        //?}
    }

    //? if <1.21.11 {
    private static boolean usesLateComposite() {
        return SodiumIrisCompat.isSodiumLoaded() || SodiumIrisCompat.isIrisShaderPackActive();
    }

    public static void compositeAfterWorld(LevelRenderer renderer, Camera camera, float partialTick,
                                           Matrix4f view, Matrix4f projection) {
        if (pendingComposite.isEmpty() || SodiumIrisCompat.isIrisShadowPass()) return;
        List<PortalCandidate> candidates = pendingComposite;
        pendingComposite = List.of();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        renderPortals(renderer, camera, partialTick, view, projection, candidates);
    }

    private static void renderPortals(LevelRenderer levelRenderer, Camera mainCamera, float partialTick,
                                      Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                      List<PortalCandidate> candidates) {
        if (currentRecursionDepth >= MAX_RECURSION_DEPTH) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel activeLevel = mc.level;
        if (activeLevel == null || mc.player == null) {
            return;
        }

        int width = mc.getMainRenderTarget().width;
        int height = mc.getMainRenderTarget().height;
        boolean stencilOk = PortalRenderTargetPool.hasActiveStencilBuffer(width, height);
        SodiumIrisCompat.FallbackReason fallback = SodiumIrisCompat.evaluateRenderPolicy(stencilOk, false,
                SodiumIrisCompat.isIrisShadowPass(), false);
        if (fallback != SodiumIrisCompat.FallbackReason.NONE) {
            logStatusOnce("fallback=" + fallback + " (stencilOk=" + stencilOk + ")");
            return;
        }

        if (candidates.isEmpty()) {
            int endpoints = ClientRemoteWorldManager.getInstance().getActiveEndpoints().size();
            if (endpoints > 0) {
                logStatusOnce("endpoints=" + endpoints + " but 0 visible/ready candidates");
            }
            return;
        }

        logStatusOnce("compositing " + candidates.size() + " portal surface(s), stencilOk=" + stencilOk);

        if (currentRecursionDepth == 0) {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        }

        WorldRenderState saved = WorldRenderState.capture();
        try {
            boolean liveViewRendered = false;
            for (int i = 0; i < Math.min(candidates.size(), MAX_PORTALS_PER_FRAME); i++) {
                PortalCandidate candidate = candidates.get(i);
                if (renderSinglePortal(mc, levelRenderer, mainCamera, partialTick, frustumMatrix, projectionMatrix,
                        candidate, !liveViewRendered)) {
                    RENDERED_SEAMLESSLY_THIS_FRAME.add(candidate.corePos());
                    liveViewRendered = true;
                }
            }
        } finally {
            if (currentRecursionDepth == 0) {
                GL11.glStencilMask(0x00);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
            saved.restore();
        }
    }

    private record WorldRenderState(float fogStart, float fogEnd, FogShape fogShape, float[] fogColor,
                                    float[] shaderColor, ShaderInstance shader, Matrix4f projection,
                                    VertexSorting vertexSorting, int[] textures) {
        static WorldRenderState capture() {
            int[] textures = new int[12];
            for (int i = 0; i < textures.length; i++) textures[i] = RenderSystem.getShaderTexture(i);
            return new WorldRenderState(RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(),
                    RenderSystem.getShaderFogShape(), RenderSystem.getShaderFogColor().clone(),
                    RenderSystem.getShaderColor().clone(), RenderSystem.getShader(),
                    new Matrix4f(RenderSystem.getProjectionMatrix()), RenderSystem.getVertexSorting(), textures);
        }

        void restore() {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
            RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setProjectionMatrix(projection, vertexSorting);
            for (int i = 0; i < textures.length; i++) RenderSystem.setShaderTexture(i, textures[i]);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
    }
    //?}

    private static void logStatusOnce(String status) {
        if (!status.equals(lastDiagnosticStatus)) {
            lastDiagnosticStatus = status;
            fr.geomtech.universegate.UniverseGate.LOGGER.info("[SeamlessPortal] {}", status);
        }
    }

    //? if <1.21.11 {
    private static List<PortalCandidate> collectCandidates(ClientLevel activeLevel, Vec3 camPos, Vec3 camLook,
                                                           Matrix4f viewProjection) {
        ClientRemoteWorldManager manager = ClientRemoteWorldManager.getInstance();
        boolean liveViewEnabled = PortalViewOptions.enabled();
        List<PortalCandidate> result = new ArrayList<>();
        long now = System.nanoTime();
        VIEW_RETRY_AT.values().removeIf(deadline -> deadline - now <= 0);

        for (PortalRemoteEndpointPayload endpoint : manager.getActiveEndpoints()) {
            // During opening blackout, stay on classic PortalMembraneLayer / PortalOpeningParticles
            if (!endpoint.isActive() || endpoint.isClosed()) {
                continue;
            }
            BlockPos corePos = endpoint.corePos();
            if (!(activeLevel.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core) || !core.isActive()) {
                continue;
            }

            Direction sourceRight = PortalFrameDetector.find(activeLevel, corePos)
                    .map(PortalFrameDetector.FrameMatch::right)
                    .orElse(null);
            if (sourceRight == null) {
                continue;
            }

            // The vortex is visible from both sides. Only the controller side can show the destination.
            Direction frontNormal = resolveSourceFrontNormal(activeLevel, corePos, sourceRight);
            Vec3 portalCenter = PortalTransform.portalCenter(corePos);
            double signedFrontDist = (camPos.x - portalCenter.x) * frontNormal.getStepX()
                    + (camPos.z - portalCenter.z) * frontNormal.getStepZ();
            boolean frontSide = signedFrontDist >= 0.0D;
            Direction sourceNormal = frontSide ? frontNormal.getOpposite() : frontNormal;
            PortalTransform transform = new PortalTransform(
                    corePos,
                    sourceNormal,
                    endpoint.targetCorePos(),
                    endpoint.exitNormal()
            );
            if (!transform.isAperturePotentiallyVisible(camPos, camLook, MAX_SEAMLESS_DISTANCE)) {
                continue;
            }
            PortalDistortionRenderer.Bounds bounds = PortalDistortionRenderer.project(transform, camPos, viewProjection);
            if (bounds == null) continue;
            double intoPortal = camLook.x * sourceNormal.getStepX() + camLook.z * sourceNormal.getStepZ();
            boolean centered = intoPortal > 0.001D && transform.isWithinSourceAperture(
                    camPos.add(camLook.scale(Math.abs(signedFrontDist) / intoPortal)), 0.0D);

            double distSqr = portalCenter.distanceToSqr(camPos);
            ClientLevel targetLevel = null;
            ClientRemoteWorldManager.RemoteWorldEntry remoteEntry = null;
            if (liveViewEnabled && frontSide && distSqr < VEIL_OPAQUE_DISTANCE * VEIL_OPAQUE_DISTANCE
                    && !VIEW_RETRY_AT.containsKey(endpoint)) {
                int targetChunkX = endpoint.targetCorePos().getX() >> 4;
                int targetChunkZ = endpoint.targetCorePos().getZ() >> 4;
                if (endpoint.targetDim().equals(activeLevel.dimension())
                        && activeLevel.getChunkSource().hasChunk(targetChunkX, targetChunkZ)) {
                    targetLevel = activeLevel;
                } else {
                    remoteEntry = manager.getRemoteWorldEntry(endpoint.targetDim());
                    if (remoteEntry != null && manager.hasLoadedChunk(endpoint.targetDim(), endpoint.targetCorePos())) {
                        targetLevel = remoteEntry.level;
                    }
                }
            }
            result.add(new PortalCandidate(corePos, endpoint, transform, targetLevel, remoteEntry, distSqr,
                    PortalBuriedFloor.isBuried(activeLevel, corePos, sourceRight), bounds, centered, frontSide));
        }

        result.sort(Comparator.comparing(PortalCandidate::centered).reversed().thenComparingDouble(PortalCandidate::distSqr));
        return result;
    }

    private static boolean renderSinglePortal(Minecraft mc,
                                              LevelRenderer levelRenderer,
                                              Camera mainCamera,
                                              float partialTick,
                                              Matrix4f mainFrustumMatrix,
                                              Matrix4f mainProjectionMatrix,
                                              PortalCandidate candidate,
                                              boolean allowLiveView) {
        int parentStencil = currentRecursionDepth;
        int childStencil = parentStencil + 1;

        PortalTransform transform = candidate.transform();
        Vec3 mainCamPos = mainCamera.getPosition();

        // 1) Stencil increment on the circular aperture disc (respects existing depth buffer so blocks in front occlude!)
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(false);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(0xFF);
        GL11.glStencilFunc(GL11.GL_EQUAL, parentStencil, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);

        drawApertureDisc(transform, candidate.buried(), mainCamPos, mainFrustumMatrix, mainProjectionMatrix);

        GL11.glStencilMask(0x00);
        GL11.glStencilFunc(GL11.GL_EQUAL, childStencil, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        RenderSystem.colorMask(true, true, true, true);
        PortalScene scene = null;
        if (allowLiveView && candidate.targetLevel() != null) {
            currentRecursionDepth = childStencil;
            try {
                scene = renderDestination(mc, levelRenderer, mainCamera, partialTick, mainFrustumMatrix,
                        mainProjectionMatrix, candidate);
            } catch (RuntimeException t) {
                VIEW_RETRY_AT.put(candidate.endpoint(), System.nanoTime() + 5_000_000_000L);
                UniverseGate.LOGGER.warn("[SeamlessPortal] Render pass failed at {}", candidate.corePos(), t);
            } finally {
                currentRecursionDepth = parentStencil;
            }
        }

        // The stencil already excludes all local geometry in front of the portal.
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(0x00);
        GL11.glStencilFunc(GL11.GL_EQUAL, childStencil, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(false);
        boolean rendered = scene != null;
        float fade = (float) Mth.clamp((Math.sqrt(candidate.distSqr()) - VEIL_FADE_START)
                / (VEIL_OPAQUE_DISTANCE - VEIL_FADE_START), 0.0D, 1.0D);
        float approach = PortalVeilRenderer.approach(transform, mainCamPos);
        float opacity = rendered ? Mth.lerp(fade * fade * (3.0F - 2.0F * fade), 0.16F + 0.13F * approach, 1.0F) : 1.0F;
        double time = mc.level.getGameTime() + (double) partialTick;
        if (rendered) {
            Matrix4f apertureView = PortalVeilRenderer.apertureView(transform, transform.sourceCenter().subtract(mainCamPos),
                    mainFrustumMatrix);
            PortalDistortionRenderer.draw(scene.target(), candidate.bounds(), scene.screenRect(), scene.width(),
                    scene.height(), apertureView, mainProjectionMatrix, time, approach);
        }
        PortalVeilRenderer.draw(transform, mainCamPos, mainFrustumMatrix, mainProjectionMatrix, time, opacity, approach,
                !candidate.frontSide());
        RENDERED_SURFACES_THIS_FRAME.add(candidate.corePos());

        // 4) Restore exact portal-plane depth (MUST keep GL_DEPTH_TEST enabled so OpenGL writes z_portal!)
        //    and decrement stencil back to parentStencil so current-world BlockEntities/Particles/Translucent
        //    behind the portal are 100% occluded while the 3D ring & foreground entities draw cleanly on top.
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(0xFF);
        GL11.glStencilFunc(GL11.GL_EQUAL, childStencil, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_DECR);

        drawApertureDisc(transform, candidate.buried(), mainCamPos, mainFrustumMatrix, mainProjectionMatrix);

        // Restore GL state cleanly for the main pass
        RenderSystem.enableDepthTest();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        GL11.glStencilMask(0x00);
        GL11.glStencilFunc(GL11.GL_EQUAL, parentStencil, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        return rendered;
    }

    private record PortalScene(TextureTarget target, int width, int height, Vector4f screenRect) {}

    private static PortalScene renderDestination(Minecraft mc, LevelRenderer levelRenderer, Camera mainCamera,
                                             float partialTick, Matrix4f mainView, Matrix4f mainProjection,
                                             PortalCandidate candidate) {
        PortalTransform transform = candidate.transform();
        Vec3 remoteCamPos = transform.transformPoint(mainCamera.getPosition());
        Matrix4f remoteView = new Matrix4f(mainView).mul(transform.inverse().rotationMatrix());
        Vector4f worldPlane = transform.targetClipPlaneCameraRelative(remoteCamPos, -0.05F);
        Vector4f cameraPlane = ObliqueFrustumMath.worldPlaneToCameraSpace(remoteView, worldPlane);
        Matrix4f oblique = ObliqueFrustumMath.applyObliqueClipPlane(mainProjection, cameraPlane);
        Matrix4f projection = candidate.bounds().crop(oblique);

        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try (var stack = MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            try {
                TextureTarget target = PortalRenderTargetPool.destination(viewport.get(2), viewport.get(3));
                int width = Math.max(1, Mth.ceil(target.width * candidate.bounds().width() * 0.5F));
                int height = Math.max(1, Mth.ceil(target.height * candidate.bounds().height() * 0.5F));
                PortalRenderTargetPool.beginDestination(target, width, height);
                GL11.glDisable(GL11.GL_STENCIL_TEST);

                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_ALWAYS);
                RenderSystem.depthMask(true);
                drawVoidBackground();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                boolean rendered = renderRemoteWorldView(mc, levelRenderer, mainCamera, candidate, remoteCamPos,
                        transform.transformYaw(mainCamera.getYRot()), mainCamera.getXRot(), partialTick,
                        remoteView, projection);
                var bounds = candidate.bounds();
                float halfWidth = viewport.get(2) * 0.5F;
                float halfHeight = viewport.get(3) * 0.5F;
                Vector4f screenRect = new Vector4f(viewport.get(0) + (bounds.minX() + 1.0F) * halfWidth,
                        viewport.get(1) + (bounds.minY() + 1.0F) * halfHeight,
                        bounds.width() * halfWidth, bounds.height() * halfHeight);
                return rendered ? new PortalScene(target, width, height, screenRect) : null;
            } finally {
                PortalRenderTargetPool.endDestination();
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                RenderSystem.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            }
        }
    }

    private static boolean renderRemoteWorldView(Minecraft mc,
                                              LevelRenderer levelRenderer,
                                              Camera mainCamera,
                                              PortalCandidate candidate,
                                              Vec3 remoteCamPos,
                                              float remoteYaw,
                                              float remotePitch,
                                              float partialTick,
                                              Matrix4f remoteFrustumMatrix,
                                              Matrix4f obliqueProjection) {
        LevelRendererAccessor lr = (LevelRendererAccessor) levelRenderer;
        ClientLevel savedMcLevel = mc.level;
        ClientLevel savedLrLevel = lr.universegate$getLevel();

        RemoteRenderWorldState renderState = candidate.remoteEntry() != null
                ? candidate.remoteEntry().getOrCreateRenderState(mc) : localCompositeWorld(mc, candidate.targetLevel());
        if (renderState == null) return false;
        ViewArea targetViewArea = renderState.getViewArea();
        SectionRenderDispatcher targetDispatcher = renderState.getSectionRenderDispatcher();
        if (targetViewArea == null || targetDispatcher == null) return false;
        BlockPos targetCore = candidate.endpoint().targetCorePos();
        renderState.repositionCamera(targetCore.getX() + 0.5D, targetCore.getY() + 3.0D, targetCore.getZ() + 0.5D);

        Camera virtualCamera = new Camera();
        CameraAccessor camAcc = (CameraAccessor) virtualCamera;
        camAcc.universegate$setLevel(candidate.targetLevel());
        camAcc.universegate$setEntity(mainCamera.getEntity() != null ? mainCamera.getEntity() : mc.player);
        camAcc.universegate$setPosition(remoteCamPos.x, remoteCamPos.y, remoteCamPos.z);
        camAcc.universegate$setRotation(remoteYaw, remotePitch);

        Frustum remoteFrustum = new Frustum(remoteFrustumMatrix, obliqueProjection);
        remoteFrustum.prepare(remoteCamPos.x, remoteCamPos.y, remoteCamPos.z);

        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        float apertureDistance = (float) Math.sqrt(candidate.distSqr());
        BlockEntityRenderDispatcher beDispatcher = mc.getBlockEntityRenderDispatcher();

        try {
            // Block entity renderers and the light texture must see the destination world.
            mc.level = candidate.targetLevel();
            lr.universegate$setLevel(candidate.targetLevel());
            if (candidate.targetLevel() != savedMcLevel) {
                var lightTex = mc.gameRenderer.lightTexture();
                ((LightTextureAccessor) lightTex).universegate$setUpdateLightTexture(true);
                lightTex.updateLightTexture(partialTick);
            }

            ObjectArrayList<SectionRenderDispatcher.RenderSection> visible = new ObjectArrayList<>();
            PortalTransform transform = candidate.transform();
            for (SectionRenderDispatcher.RenderSection section : targetViewArea.sections) {
                if (section == null) {
                    continue;
                }
                BlockPos origin = section.getOrigin();
                Vec3 secCenter = new Vec3(origin.getX() + 8.0D, origin.getY() + 8.0D, origin.getZ() + 8.0D);
                if (secCenter.distanceToSqr(transform.targetCenter()) > TERRAIN_RADIUS * TERRAIN_RADIUS) {
                    continue;
                }
                if (transform.signedDistanceToTargetPlane(secCenter) < -16.0D) {
                    continue;
                }
                if (!remoteFrustum.isVisible(section.getBoundingBox())) {
                    continue;
                }
                if (!candidate.targetLevel().getChunkSource().hasChunk(origin.getX() >> 4, origin.getZ() >> 4)) {
                    continue;
                }
                visible.add(section);
            }

            visible.sort(Comparator.comparingDouble(sec -> {
                BlockPos o = sec.getOrigin();
                double dx = (o.getX() + 8.0D) - remoteCamPos.x;
                double dy = (o.getY() + 8.0D) - remoteCamPos.y;
                double dz = (o.getZ() + 8.0D) - remoteCamPos.z;
                return dx * dx + dy * dy + dz * dz;
            }));

            targetDispatcher.setCamera(remoteCamPos);
            RenderRegionCache regionCache = new RenderRegionCache();
            int syncCompiled = 0;
            for (SectionRenderDispatcher.RenderSection section : visible) {
                boolean needsBuild = section.isDirty()
                        || section.getCompiled() == SectionRenderDispatcher.CompiledSection.UNCOMPILED;
                if (syncCompiled == MAX_SYNC_SECTION_BUILDS_PER_FRAME) break;
                if (needsBuild && section.hasAllNeighbors()) {
                    try {
                        targetDispatcher.rebuildSectionSync(section, regionCache);
                    } catch (RuntimeException failure) {
                        // Vanilla's async rebuild would report this as a fatal crash on a later frame.
                        ModelBlockRenderer.clearCache();
                        ((SectionRenderDispatcherAccessor) targetDispatcher).universegate$getFixedBuffers().discardAll();
                        throw failure;
                    }
                    syncCompiled++;
                }
            }
            targetDispatcher.uploadAllPendingUploads();

            RenderSystem.setShaderFogColor(0.0F, 0.0F, 0.0F, 1.0F);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            RenderSystem.setShaderFogStart(apertureDistance + 22.0F);
            RenderSystem.setShaderFogEnd(apertureDistance + 45.0F);
            RenderSystem.setProjectionMatrix(obliqueProjection, VertexSorting.DISTANCE_TO_ORIGIN);

            // Render solid, cutout-mipped, and cutout terrain layers
            renderTerrain(visible, RenderType.solid(), remoteCamPos, remoteFrustumMatrix, obliqueProjection);
            renderTerrain(visible, RenderType.cutoutMipped(), remoteCamPos, remoteFrustumMatrix, obliqueProjection);
            renderTerrain(visible, RenderType.cutout(), remoteCamPos, remoteFrustumMatrix, obliqueProjection);

            // Render destination BlockEntities & Entities using vanilla's exact ModelViewStack +
            // identity PoseStack convention so vertex normals remain in world space (preventing
            // camera-pitch darkening on top faces) and with the destination dimension's Lighting setup.
            if (candidate.targetLevel().effects().constantAmbientLight()) {
                Lighting.setupNetherLevel();
            } else {
                Lighting.setupLevel();
            }
            Matrix4fStack mvStack = RenderSystem.getModelViewStack();
            mvStack.pushMatrix();
            mvStack.identity();
            mvStack.mul(remoteFrustumMatrix);
            RenderSystem.applyModelViewMatrix();
            try {
                PoseStack poseStack = new PoseStack();
                MultiBufferSource.BufferSource bufferSource = PortalRenderTargetPool.modelBuffers();

                beDispatcher.prepare(candidate.targetLevel(), virtualCamera, null);
                mc.getEntityRenderDispatcher().prepare(candidate.targetLevel(), virtualCamera, null);
                renderRemoteBlockEntities(candidate, beDispatcher, remoteCamPos, remoteFrustum, partialTick, poseStack, bufferSource);

                for (Entity entity : candidate.targetLevel().entitiesForRendering()) {
                    if (entity == mc.player && candidate.targetLevel() == savedMcLevel) {
                        continue;
                    }
                    // Players keep the whole preview range even when decorative entities are culled.
                    double detailRadiusSqr = entity instanceof Player ? TERRAIN_RADIUS * TERRAIN_RADIUS : DETAIL_RADIUS_SQR;
                    if (entity.distanceToSqr(transform.targetCenter()) > detailRadiusSqr) {
                        continue;
                    }
                    if (transform.signedDistanceToTargetPlane(entity.position()) < -0.5D) {
                        continue;
                    }
                    if (!remoteFrustum.isVisible(entity.getBoundingBoxForCulling())) {
                        continue;
                    }
                    lr.universegate$renderEntity(entity, remoteCamPos.x, remoteCamPos.y, remoteCamPos.z, partialTick, poseStack, bufferSource);
                }
                bufferSource.endBatch();
            } finally {
                PortalRenderTargetPool.discardModelBuffers();
                mvStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }

            // Render translucent terrain layer
            renderTerrain(visible, RenderType.translucent(), remoteCamPos, remoteFrustumMatrix, obliqueProjection);
            return true;
        } finally {
            mc.level = savedMcLevel;
            if (savedMcLevel != null && candidate.targetLevel() != savedMcLevel) {
                var lightTex = mc.gameRenderer.lightTexture();
                ((LightTextureAccessor) lightTex).universegate$setUpdateLightTexture(true);
                lightTex.updateLightTexture(partialTick);
            }
            beDispatcher.prepare(savedMcLevel, mainCamera, mc.hitResult);
            mc.getEntityRenderDispatcher().prepare(savedMcLevel, mainCamera, mc.crosshairPickEntity);
            RenderSystem.setProjectionMatrix(savedProj, VertexSorting.DISTANCE_TO_ORIGIN);
            if (savedMcLevel != null) {
                if (savedMcLevel.effects().constantAmbientLight()) {
                    Lighting.setupNetherLevel();
                } else {
                    Lighting.setupLevel();
                }
            }
            lr.universegate$setLevel(savedLrLevel);
        }
    }

    private static RemoteRenderWorldState localCompositeWorld(Minecraft mc, ClientLevel level) {
        if (localCompositeWorld != null && localCompositeWorld.getLevel() != level) {
            localCompositeWorld.dispose();
            localCompositeWorld = null;
        }
        if (localCompositeWorld == null) {
            localCompositeWorld = new RemoteRenderWorldState(level, Math.min(mc.options.getEffectiveRenderDistance(), 4));
        }
        return localCompositeWorld.ensureInitialized(mc) ? localCompositeWorld : null;
    }

    public static void onLocalSectionDirty(ClientLevel level, int x, int y, int z, boolean immediate) {
        if (localCompositeWorld != null && localCompositeWorld.getLevel() == level && !isRenderingPortalPass()) {
            localCompositeWorld.markSectionDirty(x, y, z, immediate);
        }
    }

    private static void renderTerrain(ObjectArrayList<SectionRenderDispatcher.RenderSection> sections, RenderType layer,
                                      Vec3 camera, Matrix4f view, Matrix4f projection) {
        // Draw the destination buffers without invoking the main-world renderer or a shader-pack pass.
        layer.setupRenderState();
        PortalRenderTargetPool.bindActiveDestination();
        var shader = RenderSystem.getShader();
        try {
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, projection, Minecraft.getInstance().getWindow());
            shader.apply();
            var offset = shader.CHUNK_OFFSET;
            boolean opaque = layer != RenderType.translucent();
            var sectionsInOrder = sections.listIterator(opaque ? 0 : sections.size());
            while (opaque ? sectionsInOrder.hasNext() : sectionsInOrder.hasPrevious()) {
                var section = opaque ? sectionsInOrder.next() : sectionsInOrder.previous();
                if (section.getCompiled().isEmpty(layer)) continue;
                BlockPos pos = section.getOrigin();
                if (offset != null) {
                    offset.set((float) (pos.getX() - camera.x), (float) (pos.getY() - camera.y), (float) (pos.getZ() - camera.z));
                    offset.upload();
                }
                VertexBuffer mesh = section.getBuffer(layer);
                mesh.bind();
                mesh.draw();
            }
            if (offset != null) offset.set(0F, 0F, 0F);
        } finally {
            shader.clear();
            VertexBuffer.unbind();
            layer.clearRenderState();
            PortalRenderTargetPool.bindActiveDestination();
        }
    }

    private static void renderRemoteBlockEntities(PortalCandidate candidate,
                                                  BlockEntityRenderDispatcher beDispatcher,
                                                  Vec3 remoteCamPos,
                                                  Frustum remoteFrustum,
                                                  float partialTick,
                                                  PoseStack poseStack,
                                                  MultiBufferSource.BufferSource bufferSource) {
        BlockPos targetCorePos = candidate.endpoint().targetCorePos();
        int centerChunkX = targetCorePos.getX() >> 4;
        int centerChunkZ = targetCorePos.getZ() >> 4;
        int chunkRadius = 2;
        PortalTransform transform = candidate.transform();

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = candidate.targetLevel().getChunkSource()
                        .getChunk(centerChunkX + dx, centerChunkZ + dz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos bePos = be.getBlockPos();
                    if (bePos.equals(targetCorePos) && be instanceof PortalCoreBlockEntity core) {
                        Direction right = transform.targetRight().getAxis() == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
                        poseStack.pushPose();
                        try {
                            poseStack.translate(bePos.getX() - remoteCamPos.x, bePos.getY() - remoteCamPos.y, bePos.getZ() - remoteCamPos.z);
                            PortalGateFootRenderer.render(core, right, poseStack, bufferSource);
                        } finally {
                            poseStack.popPose();
                        }
                        continue;
                    }
                    Vec3 beCenter = new Vec3(bePos.getX() + 0.5D, bePos.getY() + 0.5D, bePos.getZ() + 0.5D);
                    if (beCenter.distanceToSqr(transform.targetCenter()) > DETAIL_RADIUS_SQR) {
                        continue;
                    }
                    if (transform.signedDistanceToTargetPlane(beCenter) < -0.5D) {
                        continue;
                    }
                    if (!remoteFrustum.isVisible(new AABB(bePos).inflate(3.0D))) {
                        continue;
                    }
                    poseStack.pushPose();
                    poseStack.translate(bePos.getX() - remoteCamPos.x, bePos.getY() - remoteCamPos.y, bePos.getZ() - remoteCamPos.z);
                    try {
                        beDispatcher.render(be, partialTick, poseStack, bufferSource);
                    } catch (Throwable ignored) {
                    }
                    poseStack.popPose();
                }
            }
        }
    }

    private static void drawApertureDisc(PortalTransform transform, boolean buried,
                                         Vec3 camPos,
                                         Matrix4f frustumMatrix,
                                         Matrix4f projectionMatrix) {
        Vec3 center = transform.sourceCenter();
        double cx = center.x - camPos.x;
        double cy = center.y - camPos.y;
        double cz = center.z - camPos.z;

        double rx = transform.sourceRight().getStepX();
        double rz = transform.sourceRight().getStepZ();
        double radius = PortalTransform.APERTURE_RADIUS;
        // For a buried gate the triangle fan starts on the floor chord, so no stencil pixels land below it.
        double floorOffset = transform.sourceCorePos().getY() + 1.0D - center.y;
        double start = buried ? Math.asin(floorOffset / radius) : 0.0D;
        double sweep = buried ? Math.PI - 2.0D * start : 2.0D * Math.PI;
        float fanY = (float) (buried ? cy + floorOffset : cy);

        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();

        try {
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
            for (int i = 0; i < APERTURE_SEGMENTS; i++) {
                double a0 = start + sweep * i / APERTURE_SEGMENTS;
                double a1 = start + sweep * (i + 1) / APERTURE_SEGMENTS;
                float x0 = (float) (cx + Math.cos(a0) * radius * rx);
                float y0 = (float) (cy + Math.sin(a0) * radius);
                float z0 = (float) (cz + Math.cos(a0) * radius * rz);
                float x1 = (float) (cx + Math.cos(a1) * radius * rx);
                float y1 = (float) (cy + Math.sin(a1) * radius);
                float z1 = (float) (cz + Math.cos(a1) * radius * rz);
                builder.addVertex(frustumMatrix, (float) cx, fanY, (float) cz);
                builder.addVertex(frustumMatrix, x0, y0, z0);
                builder.addVertex(frustumMatrix, x1, y1, z1);
            }
            var mesh = builder.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        } finally {
            RenderSystem.enableCull();
            mvStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProj, VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }

    private static void drawVoidBackground() {
        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().identity(), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();

        try {
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            builder.addVertex(-1.0F, -1.0F, 1.0F).setColor(0xFF000000);
            builder.addVertex(1.0F, -1.0F, 1.0F).setColor(0xFF000000);
            builder.addVertex(1.0F, 1.0F, 1.0F).setColor(0xFF000000);
            builder.addVertex(-1.0F, -1.0F, 1.0F).setColor(0xFF000000);
            builder.addVertex(1.0F, 1.0F, 1.0F).setColor(0xFF000000);
            builder.addVertex(-1.0F, 1.0F, 1.0F).setColor(0xFF000000);
            var mesh = builder.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        } finally {
            RenderSystem.enableCull();
            mvStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProj, VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }

    private static Direction resolveSourceFrontNormal(ClientLevel level, BlockPos corePos, Direction sourceRight) {
        if (!level.dimension().equals(frontNormalCacheDim)) {
            FRONT_NORMAL_CACHE.clear();
            frontNormalCacheDim = level.dimension();
        }
        long now = level.getGameTime();
        CachedFrontNormal cached = FRONT_NORMAL_CACHE.get(corePos);
        if (cached != null && Math.abs(now - cached.gameTime()) < 20L) {
            return cached.frontNormal();
        }

        Direction frontNormal = fr.geomtech.universegate.PortalOrientationHelper.resolveFrontNormal(level, corePos, sourceRight);
        FRONT_NORMAL_CACHE.put(corePos.immutable(), new CachedFrontNormal(frontNormal, now));
        return frontNormal;
    }

    private record CachedFrontNormal(Direction frontNormal, long gameTime) {}

    private record PortalCandidate(
            BlockPos corePos,
            PortalRemoteEndpointPayload endpoint,
            PortalTransform transform,
            ClientLevel targetLevel,
            ClientRemoteWorldManager.RemoteWorldEntry remoteEntry,
            double distSqr,
            boolean buried,
            PortalDistortionRenderer.Bounds bounds,
            boolean centered,
            boolean frontSide
    ) {}
    //?}
}
