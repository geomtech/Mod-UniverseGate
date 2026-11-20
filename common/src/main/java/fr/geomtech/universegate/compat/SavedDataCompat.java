package fr.geomtech.universegate.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.function.BiFunction;
import java.util.function.Supplier;

//? if >=1.21.11 {
/*import com.mojang.serialization.Codec;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;
*///?}

/**
 * Gets or creates a {@link SavedData} that is saved as a {@code CompoundTag} on every version
 * ({@code SavedData.Factory} up to 1.21.4, a codec-based {@code SavedDataType} from 1.21.5 on).
 */
public final class SavedDataCompat {
    private SavedDataCompat() {
    }

    @FunctionalInterface
    public interface Saver<T> {
        CompoundTag save(T data, CompoundTag tag, HolderLookup.Provider registries);
    }

    public static <T extends SavedData> T computeIfAbsent(ServerLevel level, String id, Supplier<T> constructor,
                                                          BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
                                                          Saver<T> saver) {
        //? if >=1.21.11 {
        /*HolderLookup.Provider registries = level.registryAccess();
        Codec<T> codec = CompoundTag.CODEC.xmap(
                tag -> loader.apply(tag, registries),
                data -> saver.save(data, new CompoundTag(), registries)
        );
        // Our data has no vanilla data fixes; any fix type is a no-op for data written by this version.
        return level.getDataStorage().computeIfAbsent(
                new SavedDataType<>(id, constructor, codec, DataFixTypes.SAVED_DATA_COMMAND_STORAGE)
        );
        *///?} else {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(constructor, loader::apply, null),
                id
        );
        //?}
    }
}
