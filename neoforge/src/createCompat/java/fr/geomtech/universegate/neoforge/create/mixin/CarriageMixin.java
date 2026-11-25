package fr.geomtech.universegate.neoforge.create.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import fr.geomtech.universegate.neoforge.create.CreatePortalTracks;
import net.createmod.catnip.data.Couple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Carriage.class, remap = false)
abstract class CarriageMixin {
    @Shadow public Train train;

    @Inject(method = "lambda$travel$8", at = @At("RETURN"), cancellable = true)
    private void universegate$checkPortal(Couple<TrackNodeLocation> nodes, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() && CreatePortalTracks.blocksEntry(train, nodes.getFirst(), nodes.getSecond())) {
            callback.setReturnValue(true);
        }
    }
}
