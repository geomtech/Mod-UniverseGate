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
        PortalCinematics.collapse(level, payload);
        RandomSource random = level.random;
        int count = payload.unstable() ? 12 : 8;

        if (payload.cells().isEmpty()) return;
        // Étincelles réparties dans le disque de la membrane (et non plus case par case : on voyait le
        // rectangle 3x4 du champ alors que le portail est rond).
        double cx = 0, cy = 0, cz = 0;
        for (BlockPos p : payload.cells()) { cx += p.getX() + 0.5D; cy += p.getY() + 0.5D; cz += p.getZ() + 0.5D; }
        int n = payload.cells().size();
        cx /= n; cy /= n; cz /= n;
        double radius = PortalCinematics.MEMBRANE_RADIUS;
        int total = n * count;
        for (int i = 0; i < total; i++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double r = Math.sqrt(random.nextDouble()) * radius;
            double u = Math.cos(a) * r, v = Math.sin(a) * r;
            double depth = (random.nextDouble() - 0.5D) * 0.12D;
            double x = cx + (payload.xAxis() ? u : depth);
            double z = cz + (payload.xAxis() ? depth : u);
            double y = cy + v;
            level.addParticle(ModParticles.SPARK,
                    x + random.nextGaussian() * 0.02D, y + random.nextGaussian() * 0.02D, z + random.nextGaussian() * 0.02D,
                    0.0D, 0.0D, 0.0D);
        }
    }
}
