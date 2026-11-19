package fr.geomtech.universegate;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class UniverseGateDimensions {

    public static final ResourceKey<Level> RIFT = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift")
    );

    private UniverseGateDimensions() {}

    public static ServerLevel getRiftLevel(MinecraftServer server) {
        ServerLevel level = server.getLevel(RIFT);
        if (level == null) {
            UniverseGate.LOGGER.warn("Rift dimension not found ({}). Check datapack files.", RIFT.location());
        }
        return level;
    }
}
