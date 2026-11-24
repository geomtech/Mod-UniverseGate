package fr.geomtech.universegate.seamless.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Instantiates isolated {@link ClientLevel} instances for remote portal views across MC 1.21.1 and 1.21.11.
 */
public final class RemoteClientLevelFactory {

    private static final int DEFAULT_SEA_LEVEL = 63;

    private RemoteClientLevelFactory() {}

    public static @Nullable ClientLevel create(Minecraft minecraft,
                                               ClientPacketListener connection,
                                               ResourceKey<Level> targetDim,
                                               ChunkPos centerChunk,
                                               int viewRadius) {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(targetDim, "targetDim");
        Objects.requireNonNull(centerChunk, "centerChunk");

        ClientLevel activeLevel = minecraft.level;
        Holder<DimensionType> dimensionType = resolveDimensionType(connection.registryAccess(), targetDim, activeLevel);
        if (dimensionType == null) {
            return null;
        }

        Difficulty difficulty = activeLevel != null ? activeLevel.getDifficulty() : Difficulty.NORMAL;
        boolean hardcore = activeLevel != null && activeLevel.getLevelData().isHardcore();
        boolean isFlat = false;
        boolean isDebug = activeLevel != null && activeLevel.isDebug();
        long biomeZoomSeed = 0L;

        ClientLevel.ClientLevelData levelData = new ClientLevel.ClientLevelData(difficulty, hardcore, isFlat);
        if (activeLevel != null) {
            levelData.setGameTime(activeLevel.getGameTime());
            levelData.setDayTime(activeLevel.getDayTime());
        }

        int clampedRadius = Math.max(2, Math.min(viewRadius, 12));

        //? if >=1.21.11 {
        /*int seaLevel = targetDim.equals(Level.NETHER) ? 32 : DEFAULT_SEA_LEVEL;
        ClientLevel remoteLevel = new ClientLevel(
                connection,
                levelData,
                targetDim,
                dimensionType,
                clampedRadius,
                clampedRadius,
                minecraft.levelRenderer,
                isDebug,
                biomeZoomSeed,
                seaLevel
        );
        *///?} else {
        ClientLevel remoteLevel = new ClientLevel(
                connection,
                levelData,
                targetDim,
                dimensionType,
                clampedRadius,
                clampedRadius,
                minecraft::getProfiler,
                minecraft.levelRenderer,
                isDebug,
                biomeZoomSeed
        );
        //?}

        remoteLevel.getChunkSource().updateViewRadius(clampedRadius);
        remoteLevel.getChunkSource().updateViewCenter(centerChunk.x, centerChunk.z);
        return remoteLevel;
    }

    static @Nullable Holder<DimensionType> resolveDimensionType(RegistryAccess registryAccess,
                                                                ResourceKey<Level> targetDim,
                                                                @Nullable ClientLevel fallbackActiveLevel) {
        Optional<? extends HolderGetter<DimensionType>> lookupOpt = registryAccess.lookup(Registries.DIMENSION_TYPE);
        if (lookupOpt.isPresent()) {
            HolderGetter<DimensionType> lookup = lookupOpt.get();

            // 1. Direct match by dimension id (covers custom dimensions such as universegate:rift)
            ResourceKey<DimensionType> directKey = ResourceKey.create(Registries.DIMENSION_TYPE, targetDim.location());
            Optional<Holder.Reference<DimensionType>> direct = lookup.get(directKey);
            if (direct.isPresent()) {
                return direct.get();
            }

            // 2. Builtin dimension mappings
            ResourceKey<DimensionType> builtinKey = null;
            if (targetDim.equals(Level.OVERWORLD)) {
                builtinKey = BuiltinDimensionTypes.OVERWORLD;
            } else if (targetDim.equals(Level.NETHER)) {
                builtinKey = BuiltinDimensionTypes.NETHER;
            } else if (targetDim.equals(Level.END)) {
                builtinKey = BuiltinDimensionTypes.END;
            }
            if (builtinKey != null) {
                Optional<Holder.Reference<DimensionType>> builtin = lookup.get(builtinKey);
                if (builtin.isPresent()) {
                    return builtin.get();
                }
            }
        }

        return fallbackActiveLevel != null ? fallbackActiveLevel.dimensionTypeRegistration() : null;
    }
}
