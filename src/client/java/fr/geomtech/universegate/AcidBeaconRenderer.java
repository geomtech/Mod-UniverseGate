package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class AcidBeaconRenderer implements BlockEntityRenderer<AcidBeaconBlockEntity> {
    private static final ResourceLocation BEAM = ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
    public AcidBeaconRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(AcidBeaconBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (entity.getLevel() == null || !entity.getBlockState().getValue(AcidNeonLampBlock.LIT)) return;
        BeaconRenderer.renderBeaconBeam(pose, buffers, BEAM, partialTick, 1.0F, entity.getLevel().getGameTime(),
                1, entity.getLevel().getMaxBuildHeight() - entity.getBlockPos().getY(), 0xFF9AFF60, 0.2F, 0.25F);
    }

    @Override public boolean shouldRenderOffScreen(AcidBeaconBlockEntity entity) { return true; }
    @Override public int getViewDistance() { return 192; }
}
