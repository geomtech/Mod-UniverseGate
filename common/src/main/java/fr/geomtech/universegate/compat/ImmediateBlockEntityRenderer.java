package fr.geomtech.universegate.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;

//? if >=1.21.11 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
*///?}

/**
 * Block entity renderer written the 1.21.1 way: {@link #render} draws straight into a
 * {@link MultiBufferSource}.
 * <p>
 * Since 1.21.9 block entity renderers extract a render state and submit their geometry to a
 * {@code SubmitNodeCollector}. On those versions this class keeps the block entity in the
 * render state, runs {@link #render} with a {@link RecordingBufferSource} and submits what was
 * drawn as custom geometry. Renderers that need other kinds of submissions (items, blocks,
 * beacon beams) get the collector from {@link RecordingBufferSource#collector()}.
 */
//? if >=1.21.11 {
/*public abstract class ImmediateBlockEntityRenderer<T extends BlockEntity>
        implements BlockEntityRenderer<T, ImmediateBlockEntityRenderer.State<T>> {

    public static final class State<T> extends BlockEntityRenderState {
        @Nullable T blockEntity;
        float partialTick;
    }

    @Override
    public State<T> createRenderState() {
        return new State<>();
    }

    @Override
    public void extractRenderState(T blockEntity, State<T> state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPos, crumblingOverlay);
        state.blockEntity = blockEntity;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(State<T> state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        T blockEntity = state.blockEntity;
        state.blockEntity = null;
        if (blockEntity == null) return;

        RecordingBufferSource buffers = new RecordingBufferSource(collector, poseStack);
        render(blockEntity, state.partialTick, poseStack, buffers, state.lightCoords, OverlayTexture.NO_OVERLAY);
        buffers.submit();
    }

    public abstract void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                                int packedLight, int packedOverlay);

    /^* {@code shouldRenderOffScreen(T)} lost its parameter in 1.21.9. ^/
    @Override
    public boolean shouldRenderOffScreen() {
        return rendersOffScreen();
    }

    protected boolean rendersOffScreen() {
        return false;
    }
}
*///?} else {
public abstract class ImmediateBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {

    @Override
    public abstract void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                                int packedLight, int packedOverlay);

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return rendersOffScreen();
    }

    protected boolean rendersOffScreen() {
        return false;
    }
}
//?}
