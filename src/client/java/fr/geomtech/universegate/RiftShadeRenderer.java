package fr.geomtech.universegate;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RiftShadeRenderer extends MobRenderer<RiftShadeEntity, RiftShadeModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "textures/entity/rift_shade.png");

    public RiftShadeRenderer(EntityRendererProvider.Context context) {
        super(context, new RiftShadeModel(context.bakeLayer(RiftShadeModel.LAYER_LOCATION)), 0.55F);
    }

    @Override
    public ResourceLocation getTextureLocation(RiftShadeEntity entity) {
        return TEXTURE;
    }
}
