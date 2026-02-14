package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

public class ParabolaDishRenderer implements BlockEntityRenderer<ParabolaBlockEntity> {

    private static final float SPIN_DEGREES_PER_TICK = 4.0F;
    private static final float PHASE_DEGREES = 9.0F;
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);

    private final BlockRenderDispatcher blockRenderer;

    public ParabolaDishRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = Minecraft.getInstance().getBlockRenderer();
    }

    @Override
    public void render(ParabolaBlockEntity blockEntity,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       int packedOverlay) {
        if (blockEntity.getLevel() == null) return;

        BlockState state = blockEntity.getBlockState();
        if (!state.hasProperty(ParabolaBlock.PART) || state.getValue(ParabolaBlock.PART) != ParabolaBlock.Part.BASE) {
            return;
        }

        BlockState rotorState = state.setValue(ParabolaBlock.PART, ParabolaBlock.Part.ROTOR);
        float spinDegrees = 0.0F;

        if (isPortalNetworkBlock(blockEntity.getLevel().getBlockState(blockEntity.getBlockPos().below()))) {
            float time = blockEntity.getLevel().getGameTime() + partialTick;
            float phase = (blockEntity.getBlockPos().asLong() & 31L) * PHASE_DEGREES;
            spinDegrees = time * SPIN_DEGREES_PER_TICK + phase;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(new Quaternionf().rotationY(spinDegrees * DEG_TO_RAD));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        blockRenderer.renderSingleBlock(rotorState, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static boolean isPortalNetworkBlock(BlockState state) {
        return state.is(ModBlocks.PORTAL_CORE)
                || state.is(ModBlocks.PORTAL_FRAME)
                || state.is(ModBlocks.PORTAL_FIELD)
                || state.is(ModBlocks.PORTAL_KEYBOARD);
    }
}
