package fr.geomtech.universegate.neoforge.create.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.lang.ref.WeakReference;

@Mixin(value = ControlsHandler.class, remap = false)
public interface ControlsHandlerAccessor {
    @Accessor("entityRef")
    static void universegate$setEntityRef(WeakReference<AbstractContraptionEntity> entityRef) {
        throw new AssertionError();
    }

    @Accessor("controlsPos")
    static void universegate$setControlsPos(BlockPos controlsPos) {
        throw new AssertionError();
    }
}
