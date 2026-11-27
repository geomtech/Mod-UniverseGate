package fr.geomtech.universegate.guide;

import net.minecraft.client.gui.GuiGraphics;

/** An animated guide scene, split in steps; {@code time} is in ticks from the start of the loop. */
interface SceneView {
    int stepCount();

    /** Total loop length in ticks. */
    int length();

    int stepAt(float time);

    int startOf(int index);

    int stepDuration(int index);

    /**
     * Draws the scene in the box. {@code yaw} turns it around the vertical axis (degrees); the clip
     * rectangle is the visible part of the page, for renderers that need it explicitly.
     */
    void render(GuiGraphics g, int x, int y, int w, int h, float time, float yaw,
                int clipX0, int clipY0, int clipX1, int clipY1);
}
