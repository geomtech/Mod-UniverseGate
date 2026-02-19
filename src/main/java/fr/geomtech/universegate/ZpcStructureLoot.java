package fr.geomtech.universegate;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

public final class ZpcStructureLoot {

    private static final float CHEST_CHANCE = 0.0035F;
    private static final ResourceLocation ANCIENT_CITY_CHEST = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/ancient_city");

    private ZpcStructureLoot() {}

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) return;

            ResourceLocation location = key.location();
            if (!"minecraft".equals(location.getNamespace())) return;
            if (!location.getPath().startsWith("chests/")) return;
            if (location.equals(ANCIENT_CITY_CHEST)) return;

            LootPool.Builder pool = LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(CHEST_CHANCE));

            for (int percent = 1; percent <= 30; percent++) {
                CompoundTag tag = new CompoundTag();
                tag.putLong("ZpcEnergy", ZpcItem.energyForPercent(percent));
                pool.add(
                        LootItem.lootTableItem(ModItems.ZPC)
                                .setWeight(1)
                                .apply(SetComponentsFunction.setComponent(DataComponents.CUSTOM_DATA, CustomData.of(tag)))
                );
            }

            tableBuilder.withPool(pool);
        });
    }
}
