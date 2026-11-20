package fr.geomtech.universegate;

import fr.geomtech.universegate.compat.Compat;
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

    public static void registerLightningAntennaPoi() {
        Optional<Holder.Reference<PoiType>> holder = Compat.getHolder(BuiltInRegistries.POINT_OF_INTEREST_TYPE, PoiTypes.LIGHTNING_ROD);
        if (holder.isEmpty()) {
            UniverseGate.LOGGER.warn("POI type for lightning rod not found; the lightning antenna won't attract lightning.");
            return;
        }

        // Only the top of the antenna is a lightning rod: vanilla strikes the block above it.
        HashSet<BlockState> states = new HashSet<>();
        for (BlockState state : ModBlocks.LIGHTNING_ANTENNA.getStateDefinition().getPossibleStates()) {
            if (state.getValue(LightningAntennaBlock.PART) == LightningAntennaBlock.Part.TOP) states.add(state);
        }
        PoiTypesAccessor.universegate$registerBlockStates(holder.get(), states);
    }
}
