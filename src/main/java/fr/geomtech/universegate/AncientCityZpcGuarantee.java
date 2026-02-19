package fr.geomtech.universegate;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.server.level.ServerLevel;

public final class AncientCityZpcGuarantee {

    private static final ResourceLocation ANCIENT_CITY_CHEST = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/ancient_city");
    private static final TagKey<Structure> ANCIENT_CITY_TAG = TagKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "zpc_ancient_city")
    );
    private static final int SEARCH_RADIUS_CHUNKS = 12;

    private AncientCityZpcGuarantee() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

            BlockPos chestPos = hitResult.getBlockPos();
            BlockEntity blockEntity = level.getBlockEntity(chestPos);
            if (!(blockEntity instanceof RandomizableContainerBlockEntity container)) return InteractionResult.PASS;

            var lootTable = container.getLootTable();
            if (lootTable == null || !lootTable.location().equals(ANCIENT_CITY_CHEST)) return InteractionResult.PASS;

            BlockPos cityCenter = serverLevel.findNearestMapStructure(ANCIENT_CITY_TAG, chestPos, SEARCH_RADIUS_CHUNKS, false);
            if (cityCenter == null) return InteractionResult.PASS;

            AncientCityZpcSavedData data = AncientCityZpcSavedData.get(level.getServer());
            if (data.hasReward(cityCenter)) return InteractionResult.PASS;

            container.unpackLootTable(player);
            insertGuaranteedZpc(container, level);
            data.markRewarded(cityCenter);

            UniverseGate.LOGGER.debug("Guaranteed ZPC added to Ancient City chest at {} (city center: {})", chestPos, cityCenter);
            return InteractionResult.PASS;
        });
    }

    private static void insertGuaranteedZpc(RandomizableContainerBlockEntity container, Level level) {
        ItemStack zpc = ZpcItem.createChargedStack(1 + level.random.nextInt(30));

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                container.setItem(slot, zpc);
                container.setChanged();
                return;
            }
        }

        int slot = level.random.nextInt(container.getContainerSize());
        container.setItem(slot, zpc);
        container.setChanged();
    }
}
