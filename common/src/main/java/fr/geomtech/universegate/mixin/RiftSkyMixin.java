package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.UniverseGateDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21.11 {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.MoonPhase;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.ModifyArg;
*///?} else {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.Redirect;
//?}

/**
 * Sky of the Rift dimension: no clouds, a bright shimmering sky and the Rift moon.
 * <p>
 * Up to 1.21.5 everything is in {@code LevelRenderer}. Since 1.21.9 the sky is drawn by
 * {@code SkyRenderer} and the moon phases are sprites of the celestials atlas
 * ({@code textures/environment/celestial/rift_moon/*.png} in the 1.21.11 resources); the clouds are
 * hidden by the {@code minecraft:visual/cloud_color} attribute of the Rift dimension type.
 */
//? if >=1.21.11 {
/*@Mixin(SkyRenderer.class)
*///?} else {
@Mixin(LevelRenderer.class)
//?}
public abstract class RiftSkyMixin {

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

    @Unique
    private static double universegate$riftSkyBrightness(ClientLevel level, float partialTick) {
        double worldTime = level.getDayTime() + partialTick;
        double shimmer = (Math.sin(worldTime * UNIVERSEGATE$RIFT_SKY_SPARKLE_SPEED) + 1.0D) * 0.5D;
        return UNIVERSEGATE$RIFT_SKY_MIN_BRIGHTNESS + shimmer * UNIVERSEGATE$RIFT_SKY_SPARKLE_STRENGTH;
    }

    //? if >=1.21.11 {
    /*@Unique
    private @Nullable GpuBuffer universegate$riftMoonBuffer;

    /^* Same quads as SkyRenderer.buildMoonPhases, with the Rift moon sprites. ^/
    @Inject(method = "<init>", at = @At("TAIL"))
    private void universegate$buildRiftMoon(TextureManager textureManager, AtlasManager atlasManager, CallbackInfo ci) {
        TextureAtlas atlas = atlasManager.getAtlasOrThrow(AtlasIds.CELESTIALS);
        MoonPhase[] phases = MoonPhase.values();
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;

        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(phases.length * 4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            for (MoonPhase phase : phases) {
                TextureAtlasSprite sprite = atlas.getSprite(
                        ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_moon/" + phase.getSerializedName()));
                builder.addVertex(-1.0F, 0.0F, -1.0F).setUv(sprite.getU1(), sprite.getV1());
                builder.addVertex(1.0F, 0.0F, -1.0F).setUv(sprite.getU0(), sprite.getV1());
                builder.addVertex(1.0F, 0.0F, 1.0F).setUv(sprite.getU0(), sprite.getV0());
                builder.addVertex(-1.0F, 0.0F, 1.0F).setUv(sprite.getU1(), sprite.getV0());
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                universegate$riftMoonBuffer = RenderSystem.getDevice()
                        .createBuffer(() -> "UniverseGate Rift moon phases", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void universegate$closeRiftMoon(CallbackInfo ci) {
        if (universegate$riftMoonBuffer != null) {
            universegate$riftMoonBuffer.close();
            universegate$riftMoonBuffer = null;
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void universegate$makeRiftSkySparkling(ClientLevel level, float partialTick, Camera camera,
                                                   SkyRenderState state, CallbackInfo ci) {
        if (universegate$isInRift(level)) {
            float brightness = (float) universegate$riftSkyBrightness(level, partialTick);
            state.skyColor = ARGB.colorFromFloat(1.0F, brightness, brightness, brightness);
        }
    }

    @ModifyArg(
            method = "renderMoon",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderPass;setVertexBuffer(ILcom/mojang/blaze3d/buffers/GpuBuffer;)V"
            ),
            index = 1
    )
    private GpuBuffer universegate$useRiftMoon(GpuBuffer moonBuffer) {
        if (universegate$riftMoonBuffer != null && universegate$isInRift(Minecraft.getInstance().level)) {
            return universegate$riftMoonBuffer;
        }
        return moonBuffer;
    }
    *///?} else {
    @Unique
    private static final ResourceLocation UNIVERSEGATE$VANILLA_MOON = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/environment/moon_phases.png");

    @Unique
    private static final ResourceLocation UNIVERSEGATE$RIFT_MOON = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "textures/environment/rift_moon_phases.png");

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
            double brightness = universegate$riftSkyBrightness(level, partialTick);
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
    //?}
}
