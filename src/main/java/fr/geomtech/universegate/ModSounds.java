package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class ModSounds {

    public static final SoundEvent PORTAL_OPENING = register("portal_opening");
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

    public static void register() {}

    private ModSounds() {}
}
