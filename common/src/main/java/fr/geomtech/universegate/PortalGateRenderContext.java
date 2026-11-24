package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * État du portail 3D pour une image, partagé par toutes les couches.
 *
 * @param right           direction « droite » du portail (EAST : aligné sur X, SOUTH : aligné sur Z)
 * @param time            temps en ticks, interpolé
 * @param openingProgress avancement de l'ouverture, de 0 à 1 (0 hors ouverture)
 * @param ringAngle       angle de l'anneau en degrés (PortalGateAnimator)
 */
public record PortalGateRenderContext(Level level,
                                      PortalCoreBlockEntity core,
                                      BlockPos corePos,
                                      Direction right,
                                      long tick,
                                      float partialTick,
                                      float time,
                                      boolean opening,
                                      boolean active,
                                      boolean unstable,
                                      float openingProgress,
                                      float ringAngle,
                                      float brightness,
                                      boolean cooldown) {

    public boolean xAxis() {
        return right.getAxis() == Direction.Axis.X;
    }

    /** Portail ouvert ou en cours d'ouverture. */
    public boolean running() {
        return opening || active;
    }

    /** Phase de clignotement pendant la désactivation (retour arrière + refroidissement vapeur). */
    public boolean cooldownBlinkOn() {
        return cooldown && ((tick / 4L) & 1L) == 0L;
    }
}
