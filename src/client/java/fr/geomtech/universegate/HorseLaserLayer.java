package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.geomtech.universegate.mixin.HorseModelAccessor;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class HorseLaserLayer<T extends AbstractHorse> extends RenderLayer<T, HorseModel<T>> {
    private final ItemRenderer itemRenderer;

    public HorseLaserLayer(RenderLayerParent<T, HorseModel<T>> renderer, ItemRenderer itemRenderer) {
        super(renderer);
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T horse,
                       float limbSwing, float limbSwingAmount, float partialTick, float age, float yaw, float pitch) {
        if (!((LaserMount) horse).universegate$hasLaserHarness() || horse.isInvisible()) return;
        pose.pushPose();
        ((HorseModelAccessor) getParentModel()).universegate$getBody().translateAndRotate(pose);
        pose.translate(0.0D, -0.5D, 0.0D);
        pose.mulPose(Axis.XP.rotationDegrees(180.0F));
        pose.scale(0.7F, 0.7F, 0.7F);
        itemRenderer.renderStatic(new ItemStack(ModItems.HORSE_LASER_HARNESS), ItemDisplayContext.FIXED,
                light, OverlayTexture.NO_OVERLAY, pose, buffers, horse.level(), horse.getId());
        pose.popPose();
    }
}
