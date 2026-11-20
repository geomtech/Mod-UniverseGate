package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.LightningAntennaBlock;
import fr.geomtech.universegate.LightningAntennaBlockEntity;
import fr.geomtech.universegate.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla only powers its own lightning rod block: hand strikes on the lightning antenna to it. */
@Mixin(LightningBolt.class)
public abstract class LightningBoltMixin {

    @Unique
    private boolean universegate$handledAntenna = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void universegate$powerLightningAntenna(CallbackInfo ci) {
        if (universegate$handledAntenna) return;
        LightningBolt self = (LightningBolt) (Object) this;
        Level level = self.level();
        if (!(level instanceof ServerLevel sl)) return;

        BlockPos basePos = universegate$findAntennaNear(sl, self.blockPosition());
        if (basePos == null) return;

        universegate$handledAntenna = true;
        if (sl.getBlockEntity(basePos) instanceof LightningAntennaBlockEntity antenna) {
            antenna.onLightningStrike(sl);
        }
    }

    @Unique
    private static BlockPos universegate$findAntennaNear(ServerLevel level, BlockPos center) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(p);
                    if (state.is(ModBlocks.LIGHTNING_ANTENNA)) return LightningAntennaBlock.basePos(p, state);
                }
            }
        }
        return null;
    }
}
