package fr.geomtech.universegate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Liste des couches dessinées par {@link PortalGateRenderer}, dans l'ordre d'enregistrement. */
public final class PortalGateLayers {

    private static final List<PortalGateLayer> LAYERS = new CopyOnWriteArrayList<>(List.of(
            new PortalGateBaseLayer(),
            new PortalGateRingLayer(),
            new PortalMembraneLayer()
    ));

    private PortalGateLayers() {}

    /** Ajoute un effet au portail 3D (dessiné après ceux déjà enregistrés). */
    public static void register(PortalGateLayer layer) {
        LAYERS.add(layer);
    }

    static List<PortalGateLayer> all() {
        return LAYERS;
    }
}
