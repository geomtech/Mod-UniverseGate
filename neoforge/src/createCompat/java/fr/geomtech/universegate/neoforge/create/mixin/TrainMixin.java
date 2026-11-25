package fr.geomtech.universegate.neoforge.create.mixin;

import com.simibubi.create.content.trains.entity.Train;
import fr.geomtech.universegate.neoforge.create.CreatePortalTracks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Train.class, remap = false)
abstract class TrainMixin {
    @Shadow public double speed;
    @Shadow public int carriageWaitingForChunks;
    @Unique private double universegate$attemptedMovement;
    @Unique private CreatePortalTracks.TrainFront universegate$previousFront;
    @Unique private CreatePortalTracks.LocalCrossing universegate$pendingCrossing;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/Train;tickPassiveSlowdown()V", shift = At.Shift.AFTER))
    private void universegate$captureMovement(Level level, CallbackInfo callback) {
        Train train = (Train) (Object) this;
        if (universegate$pendingCrossing != null && level instanceof ServerLevel serverLevel) {
            if (speed != 0 && (speed < 0) != universegate$pendingCrossing.backwards()) {
                universegate$pendingCrossing = null;
            } else if (CreatePortalTracks.retryLocalCrossing(serverLevel.getServer(), train, universegate$pendingCrossing)) {
                universegate$pendingCrossing = null;
            } else {
                speed = 0;
                train.speedBeforeStall = null;
            }
        }
        universegate$attemptedMovement = carriageWaitingForChunks == -1 ? speed : 0;
        universegate$previousFront = level instanceof ServerLevel && universegate$attemptedMovement != 0
                ? CreatePortalTracks.trainFront(train, universegate$attemptedMovement < 0) : null;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void universegate$crossLocalPortal(Level level, CallbackInfo callback) {
        if (!(level instanceof ServerLevel serverLevel) || universegate$attemptedMovement == 0) return;
        Train train = (Train) (Object) this;
        boolean backwards = universegate$attemptedMovement < 0;
        var crossing = CreatePortalTracks.findLocalCrossing(serverLevel.getServer(), train, universegate$previousFront, backwards);
        if (crossing != null) {
            speed = 0;
            train.speedBeforeStall = null;
            if (!CreatePortalTracks.retryLocalCrossing(serverLevel.getServer(), train, crossing)) {
                universegate$pendingCrossing = crossing;
            }
        } else if (speed == 0) {
            CreatePortalTracks.tryLocalTransfer(serverLevel.getServer(), train, backwards);
        }
    }
}
