package fr.geomtech.universegate.guide;

//? if >=1.21.11 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.function.BiConsumer;

/^*
 * 1.21.11 draws 3D content in menus as "picture in picture": the scene is rendered into its own
 * texture by a registered renderer, then blitted. The loaders register {@link Renderer} and set
 * {@link #SUBMIT}, since vanilla has no public way to submit a custom state.
 ^/
public final class GuideScenePip {
    /^* Set by the loader: hands a state to GuiGraphics' render state. ^/
    public static BiConsumer<GuiGraphics, PictureInPictureRenderState> SUBMIT;

    private GuideScenePip() {
    }

    static void submit(GuiGraphics g, GuideScene3D scene, GuideScene3D.Frame frame, int x0, int y0, int x1, int y1,
                       int clipX0, int clipY0, int clipX1, int clipY1) {
        if (SUBMIT == null) return;
        int cx0 = Math.max(x0, clipX0), cy0 = Math.max(y0, clipY0);
        int cx1 = Math.min(x1, clipX1), cy1 = Math.min(y1, clipY1);
        if (cx1 <= cx0 || cy1 <= cy0) return;
        ScreenRectangle scissor = new ScreenRectangle(cx0, cy0, cx1 - cx0, cy1 - cy0);
        SUBMIT.accept(g, new State(scene, frame, x0, y0, x1, y1, scissor,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor)));
    }

    public record State(GuideScene3D scene, GuideScene3D.Frame frame, int x0, int y0, int x1, int y1,
                        ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
        @Override
        public float scale() {
            return 1.0F;
        }
    }

    public static final class Renderer extends PictureInPictureRenderer<State> {
        public Renderer(MultiBufferSource.BufferSource buffers) {
            super(buffers);
        }

        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected void renderToTexture(State state, PoseStack pose) {
            pose.pushPose();
            float scale = state.frame().scale();
            pose.scale(scale, scale, scale);
            state.scene().drawWorld(pose, bufferSource, state.frame());
            bufferSource.endBatch();
            pose.popPose();
        }

        @Override
        protected String getTextureLabel() {
            return "universegate_guide_scene";
        }
    }
}
*///?}
