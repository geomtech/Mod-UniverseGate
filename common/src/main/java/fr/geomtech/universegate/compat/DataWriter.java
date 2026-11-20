package fr.geomtech.universegate.compat;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

//? if >=1.21.11 {
/*import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueOutput;

import java.nio.ByteBuffer;
import java.util.Arrays;
*///?} else {
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
//?}

/**
 * Writes block entity / entity data the same way on every Minecraft version:
 * a {@code CompoundTag} up to 1.21.5, a {@code ValueOutput} from 1.21.6 on.
 * Keys and value types are the same, so saved data keeps its layout.
 */
public final class DataWriter {
    //? if >=1.21.11 {
    /*private final ValueOutput out;

    private DataWriter(ValueOutput out) {
        this.out = out;
    }

    public static DataWriter of(ValueOutput out) {
        return new DataWriter(out);
    }

    public DataWriter putInt(String key, int value) { out.putInt(key, value); return this; }
    public DataWriter putLong(String key, long value) { out.putLong(key, value); return this; }
    public DataWriter putBoolean(String key, boolean value) { out.putBoolean(key, value); return this; }
    public DataWriter putFloat(String key, float value) { out.putFloat(key, value); return this; }
    public DataWriter putDouble(String key, double value) { out.putDouble(key, value); return this; }
    public DataWriter putString(String key, String value) { out.putString(key, value); return this; }
    public DataWriter putUUID(String key, UUID value) { out.store(key, UUIDUtil.CODEC, value); return this; }
    public DataWriter putIntArray(String key, int[] value) { out.putIntArray(key, value); return this; }
    public DataWriter putLongArray(String key, long[] value) {
        out.store(key, Codec.LONG_STREAM, Arrays.stream(value));
        return this;
    }
    public DataWriter putByteArray(String key, byte[] value) {
        out.store(key, Codec.BYTE_BUFFER, ByteBuffer.wrap(value));
        return this;
    }
    public DataWriter saveItems(NonNullList<ItemStack> items) {
        ContainerHelper.saveAllItems(out, items);
        return this;
    }
    *///?} else {
    private final CompoundTag tag;
    private final HolderLookup.Provider registries;

    private DataWriter(CompoundTag tag, HolderLookup.Provider registries) {
        this.tag = tag;
        this.registries = registries;
    }

    public static DataWriter of(CompoundTag tag, HolderLookup.Provider registries) {
        return new DataWriter(tag, registries);
    }

    public DataWriter putInt(String key, int value) { tag.putInt(key, value); return this; }
    public DataWriter putLong(String key, long value) { tag.putLong(key, value); return this; }
    public DataWriter putBoolean(String key, boolean value) { tag.putBoolean(key, value); return this; }
    public DataWriter putFloat(String key, float value) { tag.putFloat(key, value); return this; }
    public DataWriter putDouble(String key, double value) { tag.putDouble(key, value); return this; }
    public DataWriter putString(String key, String value) { tag.putString(key, value); return this; }
    public DataWriter putUUID(String key, UUID value) { tag.putUUID(key, value); return this; }
    public DataWriter putIntArray(String key, int[] value) { tag.putIntArray(key, value); return this; }
    public DataWriter putLongArray(String key, long[] value) { tag.putLongArray(key, value); return this; }
    public DataWriter putByteArray(String key, byte[] value) { tag.putByteArray(key, value); return this; }
    public DataWriter saveItems(NonNullList<ItemStack> items) {
        ContainerHelper.saveAllItems(tag, items, registries);
        return this;
    }
    //?}
}
