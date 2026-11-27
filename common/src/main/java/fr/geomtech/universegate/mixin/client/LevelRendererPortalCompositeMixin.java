package fr.geomtech.universegate.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.11 {
import fr.geomtech.universegate.seamless.client.PortalStencilRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

/** Draw onto the finished world framebuffer, after the shader pack has composited its world pass. */
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class LevelRendererPortalCompositeMixin {
    //? if <1.21.11 {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void universegate$compositePortals(DeltaTracker ticks, boolean outline, Camera camera,
                                               GameRenderer renderer, LightTexture light,
                                               Matrix4f view, Matrix4f projection, CallbackInfo ci) {
        PortalStencilRenderer.compositeAfterWorld((LevelRenderer) (Object) this, camera,
                ticks.getGameTimeDeltaPartialTick(false), view, projection);
    }
    //?}
}
