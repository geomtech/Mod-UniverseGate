package fr.geomtech.universegate.compat;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

//? if >=1.21.11 {
/*import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueInput;

import java.nio.ByteBuffer;
*///?} else {
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
//?}

/**
 * Reads what {@link DataWriter} wrote, on every Minecraft version. Missing keys give the same
 * defaults as {@code CompoundTag} in 1.21.1 (0, false, "" and empty arrays).
 */
public final class DataReader {
    //? if >=1.21.11 {
    /*private final ValueInput in;

    private DataReader(ValueInput in) {
        this.in = in;
    }

    public static DataReader of(ValueInput in) {
        return new DataReader(in);
    }

    public boolean contains(String key) { return in.read(key, Codec.PASSTHROUGH).isPresent(); }
    public int getInt(String key) { return in.getIntOr(key, 0); }
    public long getLong(String key) { return in.getLongOr(key, 0L); }
    public boolean getBoolean(String key) { return in.getBooleanOr(key, false); }
    public float getFloat(String key) { return in.getFloatOr(key, 0.0F); }
    public double getDouble(String key) { return in.getDoubleOr(key, 0.0D); }
    public String getString(String key) { return in.getStringOr(key, ""); }
    public @Nullable UUID getUUID(String key) { return in.read(key, UUIDUtil.CODEC).orElse(null); }
    public int[] getIntArray(String key) { return in.getIntArray(key).orElse(new int[0]); }
    public long[] getLongArray(String key) {
        return in.read(key, Codec.LONG_STREAM).map(java.util.stream.LongStream::toArray).orElse(new long[0]);
    }
    public byte[] getByteArray(String key) {
        return in.read(key, Codec.BYTE_BUFFER).map(DataReader::toArray).orElse(new byte[0]);
    }
    public void loadItems(NonNullList<ItemStack> items) {
        ContainerHelper.loadAllItems(in, items);
    }

    private static byte[] toArray(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }
    *///?} else {
    private final CompoundTag tag;
    private final HolderLookup.Provider registries;

    private DataReader(CompoundTag tag, HolderLookup.Provider registries) {
        this.tag = tag;
        this.registries = registries;
    }

    public static DataReader of(CompoundTag tag, HolderLookup.Provider registries) {
        return new DataReader(tag, registries);
    }

    public boolean contains(String key) { return tag.contains(key); }
    public int getInt(String key) { return tag.getInt(key); }
    public long getLong(String key) { return tag.getLong(key); }
    public boolean getBoolean(String key) { return tag.getBoolean(key); }
    public float getFloat(String key) { return tag.getFloat(key); }
    public double getDouble(String key) { return tag.getDouble(key); }
    public String getString(String key) { return tag.getString(key); }
    public @Nullable UUID getUUID(String key) { return tag.hasUUID(key) ? tag.getUUID(key) : null; }
    public int[] getIntArray(String key) { return tag.getIntArray(key); }
    public long[] getLongArray(String key) { return tag.getLongArray(key); }
    public byte[] getByteArray(String key) { return tag.getByteArray(key); }
    public void loadItems(NonNullList<ItemStack> items) {
        ContainerHelper.loadAllItems(tag, items, registries);
    }
    //?}
}
