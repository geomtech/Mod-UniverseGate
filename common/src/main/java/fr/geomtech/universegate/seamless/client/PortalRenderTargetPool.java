package fr.geomtech.universegate.seamless.client;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
//? if <1.21.11 {
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
//?}

/**
 * Tracks OpenGL stencil availability on the active framebuffer and manages any pooled offscreen
 * depth-stencil buffers used by the recursive portal renderer.
 */
public final class PortalRenderTargetPool {

    private static volatile boolean stencilRequested = false;
    private static volatile boolean lastStencilQueryValid = false;
    private static volatile boolean mainTargetHasStencil = false;
    private static int cachedWidth = -1;
    private static int cachedHeight = -1;
    private static int cachedFramebuffer = -1;
    //? if <1.21.11 {
    private static TextureTarget destination;
    private static TextureTarget drawingDestination;
    private static int destinationWidth;
    private static int destinationHeight;
    private static ByteBufferBuilder modelBytes;

    static TextureTarget destination(int width, int height) {
        int halfWidth = Math.max(1, (width + 1) / 2);
        int halfHeight = Math.max(1, (height + 1) / 2);
        if (destination == null) {
            destination = new TextureTarget(halfWidth, halfHeight, true, Minecraft.ON_OSX);
            destination.setFilterMode(GL11.GL_LINEAR);
        } else if (destination.width != halfWidth || destination.height != halfHeight) {
            destination.resize(halfWidth, halfHeight, Minecraft.ON_OSX);
            destination.setFilterMode(GL11.GL_LINEAR);
        }
        return destination;
    }

    static void beginDestination(TextureTarget target, int width, int height) {
        drawingDestination = target;
        destinationWidth = width;
        destinationHeight = height;
        bindActiveDestination();
    }

    public static void bindActiveDestination() {
        if (drawingDestination == null) return;
        drawingDestination.bindWrite(false);
        RenderSystem.viewport(0, 0, destinationWidth, destinationHeight);
    }

    static void endDestination() {
        drawingDestination = null;
    }

    static MultiBufferSource.BufferSource modelBuffers() {
        if (modelBytes == null) modelBytes = new ByteBufferBuilder(262144);
        return MultiBufferSource.immediate(modelBytes);
    }

    static void discardModelBuffers() {
        if (modelBytes != null) modelBytes.discard();
    }
    //?}

    private PortalRenderTargetPool() {}

    public static void markStencilRequested() {
        stencilRequested = true;
        lastStencilQueryValid = false;
    }

    public static boolean isStencilRequested() {
        return stencilRequested;
    }

    /**
     * Checks on the render thread whether the currently bound framebuffer has stencil bits (> 0).
     */
    public static boolean hasActiveStencilBuffer(int width, int height) {
        return hasStencilBuffer(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), width, height);
    }

    public static boolean hasStencilBuffer(int framebuffer, int width, int height) {
        if (lastStencilQueryValid && width == cachedWidth && height == cachedHeight && framebuffer == cachedFramebuffer) {
            return mainTargetHasStencil;
        }
        int bound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            if (bound != framebuffer) GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            int stencilBits = 0;
            if (framebuffer == 0) {
                stencilBits = GL11.glGetInteger(GL11.GL_STENCIL_BITS);
            } else {
                int type = GL30.glGetFramebufferAttachmentParameteri(
                        GL30.GL_DRAW_FRAMEBUFFER,
                        GL30.GL_STENCIL_ATTACHMENT,
                        GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
                );
                if (type != GL11.GL_NONE) {
                    stencilBits = GL30.glGetFramebufferAttachmentParameteri(
                            GL30.GL_DRAW_FRAMEBUFFER,
                            GL30.GL_STENCIL_ATTACHMENT,
                            GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE
                    );
                }
            }
            mainTargetHasStencil = stencilBits > 0;
        } catch (Throwable ignored) {
            mainTargetHasStencil = false;
        } finally {
            if (bound != framebuffer) GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, bound);
            cachedWidth = width;
            cachedHeight = height;
            cachedFramebuffer = framebuffer;
            lastStencilQueryValid = true;
        }
        return mainTargetHasStencil;
    }

    public static void invalidate() {
        lastStencilQueryValid = false;
        cachedWidth = -1;
        cachedHeight = -1;
    }

    public static void releaseAll() {
        //? if <1.21.11 {
        if (destination != null) {
            destination.destroyBuffers();
            destination = null;
        }
        if (modelBytes != null) {
            modelBytes.close();
            modelBytes = null;
        }
        //?}
        invalidate();
    }
}
