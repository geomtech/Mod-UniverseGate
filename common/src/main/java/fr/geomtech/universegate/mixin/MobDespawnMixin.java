package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.PortalPursuitTracker;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobDespawnMixin {

    @Inject(method = "removeWhenFarAway", at = @At("HEAD"), cancellable = true)
    private void universegate$preventDespawnDuringPortalPursuit(double distanceToClosestPlayer,
                                                                CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (PortalPursuitTracker.isProtectedFromNaturalDespawn(self)) {
            cir.setReturnValue(false);
        }
    }
}
