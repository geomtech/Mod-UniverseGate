package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.AcidRainManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class AcidRainWeatherMixin {
    @Unique private boolean universegate$wasRaining;

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"))
    private void universegate$captureWeather(CallbackInfo ci) {
        universegate$wasRaining = ((ServerLevel) (Object) this).getLevelData().isRaining();
    }

    @Inject(method = "advanceWeatherCycle", at = @At("TAIL"))
    private void universegate$detectNaturalRain(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.dimension().equals(Level.OVERWORLD) && !universegate$wasRaining && level.getLevelData().isRaining()) {
            AcidRainManager.onNaturalRainStarted(level);
        }
    }
}
