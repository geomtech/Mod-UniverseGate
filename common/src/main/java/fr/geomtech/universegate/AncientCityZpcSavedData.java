package fr.geomtech.universegate;

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
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        AncientCityZpcSavedData::new,
                        AncientCityZpcSavedData::load,
                        null
                ),
                DATA_NAME
        );
    }

    public boolean hasReward(BlockPos cityCenter) {
        return rewardedCities.contains(pack(cityCenter));
    }

    public void markRewarded(BlockPos cityCenter) {
        if (rewardedCities.add(pack(cityCenter))) {
            setDirty();
        }
    }

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
        for (long value : tag.getLongArray("RewardedCities")) {
            data.rewardedCities.add(value);
        }
        return data;
    }

    private static long pack(BlockPos pos) {
        return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
    }
}
