package fr.geomtech.universegate.mixin.client;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.geomtech.universegate.seamless.client.PortalRenderTargetPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.21.11 {
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
//?}

/**
 * Preserves the 8-bit hardware stencil attachment on {@link MainTarget} across every window resize
 * on MC 1.21.1 (since {@link RenderTarget#resize} invokes {@code RenderTarget.createBuffers}, not
 * {@code MainTarget.createFrameBuffer}).
 */
@Mixin(RenderTarget.class)
public abstract class RenderTargetStencilMixin {

    @Shadow
    public int width;

    @Shadow
    public int height;

    //? if <1.21.11 {
    @Shadow
    public int frameBufferId;

    @Shadow
    protected int depthBufferId;

    @Inject(method = "createBuffers", at = @At("TAIL"))
    private void universegate$attachMainStencilOnResize(int w, int h, boolean clearError, CallbackInfo ci) {
        if (!((Object) this instanceof MainTarget)) {
            return;
        }
        PortalRenderTargetPool.invalidate();
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
    }
    //?}
}
