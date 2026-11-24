package fr.geomtech.universegate.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.geomtech.universegate.client.AcidRainClientState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class AcidRainFogMixin {

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void universegate$applyAcidRainFog(Camera camera,
                                                       FogRenderer.FogMode fogMode,
                                                       float farPlaneDistance,
                                                       boolean nearFog,
                                                       float partialTick,
                                                       CallbackInfo ci) {
        float strength = AcidRainClientState.intensity(partialTick);
        if (strength <= 0.0F || fogMode != FogRenderer.FogMode.FOG_TERRAIN || camera.getFluidInCamera() != FogType.NONE) return;

        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Mth.lerp(strength, start, Math.min(start, 4.0F)));
        RenderSystem.setShaderFogEnd(Mth.lerp(strength, end, Math.min(end, 28.0F)));
    }

    @Inject(method = "levelFogColor", at = @At("TAIL"))
    private static void universegate$tintAcidRainFog(CallbackInfo ci) {
        float strength = AcidRainClientState.intensity(0.0F);
        if (strength <= 0.0F || Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) return;
        float[] color = RenderSystem.getShaderFogColor();
        RenderSystem.setShaderFogColor(Mth.lerp(strength, color[0], 0.58F),
                Mth.lerp(strength, color[1], 0.72F), Mth.lerp(strength, color[2], 0.34F));
    }
}
