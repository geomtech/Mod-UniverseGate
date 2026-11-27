package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.PortalGateGeometry;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockGetter.class)
public interface BlockGetterPortalShapeMixin {
    @Inject(method = "clip", at = @At("RETURN"), cancellable = true)
    default void universegate$clipGateModel(ClipContext context, CallbackInfoReturnable<BlockHitResult> cir) {
        cir.setReturnValue(PortalGateGeometry.clip((BlockGetter) this, context, cir.getReturnValue()));
    }
}
