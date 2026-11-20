package fr.geomtech.universegate;

import fr.geomtech.universegate.net.PortalFieldCollapsePayload;
import fr.geomtech.universegate.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Étincelles de l'éclatement du champ quand un portail se ferme.
 *
 * <p>Le serveur envoyait un paquet par étincelle (8 à 12 par case, soit une centaine par portail).
 * Il envoie maintenant un seul {@link PortalFieldCollapsePayload} aux joueurs proches, et chaque
 * client génère les mêmes étincelles, au même endroit, comme s'il avait reçu les paquets.</p>
 */
public final class PortalFieldCollapseParticles {

    /** Même rayon que ServerLevel#sendParticles sans « force ». */
    private static final double SEND_RANGE_SQR = 32.0D * 32.0D;

    private PortalFieldCollapseParticles() {}

    static void send(ServerLevel level, List<BlockPos> cells, Direction.Axis axis, boolean unstable) {
        if (cells.isEmpty()) return;
        var payload = new PortalFieldCollapsePayload(List.copyOf(cells), axis == Direction.Axis.X, unstable);
        for (ServerPlayer player : level.players()) {
            for (BlockPos cell : cells) {
                if (player.distanceToSqr(cell.getCenter()) <= SEND_RANGE_SQR) {
                    Services.PLATFORM.sendToPlayer(player, payload);
                    break;
                }
            }
        }
    }

    /** Appelé côté client à la réception du paquet. */
    public static void spawn(Level level, PortalFieldCollapsePayload payload) {
        RandomSource random = level.random;
        int count = payload.unstable() ? 12 : 8;

        for (BlockPos pos : payload.cells()) {
            for (int i = 0; i < count; i++) {
                double x = pos.getX() + 0.5;
                double y = pos.getY() + random.nextDouble();
                double z = pos.getZ() + 0.5;

                if (payload.xAxis()) {
                    x += (random.nextDouble() - 0.5) * 0.9;
                    z += (random.nextDouble() - 0.5) * 0.12;
                } else {
                    x += (random.nextDouble() - 0.5) * 0.12;
                    z += (random.nextDouble() - 0.5) * 0.9;
                }

                // L'ancien sendParticles(count = 1, dx, dy, dz, speed = 0) décalait l'étincelle
                // d'un bruit gaussien de cette amplitude, sans vitesse.
                double dx = (random.nextDouble() - 0.5) * 0.05;
                double dy = (random.nextDouble() - 0.5) * 0.04;
                double dz = (random.nextDouble() - 0.5) * 0.05;
                level.addParticle(ModParticles.SPARK,
                        x + random.nextGaussian() * dx,
                        y + random.nextGaussian() * dy,
                        z + random.nextGaussian() * dz,
                        0.0D, 0.0D, 0.0D);
            }
        }
    }
}
