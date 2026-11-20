package fr.geomtech.universegate;

import fr.geomtech.universegate.compat.Nbt;
import fr.geomtech.universegate.compat.SavedDataCompat;
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
    private BlockPos outpostBedPos = BlockPos.ZERO;

    public static RiftScenarioSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return SavedDataCompat.computeIfAbsent(overworld, DATA_NAME, RiftScenarioSavedData::new, RiftScenarioSavedData::load, RiftScenarioSavedData::save);
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

    public BlockPos getOutpostBedPos() {
        return outpostBedPos;
    }

    public void setGenerated(BlockPos outpostPos, BlockPos workingPortalPos) {
        setGenerated(outpostPos, workingPortalPos, outpostPos);
    }

    public void setGenerated(BlockPos outpostPos, BlockPos workingPortalPos, BlockPos outpostBedPos) {
        this.generated = true;
        this.outpostPos = outpostPos;
        this.workingPortalPos = workingPortalPos;
        this.outpostBedPos = outpostBedPos;
        setDirty();
    }

    //? if <1.21.11
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

            tag.putInt("BedX", outpostBedPos.getX());
            tag.putInt("BedY", outpostBedPos.getY());
            tag.putInt("BedZ", outpostBedPos.getZ());
        }
        return tag;
    }

    public static RiftScenarioSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftScenarioSavedData data = new RiftScenarioSavedData();
        data.generated = Nbt.getBoolean(tag, "Generated");
        if (data.generated) {
            data.outpostPos = new BlockPos(Nbt.getInt(tag, "OutpostX"), Nbt.getInt(tag, "OutpostY"), Nbt.getInt(tag, "OutpostZ"));
            data.workingPortalPos = new BlockPos(Nbt.getInt(tag, "PortalX"), Nbt.getInt(tag, "PortalY"), Nbt.getInt(tag, "PortalZ"));
            if (tag.contains("BedX")) {
                data.outpostBedPos = new BlockPos(Nbt.getInt(tag, "BedX"), Nbt.getInt(tag, "BedY"), Nbt.getInt(tag, "BedZ"));
            } else {
                data.outpostBedPos = data.outpostPos;
            }
        }
        return data;
    }
}
