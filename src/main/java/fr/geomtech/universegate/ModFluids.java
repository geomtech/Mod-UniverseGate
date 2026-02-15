package fr.geomtech.universegate;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

public final class ModFluids {

    public static final FlowingFluid STILL_DARK_MATTER = register("dark_matter_still", new DarkMatterFluid.Source());
    public static final FlowingFluid FLOWING_DARK_MATTER = register("dark_matter_flowing", new DarkMatterFluid.Flowing());

    private static <T extends Fluid> T register(String name, T fluid) {
        return Registry.register(BuiltInRegistries.FLUID, ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, name), fluid);
    }

    public static void register() {
    }

    private ModFluids() {
    }
}
