package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class ModSounds {

    public static final SoundEvent PORTAL_OPENING = register("portal_opening");
    public static final SoundEvent PORTAL_OPENING_END = register("portal_opening_end");
    public static final SoundEvent PORTAL_IDLE = register("portal_idle");
    public static final SoundEvent PORTAL_CLOSING = register("portal_closing");
    public static final SoundEvent PORTAL_ERROR = register("portal_error");
    public static final SoundEvent PORTAL_UNSTABLE = register("portal_unstable");
    public static final SoundEvent PORTAL_ENTITY_GOING_THROUGH = register("portal_entity_going_through");
    public static final SoundEvent RIFT_SHADE_SOUND = register("rift_shade_sound");
    public static final SoundEvent RIFT_SHADE_HIT = register("rift_shade_hit");
    public static final SoundEvent RIFT_SHADE_DEATH = register("rift_shade_death");
    public static final SoundEvent RIFT_DIMENSION_AMBIENT_SOUND = register("rift_dimension_ambient_sound");

    private static SoundEvent register(String id) {
        ResourceLocation soundId = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, id);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, soundId, SoundEvent.createVariableRangeEvent(soundId));
    }

    public static void playAt(ServerLevel level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }

    public static void playPortalAmbientAt(ServerLevel level, BlockPos pos, boolean unstable) {
        playAt(level, pos, unstable ? PORTAL_UNSTABLE : PORTAL_IDLE, 0.35F, 1.0F);
    }

    public static void playPortalOpenedAt(ServerLevel level, BlockPos pos, boolean unstable) {
        stopPortalOpeningNear(level, pos);
        playAt(level, pos, PORTAL_OPENING_END, 1.0F, 1.0F);
        playPortalAmbientAt(level, pos, unstable);
    }

    public static void stopPortalOpeningNear(ServerLevel level, BlockPos pos) {
        stopSoundNear(level, pos, PORTAL_OPENING);
        stopSoundNear(level, pos, PORTAL_OPENING_END);
    }

    public static void stopPortalAmbientNear(ServerLevel level, BlockPos pos) {
        stopSoundNear(level, pos, PORTAL_IDLE);
        stopSoundNear(level, pos, PORTAL_UNSTABLE);
    }

    public static void stopPortalAmbientNear(ServerLevel level, BlockPos pos, boolean unstable) {
        stopSoundNear(level, pos, unstable ? PORTAL_UNSTABLE : PORTAL_IDLE);
    }

    public static void stopPortalLifecycleNear(ServerLevel level, BlockPos pos) {
        stopPortalOpeningNear(level, pos);
        stopPortalAmbientNear(level, pos);
    }

    public static void stopPortalLifecycleNear(ServerLevel level, BlockPos pos, boolean unstable) {
        stopPortalOpeningNear(level, pos);
        stopPortalAmbientNear(level, pos, unstable);
    }

    private static void stopSoundNear(ServerLevel level, BlockPos pos, SoundEvent sound) {
        ResourceLocation soundId = BuiltInRegistries.SOUND_EVENT.getKey(sound);
        ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket(soundId, SoundSource.BLOCKS);

        double px = pos.getX() + 0.5;
        double py = pos.getY() + 0.5;
        double pz = pos.getZ() + 0.5;
        double maxDistanceSqr = 128.0D * 128.0D;

        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - px;
            double dy = player.getY() - py;
            double dz = player.getZ() - pz;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr <= maxDistanceSqr) {
                player.connection.send(packet);
            }
        }
    }

    public static void register() {}

    private ModSounds() {}
}
