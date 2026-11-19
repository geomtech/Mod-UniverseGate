package fr.geomtech.universegate.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.UniverseGateDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
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
    private static final double UNIVERSEGATE$RIFT_SKY_MIN_BRIGHTNESS = 0.93D;

    @Unique
    private static final double UNIVERSEGATE$RIFT_SKY_SPARKLE_STRENGTH = 0.07D;

    @Unique
    private static final double UNIVERSEGATE$RIFT_SKY_SPARKLE_SPEED = 0.18D;

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
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getSkyColor(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 universegate$makeRiftSkySparkling(ClientLevel level, Vec3 cameraPos, float partialTick) {
        if (universegate$isInRift(level)) {
            double worldTime = level.getDayTime() + partialTick;
            double shimmer = (Math.sin(worldTime * UNIVERSEGATE$RIFT_SKY_SPARKLE_SPEED) + 1.0D) * 0.5D;
            double brightness = UNIVERSEGATE$RIFT_SKY_MIN_BRIGHTNESS + shimmer * UNIVERSEGATE$RIFT_SKY_SPARKLE_STRENGTH;
            return new Vec3(brightness, brightness, brightness);
        }
        return level.getSkyColor(cameraPos, partialTick);
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
