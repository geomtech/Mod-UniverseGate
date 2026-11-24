package fr.geomtech.universegate;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class AcidRainSavedData extends SavedData {
    public static final int WARNING_TICKS = 20 * 30;
    public static final long MIN_EPISODE_GAP_TICKS = 24_000L * 3L;
    public static final float NATURAL_CHANCE = 0.1F;
    private static final String DATA_NAME = UniverseGate.MOD_ID + "_acid_rain";

    public enum Phase { CLEAR, WARNING, ACTIVE }

    private Phase phase = Phase.CLEAR;
    private long warningEndsAt;
    private long nextEpisodeAt;

    public static AcidRainSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AcidRainSavedData::new, AcidRainSavedData::load, null), DATA_NAME);
    }

    public Phase phase() { return phase; }
    public boolean isActive() { return phase == Phase.ACTIVE; }
    public boolean canStart(long gameTime) { return phase == Phase.CLEAR && gameTime >= nextEpisodeAt; }
    public long cooldownRemaining(long gameTime) { return Math.max(0L, nextEpisodeAt - gameTime); }

    public int warningTicksRemaining(long gameTime) {
        if (phase != Phase.WARNING) return 0;
        return (int) Math.max(0L, Math.min(WARNING_TICKS, warningEndsAt - gameTime));
    }

    public boolean beginNaturalEpisode(long gameTime, float roll) {
        return roll >= 0.0F && roll < NATURAL_CHANCE && beginEpisode(gameTime);
    }

    public boolean beginEpisode(long gameTime) {
        if (!canStart(gameTime)) return false;
        phase = Phase.WARNING;
        warningEndsAt = gameTime + WARNING_TICKS;
        setDirty();
        return true;
    }

    public boolean tick(long gameTime, boolean raining) {
        if (phase == Phase.CLEAR) return false;
        if (!raining) return endEpisode(gameTime);
        if (phase == Phase.WARNING && gameTime >= warningEndsAt) {
            phase = Phase.ACTIVE;
            setDirty();
            return true;
        }
        return false;
    }

    public boolean endEpisode(long gameTime) {
        if (phase == Phase.CLEAR) return false;
        phase = Phase.CLEAR;
        warningEndsAt = 0L;
        nextEpisodeAt = gameTime + MIN_EPISODE_GAP_TICKS;
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("Phase", phase.name());
        tag.putLong("WarningEndsAt", warningEndsAt);
        tag.putLong("NextEpisodeAt", nextEpisodeAt);
        return tag;
    }

    public static AcidRainSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AcidRainSavedData data = new AcidRainSavedData();
        try {
            data.phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException ignored) {
            data.phase = Phase.CLEAR;
        }
        data.warningEndsAt = Math.max(0L, tag.getLong("WarningEndsAt"));
        data.nextEpisodeAt = Math.max(0L, tag.getLong("NextEpisodeAt"));
        return data;
    }
}
