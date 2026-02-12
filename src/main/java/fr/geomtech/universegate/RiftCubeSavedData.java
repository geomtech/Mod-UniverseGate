package fr.geomtech.universegate;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public class RiftCubeSavedData extends SavedData {

    private static final String DATA_NAME = UniverseGate.MOD_ID + "_rift_cubes";
    private final Set<Long> generatedCells = new HashSet<>();

    public static RiftCubeSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        RiftCubeSavedData::new,
                        RiftCubeSavedData::load,
                        null
                ),
                DATA_NAME
        );
    }

    public boolean isGenerated(int cellX, int cellZ) {
        return generatedCells.contains(packCell(cellX, cellZ));
    }

    public void markGenerated(int cellX, int cellZ) {
        if (generatedCells.add(packCell(cellX, cellZ))) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] values = new long[generatedCells.size()];
        int i = 0;
        for (long v : generatedCells) {
            values[i++] = v;
        }
        tag.putLongArray("Cells", values);
        return tag;
    }

    public static RiftCubeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftCubeSavedData data = new RiftCubeSavedData();
        long[] arr = tag.getLongArray("Cells");
        for (long v : arr) {
            data.generatedCells.add(v);
        }
        return data;
    }

    private static long packCell(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
