package fr.geomtech.universegate.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.11 {
import com.mojang.blaze3d.systems.RenderSystem;
import fr.geomtech.universegate.seamless.client.PortalStencilRenderer;
import fr.geomtech.universegate.seamless.client.PortalTintAccess;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

//? if <1.21.11 {
@Mixin(RenderChunkRegion.class)
//?} else {
/*@Mixin(ClientLevel.class)
*///?}
public abstract class PortalChunkRegionTintMixin {
    //? if <1.21.11 {
    @Shadow @Final protected Level level;

    @Inject(method = "getBlockTint", at = @At("HEAD"), cancellable = true)
    private void universegate$usePortalRegionTint(BlockPos pos, ColorResolver resolver, CallbackInfoReturnable<Integer> cir) {
        if (RenderSystem.isOnRenderThread() && PortalStencilRenderer.isRenderingPortalPass()
                && level instanceof PortalTintAccess tintAccess) {
            cir.setReturnValue(tintAccess.universegate$getVanillaTint(pos, resolver));
        }
    }
    //?}
}
