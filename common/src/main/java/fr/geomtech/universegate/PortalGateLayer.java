package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Une couche d'effet du portail 3D (anneau, socle, membrane, lumières...).
 *
 * <p>{@link PortalGateRenderer} dessine toutes les couches enregistrées dans {@link PortalGateLayers}, dans
 * l'ordre, avec l'état courant du portail. Pour ajouter un effet, il suffit d'écrire une nouvelle couche et de
 * l'enregistrer : les autres ne bougent pas.</p>
 */
public interface PortalGateLayer {

    void render(PortalGateRenderContext context, PoseStack poseStack, MultiBufferSource buffer);
}
