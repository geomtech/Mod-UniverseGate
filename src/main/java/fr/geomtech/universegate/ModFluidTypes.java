package fr.geomtech.universegate;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModFluidTypes {
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, UniverseGate.MOD_ID);

    public static final Supplier<FluidType> DARK_MATTER = register("dark_matter", "block.universegate.dark_matter");
    public static final Supplier<FluidType> ACID_RAIN = register("acid_rain", "block.universegate.acid_rain");

    private static Supplier<FluidType> register(String name, String descriptionId) {
        return FLUID_TYPES.register(name, () -> new FluidType(
                FluidType.Properties.create().descriptionId(descriptionId)));
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
    }

    private ModFluidTypes() {}
}
