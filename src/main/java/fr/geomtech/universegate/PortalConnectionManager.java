package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public final class PortalConnectionManager {

    private static final long OPEN_DURATION_TICKS = 20L * 60L; // 1 minute

    private PortalConnectionManager() {}

    /**
     * Ouvre A => B (sens unique) en activant les deux côtés.
     * sourceLevel/sourcePos = core A (origine, où le joueur est)
     * targetId = portail B (destination) choisi via UI
     */
    public static boolean openBothSides(ServerLevel sourceLevel, BlockPos sourceCorePos, UUID targetId) {
        return openBothSides(sourceLevel, sourceCorePos, targetId, false);
    }

    public static boolean openBothSides(ServerLevel sourceLevel, BlockPos sourceCorePos, UUID targetId, boolean riftLightningLink) {
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
        if (!placeField(sourceLevel, frameA.get(), riftLightningLink)) return false;

        // 2) Place champ B
        if (!placeField(targetLevel, frameB.get(), riftLightningLink)) {
            // rollback A
            removeField(sourceLevel, frameA.get());
            return false;
        }

        // 3) Etat actif
        a.setActiveState(connectionId, b.getPortalId(), untilA, riftLightningLink, true);
        b.setActiveState(connectionId, a.getPortalId(), untilB, riftLightningLink, false);

        setFrameActive(sourceLevel, frameA.get(), sourceCorePos, true, riftLightningLink);
        setFrameActive(targetLevel, frameB.get(), bEntry.pos(), true, riftLightningLink);

        ModSounds.playAt(sourceLevel, sourceCorePos, ModSounds.PORTAL_OPENING, 1.0F, 1.0F);
        ModSounds.playAt(targetLevel, bEntry.pos(), ModSounds.PORTAL_OPENING, 1.0F, 1.0F);
        ModSounds.playPortalAmbientAt(sourceLevel, sourceCorePos, riftLightningLink);
        ModSounds.playPortalAmbientAt(targetLevel, bEntry.pos(), riftLightningLink);

        a.setChanged();
        b.setChanged();
        return true;
    }

    /** Ferme un portail (un côté) + tente de fermer le target si chargé. */
    public static void forceCloseOneSide(ServerLevel level, BlockPos corePos) {
        if (!(level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity a)) return;

        UUID targetId = a.getTargetPortalId();
        boolean unstable = a.isRiftLightningLink();

        // Recalculer frame + retirer champ
        var match = PortalFrameDetector.find(level, corePos);
        if (match.isPresent()) {
            removeField(level, match.get());
            setFrameActive(level, match.get(), corePos, false, false);
        } else {
            removeFieldFallback(level, corePos);
            setFrameActiveFallback(level, corePos, false, false);
        }
        if (a.isActive()) {
            ModSounds.playAt(level, corePos, ModSounds.PORTAL_CLOSING, 1.0F, 1.0F);
        }
        ModSounds.stopPortalAmbientNear(level, corePos, unstable);
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
        boolean unstable = b.isRiftLightningLink();

        var match = PortalFrameDetector.find(targetLevel, entry.pos());
        if (match.isPresent()) {
            removeField(targetLevel, match.get());
            setFrameActive(targetLevel, match.get(), entry.pos(), false, false);
        } else {
            removeFieldFallback(targetLevel, entry.pos());
            setFrameActiveFallback(targetLevel, entry.pos(), false, false);
        }
        if (b.isActive()) {
            ModSounds.playAt(targetLevel, entry.pos(), ModSounds.PORTAL_CLOSING, 1.0F, 1.0F);
        }
        ModSounds.stopPortalAmbientNear(targetLevel, entry.pos(), unstable);
        b.clearActiveState();
        b.setChanged();
    }

    // ----------------------------
    // Champ portal (à brancher ensuite)
    // ----------------------------
    private static boolean placeField(ServerLevel level, PortalFrameDetector.FrameMatch match, boolean unstable) {
        var axis = match.right() == net.minecraft.core.Direction.EAST
                ? net.minecraft.core.Direction.Axis.X
                : net.minecraft.core.Direction.Axis.Z;
        var fieldState = ModBlocks.PORTAL_FIELD.defaultBlockState()
                .setValue(PortalFieldBlock.AXIS, axis)
                .setValue(PortalFieldBlock.UNSTABLE, unstable);
        for (BlockPos p : match.interior()) {
            level.setBlock(p, fieldState, 3);
        }
        return true;
    }

    private static void removeField(ServerLevel level, PortalFrameDetector.FrameMatch match) {
        for (BlockPos p : match.interior()) {
            var state = level.getBlockState(p);
            if (state.is(ModBlocks.PORTAL_FIELD)) {
                var axis = state.hasProperty(PortalFieldBlock.AXIS)
                        ? state.getValue(PortalFieldBlock.AXIS)
                        : (match.right() == net.minecraft.core.Direction.EAST
                        ? net.minecraft.core.Direction.Axis.X
                        : net.minecraft.core.Direction.Axis.Z);
                boolean unstable = state.hasProperty(PortalFieldBlock.UNSTABLE)
                        && state.getValue(PortalFieldBlock.UNSTABLE);
                spawnFieldCollapseParticles(level, p, axis, unstable);
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
                var state = level.getBlockState(p);
                if (state.is(ModBlocks.PORTAL_FIELD)) {
                    var axis = state.hasProperty(PortalFieldBlock.AXIS)
                            ? state.getValue(PortalFieldBlock.AXIS)
                            : (right == net.minecraft.core.Direction.EAST
                            ? net.minecraft.core.Direction.Axis.X
                            : net.minecraft.core.Direction.Axis.Z);
                    boolean unstable = state.hasProperty(PortalFieldBlock.UNSTABLE)
                            && state.getValue(PortalFieldBlock.UNSTABLE);
                    spawnFieldCollapseParticles(level, p, axis, unstable);
                    level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void spawnFieldCollapseParticles(ServerLevel level,
                                                    BlockPos pos,
                                                    net.minecraft.core.Direction.Axis axis,
                                                    boolean unstable) {
        RandomSource random = level.random;
        int count = unstable ? 12 : 8;

        for (int i = 0; i < count; i++) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + 0.5;

            if (axis == net.minecraft.core.Direction.Axis.X) {
                x += (random.nextDouble() - 0.5) * 0.9;
                z += (random.nextDouble() - 0.5) * 0.12;
            } else {
                x += (random.nextDouble() - 0.5) * 0.12;
                z += (random.nextDouble() - 0.5) * 0.9;
            }

            double vx = (random.nextDouble() - 0.5) * 0.05;
            double vy = (random.nextDouble() - 0.5) * 0.04;
            double vz = (random.nextDouble() - 0.5) * 0.05;
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, vx, vy, vz, 0.0);
        }
    }

    private static void setFrameActive(ServerLevel level,
                                       PortalFrameDetector.FrameMatch match,
                                       BlockPos corePos,
                                       boolean active,
                                       boolean unstable) {
        for (BlockPos p : PortalFrameHelper.collectFrame(match, corePos)) {
            setFrameBlockState(level, p, active, unstable);
        }
    }

    private static void setFrameActiveFallback(ServerLevel level, BlockPos corePos, boolean active, boolean unstable) {
        setFrameAreaActive(level, corePos, net.minecraft.core.Direction.EAST, active, unstable);
        setFrameAreaActive(level, corePos, net.minecraft.core.Direction.SOUTH, active, unstable);
    }

    private static void setFrameAreaActive(ServerLevel level,
                                           BlockPos corePos,
                                           net.minecraft.core.Direction right,
                                           boolean active,
                                           boolean unstable) {
        int halfWidth = PortalFrameDetector.INNER_WIDTH / 2 + 1;
        int topY = PortalFrameDetector.INNER_HEIGHT + 1;

        for (int dy = 0; dy <= topY; dy++) {
            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                boolean isBorder = dy == 0 || dy == topY || dx == -halfWidth || dx == halfWidth;
                if (!isBorder || (dx == 0 && dy == 0)) continue;

                BlockPos p = corePos.offset(right.getStepX() * dx, dy, right.getStepZ() * dx);
                setFrameBlockState(level, p, active, unstable);
            }
        }
    }

    private static void setFrameBlockState(ServerLevel level, BlockPos pos, boolean active, boolean unstable) {
        var state = level.getBlockState(pos);
        if (!state.is(ModBlocks.PORTAL_FRAME)) return;
        if (!state.hasProperty(PortalFrameBlock.ACTIVE)
                || !state.hasProperty(PortalFrameBlock.UNSTABLE)
                || !state.hasProperty(PortalFrameBlock.BLINK_ON)) {
            return;
        }

        boolean blinkOn = active && unstable;

        var updated = state
                .setValue(PortalFrameBlock.ACTIVE, active)
                .setValue(PortalFrameBlock.UNSTABLE, unstable)
                .setValue(PortalFrameBlock.BLINK_ON, blinkOn);
        if (state.equals(updated)) return;

        level.setBlock(pos, updated, 3);
    }

}
