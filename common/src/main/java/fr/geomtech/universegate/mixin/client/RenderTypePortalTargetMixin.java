package fr.geomtech.universegate.mixin.client;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.11 {
import com.mojang.blaze3d.vertex.MeshData;
import fr.geomtech.universegate.seamless.client.PortalRenderTargetPool;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

@Mixin(RenderType.class)
public abstract class RenderTypePortalTargetMixin {
    //? if <1.21.11 {
    @Inject(method = "draw", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void universegate$keepPortalModelInDestination(MeshData mesh, CallbackInfo ci) {
        // Modded layer setup can bind another framebuffer before the mesh is submitted.
        PortalRenderTargetPool.bindActiveDestination();
    }
    //?}
}
