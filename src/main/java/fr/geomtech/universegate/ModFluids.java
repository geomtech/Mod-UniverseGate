package fr.geomtech.universegate;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

public final class ModFluids {

    public static final FlowingFluid STILL_DARK_MATTER = register("dark_matter_still", new DarkMatterFluid.Source());
    public static final FlowingFluid FLOWING_DARK_MATTER = register("dark_matter_flowing", new DarkMatterFluid.Flowing());
    public static final FlowingFluid STILL_ACID_RAIN = register("acid_rain_still", new AcidRainFluid.Source());
    public static final FlowingFluid FLOWING_ACID_RAIN = register("acid_rain_flowing", new AcidRainFluid.Flowing());

    private static <T extends Fluid> T register(String name, T fluid) {
        return NeoForgeRegistryBootstrap.register(BuiltInRegistries.FLUID, ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, name), fluid);
    }

    public static void register() {
    }

    private ModFluids() {
    }
}
