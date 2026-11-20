package fr.geomtech.universegate.compat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

//? if >=1.21.11 {
/*import net.minecraft.core.GlobalPos;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelData;

import java.util.Set;
*///?}

/**
 * Small server-side API differences between the supported Minecraft versions.
 * Each method does exactly what the 1.21.1 call it replaces did.
 */
public final class Compat {
    private Compat() {
    }

    public static ServerLevel serverLevel(ServerPlayer player) {
        //? if >=1.21.11 {
        /*return player.level();
        *///?} else {
        return player.serverLevel();
        //?}
    }

    public static MinecraftServer server(ServerPlayer player) {
        //? if >=1.21.11 {
        /*return player.level().getServer();
        *///?} else {
        return player.server;
        //?}
    }

    /** {@code EntityType.create(level)}; since 1.21.2 a spawn reason is required. */
    public static <T extends Entity> @Nullable T create(EntityType<T> type, ServerLevel level, MobSpawnType reason) {
        //? if >=1.21.11 {
        /*return type.create(level, reason);
        *///?} else {
        return type.create(level);
        //?}
    }

    /** {@code source.hasPermission(level)} (permission levels 1 to 4). */
    public static boolean hasPermission(CommandSourceStack source, int level) {
        //? if >=1.21.11 {
        /*Permission permission = switch (level) {
            case 0 -> null;
            case 1 -> Permissions.COMMANDS_MODERATOR;
            case 2 -> Permissions.COMMANDS_GAMEMASTER;
            case 3 -> Permissions.COMMANDS_ADMIN;
            default -> Permissions.COMMANDS_OWNER;
        };
        return permission == null || source.permissions().hasPermission(permission);
        *///?} else {
        return source.hasPermission(level);
        //?}
    }

    public static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        //? if >=1.21.11 {
        /*player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, true);
        *///?} else {
        player.teleportTo(level, x, y, z, yaw, pitch);
        //?}
    }

    public static void setRespawnPosition(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos, float angle,
                                          boolean forced, boolean sendMessage) {
        //? if >=1.21.11 {
        /*player.setRespawnPosition(
                new ServerPlayer.RespawnConfig(new LevelData.RespawnData(GlobalPos.of(dimension, pos), angle, 0.0F), forced),
                sendMessage
        );
        *///?} else {
        player.setRespawnPosition(dimension, pos, angle, forced, sendMessage);
        //?}
    }

    /** The main inventory (hotbar included) followed by the offhand, without the armor slots. */
    public static List<ItemStack> mainAndOffhandItems(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        //? if >=1.21.11 {
        /*stacks.addAll(player.getInventory().getNonEquipmentItems());
        stacks.add(player.getOffhandItem());
        *///?} else {
        stacks.addAll(player.getInventory().items);
        stacks.addAll(player.getInventory().offhand);
        //?}
        return stacks;
    }

    /** {@code AbstractFurnaceBlockEntity.isFuel(stack)}; since 1.21.2 fuels are per level. */
    public static boolean isFuel(@Nullable Level level, ItemStack stack) {
        //? if >=1.21.11 {
        /*return level != null && level.fuelValues().isFuel(stack);
        *///?} else {
        return net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.isFuel(stack);
        //?}
    }

    /** Furnace burn time of the item, 0 if it is not a fuel. */
    public static int burnDuration(@Nullable Level level, ItemStack stack) {
        //? if >=1.21.11 {
        /*return level == null ? 0 : level.fuelValues().burnDuration(stack);
        *///?} else {
        return net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.getFuel().getOrDefault(stack.getItem(), 0);
        //?}
    }

    /** {@code registry.getHolder(key)}, renamed {@code get(key)} in 1.21.2. */
    public static <T> Optional<Holder.Reference<T>> getHolder(Registry<T> registry, ResourceKey<T> key) {
        //? if >=1.21.11 {
        /*return registry.get(key);
        *///?} else {
        return registry.getHolder(key);
        //?}
    }

    /** {@code entity.moveTo(x, y, z)}, renamed {@code snapTo} in 1.21.5. */
    public static void moveTo(Entity entity, double x, double y, double z) {
        //? if >=1.21.11 {
        /*entity.snapTo(x, y, z);
        *///?} else {
        entity.moveTo(x, y, z);
        //?}
    }

    public static void moveTo(Entity entity, double x, double y, double z, float yaw, float pitch) {
        //? if >=1.21.11 {
        /*entity.snapTo(x, y, z, yaw, pitch);
        *///?} else {
        entity.moveTo(x, y, z, yaw, pitch);
        //?}
    }

    /** {@code entity.spawnAtLocation(stack)}; since 1.21.2 it needs the server level. */
    public static void spawnAtLocation(Entity entity, ItemStack stack) {
        //? if >=1.21.11 {
        /*if (entity.level() instanceof ServerLevel level) {
            entity.spawnAtLocation(level, stack);
        }
        *///?} else {
        entity.spawnAtLocation(stack);
        //?}
    }
}
