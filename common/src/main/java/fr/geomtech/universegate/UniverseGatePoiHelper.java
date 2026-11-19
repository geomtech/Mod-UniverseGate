package fr.geomtech.universegate;

import fr.geomtech.universegate.mixin.PoiTypesAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Optional;

public final class UniverseGatePoiHelper {

    private UniverseGatePoiHelper() {}

    public static void registerChargedLightningRodPoi() {
        Optional<Holder.Reference<PoiType>> holder = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolder(PoiTypes.LIGHTNING_ROD);
        if (holder.isEmpty()) {
            UniverseGate.LOGGER.warn("POI type for lightning rod not found; charged rod won't attract lightning.");
            return;
        }

        HashSet<BlockState> states = new HashSet<>(ModBlocks.CHARGED_LIGHTNING_ROD.getStateDefinition().getPossibleStates());
        PoiTypesAccessor.universegate$registerBlockStates(holder.get(), states);
    }
}
