package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class RiftScenarioSavedData extends SavedData {

    private static final String DATA_NAME = UniverseGate.MOD_ID + "_rift_story";

    private boolean generated = false;
    private BlockPos outpostPos = BlockPos.ZERO;
    private BlockPos workingPortalPos = BlockPos.ZERO;

    public static RiftScenarioSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        RiftScenarioSavedData::new,
                        RiftScenarioSavedData::load,
                        null
                ),
                DATA_NAME
        );
    }

    public boolean isGenerated() {
        return generated;
    }

    public BlockPos getOutpostPos() {
        return outpostPos;
    }

    public BlockPos getWorkingPortalPos() {
        return workingPortalPos;
    }

    public void setGenerated(BlockPos outpostPos, BlockPos workingPortalPos) {
        this.generated = true;
        this.outpostPos = outpostPos;
        this.workingPortalPos = workingPortalPos;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Generated", generated);
        if (generated) {
            tag.putInt("OutpostX", outpostPos.getX());
            tag.putInt("OutpostY", outpostPos.getY());
            tag.putInt("OutpostZ", outpostPos.getZ());

            tag.putInt("PortalX", workingPortalPos.getX());
            tag.putInt("PortalY", workingPortalPos.getY());
            tag.putInt("PortalZ", workingPortalPos.getZ());
        }
        return tag;
    }

    public static RiftScenarioSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftScenarioSavedData data = new RiftScenarioSavedData();
        data.generated = tag.getBoolean("Generated");
        if (data.generated) {
            data.outpostPos = new BlockPos(tag.getInt("OutpostX"), tag.getInt("OutpostY"), tag.getInt("OutpostZ"));
            data.workingPortalPos = new BlockPos(tag.getInt("PortalX"), tag.getInt("PortalY"), tag.getInt("PortalZ"));
        }
        return data;
    }
}
