package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public final class PortalDialService {

    public static final UUID DARK_DIMENSION_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private PortalDialService() {}

    public static PortalDialResult dial(ServerLevel sourceLevel,
                                        BlockPos sourceCorePos,
                                        UUID requestedTargetId,
                                        boolean naturalKeyboard) {
        if (!(sourceLevel.getBlockEntity(sourceCorePos) instanceof PortalCoreBlockEntity sourceCore)) {
            return new PortalDialResult(false, "Aucun cœur de portail à proximité du contrôleur.");
        }

        UUID targetPortalId = requestedTargetId;
        if (DARK_DIMENSION_ID.equals(targetPortalId)) {
            if (!sourceCore.isDarkEnergyComplete()) {
                return new PortalDialResult(false, "Énergie Noire insuffisante pour la Dark Dimension.");
            }
            if (!DarkEnergyNetworkHelper.isPortalPowered(sourceLevel, sourceCorePos)) {
                return new PortalDialResult(false, "La destination Dark est verrouillée: générateur noir non relié.");
            }

            PortalRegistrySavedData.PortalEntry darkTarget = PortalRiftHelper.ensureDarkDimensionTarget(sourceLevel);
            if (darkTarget == null) {
                return new PortalDialResult(false, "Aucun portail Dark disponible pour le moment.");
            }
            targetPortalId = darkTarget.id();
        }

        boolean requiresOpenEnergy = !EnergyNetworkHelper.isRiftDimension(sourceLevel) && !naturalKeyboard;
        int openEnergyCost = requiresOpenEnergy
                ? EnergyNetworkHelper.getPortalOpenEnergyCost(sourceLevel, sourceCorePos, targetPortalId)
                : 0;
        if (requiresOpenEnergy
                && EnergyNetworkHelper.getAvailableEnergyForPortal(sourceLevel, sourceCorePos) < openEnergyCost) {
            return new PortalDialResult(false,
                    "Énergie insuffisante pour l'ouverture du portail (" + openEnergyCost + " FE requis).");
        }

        boolean opened = PortalConnectionManager.openBothSides(
                sourceLevel,
                sourceCorePos,
                targetPortalId,
                false,
                naturalKeyboard
        );
        if (!opened) {
            return new PortalDialResult(false, "Le portail cible est introuvable ou déjà actif.");
        }

        if (requiresOpenEnergy
                && !EnergyNetworkHelper.consumePortalEnergy(sourceLevel, sourceCorePos, openEnergyCost)) {
            PortalConnectionManager.forceCloseOneSide(sourceLevel, sourceCorePos);
            return new PortalDialResult(false,
                    "Énergie insuffisante pour l'ouverture du portail (" + openEnergyCost + " FE requis).");
        }

        return new PortalDialResult(true, "");
    }
}
