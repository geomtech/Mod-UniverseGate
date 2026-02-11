package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;


import java.util.*;

public class PortalRegistrySavedData extends SavedData {

    private static final String DATA_NAME = UniverseGate.MOD_ID + "_portal_registry";

    public record PortalEntry(UUID id, String name, ResourceKey<Level> dim, BlockPos pos) {}

    private final Map<UUID, PortalEntry> portals = new HashMap<>();

    public static PortalRegistrySavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();

        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        PortalRegistrySavedData::new,
                        PortalRegistrySavedData::load,
                        null
                ),
                DATA_NAME
        );
    }

    public Collection<PortalEntry> listAll() {
        return Collections.unmodifiableCollection(portals.values());
    }

    public PortalEntry get(UUID id) {
        return portals.get(id);
    }

    public void upsertPortal(ServerLevel level, UUID id, String name, BlockPos pos) {
        ResourceKey<Level> dim = level.dimension();
        portals.put(id, new PortalEntry(id, name == null ? "" : name, dim, pos));
        setDirty();
    }

    public void removePortal(UUID id) {
        if (id == null) return;
        if (portals.remove(id) != null) setDirty();
    }

    // ---------- Save/Load ----------
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();

        for (PortalEntry e : portals.values()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("Id", e.id());
            p.putString("Name", e.name());

            p.putString("Dim", e.dim().location().toString());

            p.putInt("X", e.pos().getX());
            p.putInt("Y", e.pos().getY());
            p.putInt("Z", e.pos().getZ());

            list.add(p);
        }

        tag.put("Portals", list);
        return tag;
    }

    public static PortalRegistrySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PortalRegistrySavedData data = new PortalRegistrySavedData();

        ListTag list = tag.getList("Portals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag p = list.getCompound(i);

            UUID id = p.getUUID("Id");
            String name = p.getString("Name");

            ResourceLocation dimLoc = ResourceLocation.tryParse(p.getString("Dim"));
            if (dimLoc == null) continue;

            ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimLoc);

            BlockPos pos = new BlockPos(p.getInt("X"), p.getInt("Y"), p.getInt("Z"));

            data.portals.put(id, new PortalEntry(id, name, dim, pos));
        }

        return data;
    }
}
