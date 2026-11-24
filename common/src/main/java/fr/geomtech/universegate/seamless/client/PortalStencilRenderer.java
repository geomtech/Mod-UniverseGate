package fr.geomtech.universegate.seamless.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
//? if <1.21.11 {
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.PortalGateBaseLayer;
import fr.geomtech.universegate.PortalGateRenderer;
import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.mixin.client.CameraAccessor;
import fr.geomtech.universegate.mixin.client.LevelRendererAccessor;
import fr.geomtech.universegate.mixin.client.LightTextureAccessor;
import fr.geomtech.universegate.seamless.math.ObliqueFrustumMath;
import fr.geomtech.universegate.seamless.math.PortalTransform;
import fr.geomtech.universegate.seamless.net.PortalRemoteEndpointPayload;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

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
 * {@link #wasRenderedSeamlesslyThisFrame(BlockPos)} so any unsupported environment (Iris shaderpack,
 * shadow pass, Sodium chunk pipeline mismatch, missing stencil bits, opening blackout, recursion cap)
 * falls back cleanly to the classic pixel-art membrane.</p>
 */
public final class PortalStencilRenderer {

    public static final int MAX_PORTALS_PER_FRAME = 4;
    public static final int MAX_RECURSION_DEPTH = 2;
    public static final double MAX_SEAMLESS_DISTANCE = 48.0D;
    private static final int APERTURE_SEGMENTS = 32;
    private static final int MAX_SYNC_SECTION_BUILDS_PER_FRAME = 16;

    private static final Set<BlockPos> RENDERED_SEAMLESSLY_THIS_FRAME = new HashSet<>();
    //? if <1.21.11 {
    private static final Map<BlockPos, CachedFrontNormal> FRONT_NORMAL_CACHE = new HashMap<>();
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> frontNormalCacheDim = null;
    //?}
    private static int currentRecursionDepth = 0;
    private static String lastDiagnosticStatus = "";

    private PortalStencilRenderer() {}

    public static void beginFrame() {
        if (currentRecursionDepth == 0) {
            RENDERED_SEAMLESSLY_THIS_FRAME.clear();
        }
    }

    public static boolean isRenderingPortalPass() {
        return currentRecursionDepth > 0;
    }

    public static int getCurrentRecursionDepth() {
        return currentRecursionDepth;
    }

    /**
     * Queried by {@code PortalMembraneLayer} to decide whether to skip drawing the opaque black
     * membrane disc (or fall back automatically when seamless rendering was skipped).
     */
    public static boolean wasRenderedSeamlesslyThisFrame(BlockPos corePos) {
        return corePos != null && RENDERED_SEAMLESSLY_THIS_FRAME.contains(corePos);
    }

    public static void onClientDisconnect() {
        RENDERED_SEAMLESSLY_THIS_FRAME.clear();
        //? if <1.21.11 {
        FRONT_NORMAL_CACHE.clear();
        frontNormalCacheDim = null;
        //?}
        currentRecursionDepth = 0;
        lastDiagnosticStatus = "";
        PortalRenderTargetPool.releaseAll();
    }

    /**
     * Renders visible seamless portals in the current world using hardware stencil masking and
     * Lengyel oblique near-plane clipping.
     */
    public static void renderActivePortals(LevelRenderer levelRenderer,
                                           Camera mainCamera,
                                           float partialTick,
                                           Matrix4f frustumMatrix,
                                           Matrix4f projectionMatrix) {
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
        SodiumIrisCompat.FallbackReason fallback = SodiumIrisCompat.currentFallbackReason(stencilOk);
        if (fallback != SodiumIrisCompat.FallbackReason.NONE) {
            logStatusOnce("fallback=" + fallback + " (stencilOk=" + stencilOk + ")");
            return;
        }

        //? if >=1.21.11 {
        /*// MC 1.21.11 uses Blaze3D RenderPass command buffers rather than immediate GL11 draw calls.
        // Remote worlds are still streamed, ticked, and promoted seamlessly on traversal, while
        // visual rendering cleanly falls back to PortalMembraneLayer unless a 1.21.11 RenderPass
        // stencil pipeline is active.
        return;
        *///?} else {
        Vec3 camPos = mainCamera.getPosition();
        Vec3 camLook = new Vec3(mainCamera.getLookVector());

        List<PortalCandidate> candidates = collectCandidates(activeLevel, camPos, camLook);
        if (candidates.isEmpty()) {
            int endpoints = ClientRemoteWorldManager.getInstance().getActiveEndpoints().size();
            if (endpoints > 0) {
                logStatusOnce("endpoints=" + endpoints + " but 0 visible/ready candidates");
            }
            return;
        }

        logStatusOnce("rendering " + candidates.size() + " seamless portal(s), stencilOk=" + stencilOk);

        if (currentRecursionDepth == 0) {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        }

        int renderedCount = 0;
        for (PortalCandidate candidate : candidates) {
            if (renderedCount >= MAX_PORTALS_PER_FRAME) {
                break;
            }
            if (renderSinglePortal(mc, levelRenderer, mainCamera, partialTick, frustumMatrix, projectionMatrix, candidate)) {
                RENDERED_SEAMLESSLY_THIS_FRAME.add(candidate.corePos());
                renderedCount++;
            }
        }

        if (currentRecursionDepth == 0) {
            GL11.glStencilMask(0x00);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
        //?}
    }

    private static void logStatusOnce(String status) {
        if (!status.equals(lastDiagnosticStatus)) {
            lastDiagnosticStatus = status;
            fr.geomtech.universegate.UniverseGate.LOGGER.info("[SeamlessPortal] {}", status);
        }
    }

    //? if <1.21.11 {
    private static List<PortalCandidate> collectCandidates(ClientLevel activeLevel, Vec3 camPos, Vec3 camLook) {
        ClientRemoteWorldManager manager = ClientRemoteWorldManager.getInstance();
        List<PortalCandidate> result = new ArrayList<>();

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

            ClientLevel targetLevel;
            ClientRemoteWorldManager.RemoteWorldEntry remoteEntry = null;
            int targetChunkX = endpoint.targetCorePos().getX() >> 4;
            int targetChunkZ = endpoint.targetCorePos().getZ() >> 4;

            if (endpoint.targetDim().equals(activeLevel.dimension())
                    && activeLevel.getChunkSource().hasChunk(targetChunkX, targetChunkZ)) {
                targetLevel = activeLevel;
            } else {
                remoteEntry = manager.getRemoteWorldEntry(endpoint.targetDim());
                if (remoteEntry == null || !manager.hasLoadedChunk(endpoint.targetDim(), endpoint.targetCorePos())) {
                    continue;
                }
                targetLevel = remoteEntry.level;
            }

            // Only render the live destination world on the FRONT face of the portal (the side
            // with the portal controller / keyboard). From the back face, PortalMembraneLayer
            // renders the classic pixel-art membrane instead.
            Direction frontNormal = resolveSourceFrontNormal(activeLevel, corePos, sourceRight);
            Vec3 portalCenter = PortalTransform.portalCenter(corePos);
            double signedFrontDist = (camPos.x - portalCenter.x) * frontNormal.getStepX()
                    + (camPos.z - portalCenter.z) * frontNormal.getStepZ();
            if (signedFrontDist <= 0.0D) {
                continue;
            }

            Direction sourceNormal = frontNormal.getOpposite();
            PortalTransform transform = new PortalTransform(
                    corePos,
                    sourceNormal,
                    endpoint.targetCorePos(),
                    endpoint.exitNormal()
            );
            if (!transform.isAperturePotentiallyVisible(camPos, camLook, MAX_SEAMLESS_DISTANCE)) {
                continue;
            }

            double distSqr = portalCenter.distanceToSqr(camPos);
            boolean sourceBuried = PortalGateBaseLayer.isBuried(activeLevel, corePos, sourceRight);
            result.add(new PortalCandidate(corePos, endpoint, transform, targetLevel, remoteEntry, distSqr, sourceBuried));
        }

        result.sort(Comparator.comparingDouble(PortalCandidate::distSqr));
        return result;
    }

    private static boolean renderSinglePortal(Minecraft mc,
                                              LevelRenderer levelRenderer,
                                              Camera mainCamera,
                                              float partialTick,
                                              Matrix4f mainFrustumMatrix,
                                              Matrix4f mainProjectionMatrix,
                                              PortalCandidate candidate) {
        int parentStencil = currentRecursionDepth;
        int childStencil = parentStencil + 1;

        PortalTransform transform = candidate.transform();
        Vec3 mainCamPos = mainCamera.getPosition();
        Vec3 remoteCamPos = transform.transformPoint(mainCamPos);
        float remoteYaw = transform.transformYaw(mainCamera.getYRot());
        float remotePitch = mainCamera.getXRot();

        // Build rotated view matrix for destination world
        Matrix4f invRot = transform.inverse().rotationMatrix();
        Matrix4f remoteFrustumMatrix = new Matrix4f(mainFrustumMatrix).mul(invRot);

        // Compute oblique near clipping plane in destination camera space so geometry behind destination portal is clipped
        Vector4f clipPlaneWorldRel = transform.targetClipPlaneCameraRelative(remoteCamPos, -0.05F);
        Vector4f clipPlaneCamSpace = ObliqueFrustumMath.worldPlaneToCameraSpace(remoteFrustumMatrix, clipPlaneWorldRel);
        Matrix4f obliqueProjection = ObliqueFrustumMath.applyObliqueClipPlane(mainProjectionMatrix, clipPlaneCamSpace);

        // 1) Stencil increment on the circular aperture disc (respects existing depth buffer so blocks in front occlude!)
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(false);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(0xFF);
        GL11.glStencilFunc(GL11.GL_EQUAL, parentStencil, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);

        drawApertureDisc(transform, candidate.sourceBuried(), mainCamPos, mainFrustumMatrix, mainProjectionMatrix);

        // 2) Clear depth to 1.0 AND fill destination sky/fog background color ONLY inside childStencil
        Vec3 skyColor = resolveDestinationBackgroundColor(candidate.targetLevel(), remoteCamPos, partialTick);
        int skyR = (int) Math.max(0, Math.min(255, Math.round(skyColor.x * 255.0D)));
        int skyG = (int) Math.max(0, Math.min(255, Math.round(skyColor.y * 255.0D)));
        int skyB = (int) Math.max(0, Math.min(255, Math.round(skyColor.z * 255.0D)));

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        GL11.glStencilMask(0x00);
        GL11.glStencilFunc(GL11.GL_EQUAL, childStencil, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        drawFarPlaneQuad(skyR, skyG, skyB);

        // 3) Render destination world sky, terrain, BlockEntities & entities inside childStencil
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);

        currentRecursionDepth = childStencil;
        try {
            renderRemoteWorldView(
                    mc,
                    levelRenderer,
                    mainCamera,
                    candidate,
                    remoteCamPos,
                    remoteYaw,
                    remotePitch,
                    partialTick,
                    remoteFrustumMatrix,
                    mainProjectionMatrix,
                    obliqueProjection
            );
        } catch (Throwable t) {
            UniverseGate.LOGGER.warn("[SeamlessPortal] Render pass failed at {}", candidate.corePos(), t);
        } finally {
            currentRecursionDepth = parentStencil;
        }

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

        drawApertureDisc(transform, candidate.sourceBuried(), mainCamPos, mainFrustumMatrix, mainProjectionMatrix);

        // Restore GL state cleanly for the main pass
        RenderSystem.enableDepthTest();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        GL11.glStencilMask(0x00);
        GL11.glStencilFunc(GL11.GL_EQUAL, parentStencil, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        return true;
    }

    private static void renderRemoteWorldView(Minecraft mc,
                                              LevelRenderer levelRenderer,
                                              Camera mainCamera,
                                              PortalCandidate candidate,
                                              Vec3 remoteCamPos,
                                              float remoteYaw,
                                              float remotePitch,
                                              float partialTick,
                                              Matrix4f remoteFrustumMatrix,
                                              Matrix4f mainProjectionMatrix,
                                              Matrix4f obliqueProjection) {
        LevelRendererAccessor lr = (LevelRendererAccessor) levelRenderer;
        ClientLevel savedMcLevel = mc.level;
        ClientLevel savedLrLevel = lr.universegate$getLevel();
        ViewArea savedViewArea = lr.universegate$getViewArea();
        SectionRenderDispatcher savedDispatcher = lr.universegate$getSectionRenderDispatcher();
        Frustum savedFrustum = lr.universegate$getCullingFrustum();
        ObjectArrayList<SectionRenderDispatcher.RenderSection> savedVisible = new ObjectArrayList<>(lr.universegate$getVisibleSections());

        Camera virtualCamera = new Camera();
        CameraAccessor camAcc = (CameraAccessor) virtualCamera;
        camAcc.universegate$setLevel(candidate.targetLevel());
        camAcc.universegate$setEntity(mainCamera.getEntity() != null ? mainCamera.getEntity() : mc.player);
        camAcc.universegate$setPosition(remoteCamPos.x, remoteCamPos.y, remoteCamPos.z);
        camAcc.universegate$setRotation(remoteYaw, remotePitch);

        Frustum remoteFrustum = new Frustum(remoteFrustumMatrix, obliqueProjection);
        remoteFrustum.prepare(remoteCamPos.x, remoteCamPos.y, remoteCamPos.z);

        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        float renderDistBlocks = mc.options.getEffectiveRenderDistance() * 16.0F;
        float darkenAmount = mc.gameRenderer.getDarkenWorldAmount(partialTick);
        BlockEntityRenderDispatcher beDispatcher = mc.getBlockEntityRenderDispatcher();

        try {
            ViewArea targetViewArea = savedViewArea;
            SectionRenderDispatcher targetDispatcher = savedDispatcher;

            // Swap mc.level temporarily so RiftSkyMixin, FogRenderer, LightTexture, and BlockEntityRenderers see destination dimension
            mc.level = candidate.targetLevel();
            if (candidate.targetLevel() != savedMcLevel) {
                var lightTex = mc.gameRenderer.lightTexture();
                ((LightTextureAccessor) lightTex).universegate$setUpdateLightTexture(true);
                lightTex.updateLightTexture(partialTick);
            }

            // Bind remote world's dedicated ViewArea/SectionRenderDispatcher when cross-dimension / far remote
            if (candidate.remoteEntry() != null) {
                RemoteRenderWorldState renderState = candidate.remoteEntry().getOrCreateRenderState(mc);
                if (renderState.getViewArea() == null || renderState.getSectionRenderDispatcher() == null) {
                    return;
                }
                BlockPos targetCore = candidate.endpoint().targetCorePos();
                renderState.repositionCamera(targetCore.getX() + 0.5D, targetCore.getY() + 3.0D, targetCore.getZ() + 0.5D);
                targetViewArea = renderState.getViewArea();
                targetDispatcher = renderState.getSectionRenderDispatcher();
                lr.universegate$setLevel(candidate.targetLevel());
                lr.universegate$setViewArea(targetViewArea);
                lr.universegate$setSectionRenderDispatcher(targetDispatcher);
            }

            if (targetViewArea == null || targetDispatcher == null) {
                return;
            }

            // Populate visibleSections directly from targetViewArea (never mutating active SectionOcclusionGraph!)
            ObjectArrayList<SectionRenderDispatcher.RenderSection> visible = lr.universegate$getVisibleSections();
            visible.clear();
            double maxDistSqr = (MAX_SEAMLESS_DISTANCE + 32.0D) * (MAX_SEAMLESS_DISTANCE + 32.0D);
            PortalTransform transform = candidate.transform();
            for (SectionRenderDispatcher.RenderSection section : targetViewArea.sections) {
                if (section == null) {
                    continue;
                }
                BlockPos origin = section.getOrigin();
                if (!candidate.targetLevel().getChunkSource().hasChunk(origin.getX() >> 4, origin.getZ() >> 4)) {
                    continue;
                }
                Vec3 secCenter = new Vec3(origin.getX() + 8.0D, origin.getY() + 8.0D, origin.getZ() + 8.0D);
                if (secCenter.distanceToSqr(remoteCamPos) > maxDistSqr) {
                    continue;
                }
                if (transform.signedDistanceToTargetPlane(secCenter) < -16.0D) {
                    continue;
                }
                if (!remoteFrustum.isVisible(section.getBoundingBox())) {
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
                if (needsBuild && section.hasAllNeighbors()) {
                    if (syncCompiled < MAX_SYNC_SECTION_BUILDS_PER_FRAME) {
                        targetDispatcher.rebuildSectionSync(section, regionCache);
                        syncCompiled++;
                    } else {
                        section.rebuildSectionAsync(targetDispatcher, regionCache);
                    }
                }
            }
            targetDispatcher.uploadAllPendingUploads();

            // Setup destination dimension fog & sky using MAIN (non-oblique!) projection so sky dome is never clipped.
            // Use destination background color + setupNoFog so NeoForge's onFogRender (which reads mc.player.level())
            // never applies Nether/source fog over the destination sky dome.
            Vec3 destBg = resolveDestinationBackgroundColor(candidate.targetLevel(), remoteCamPos, partialTick);
            float bgR = (float) destBg.x;
            float bgG = (float) destBg.y;
            float bgB = (float) destBg.z;
            RenderSystem.setShaderFogColor(bgR, bgG, bgB, 1.0F);
            FogRenderer.setupNoFog();
            RenderSystem.setProjectionMatrix(mainProjectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.depthMask(false);
            try {
                // Only invoke vanilla renderSky when player eye Y is above the destination horizon;
                // otherwise LevelRenderer.renderSky draws darkBuffer (black void) over the upper sky.
                double horizonY = candidate.targetLevel().getLevelData().getHorizonHeight(candidate.targetLevel());
                boolean safeForSkyDome = mc.player == null || mc.player.getEyePosition(partialTick).y >= horizonY;
                if (safeForSkyDome) {
                    levelRenderer.renderSky(remoteFrustumMatrix, mainProjectionMatrix, partialTick, virtualCamera, false, FogRenderer::setupNoFog);
                }
            } catch (Throwable ignored) {
            }

            // Switch to oblique projection & clean destination terrain fog for destination world geometry
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.setShaderFogColor(bgR, bgG, bgB, 1.0F);
            if (!candidate.targetLevel().dimensionType().hasSkyLight()) {
                float netherFogEnd = Math.min(renderDistBlocks * 0.5F, 72.0F);
                RenderSystem.setShaderFogStart(netherFogEnd * 0.05F);
                RenderSystem.setShaderFogEnd(netherFogEnd);
            } else {
                RenderSystem.setShaderFogStart(renderDistBlocks * 0.70F);
                RenderSystem.setShaderFogEnd(renderDistBlocks);
            }
            lr.universegate$setCullingFrustum(remoteFrustum);
            RenderSystem.setProjectionMatrix(obliqueProjection, VertexSorting.DISTANCE_TO_ORIGIN);

            // Render solid, cutout-mipped, and cutout terrain layers
            lr.universegate$renderSectionLayer(RenderType.solid(), remoteCamPos.x, remoteCamPos.y, remoteCamPos.z, remoteFrustumMatrix, obliqueProjection);
            lr.universegate$renderSectionLayer(RenderType.cutoutMipped(), remoteCamPos.x, remoteCamPos.y, remoteCamPos.z, remoteFrustumMatrix, obliqueProjection);
            lr.universegate$renderSectionLayer(RenderType.cutout(), remoteCamPos.x, remoteCamPos.y, remoteCamPos.z, remoteFrustumMatrix, obliqueProjection);

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
                MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

                beDispatcher.prepare(candidate.targetLevel(), virtualCamera, null);
                mc.getEntityRenderDispatcher().prepare(candidate.targetLevel(), virtualCamera, null);
                renderRemoteBlockEntities(candidate, beDispatcher, remoteCamPos, remoteFrustum, partialTick, poseStack, bufferSource);

                for (Entity entity : candidate.targetLevel().entitiesForRendering()) {
                    if (entity == mc.player && candidate.targetLevel() == savedMcLevel) {
                        continue;
                    }
                    if (entity.distanceToSqr(remoteCamPos) > MAX_SEAMLESS_DISTANCE * MAX_SEAMLESS_DISTANCE) {
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
                mvStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }

            // Render translucent terrain layer
            lr.universegate$renderSectionLayer(RenderType.translucent(), remoteCamPos.x, remoteCamPos.y, remoteCamPos.z, remoteFrustumMatrix, obliqueProjection);
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
                FogRenderer.setupColor(mainCamera, partialTick, savedMcLevel, mc.options.getEffectiveRenderDistance(), darkenAmount);
                FogRenderer.levelFogColor();
                FogRenderer.setupFog(mainCamera, FogRenderer.FogMode.FOG_TERRAIN, renderDistBlocks, false, partialTick);
            }
            lr.universegate$setLevel(savedLrLevel);
            lr.universegate$setViewArea(savedViewArea);
            lr.universegate$setSectionRenderDispatcher(savedDispatcher);
            lr.universegate$setCullingFrustum(savedFrustum);
            lr.universegate$getVisibleSections().clear();
            lr.universegate$getVisibleSections().addAll(savedVisible);
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
        int chunkRadius = 3;
        PortalTransform transform = candidate.transform();
        double maxDistSqr = MAX_SEAMLESS_DISTANCE * MAX_SEAMLESS_DISTANCE;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = candidate.targetLevel().getChunkSource()
                        .getChunk(centerChunkX + dx, centerChunkZ + dz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos bePos = be.getBlockPos();
                    // For the destination portal's own core, render ONLY its base (buried floor + sockets / plinth)
                    // so buried floors have no hole at the threshold, without drawing a duplicate ring/membrane.
                    if (bePos.equals(targetCorePos)) {
                        if (be instanceof PortalCoreBlockEntity targetCore) {
                            poseStack.pushPose();
                            poseStack.translate(bePos.getX() - remoteCamPos.x, bePos.getY() - remoteCamPos.y, bePos.getZ() - remoteCamPos.z);
                            try {
                                PortalGateRenderer.renderBaseOnly(targetCore, partialTick, poseStack, bufferSource);
                            } catch (Throwable ignored) {
                            }
                            poseStack.popPose();
                        }
                        continue;
                    }
                    Vec3 beCenter = new Vec3(bePos.getX() + 0.5D, bePos.getY() + 0.5D, bePos.getZ() + 0.5D);
                    if (beCenter.distanceToSqr(remoteCamPos) > maxDistSqr) {
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

    private static Vec3 resolveDestinationBackgroundColor(ClientLevel targetLevel, Vec3 remoteCamPos, float partialTick) {
        if (targetLevel.dimension().equals(fr.geomtech.universegate.UniverseGateDimensions.RIFT)) {
            double shimmer = (Math.sin((targetLevel.getDayTime() + partialTick) * 0.18D) + 1.0D) * 0.5D;
            double b = 0.93D + shimmer * 0.07D;
            return new Vec3(b, b, b);
        }
        Vec3 skyColor = targetLevel.getSkyColor(remoteCamPos, partialTick);
        if (!targetLevel.dimensionType().hasSkyLight()
                && skyColor.x < 0.02D && skyColor.y < 0.02D && skyColor.z < 0.02D) {
            int biomeFog = targetLevel.getBiome(BlockPos.containing(remoteCamPos)).value().getFogColor();
            return new Vec3(((biomeFog >> 16) & 0xFF) / 255.0D, ((biomeFog >> 8) & 0xFF) / 255.0D, (biomeFog & 0xFF) / 255.0D);
        }
        return skyColor;
    }

    private static void drawApertureDisc(PortalTransform transform,
                                         boolean sourceBuried,
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
        // When the portal base is buried in the floor, clip the bottom of the aperture disc at the
        // floor surface (coreY + 1.0) so PortalGateBaseLayer.renderBuried is never cut out in depth.
        double relMinY = sourceBuried ? (transform.sourceCorePos().getY() + 1.0D - center.y) : -radius;

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
            if (relMinY > -radius + 1.0E-4D && relMinY < radius) {
                double startAngle = Math.asin(relMinY / radius);
                double endAngle = Math.PI - startAngle;
                double step = (endAngle - startAngle) / APERTURE_SEGMENTS;
                for (int i = 0; i < APERTURE_SEGMENTS; i++) {
                    double a0 = startAngle + step * i;
                    double a1 = startAngle + step * (i + 1);
                    float x0 = (float) (cx + Math.cos(a0) * radius * rx);
                    float y0 = (float) (cy + Math.sin(a0) * radius);
                    float z0 = (float) (cz + Math.cos(a0) * radius * rz);

                    float x1 = (float) (cx + Math.cos(a1) * radius * rx);
                    float y1 = (float) (cy + Math.sin(a1) * radius);
                    float z1 = (float) (cz + Math.cos(a1) * radius * rz);

                    builder.addVertex(frustumMatrix, (float) cx, (float) cy, (float) cz);
                    builder.addVertex(frustumMatrix, x0, y0, z0);
                    builder.addVertex(frustumMatrix, x1, y1, z1);
                }
                // Closing chord triangle along y = cy + relMinY between left and right intersections
                double chordHalf = Math.sqrt(Math.max(0.0D, radius * radius - relMinY * relMinY));
                float yFloor = (float) (cy + relMinY);
                float xLeft = (float) (cx - chordHalf * rx);
                float zLeft = (float) (cz - chordHalf * rz);
                float xRight = (float) (cx + chordHalf * rx);
                float zRight = (float) (cz + chordHalf * rz);
                builder.addVertex(frustumMatrix, (float) cx, (float) cy, (float) cz);
                builder.addVertex(frustumMatrix, xLeft, yFloor, zLeft);
                builder.addVertex(frustumMatrix, xRight, yFloor, zRight);
            } else {
                for (int i = 0; i < APERTURE_SEGMENTS; i++) {
                    double a0 = (Math.PI * 2.0D * i) / APERTURE_SEGMENTS;
                    double a1 = (Math.PI * 2.0D * (i + 1)) / APERTURE_SEGMENTS;
                    float x0 = (float) (cx + Math.cos(a0) * radius * rx);
                    float y0 = (float) (cy + Math.sin(a0) * radius);
                    float z0 = (float) (cz + Math.cos(a0) * radius * rz);

                    float x1 = (float) (cx + Math.cos(a1) * radius * rx);
                    float y1 = (float) (cy + Math.sin(a1) * radius);
                    float z1 = (float) (cz + Math.cos(a1) * radius * rz);

                    builder.addVertex(frustumMatrix, (float) cx, (float) cy, (float) cz);
                    builder.addVertex(frustumMatrix, x0, y0, z0);
                    builder.addVertex(frustumMatrix, x1, y1, z1);
                }
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

    private static void drawFarPlaneQuad(int r, int g, int b) {
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
            builder.addVertex(-1.0F, -1.0F, 1.0F).setColor(r, g, b, 255);
            builder.addVertex(1.0F, -1.0F, 1.0F).setColor(r, g, b, 255);
            builder.addVertex(1.0F, 1.0F, 1.0F).setColor(r, g, b, 255);
            builder.addVertex(-1.0F, -1.0F, 1.0F).setColor(r, g, b, 255);
            builder.addVertex(1.0F, 1.0F, 1.0F).setColor(r, g, b, 255);
            builder.addVertex(-1.0F, 1.0F, 1.0F).setColor(r, g, b, 255);
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
            boolean sourceBuried
    ) {}
    //?}
}
