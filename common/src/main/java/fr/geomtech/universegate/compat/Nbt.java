package fr.geomtech.universegate.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//? if >=1.21.11 {
/*import net.minecraft.core.UUIDUtil;
*///?} else {
import net.minecraft.nbt.Tag;
//?}

/**
 * {@code CompoundTag} reads with the 1.21.1 semantics on every version: a missing key gives
 * 0, false, "" or an empty array/list/compound (since 1.21.5 the getters return {@code Optional}).
 */
public final class Nbt {
    private Nbt() {
    }

    //? if >=1.21.11 {
    /*public static int getInt(CompoundTag tag, String key) { return tag.getIntOr(key, 0); }
    public static long getLong(CompoundTag tag, String key) { return tag.getLongOr(key, 0L); }
    public static float getFloat(CompoundTag tag, String key) { return tag.getFloatOr(key, 0.0F); }
    public static double getDouble(CompoundTag tag, String key) { return tag.getDoubleOr(key, 0.0D); }
    public static boolean getBoolean(CompoundTag tag, String key) { return tag.getBooleanOr(key, false); }
    public static String getString(CompoundTag tag, String key) { return tag.getStringOr(key, ""); }
    public static int[] getIntArray(CompoundTag tag, String key) { return tag.getIntArray(key).orElse(new int[0]); }
    public static long[] getLongArray(CompoundTag tag, String key) { return tag.getLongArray(key).orElse(new long[0]); }
    public static byte[] getByteArray(CompoundTag tag, String key) { return tag.getByteArray(key).orElse(new byte[0]); }
    public static CompoundTag getCompound(CompoundTag tag, String key) { return tag.getCompoundOrEmpty(key); }
    public static boolean hasUUID(CompoundTag tag, String key) { return tag.read(key, UUIDUtil.CODEC).isPresent(); }
    public static @Nullable UUID getUUID(CompoundTag tag, String key) { return tag.read(key, UUIDUtil.CODEC).orElse(null); }
    public static void putUUID(CompoundTag tag, String key, UUID value) { tag.store(key, UUIDUtil.CODEC, value); }

    /^* Compound elements of a list, like {@code getList(key, Tag.TAG_COMPOUND)} in 1.21.1. ^/
    public static List<CompoundTag> getCompoundList(CompoundTag tag, String key) {
        List<CompoundTag> result = new ArrayList<>();
        ListTag list = tag.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            list.getCompound(i).ifPresent(result::add);
        }
        return result;
    }
    *///?} else {
    public static int getInt(CompoundTag tag, String key) { return tag.getInt(key); }
    public static long getLong(CompoundTag tag, String key) { return tag.getLong(key); }
    public static float getFloat(CompoundTag tag, String key) { return tag.getFloat(key); }
    public static double getDouble(CompoundTag tag, String key) { return tag.getDouble(key); }
    public static boolean getBoolean(CompoundTag tag, String key) { return tag.getBoolean(key); }
    public static String getString(CompoundTag tag, String key) { return tag.getString(key); }
    public static int[] getIntArray(CompoundTag tag, String key) { return tag.getIntArray(key); }
    public static long[] getLongArray(CompoundTag tag, String key) { return tag.getLongArray(key); }
    public static byte[] getByteArray(CompoundTag tag, String key) { return tag.getByteArray(key); }
    public static CompoundTag getCompound(CompoundTag tag, String key) { return tag.getCompound(key); }
    public static boolean hasUUID(CompoundTag tag, String key) { return tag.hasUUID(key); }
    public static @Nullable UUID getUUID(CompoundTag tag, String key) { return tag.hasUUID(key) ? tag.getUUID(key) : null; }
    public static void putUUID(CompoundTag tag, String key, UUID value) { tag.putUUID(key, value); }

    // Compound elements of a list, like getList(key, Tag.TAG_COMPOUND).
    public static List<CompoundTag> getCompoundList(CompoundTag tag, String key) {
        List<CompoundTag> result = new ArrayList<>();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getCompound(i));
        }
        return result;
    }
    //?}
}
