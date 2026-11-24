package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.seamless.server.ServerPortalStreamManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelUnloadMixin {

    @Inject(method = "unload", at = @At("HEAD"))
    private void universegate$onChunkUnload(LevelChunk chunk, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        ServerPortalStreamManager.onChunkUnloaded(self.dimension(), chunk.getPos().toLong());
    }
}
