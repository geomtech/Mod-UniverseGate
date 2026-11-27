package fr.geomtech.universegate.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.11 {
import com.mojang.blaze3d.systems.RenderSystem;
import fr.geomtech.universegate.seamless.client.PortalStencilRenderer;
import fr.geomtech.universegate.seamless.client.PortalTintAccess;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.color.block.BlockTintCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

@Mixin(value = ClientLevel.class, priority = 900)
public abstract class ClientLevelPortalTintMixin
        //? if <1.21.11 {
        implements PortalTintAccess
        //?}
{
    //? if <1.21.11 {
    @Shadow @Final private Object2ObjectArrayMap<ColorResolver, BlockTintCache> tintCaches;
    @Shadow public abstract int calculateBlockTint(BlockPos pos, ColorResolver resolver);

    @Override
    public int universegate$getVanillaTint(BlockPos pos, ColorResolver resolver) {
        BlockTintCache cache = tintCaches.get(resolver);
        return cache != null ? cache.getColor(pos) : calculateBlockTint(pos, resolver);
    }

    @Inject(method = "getBlockTint", at = @At("HEAD"), cancellable = true)
    private void universegate$useVanillaTintForPortal(BlockPos pos, ColorResolver resolver, CallbackInfoReturnable<Integer> cir) {
        // Third-party blend caches are populated around the local player, not the streamed camera.
        if (RenderSystem.isOnRenderThread() && PortalStencilRenderer.isRenderingPortalPass()) {
            cir.setReturnValue(universegate$getVanillaTint(pos, resolver));
        }
    }
    //?}
}
