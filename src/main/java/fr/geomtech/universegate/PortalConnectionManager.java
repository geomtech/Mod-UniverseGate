package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public final class PortalConnectionManager {

    private static final long OPEN_DURATION_TICKS = 20L * 60L; // 1 minute

    private PortalConnectionManager() {}

    /**
     * Ouvre A <-> B (deux côtés) façon Stargate.
     * sourceLevel/sourcePos = core A (où le joueur est)
     * targetId = portail B choisi via UI
     */
    public static boolean openBothSides(ServerLevel sourceLevel, BlockPos sourceCorePos, UUID targetId) {
        MinecraftServer server = sourceLevel.getServer();
        PortalRegistrySavedData reg = PortalRegistrySavedData.get(server);

        // Récup source A
        if (!(sourceLevel.getBlockEntity(sourceCorePos) instanceof PortalCoreBlockEntity a)) return false;
        if (a.getPortalId() == null) return false;

        // Récup B via registre
        PortalRegistrySavedData.PortalEntry bEntry = reg.get(targetId);
        if (bEntry == null) return false;

        // Interdire self link
        if (a.getPortalId().equals(targetId)) return false;

        // Refuser si déjà actif
        if (a.isActive()) return false;

        // Charger la dimension B
        ServerLevel targetLevel = server.getLevel(bEntry.dim());
        if (targetLevel == null) return false;

        // Charger chunk du core B
        targetLevel.getChunk(bEntry.pos());

        if (!(targetLevel.getBlockEntity(bEntry.pos()) instanceof PortalCoreBlockEntity b)) return false;
        if (b.isActive()) return false;

        // Vérifier cadres (A et B)
        Optional<PortalFrameDetector.FrameMatch> frameA = PortalFrameDetector.find(sourceLevel, sourceCorePos);
        Optional<PortalFrameDetector.FrameMatch> frameB = PortalFrameDetector.find(targetLevel, bEntry.pos());
        if (frameA.isEmpty() || frameB.isEmpty()) return false;

        UUID connectionId = UUID.randomUUID();
        long nowA = sourceLevel.getGameTime();
        long nowB = targetLevel.getGameTime();
        long untilA = nowA + OPEN_DURATION_TICKS;
        long untilB = nowB + OPEN_DURATION_TICKS;

        // --- OUVERTURE “ATOMIC” (si B rate => rollback A) ---
        // 1) Place champ A
        if (!placeField(sourceLevel, frameA.get())) return false;

        // 2) Place champ B
        if (!placeField(targetLevel, frameB.get())) {
            // rollback A
            removeField(sourceLevel, frameA.get());
            return false;
        }

        // 3) Etat actif
        a.setActiveState(connectionId, b.getPortalId(), untilA);
        b.setActiveState(connectionId, a.getPortalId(), untilB);

        a.setChanged();
        b.setChanged();
        return true;
    }

    /** Ferme un portail (un côté) + tente de fermer le target si chargé. */
    public static void forceCloseOneSide(ServerLevel level, BlockPos corePos) {
        if (!(level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity a)) return;

        UUID targetId = a.getTargetPortalId();

        // Recalculer frame + retirer champ
        var match = PortalFrameDetector.find(level, corePos);
        if (match.isPresent()) removeField(level, match.get());
        else removeFieldFallback(level, corePos);
        a.clearActiveState();

        // Essayer de fermer l’autre côté (si on peut le charger)
        if (targetId != null) {
            closeOtherSideIfPossible(level.getServer(), targetId);
        }
    }

    private static void closeOtherSideIfPossible(MinecraftServer server, UUID targetId) {
        PortalRegistrySavedData reg = PortalRegistrySavedData.get(server);
        PortalRegistrySavedData.PortalEntry entry = reg.get(targetId);
        if (entry == null) return;

        ServerLevel targetLevel = server.getLevel(entry.dim());
        if (targetLevel == null) return;

        // On tente de charger le chunk (tu peux décider de ne PAS forcer la charge ici si tu veux)
        targetLevel.getChunk(entry.pos());

        if (!(targetLevel.getBlockEntity(entry.pos()) instanceof PortalCoreBlockEntity b)) return;

        var match = PortalFrameDetector.find(targetLevel, entry.pos());
        if (match.isPresent()) removeField(targetLevel, match.get());
        else removeFieldFallback(targetLevel, entry.pos());
        b.clearActiveState();
        b.setChanged();
    }

    // ----------------------------
    // Champ portal (à brancher ensuite)
    // ----------------------------
    private static boolean placeField(ServerLevel level, PortalFrameDetector.FrameMatch match) {
        for (BlockPos p : match.interior()) {
            level.setBlock(p, ModBlocks.PORTAL_FIELD.defaultBlockState(), 3);
        }
        return true;
    }

    private static void removeField(ServerLevel level, PortalFrameDetector.FrameMatch match) {
        for (BlockPos p : match.interior()) {
            if (level.getBlockState(p).is(ModBlocks.PORTAL_FIELD)) {
                level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void removeFieldFallback(ServerLevel level, BlockPos corePos) {
        removeFieldArea(level, corePos, net.minecraft.core.Direction.EAST);
        removeFieldArea(level, corePos, net.minecraft.core.Direction.SOUTH);
    }

    private static void removeFieldArea(ServerLevel level, BlockPos corePos, net.minecraft.core.Direction right) {
        for (int dy = 1; dy <= PortalFrameDetector.INNER_HEIGHT; dy++) {
            for (int dx = -PortalFrameDetector.INNER_WIDTH / 2; dx <= PortalFrameDetector.INNER_WIDTH / 2; dx++) {
                BlockPos p = corePos.offset(right.getStepX() * dx, dy, right.getStepZ() * dx);
                if (level.getBlockState(p).is(ModBlocks.PORTAL_FIELD)) {
                    level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

}
