package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.seamless.server.ServerPortalStreamManager;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapEntityRemoveMixin {

    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void universegate$onRemoveEntity(Entity entity, CallbackInfo ci) {
        ServerPortalStreamManager.onTrackedEntityRemoved(this.level, entity);
    }
}
