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

public class VillagePortalSavedData extends SavedData {

    private static final String DATA_NAME = UniverseGate.MOD_ID + "_village_portals";
    private final Set<Long> processedVillages = new HashSet<>();
    private long bootstrapIndex = 0L;
    private int bootstrapGeneratedPortals = 0;

    public static VillagePortalSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return SavedDataCompat.computeIfAbsent(overworld, DATA_NAME, VillagePortalSavedData::new, VillagePortalSavedData::load, VillagePortalSavedData::save);
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

    //? if <1.21.11
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
        for (long value : Nbt.getLongArray(tag, "Villages")) {
            data.processedVillages.add(value);
        }
        data.bootstrapIndex = tag.contains("BootstrapIndex") ? Nbt.getLong(tag, "BootstrapIndex") : 0L;
        data.bootstrapGeneratedPortals = tag.contains("BootstrapGeneratedPortals")
                ? Nbt.getInt(tag, "BootstrapGeneratedPortals")
                : 0;
        return data;
    }

    private static long packVillage(BlockPos pos) {
        return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
    }
}
