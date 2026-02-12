package fr.geomtech.universegate;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RiftDeathRecoveryHandler {

    private static final Map<UUID, ItemStack> PENDING_COMPASS_RESTORE = new HashMap<>();

    private RiftDeathRecoveryHandler() {}

    public static void register() {
        ServerPlayerEvents.ALLOW_DEATH.register((player, source, amount) -> {
            if (!player.serverLevel().dimension().equals(UniverseGateDimensions.RIFT)) return true;

            ItemStack compass = getRiftCompassFromInventory(player);
            if (compass.isEmpty()) return true;

            PENDING_COMPASS_RESTORE.put(player.getUUID(), compass.copy());
            UniverseGate.LOGGER.info("Queued Rift respawn recovery for {} (compass in inventory)", player.getGameProfile().getName());
            return true;
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            ItemStack restore = PENDING_COMPASS_RESTORE.remove(newPlayer.getUUID());
            if (restore == null || restore.isEmpty()) {
                return;
            }

            var server = newPlayer.server;
            if (server == null) return;

            ServerLevel rift = UniverseGateDimensions.getRiftLevel(server);
            if (rift == null) return;

            RiftScenarioSavedData data = RiftScenarioSavedData.get(server);
            BlockPos bedPos = data.getOutpostBedPos();
            if (bedPos.equals(BlockPos.ZERO)) {
                bedPos = data.getOutpostPos();
            }
            if (bedPos.equals(BlockPos.ZERO)) {
                UniverseGate.LOGGER.warn("No outpost position stored for Rift death recovery.");
                return;
            }

            BlockPos spawnPos = findSafeSpawnNearBed(rift, bedPos);
            newPlayer.teleportTo(rift,
                    spawnPos.getX() + 0.5,
                    spawnPos.getY() + 0.1,
                    spawnPos.getZ() + 0.5,
                    newPlayer.getYRot(),
                    newPlayer.getXRot());

            newPlayer.setRespawnPosition(UniverseGateDimensions.RIFT, bedPos, 0.0F, true, false);

            if (!hasRiftCompassInInventory(newPlayer)) {
                if (!newPlayer.getInventory().add(restore.copy())) {
                    newPlayer.drop(restore.copy(), false);
                }
                UniverseGate.LOGGER.info("Restored Rift Compass for {} after Rift death", newPlayer.getGameProfile().getName());
            }
        });
    }

    private static ItemStack getRiftCompassFromInventory(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (isRiftCompass(stack)) return stack;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isRiftCompass(stack)) return stack;
        }

        return ItemStack.EMPTY;
    }

    private static boolean hasRiftCompassInInventory(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (isRiftCompass(stack)) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isRiftCompass(stack)) return true;
        }
        return false;
    }

    private static boolean isRiftCompass(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!stack.is(Items.COMPASS) && !stack.is(ModItems.RIFT_COMPASS)) return false;

        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        if (tracker == null || tracker.target().isEmpty()) return false;
        return tracker.target().get().dimension().equals(UniverseGateDimensions.RIFT);
    }

    private static BlockPos findSafeSpawnNearBed(ServerLevel level, BlockPos bedPos) {
        BlockPos[] candidates = new BlockPos[] {
                bedPos.above(),
                bedPos.above().north(),
                bedPos.above().south(),
                bedPos.above().east(),
                bedPos.above().west(),
                bedPos.above().north().east(),
                bedPos.above().north().west(),
                bedPos.above().south().east(),
                bedPos.above().south().west()
        };

        for (BlockPos candidate : candidates) {
            if (!level.getBlockState(candidate).isAir()) continue;
            if (!level.getBlockState(candidate.above()).isAir()) continue;
            return candidate;
        }

        return bedPos.above();
    }
}
