package fr.geomtech.universegate.mixin.client;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.geomtech.universegate.seamless.client.PortalRenderTargetPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.21.11 {
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
//?}

/**
 * Ensures the main framebuffer carries an 8-bit hardware stencil attachment on MC 1.21.1
 * (on MC 1.21.11, NeoForge enables stencil via ConfigureMainRenderTargetEvent and Fabric
 * falls back cleanly via {@link PortalRenderTargetPool}).
 */
@Mixin(MainTarget.class)
public abstract class MainTargetStencilMixin extends RenderTarget {

    protected MainTargetStencilMixin(boolean useDepth) {
        //? if >=1.21.11 {
        /*super("universegate_main_stencil_mixin", useDepth);
        *///?} else {
        super(useDepth);
        //?}
    }

    @Inject(method = "createFrameBuffer", at = @At("TAIL"))
    private void universegate$attachStencilBuffer(int width, int height, CallbackInfo ci) {
        PortalRenderTargetPool.invalidate();
        //? if <1.21.11 {
        if (this.frameBufferId <= 0 || this.depthBufferId <= 0) {
            return;
        }
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.frameBufferId);
            if (PortalRenderTargetPool.hasActiveStencilBuffer(this.width, this.height)) {
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                return;
            }
            GlStateManager._bindTexture(this.depthBufferId);
            GlStateManager._texImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL30.GL_DEPTH24_STENCIL8,
                    this.width,
                    this.height,
                    0,
                    GL30.GL_DEPTH_STENCIL,
                    GL30.GL_UNSIGNED_INT_24_8,
                    null
            );
            GlStateManager._glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL11.GL_TEXTURE_2D,
                    this.depthBufferId,
                    0
            );
            int status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                // Revert cleanly to depth-only if driver rejects packed depth-stencil
                GlStateManager._texImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        GL11.GL_DEPTH_COMPONENT,
                        this.width,
                        this.height,
                        0,
                        GL11.GL_DEPTH_COMPONENT,
                        GL11.GL_FLOAT,
                        null
                );
                GlStateManager._glFramebufferTexture2D(
                        GL30.GL_FRAMEBUFFER,
                        GL30.GL_DEPTH_ATTACHMENT,
                        GL11.GL_TEXTURE_2D,
                        this.depthBufferId,
                        0
                );
            }
            GlStateManager._bindTexture(0);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            PortalRenderTargetPool.markStencilRequested();
        } catch (Throwable ignored) {
            PortalRenderTargetPool.invalidate();
        }
        //?}
    }
}
