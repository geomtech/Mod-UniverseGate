package fr.geomtech.universegate;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;

import net.minecraft.util.Mth;

import java.util.List;
import java.util.Optional;

public class DnaSampleItem extends Item {

    private static final String TAG_ENTITY_TYPE = "EntityType";
    private static final String TAG_CUSTOM_NAME = "EntityCustomName";
    private static final String TAG_REMAINING_USES = "RemainingUses";
    private static final String TAG_LAST_MAX_USES = "LastMaxUses";

    public static final int BASE_USES = 100;
    public static final int USES_PER_UNBREAKING_LEVEL = 50;

    public DnaSampleItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack createFromMob(Mob mob) {
        ItemStack stack = new ItemStack(ModItems.DNA);

        CompoundTag tag = readTag(stack);
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (entityId != null) {
            tag.putString(TAG_ENTITY_TYPE, entityId.toString());
        }

        if (mob.hasCustomName() && mob.getCustomName() != null) {
            tag.putString(TAG_CUSTOM_NAME, mob.getCustomName().getString());
        }

        int maxUses = getMaxUses(stack);
        tag.putInt(TAG_REMAINING_USES, maxUses);
        tag.putInt(TAG_LAST_MAX_USES, maxUses);

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static Optional<ResourceLocation> getEntityTypeId(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (!tag.contains(TAG_ENTITY_TYPE)) {
            return Optional.empty();
        }

        ResourceLocation location = ResourceLocation.tryParse(tag.getString(TAG_ENTITY_TYPE));
        return Optional.ofNullable(location);
    }

    public static Optional<EntityType<?>> getEntityType(ItemStack stack) {
        return getEntityTypeId(stack).flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional);
    }

    public static Optional<String> getCustomName(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (!tag.contains(TAG_CUSTOM_NAME)) {
            return Optional.empty();
        }

        String name = tag.getString(TAG_CUSTOM_NAME);
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(name);
    }

    public static int getMaxUses(ItemStack stack) {
        return BASE_USES + (getUnbreakingLevel(stack) * USES_PER_UNBREAKING_LEVEL);
    }

    public static int getRemainingUses(ItemStack stack) {
        return normalizeUsesAndGet(stack);
    }

    public static boolean consumeUse(ItemStack stack) {
        int remaining = normalizeUsesAndGet(stack);
        if (remaining <= 0) {
            return false;
        }

        setRemainingUses(stack, remaining - 1);
        return true;
    }

    public static void setRemainingUses(ItemStack stack, int uses) {
        int maxUses = getMaxUses(stack);
        CompoundTag tag = readTag(stack);
        tag.putInt(TAG_REMAINING_USES, Mth.clamp(uses, 0, maxUses));
        tag.putInt(TAG_LAST_MAX_USES, maxUses);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                TooltipContext context,
                                List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        Optional<EntityType<?>> storedType = getEntityType(stack);
        if (storedType.isPresent()) {
            tooltipComponents.add(
                    Component.translatable("tooltip.universegate.dna_sample_entity", storedType.get().getDescription())
                            .withStyle(ChatFormatting.GOLD)
            );
        } else {
            tooltipComponents.add(Component.translatable("tooltip.universegate.dna_sample_empty").withStyle(ChatFormatting.RED));
        }

        getEntityTypeId(stack).ifPresent(entityId -> tooltipComponents.add(
                Component.translatable("tooltip.universegate.dna_sample_entity_id", entityId.toString())
                        .withStyle(ChatFormatting.DARK_GRAY)
        ));

        getCustomName(stack).ifPresent(customName -> tooltipComponents.add(
                Component.translatable("tooltip.universegate.dna_sample_custom_name", customName)
                        .withStyle(ChatFormatting.GRAY)
        ));

        tooltipComponents.add(
                Component.translatable("tooltip.universegate.dna_sample_uses", getRemainingUses(stack), getMaxUses(stack))
                        .withStyle(ChatFormatting.AQUA)
        );
    }

    private static int normalizeUsesAndGet(ItemStack stack) {
        int maxUses = getMaxUses(stack);

        CompoundTag tag = readTag(stack);
        int remainingUses = tag.contains(TAG_REMAINING_USES)
                ? Mth.clamp(tag.getInt(TAG_REMAINING_USES), 0, maxUses)
                : maxUses;

        int lastMaxUses = tag.contains(TAG_LAST_MAX_USES)
                ? Math.max(0, tag.getInt(TAG_LAST_MAX_USES))
                : maxUses;

        if (maxUses > lastMaxUses) {
            remainingUses = Math.min(maxUses, remainingUses + (maxUses - lastMaxUses));
        }

        boolean changed = !tag.contains(TAG_REMAINING_USES)
                || !tag.contains(TAG_LAST_MAX_USES)
                || tag.getInt(TAG_REMAINING_USES) != remainingUses
                || tag.getInt(TAG_LAST_MAX_USES) != maxUses;

        if (changed) {
            tag.putInt(TAG_REMAINING_USES, remainingUses);
            tag.putInt(TAG_LAST_MAX_USES, maxUses);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }

        return remainingUses;
    }

    private static int getUnbreakingLevel(ItemStack stack) {
        int level = 0;
        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.UNBREAKING)) {
                level = Math.max(level, entry.getIntValue());
            }
        }
        return Mth.clamp(level, 0, 3);
    }

    private static CompoundTag readTag(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag();
    }
}
