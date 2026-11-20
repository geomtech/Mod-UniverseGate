package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21.11 {
/*import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
*///?}

//? if >=1.21.11 {
/*// Babies use the baby cow model, as CowModel scaled young cows itself before 1.21.2.
public class RiftBeastRenderer extends AgeableMobRenderer<RiftBeastEntity, LivingEntityRenderState, CowModel> {
*///?} else {
public class RiftBeastRenderer extends MobRenderer<RiftBeastEntity, CowModel<RiftBeastEntity>> {
//?}

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "textures/entity/rift_beast.png");

    public RiftBeastRenderer(EntityRendererProvider.Context context) {
        //? if >=1.21.11 {
        /*super(context, new CowModel(context.bakeLayer(ModelLayers.COW)), new CowModel(context.bakeLayer(ModelLayers.COW_BABY)), 0.45F);
        *///?} else {
        super(context, new CowModel<>(context.bakeLayer(ModelLayers.COW)), 0.45F);
        //?}
    }

    //? if >=1.21.11 {
    /*@Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public ResourceLocation getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        poseStack.scale(0.76F, 2.85F, 0.72F);
    }
    *///?} else {
    @Override
    public ResourceLocation getTextureLocation(RiftBeastEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(RiftBeastEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(0.76F, 2.85F, 0.72F);
    }
    //?}
}
