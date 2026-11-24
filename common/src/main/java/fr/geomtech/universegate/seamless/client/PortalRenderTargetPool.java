package fr.geomtech.universegate.seamless.client;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

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
        if (lastStencilQueryValid && width == cachedWidth && height == cachedHeight) {
            return mainTargetHasStencil;
        }
        try {
            int stencilBits = 0;
            int fbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            if (fbo == 0) {
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
            cachedWidth = width;
            cachedHeight = height;
            lastStencilQueryValid = true;
            return mainTargetHasStencil;
        } catch (Throwable ignored) {
            mainTargetHasStencil = false;
            lastStencilQueryValid = true;
            return false;
        }
    }

    public static void invalidate() {
        lastStencilQueryValid = false;
        cachedWidth = -1;
        cachedHeight = -1;
    }

    public static void releaseAll() {
        invalidate();
    }
}
