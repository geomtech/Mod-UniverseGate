package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RiftBeastRenderer extends MobRenderer<RiftBeastEntity, CowModel<RiftBeastEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "textures/entity/rift_beast.png");

    public RiftBeastRenderer(EntityRendererProvider.Context context) {
        super(context, new CowModel<>(context.bakeLayer(ModelLayers.COW)), 0.45F);
    }

    @Override
    public ResourceLocation getTextureLocation(RiftBeastEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(RiftBeastEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(0.76F, 2.85F, 0.72F);
    }
}
