package fr.geomtech.universegate.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.UniverseGateDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Unique
    private static final ResourceLocation UNIVERSEGATE$VANILLA_MOON = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/environment/moon_phases.png");

    @Unique
    private static final ResourceLocation UNIVERSEGATE$RIFT_MOON = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "textures/environment/rift_moon_phases.png");

    @Unique
    private static boolean universegate$isInRift(ClientLevel level) {
        return level != null && level.dimension().equals(UniverseGateDimensions.RIFT);
    }

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
        if (universegate$isInRift(level)) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"
            )
    )
    private void universegate$swapMoonTextureInRift(int textureSlot, ResourceLocation texture) {
        ClientLevel level = Minecraft.getInstance().level;
        if (universegate$isInRift(level) && UNIVERSEGATE$VANILLA_MOON.equals(texture)) {
            RenderSystem.setShaderTexture(textureSlot, UNIVERSEGATE$RIFT_MOON);
            return;
        }
        RenderSystem.setShaderTexture(textureSlot, texture);
    }
}
