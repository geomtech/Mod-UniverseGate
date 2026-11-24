package fr.geomtech.universegate;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public class AcidBatteryItem extends Item {

    public static final int CAPACITY = 24_000;
    private static final String TAG_ENERGY = "AcidBatteryEnergy";

    public AcidBatteryItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int getStoredEnergy(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ModItems.ACID_BATTERY)) return 0;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(TAG_ENERGY) ? clampEnergy(tag.getInt(TAG_ENERGY)) : CAPACITY;
    }

    public static void setStoredEnergy(ItemStack stack, int energy) {
        if (stack.isEmpty() || !stack.is(ModItems.ACID_BATTERY)) return;

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putInt(TAG_ENERGY, clampEnergy(energy));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static ItemStack createChargedStack() {
        return new ItemStack(ModItems.ACID_BATTERY);
    }

    public static int getChargePercent(ItemStack stack) {
        return (int) Math.round((getStoredEnergy(stack) * 100.0D) / CAPACITY);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.max(0, Math.min(13, Math.round((getChargePercent(stack) / 100.0F) * 13.0F)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int percent = getChargePercent(stack);
        if (percent >= 65) return 0x7FE34B;
        if (percent >= 25) return 0xD5CF3F;
        return 0xD65742;
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                TooltipContext context,
                                List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("tooltip.universegate.acid_battery_charge",
                getChargePercent(stack)).withStyle(ChatFormatting.GREEN));
        tooltipComponents.add(Component.translatable("tooltip.universegate.acid_battery_energy",
                getStoredEnergy(stack), CAPACITY).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static int clampEnergy(int value) {
        return Math.max(0, Math.min(CAPACITY, value));
    }
}
