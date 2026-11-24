package fr.geomtech.universegate;

import fr.geomtech.universegate.net.AcidRainStatePayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class AcidRainManager {

    private static final int EFFECT_INTERVAL_TICKS = 20;
    private static final int TERRAIN_INTERVAL_TICKS = 100;
    private static final int MOB_SPAWN_INTERVAL_TICKS = 120;
    private static final int TERRAIN_RADIUS = 8;
    private static final int MOB_RADIUS = 24;
    private static final int MAX_ACID_MOBS = 6;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private AcidRainManager() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncPlayer(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((entity, origin, destination) -> {
            if (entity instanceof ServerPlayer player) syncPlayer(player);
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            ItemStack held = player.getItemInHand(hand);
            if (!state.is(ModBlocks.ACID_RAIN_CAULDRON)
                    || state.getValue(LayeredCauldronBlock.LEVEL) < LayeredCauldronBlock.MAX_FILL_LEVEL
                    || !held.is(Items.BUCKET)) {
                return InteractionResult.PASS;
            }

            if (level.isClientSide) return InteractionResult.SUCCESS;

            ItemStack acidBucket = new ItemStack(ModItems.ACID_RAIN_BUCKET);
            if (player.isCreative()) {
                giveItem(player, acidBucket);
            } else if (held.getCount() == 1) {
                player.setItemInHand(hand, acidBucket);
            } else {
                held.shrink(1);
                giveItem(player, acidBucket);
            }

            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(false);
        });
    }

    public static boolean canStartEpisode(ServerLevel level) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling()
                && AcidRainSavedData.get(level).canStart(level.getGameTime());
    }

    public static boolean setActive(ServerLevel level, boolean active) {
        AcidRainSavedData state = AcidRainSavedData.get(level);
        if (active) {
            if (!canStartEpisode(level) || !state.beginEpisode(level.getGameTime())) return false;
            announceWarning(level);
        } else if (!state.endEpisode(level.getGameTime())) {
            return true;
        }
        syncLevel(level);
        return true;
    }

    public static void onNaturalRainStarted(ServerLevel level) {
        if (!canStartEpisode(level)) return;
        AcidRainSavedData state = AcidRainSavedData.get(level);
        if (state.beginNaturalEpisode(level.getGameTime(), level.getRandom().nextFloat())) {
            announceWarning(level);
            syncLevel(level);
        }
    }

    private static void announceWarning(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(Component.translatable("message.universegate.acid_rain_warning",
                    AcidRainSavedData.WARNING_TICKS / 20), false);
        }
    }

    private static void syncLevel(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            syncPlayer(player);
        }
    }

    public static void tickWorld(ServerLevel level) {
        AcidRainSavedData state = AcidRainSavedData.get(level);
        long gameTime = level.getGameTime();
        boolean changed = state.tick(gameTime, level.getLevelData().isRaining());
        if (changed || state.phase() == AcidRainSavedData.Phase.WARNING && gameTime % 20L == 0L) syncLevel(level);
        if (!state.isActive()) return;

        if (gameTime % EFFECT_INTERVAL_TICKS == 0L) {
            damageExposedEntities(level, gameTime % 40L == 0L);
        }
        if (gameTime % TERRAIN_INTERVAL_TICKS == 0L) {
            affectExposedBlocks(level);
        }
        if (gameTime % MOB_SPAWN_INTERVAL_TICKS == 0L) {
            spawnRiftMobs(level);
        }
    }

    private static void syncPlayer(ServerPlayer player) {
        AcidRainSavedData state = AcidRainSavedData.get(player.serverLevel());
        ServerPlayNetworking.send(player, new AcidRainStatePayload(state.isActive(),
                state.warningTicksRemaining(player.serverLevel().getGameTime())));
    }

    private static void giveItem(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private static void damageExposedEntities(ServerLevel level, boolean wearArmor) {
        Set<UUID> affected = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            AABB area = player.getBoundingBox().inflate(14.0D, 8.0D, 14.0D);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (!affected.add(entity.getUUID()) || isRiftMob(entity) || !level.isRainingAt(entity.blockPosition())) continue;
                if (AcidTechnologyManager.onAcidExposure(entity)) continue;
                if (wearArmor) damageArmor(entity);
                entity.hurt(level.damageSources().magic(), 1.0F);
            }
        }
    }

    private static boolean isRiftMob(LivingEntity entity) {
        return entity instanceof RiftShadeEntity || entity instanceof RiftBeastEntity;
    }

    private static void damageArmor(LivingEntity entity) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = entity.getItemBySlot(slot);
            if (!armor.isEmpty() && armor.isDamageableItem()) {
                armor.hurtAndBreak(1, entity, slot);
            }
        }
    }

    private static void affectExposedBlocks(ServerLevel level) {
        Set<BlockPos> visitedColumns = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            int centerX = player.blockPosition().getX();
            int centerZ = player.blockPosition().getZ();
            for (int x = centerX - TERRAIN_RADIUS; x <= centerX + TERRAIN_RADIUS; x++) {
                for (int z = centerZ - TERRAIN_RADIUS; z <= centerZ + TERRAIN_RADIUS; z++) {
                    BlockPos column = new BlockPos(x, 0, z);
                    if (visitedColumns.add(column)) affectColumn(level, x, z);
                }
            }
        }
    }

    private static void affectColumn(ServerLevel level, int x, int z) {
        BlockPos column = new BlockPos(x, level.getMinBuildHeight(), z);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);

        for (int y = surface.getY() + 1; y >= surface.getY() - 2; y--) {
            if (y < level.getMinBuildHeight()) continue;
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isRainingAt(pos.above())) continue;

            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.ACID_RAIN_CAULDRON)) {
                int fillLevel = state.getValue(LayeredCauldronBlock.LEVEL);
                if (fillLevel < LayeredCauldronBlock.MAX_FILL_LEVEL) {
                    level.setBlock(pos, state.setValue(LayeredCauldronBlock.LEVEL, fillLevel + 1), 3);
                }
                return;
            }
            if (state.is(Blocks.CAULDRON)) {
                level.setBlock(pos, ModBlocks.ACID_RAIN_CAULDRON.defaultBlockState()
                        .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MIN_FILL_LEVEL), 3);
                return;
            }
            if (state.is(BlockTags.CROPS)) {
                level.destroyBlock(pos, false);
                continue;
            }
            if (isDirt(state)) {
                level.setBlock(pos, ModBlocks.MOLDY_DIRT.defaultBlockState(), 3);
                return;
            }
        }
    }

    private static boolean isDirt(BlockState state) {
        return state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.PODZOL);
    }

    private static void spawnRiftMobs(ServerLevel level) {
        if (level.dimension().equals(UniverseGateDimensions.RIFT)) return;

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            AABB area = player.getBoundingBox().inflate(MOB_RADIUS, 16.0D, MOB_RADIUS);
            int count = level.getEntitiesOfClass(Mob.class, area, AcidRainManager::isAcidSpawnedMob).size();
            if (count >= MAX_ACID_MOBS) continue;

            spawnOneRiftMob(level, player);
        }
    }

    private static boolean isAcidSpawnedMob(Mob mob) {
        return mob.getType() == ModEntityTypes.ACID_RIFT_SHADE || mob.getType() == ModEntityTypes.ACID_RIFT_BEAST;
    }

    private static void spawnOneRiftMob(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            int distance = 12 + level.random.nextInt(9);
            int x = player.blockPosition().getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.blockPosition().getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, level.getMinBuildHeight(), z);
            if (!level.hasChunkAt(column)) continue;

            BlockPos spawnPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            BlockState ground = level.getBlockState(spawnPos.below());
            if (!ground.isFaceSturdy(level, spawnPos.below(), Direction.UP)
                    || !level.getBlockState(spawnPos).isAir()
                    || !level.getBlockState(spawnPos.above()).isAir()
                    || !level.isRainingAt(spawnPos)) {
                continue;
            }

            Mob mob = level.random.nextInt(4) == 0
                    ? ModEntityTypes.ACID_RIFT_BEAST.create(level)
                    : ModEntityTypes.ACID_RIFT_SHADE.create(level);
            if (mob == null) return;

            mob.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            if (level.noCollision(mob) && level.addFreshEntity(mob)) return;
        }
    }
}
