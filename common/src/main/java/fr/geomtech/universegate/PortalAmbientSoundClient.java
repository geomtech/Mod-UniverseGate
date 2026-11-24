package fr.geomtech.universegate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Ambiance sonore d'un portail ouvert, jouée par le client (classe chargée uniquement côté client).
 *
 * <p>Avant, le serveur rejouait lui-même l'ambiance : {@code portal_idle} toutes les 27 s et
 * {@code portal_unstable} toutes les 10 ticks pour un portail instable, puis l'arrêtait avec un
 * paquet « stop » qui coupait ce son pour tous les portails autour du joueur. Le client rejoue
 * maintenant les mêmes sons, au même volume et à la même cadence, à partir de l'état synchronisé
 * du Portal Core, et n'arrête que les sons de ce portail-là.</p>
 */
final class PortalAmbientSoundClient {

    static final long AMBIENT_LOOP_INTERVAL_TICKS = 20L * 27L;
    static final long UNSTABLE_AMBIENT_LOOP_INTERVAL_TICKS = 10L;
    private static final float AMBIENT_VOLUME = 0.35F;
    private static final double HEARING_RANGE_SQR = 16.0D * 16.0D;
    /** portal_unstable dure 0,68 s : au plus 2 occurrences se chevauchent à 10 ticks d'écart. */
    private static final int MAX_TRACKED_SOUNDS = 3;

    private final Deque<SoundInstance> playing = new ArrayDeque<>();
    private long nextPlayGameTime = Long.MIN_VALUE;
    private boolean playingUnstable;

    void tick(PortalCoreBlockEntity core, long now) {
        // Never play ambient sounds for cores living in a remote seamless ClientLevel.
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || core.getLevel() != mc.level) {
            stop();
            return;
        }
        boolean unstable = core.isClientAmbientUnstable();
        if (unstable != playingUnstable) {
            // Même comportement que le serveur : on coupe l'ancienne ambiance et on relance tout de suite.
            stop();
            playingUnstable = unstable;
        }
        if (now < nextPlayGameTime) return;

        // Comme playSound côté serveur (portée de 16 blocs à ce volume) : on ne lance pas de son
        // inaudible. Le joueur qui entre dans la portée l'entend tout de suite.
        var player = Minecraft.getInstance().player;
        if (player == null || player.distanceToSqr(core.getBlockPos().getCenter()) > HEARING_RANGE_SQR) return;

        SoundEvent sound = unstable ? ModSounds.PORTAL_UNSTABLE : ModSounds.PORTAL_IDLE;
        SoundInstance instance = new SimpleSoundInstance(
                sound, SoundSource.BLOCKS, AMBIENT_VOLUME, 1.0F, RandomSource.create(), core.getBlockPos());
        Minecraft.getInstance().getSoundManager().play(instance);
        playing.addLast(instance);
        while (playing.size() > MAX_TRACKED_SOUNDS) playing.removeFirst();

        nextPlayGameTime = now + (unstable ? UNSTABLE_AMBIENT_LOOP_INTERVAL_TICKS : AMBIENT_LOOP_INTERVAL_TICKS);
    }

    void stop() {
        var soundManager = Minecraft.getInstance().getSoundManager();
        for (SoundInstance instance : playing) {
            soundManager.stop(instance);
        }
        playing.clear();
        nextPlayGameTime = Long.MIN_VALUE;
    }
}
