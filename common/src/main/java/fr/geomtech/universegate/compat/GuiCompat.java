package fr.geomtech.universegate.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

//? if >=1.21.11 {
/*import net.minecraft.client.renderer.RenderPipelines;
*///?}

/**
 * GUI texture drawing with the 1.21.1 {@code GuiGraphics.blit} arguments. Since 1.21.6 blit takes a
 * render pipeline, and the old argument lists bind to an unrelated overload (corner coordinates
 * and normalized UVs), so calls must not be left as they were.
 */
public final class GuiCompat {
    private GuiCompat() {
    }

    /** Draws a {@code width}x{@code height} region at (u, v) of a {@code textureWidth}x{@code textureHeight} texture. */
    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v,
                            int width, int height, int textureWidth, int textureHeight) {
        //? if >=1.21.11 {
        /*graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        *///?} else {
        graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        //?}
    }

    /** Same as {@link #blit(GuiGraphics, ResourceLocation, int, int, float, float, int, int, int, int)} for a 256x256 texture. */
    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        blit(graphics, texture, x, y, u, v, width, height, 256, 256);
    }
}
