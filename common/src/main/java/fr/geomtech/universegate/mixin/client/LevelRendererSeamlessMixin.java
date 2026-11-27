package fr.geomtech.universegate.mixin.client;

import fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager;
import fr.geomtech.universegate.seamless.client.PortalStencilRenderer;
import fr.geomtech.universegate.seamless.client.RemoteRenderWorldState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.11 {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import org.joml.Vector4f;
*///?} else {
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.geomtech.universegate.ModBlocks;
import java.util.Collection;
//?}

/**
 * Hooks {@link LevelRenderer#renderLevel} on both MC 1.21.1 and 1.21.11 to drive seamless
 * recursive stencil portal rendering outside {@code PortalGateRenderer}'s block-entity recording pass,
 * and prevents remote {@code ViewArea} sections / global {@code BlockEntity} instances from leaking
 * into the active world's render graph.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSeamlessMixin {

    @Inject(method = "addRecentlyCompiledSection", at = @At("HEAD"), cancellable = true)
    private void universegate$isolateRemoteCompiledSections(SectionRenderDispatcher.RenderSection section, CallbackInfo ci) {
        if (RemoteRenderWorldState.isRemoteRenderSection(section)) {
            ci.cancel();
        }
    }

    //? if <1.21.11 {
    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void universegate$hidePortalFieldOutline(PoseStack pose, VertexConsumer vertices, Entity cameraEntity,
                                                     double x, double y, double z, BlockPos pos, BlockState state,
                                                     CallbackInfo ci) {
        if (state.is(ModBlocks.PORTAL_FIELD)) ci.cancel();
    }

    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void universegate$dirtyPortalCompositeSection(int x, int y, int z, boolean immediate, CallbackInfo ci) {
        ClientLevel level = ((LevelRendererAccessor) this).universegate$getLevel();
        PortalStencilRenderer.onLocalSectionDirty(level, x, y, z, immediate);
    }

    @Inject(method = "updateGlobalBlockEntities", at = @At("HEAD"), cancellable = true)
    private void universegate$isolateRemoteGlobalBlockEntities(Collection<BlockEntity> toRemove,
                                                               Collection<BlockEntity> toAdd,
                                                               CallbackInfo ci) {
        // Dedicated portal sections also compile when the destination is in the active ClientLevel.
        if (RenderSystem.isOnRenderThread() && PortalStencilRenderer.isRenderingPortalPass()) {
            ci.cancel();
            return;
        }
        for (BlockEntity be : toAdd) {
            if (ClientRemoteWorldManager.isRemoteLevel(be.getLevel())) {
                ci.cancel();
                return;
            }
        }
        for (BlockEntity be : toRemove) {
            if (ClientRemoteWorldManager.isRemoteLevel(be.getLevel())) {
                ci.cancel();
                return;
            }
        }
    }
    //?}

    //? if >=1.21.11 {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void universegate$beginSeamlessFrame(GraphicsResourceAllocator allocator,
                                                 DeltaTracker deltaTracker,
                                                 boolean renderBlockOutline,
                                                 Camera camera,
                                                 Matrix4f frustumMatrix,
                                                 Matrix4f projectionMatrix,
                                                 Matrix4f cullingProjectionMatrix,
                                                 GpuBufferSlice fogBuffer,
                                                 Vector4f fogColor,
                                                 boolean renderSky,
                                                 CallbackInfo ci) {
        PortalStencilRenderer.beginFrame();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void universegate$renderSeamlessPortals(GraphicsResourceAllocator allocator,
                                                    DeltaTracker deltaTracker,
                                                    boolean renderBlockOutline,
                                                    Camera camera,
                                                    Matrix4f frustumMatrix,
                                                    Matrix4f projectionMatrix,
                                                    Matrix4f cullingProjectionMatrix,
                                                    GpuBufferSlice fogBuffer,
                                                    Vector4f fogColor,
                                                    boolean renderSky,
                                                    CallbackInfo ci) {
        PortalStencilRenderer.renderActivePortals(
                (LevelRenderer) (Object) this,
                camera,
                deltaTracker.getGameTimeDeltaPartialTick(false),
                frustumMatrix,
                projectionMatrix
        );
    }
    *///?} else {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void universegate$beginSeamlessFrame(DeltaTracker deltaTracker,
                                                 boolean renderBlockOutline,
                                                 Camera camera,
                                                 GameRenderer gameRenderer,
                                                 LightTexture lightTexture,
                                                 Matrix4f frustumMatrix,
                                                 Matrix4f projectionMatrix,
                                                 CallbackInfo ci) {
        PortalStencilRenderer.beginFrame();
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    ordinal = 2,
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void universegate$renderSeamlessPortals(DeltaTracker deltaTracker,
                                                    boolean renderBlockOutline,
                                                    Camera camera,
                                                    GameRenderer gameRenderer,
                                                    LightTexture lightTexture,
                                                    Matrix4f frustumMatrix,
                                                    Matrix4f projectionMatrix,
                                                    CallbackInfo ci) {
        PortalStencilRenderer.renderActivePortals(
                (LevelRenderer) (Object) this,
                camera,
                deltaTracker.getGameTimeDeltaPartialTick(false),
                frustumMatrix,
                projectionMatrix
        );
    }
    //?}
}
