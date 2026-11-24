package fr.geomtech.universegate.mixin.client;

import fr.geomtech.universegate.seamless.physics.PortalHalfCollisionHandler;
import net.minecraft.client.Camera;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents blocks located directly behind an active portal ring from clipping the third-person
 * camera when looking through the portal aperture.
 */
@Mixin(Camera.class)
public abstract class CameraPortalClipMixin {

    @Shadow
    private Vec3 position;

    @Shadow
    @Final
    private Vector3f forwards;

    //? if >=1.21.11 {
    /*@Shadow
    private Level level;
    *///?} else {
    @Shadow
    private BlockGetter level;
    //?}

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void universegate$protectCameraBehindPortal(float startingDistance, CallbackInfoReturnable<Float> cir) {
        if (!(this.level instanceof Level actualLevel) || this.position == null) {
            return;
        }
        Vec3 targetPoint = this.position.add(
                -this.forwards.x() * startingDistance,
                -this.forwards.y() * startingDistance,
                -this.forwards.z() * startingDistance
        );
        if (PortalHalfCollisionHandler.isCameraRayThroughActivePortal(actualLevel, this.position, targetPoint)) {
            cir.setReturnValue(startingDistance);
        }
    }
}
