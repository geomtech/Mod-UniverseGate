package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public class VillagePortalSavedData extends SavedData {

    private static final String DATA_NAME = UniverseGate.MOD_ID + "_village_portals";
    private final Set<Long> processedVillages = new HashSet<>();
    private long bootstrapIndex = 0L;
    private int bootstrapGeneratedPortals = 0;

    public static VillagePortalSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        VillagePortalSavedData::new,
                        VillagePortalSavedData::load,
                        null
                ),
                DATA_NAME
        );
    }

    public boolean isProcessed(BlockPos villageCenter) {
        return processedVillages.contains(packVillage(villageCenter));
    }

    public void markProcessed(BlockPos villageCenter) {
        if (processedVillages.add(packVillage(villageCenter))) {
            setDirty();
        }
    }

    public long nextBootstrapIndex() {
        long index = bootstrapIndex;
        bootstrapIndex++;
        setDirty();
        return index;
    }

    public int getBootstrapGeneratedPortals() {
        return bootstrapGeneratedPortals;
    }

    public void incrementBootstrapGeneratedPortals() {
        bootstrapGeneratedPortals++;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] values = new long[processedVillages.size()];
        int i = 0;
        for (long value : processedVillages) {
            values[i++] = value;
        }
        tag.putLongArray("Villages", values);
        tag.putLong("BootstrapIndex", bootstrapIndex);
        tag.putInt("BootstrapGeneratedPortals", bootstrapGeneratedPortals);
        return tag;
    }

    public static VillagePortalSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        VillagePortalSavedData data = new VillagePortalSavedData();
        for (long value : tag.getLongArray("Villages")) {
            data.processedVillages.add(value);
        }
        data.bootstrapIndex = tag.contains("BootstrapIndex") ? tag.getLong("BootstrapIndex") : 0L;
        data.bootstrapGeneratedPortals = tag.contains("BootstrapGeneratedPortals")
                ? tag.getInt("BootstrapGeneratedPortals")
                : 0;
        return data;
    }

    private static long packVillage(BlockPos pos) {
        return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
    }
}
