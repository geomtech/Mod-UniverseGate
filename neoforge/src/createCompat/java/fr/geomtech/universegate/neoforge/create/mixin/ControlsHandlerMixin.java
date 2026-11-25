package fr.geomtech.universegate.neoforge.create.mixin;

import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import fr.geomtech.universegate.neoforge.create.CarriageTeleportClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ControlsHandler.class, remap = false)
abstract class ControlsHandlerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void universegate$followTeleportedCarriage(CallbackInfo callback) {
        if (CarriageTeleportClient.holdControls()) callback.cancel();
    }
}
