package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.client.AcidRainClientState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class AcidRainSkyMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void universegate$greenWarningSky(Vec3 position, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        float strength = AcidRainClientState.intensity(partialTick);
        if (strength > 0.0F) cir.setReturnValue(cir.getReturnValue().lerp(new Vec3(0.48D, 0.68D, 0.22D), strength * 0.85D));
    }
}
