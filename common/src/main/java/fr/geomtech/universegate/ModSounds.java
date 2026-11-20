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
    public static final SoundEvent PORTAL_DIAL = register("portal_dial");
    public static final SoundEvent PORTAL_IDLE = register("portal_idle");
    public static final SoundEvent PORTAL_CLOSING = register("portal_closing");
    public static final SoundEvent PORTAL_ERROR = register("portal_error");
    public static final SoundEvent PORTAL_UNSTABLE = register("portal_unstable");
    /** Échappement de vapeur de l'anneau à la fermeture (son provisoire : à remplacer par un son du mod). */
    public static final SoundEvent PORTAL_VENT = register("portal_vent");
    /** Crépitement d'un arc électrique de l'anneau (pas la foudre vanilla). */
    public static final SoundEvent PORTAL_ARC = register("portal_arc");
    public static final SoundEvent PORTAL_ENTITY_GOING_THROUGH = register("portal_entity_going_through");
    public static final SoundEvent METEO_MACHINE = register("meteo_machine");
    public static final SoundEvent BEAM = register("beam");
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

    public static void playPortalOpenedAt(ServerLevel level, BlockPos pos, boolean unstable) {
        stopPortalOpeningNear(level, pos);
        playAt(level, pos, PORTAL_OPENING_END, 1.0F, 1.0F);
        // L'ambiance du portail ouvert est jouée par le client (PortalAmbientSoundClient).
    }

    public static void playPortalDialingAt(ServerLevel level, BlockPos pos) {
        playAt(level, pos, PORTAL_DIAL, 1.0F, 1.0F);
        playAt(level, pos, PORTAL_OPENING, 1.0F, 1.0F);
    }

    public static void playMeteoMachineAt(ServerLevel level, BlockPos pos) {
        playAt(level, pos, METEO_MACHINE, 1.0F, 1.0F);
    }

    public static void stopMeteoMachineNear(ServerLevel level, BlockPos pos) {
        stopSoundNear(level, pos, METEO_MACHINE);
    }

    public static void playBeamAt(ServerLevel level, BlockPos pos) {
        playAt(level, pos, BEAM, 1.0F, 1.0F);
    }

    public static void stopBeamNear(ServerLevel level, BlockPos pos) {
        stopSoundNear(level, pos, BEAM);
    }

    public static void stopPortalOpeningNear(ServerLevel level, BlockPos pos) {
        stopSoundNear(level, pos, PORTAL_DIAL);
        stopSoundNear(level, pos, PORTAL_OPENING);
        stopSoundNear(level, pos, PORTAL_OPENING_END);
    }

    /** L'ambiance du portail ouvert s'arrête côté client ; seule la séquence d'ouverture est coupée ici. */
    public static void stopPortalLifecycleNear(ServerLevel level, BlockPos pos) {
        stopPortalOpeningNear(level, pos);
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
