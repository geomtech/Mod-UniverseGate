package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.seamless.server.ServerPortalStreamManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheBlockDeltaMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "blockChanged", at = @At("HEAD"))
    private void universegate$onBlockChanged(BlockPos pos, CallbackInfo ci) {
        ServerPortalStreamManager.onBlockChanged(this.level, pos);
    }
}
