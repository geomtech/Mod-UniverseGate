package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class MeteorologicalControllerBeamRenderer implements BlockEntityRenderer<MeteorologicalControllerBlockEntity> {

    private static final ResourceLocation BEAM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/beacon_beam.png");
    private static final int BEAM_COLOR = 0xB5F0FF;

    public MeteorologicalControllerBeamRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MeteorologicalControllerBlockEntity blockEntity,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       int packedOverlay) {
        if (!blockEntity.isBeamPhaseActive()) return;

        BlockPos catalystPos = blockEntity.getActiveCatalystPosForRender();
        Level level = blockEntity.getLevel();
        if (catalystPos == null || level == null) return;

        int beamHeight = level.getMaxBuildHeight() - catalystPos.getY();
        if (beamHeight <= 0) return;

        BlockPos controllerPos = blockEntity.getBlockPos();

        poseStack.pushPose();
        poseStack.translate(
                catalystPos.getX() - controllerPos.getX(),
                catalystPos.getY() - controllerPos.getY(),
                catalystPos.getZ() - controllerPos.getZ()
        );

        BeaconRenderer.renderBeaconBeam(
                poseStack,
                buffer,
                BEAM_TEXTURE,
                partialTick,
                1.0F,
                level.getGameTime(),
                1,
                beamHeight,
                BEAM_COLOR,
                0.22F,
                0.26F
        );

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(MeteorologicalControllerBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
