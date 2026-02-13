package fr.geomtech.universegate;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class EngineerExpeditionSavedData extends SavedData {

    private static final String DATA_NAME = UniverseGate.MOD_ID + "_engineer_expedition";

    private long nextEventTick = -1L;

    public static EngineerExpeditionSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        EngineerExpeditionSavedData::new,
                        EngineerExpeditionSavedData::load,
                        null
                ),
                DATA_NAME
        );
    }

    public long getNextEventTick() {
        return nextEventTick;
    }

    public void setNextEventTick(long nextEventTick) {
        if (this.nextEventTick == nextEventTick) return;
        this.nextEventTick = nextEventTick;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("NextEventTick", nextEventTick);
        return tag;
    }

    public static EngineerExpeditionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EngineerExpeditionSavedData data = new EngineerExpeditionSavedData();
        data.nextEventTick = tag.contains("NextEventTick") ? tag.getLong("NextEventTick") : -1L;
        return data;
    }
}
