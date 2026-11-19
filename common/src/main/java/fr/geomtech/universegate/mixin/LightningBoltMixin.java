package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.ChargedLightningRodBlock;
import fr.geomtech.universegate.ChargedLightningRodBlockEntity;
import fr.geomtech.universegate.PortalRiftHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBolt.class)
public abstract class LightningBoltMixin {

    @Unique
    private boolean universegate$handledChargedRod = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void universegate$powerChargedRod(CallbackInfo ci) {
        if (universegate$handledChargedRod) return;
        LightningBolt self = (LightningBolt) (Object) this;
        Level level = self.level();
        if (!(level instanceof ServerLevel sl)) return;

        BlockPos strike = self.blockPosition();
        BlockPos rodPos = findChargedRodNear(sl, strike);
        if (rodPos == null) return;

        if (sl.getBlockEntity(rodPos) instanceof ChargedLightningRodBlockEntity be) {
            be.addCharge(1);
            PortalRiftHelper.tryOpenRiftFromRod(sl, rodPos, ChargedLightningRodBlock.PORTAL_RADIUS);
            universegate$handledChargedRod = true;
        }
    }

    private BlockPos findChargedRodNear(ServerLevel level, BlockPos center) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (level.getBlockEntity(p) instanceof ChargedLightningRodBlockEntity) return p;
                }
            }
        }
        return null;
    }
}
