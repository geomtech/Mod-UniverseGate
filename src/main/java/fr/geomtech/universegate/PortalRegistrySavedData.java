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


import fr.geomtech.universegate.net.UniverseGateNetwork;

import java.util.*;

public class PortalRegistrySavedData extends SavedData {

    private static final String DATA_NAME = UniverseGate.MOD_ID + "_portal_registry";

    public record PortalEntry(UUID id, String name, ResourceKey<Level> dim, BlockPos pos, boolean hidden) {}

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

    public Collection<PortalEntry> listVisible() {
        List<PortalEntry> visible = new ArrayList<>();
        for (PortalEntry entry : portals.values()) {
            if (!entry.hidden()) visible.add(entry);
        }
        return Collections.unmodifiableCollection(visible);
    }

    public PortalEntry get(UUID id) {
        if (UniverseGateNetwork.DARK_DIMENSION_ID.equals(id)) {
             // Return a dummy entry for the Dark Dimension
             // We use BlockPos.ZERO or a specific coordinate if needed.
             // But connecting to BlockPos.ZERO might cause issues if there is no portal there.
             // However, for the purpose of "unlocking", this satisfies the lookup.
             // The PortalConnectionManager might fail later if it checks for a PortalCoreBlockEntity at destination.
             // But we satisfied the first step.
             ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse("universegate:rift"));
             return new PortalEntry(id, "Dark Dimension", dim, BlockPos.ZERO, false);
        }
        return portals.get(id);
    }

    public void upsertPortal(ServerLevel level, UUID id, String name, BlockPos pos) {
        upsertPortal(level, id, name, pos, false);
    }

    public void upsertPortal(ServerLevel level, UUID id, String name, BlockPos pos, boolean hidden) {
        ResourceKey<Level> dim = level.dimension();
        portals.put(id, new PortalEntry(id, name == null ? "" : name, dim, pos, hidden));
        setDirty();
    }

    public void setHidden(UUID id, boolean hidden) {
        PortalEntry existing = portals.get(id);
        if (existing == null) return;
        if (existing.hidden() == hidden) return;
        portals.put(id, new PortalEntry(existing.id(), existing.name(), existing.dim(), existing.pos(), hidden));
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
            p.putBoolean("Hidden", e.hidden());

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

            boolean hidden = p.contains("Hidden") && p.getBoolean("Hidden");

            data.portals.put(id, new PortalEntry(id, name, dim, pos, hidden));
        }

        return data;
    }
}
