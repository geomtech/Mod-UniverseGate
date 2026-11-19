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
import java.util.Locale;

public class ZpcItem extends Item {

    public static final long CAPACITY = 40_000_000_000L;

    private static final String TAG_ENERGY = "ZpcEnergy";

    public ZpcItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static long getStoredEnergy(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ModItems.ZPC)) return 0L;

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(TAG_ENERGY)) return 0L;

        return clampEnergy(tag.getLong(TAG_ENERGY));
    }

    public static void setStoredEnergy(ItemStack stack, long energy) {
        if (stack.isEmpty() || !stack.is(ModItems.ZPC)) return;

        long clamped = clampEnergy(energy);
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        if (clamped <= 0L) {
            tag.remove(TAG_ENERGY);
        } else {
            tag.putLong(TAG_ENERGY, clamped);
        }

        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    public static int getChargePercent(ItemStack stack) {
        return chargePercentFromEnergy(getStoredEnergy(stack));
    }

    public static int chargePercentFromEnergy(long energy) {
        if (energy <= 0L) return 0;
        return (int) Math.max(0L, Math.min(100L, Math.round((energy * 100.0D) / (double) CAPACITY)));
    }

    public static long energyForPercent(int percent) {
        int clampedPercent = Math.max(0, Math.min(100, percent));
        return Math.round((CAPACITY * clampedPercent) / 100.0D);
    }

    public static ItemStack createChargedStack(int percent) {
        ItemStack stack = new ItemStack(ModItems.ZPC);
        setStoredEnergy(stack, energyForPercent(percent));
        return stack;
    }

    public static String formatEnergy(long energy) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, energy));
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
        if (percent >= 65) return 0x4FD46C;
        if (percent >= 25) return 0xE2C34D;
        return 0xD65742;
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                TooltipContext context,
                                List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        long stored = getStoredEnergy(stack);
        int percent = chargePercentFromEnergy(stored);
        tooltipComponents.add(Component.translatable("tooltip.universegate.zpc_charge", percent).withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable(
                "tooltip.universegate.zpc_energy",
                formatEnergy(stored),
                formatEnergy(CAPACITY)
        ).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static long clampEnergy(long value) {
        return Math.max(0L, Math.min(CAPACITY, value));
    }
}
