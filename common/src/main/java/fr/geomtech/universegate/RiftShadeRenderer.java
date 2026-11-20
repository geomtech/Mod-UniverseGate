package fr.geomtech.universegate;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21.11 {
/*import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
*///?}

//? if >=1.21.11 {
/*public class RiftShadeRenderer extends MobRenderer<RiftShadeEntity, HumanoidRenderState, RiftShadeModel> {
*///?} else {
public class RiftShadeRenderer extends MobRenderer<RiftShadeEntity, RiftShadeModel> {
//?}

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "textures/entity/rift_shade.png");

    public RiftShadeRenderer(EntityRendererProvider.Context context) {
        super(context, new RiftShadeModel(context.bakeLayer(RiftShadeModel.LAYER_LOCATION)), 0.55F);
    }

    //? if >=1.21.11 {
    /*@Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    // Humanoid animation inputs (arm poses, attack swing, crouching), without the extra layers
    // that HumanoidMobRenderer adds: the 1.21.1 MobRenderer had none either.
    @Override
    public void extractRenderState(RiftShadeEntity entity, HumanoidRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTick, this.itemModelResolver);
    }

    @Override
    public ResourceLocation getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }
    *///?} else {
    @Override
    public ResourceLocation getTextureLocation(RiftShadeEntity entity) {
        return TEXTURE;
    }
    //?}
}
