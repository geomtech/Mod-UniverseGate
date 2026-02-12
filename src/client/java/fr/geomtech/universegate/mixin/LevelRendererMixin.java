package fr.geomtech.universegate.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.geomtech.universegate.UniverseGateDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void universegate$hideRiftClouds(PoseStack poseStack,
                                             Matrix4f modelViewMatrix,
                                             Matrix4f projectionMatrix,
                                             float partialTick,
                                             double camX,
                                             double camY,
                                             double camZ,
                                             CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.dimension().equals(UniverseGateDimensions.RIFT)) {
            ci.cancel();
        }
    }
}
