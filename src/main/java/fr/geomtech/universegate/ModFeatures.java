package fr.geomtech.universegate;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class ModFeatures {

    public static final Feature<NoneFeatureConfiguration> KELO_DEAD_TREE = Registry.register(
            BuiltInRegistries.FEATURE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "kelo_dead_tree"),
            new KeloDeadTreeFeature(NoneFeatureConfiguration.CODEC)
    );

    public static void register() {}

    private ModFeatures() {}
}
