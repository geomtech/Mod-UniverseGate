package fr.geomtech.universegate;

import fr.geomtech.universegate.compat.Nbt;
import fr.geomtech.universegate.compat.SavedDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public class AncientCityZpcSavedData extends SavedData {

    private static final String DATA_NAME = UniverseGate.MOD_ID + "_ancient_city_zpc";
    private final Set<Long> rewardedCities = new HashSet<>();

    public static AncientCityZpcSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return SavedDataCompat.computeIfAbsent(overworld, DATA_NAME, AncientCityZpcSavedData::new, AncientCityZpcSavedData::load, AncientCityZpcSavedData::save);
    }

    public boolean hasReward(BlockPos cityCenter) {
        return rewardedCities.contains(pack(cityCenter));
    }

    public void markRewarded(BlockPos cityCenter) {
        if (rewardedCities.add(pack(cityCenter))) {
            setDirty();
        }
    }

    //? if <1.21.11
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] values = new long[rewardedCities.size()];
        int i = 0;
        for (long value : rewardedCities) {
            values[i++] = value;
        }
        tag.putLongArray("RewardedCities", values);
        return tag;
    }

    public static AncientCityZpcSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AncientCityZpcSavedData data = new AncientCityZpcSavedData();
        for (long value : Nbt.getLongArray(tag, "RewardedCities")) {
            data.rewardedCities.add(value);
        }
        return data;
    }

    private static long pack(BlockPos pos) {
        return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
    }
}
