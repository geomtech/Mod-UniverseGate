package fr.geomtech.universegate.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

@Mixin(PoiTypes.class)
public interface PoiTypesAccessor {

    @Invoker("registerBlockStates")
    static void universegate$registerBlockStates(Holder<PoiType> holder, Set<BlockState> states) {
        throw new AssertionError("Mixin invoker not applied");
    }
}
