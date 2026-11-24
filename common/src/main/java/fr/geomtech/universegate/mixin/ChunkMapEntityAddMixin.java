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
public abstract class ChunkMapEntityAddMixin {

    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "addEntity", at = @At("TAIL"))
    private void universegate$onAddEntity(Entity entity, CallbackInfo ci) {
        ServerPortalStreamManager.onTrackedEntityAdded(this.level, entity);
    }
}
