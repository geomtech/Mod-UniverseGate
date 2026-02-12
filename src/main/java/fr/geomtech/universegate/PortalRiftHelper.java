package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;

import java.util.Optional;

public final class PortalRiftHelper {

    private static final String RIFT_PORTAL_NAME = "Rift Anchor";
    private static final BlockPos RIFT_CORE_ANCHOR = new BlockPos(0, 0, 0);

    private PortalRiftHelper() {}

    public static Optional<ChargedLightningRodBlockEntity> findNearestChargedRod(ServerLevel level, BlockPos portalPos, int radius) {
        BlockPos fixed = portalPos.above(6);
        if (level.getBlockEntity(fixed) instanceof ChargedLightningRodBlockEntity fixedRod && fixedRod.hasCharge()) {
            return Optional.of(fixedRod);
        }
        ChargedLightningRodBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = portalPos.offset(dx, dy, dz);
                    if (!(level.getBlockEntity(p) instanceof ChargedLightningRodBlockEntity be)) continue;
                    if (!be.hasCharge()) continue;
                    double dist = p.distSqr(portalPos);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = be;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean tryConsumeChargeAndOpenRift(ServerLevel level, BlockPos corePos, int radius) {
        Optional<ChargedLightningRodBlockEntity> rod = findNearestChargedRod(level, corePos, radius);
        if (rod.isEmpty()) return false;
        boolean opened = openRiftPortal(level, corePos);
        if (opened) rod.get().consumeCharge(1);
        return opened;
    }

    public static void tryOpenRiftFromRod(ServerLevel level, BlockPos rodPos, int radius) {
        UniverseGate.LOGGER.info("Rift rod strike at {}", rodPos);
        BlockPos corePos = null;
        BlockPos below = rodPos.below();
        if (level.getBlockState(below).is(ModBlocks.PORTAL_FRAME)) {
            UniverseGate.LOGGER.info("Rod is on portal frame at {}", below);
            corePos = findCoreNear(level, below, radius, 5);
        }
        if (corePos == null) {
            BlockPos candidate = rodPos.below(6);
            if (level.getBlockState(candidate).is(ModBlocks.PORTAL_CORE)) {
                UniverseGate.LOGGER.info("Rod is 6 blocks above core at {}", candidate);
                corePos = candidate;
            } else {
                corePos = findCoreNear(level, rodPos, radius, 5);
                UniverseGate.LOGGER.info("Fallback core search near rod -> {}", corePos);
            }
        }
        if (corePos == null) {
            UniverseGate.LOGGER.warn("No portal core found near rod.");
            return;
        }
        if (!(level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)) return;
        if (core.isActive()) return;

        if (!(level.getBlockEntity(rodPos) instanceof ChargedLightningRodBlockEntity be)) return;
        if (!be.hasCharge()) return;

        UniverseGate.LOGGER.info("Attempting to open rift from core at {}", corePos);
        boolean opened = openRiftPortal(level, corePos);
        UniverseGate.LOGGER.info("Rift open result: {}", opened);
        if (opened) be.consumeCharge(1);
    }

    public static boolean openRiftPortal(ServerLevel sourceLevel, BlockPos sourceCorePos) {
        if (!(sourceLevel.getBlockEntity(sourceCorePos) instanceof PortalCoreBlockEntity core)) return false;
        if (core.isActive()) return false;

        ServerLevel rift = UniverseGateDimensions.getRiftLevel(sourceLevel.getServer());
        if (rift == null) {
            UniverseGate.LOGGER.warn("Rift dimension is null (not loaded)");
            return false;
        }

        PortalRegistrySavedData reg = PortalRegistrySavedData.get(sourceLevel.getServer());
        PortalRegistrySavedData.PortalEntry riftEntry = ensureRiftPortal(rift, reg);
        if (riftEntry == null) {
            UniverseGate.LOGGER.warn("Rift portal entry could not be created");
            return false;
        }

        return PortalConnectionManager.openBothSides(sourceLevel, sourceCorePos, riftEntry.id(), true);
    }

    public static void handleRiftArrival(ServerLevel riftLevel, BlockPos corePos) {
        if (!(riftLevel.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)) return;
        if (!core.isActive()) return;
        if (!core.isRiftLightningLink()) return;

        var match = PortalFrameDetector.find(riftLevel, corePos);

        // Close portal first (removes field + closes other side)
        PortalConnectionManager.forceCloseOneSide(riftLevel, corePos);

        // Small visual-only explosion
        double x = corePos.getX() + 0.5;
        double y = corePos.getY() + 1.0;
        double z = corePos.getZ() + 0.5;
        riftLevel.explode(
                null,
                riftLevel.damageSources().explosion(null),
                new ExplosionDamageCalculator() {
                    @Override
                    public boolean shouldDamageEntity(net.minecraft.world.level.Explosion explosion, net.minecraft.world.entity.Entity entity) {
                        return false;
                    }

                    @Override
                    public float getEntityDamageAmount(net.minecraft.world.level.Explosion explosion, net.minecraft.world.entity.Entity entity) {
                        return 0.0F;
                    }

                    @Override
                    public float getKnockbackMultiplier(net.minecraft.world.entity.Entity entity) {
                        return 0.0F;
                    }
                },
                x, y, z,
                1.8F,
                false,
                Level.ExplosionInteraction.NONE
        );

        // Destroy core
        riftLevel.destroyBlock(corePos, false);

        // Break 30% of frame blocks
        if (match.isPresent()) {
            var frameBlocks = PortalFrameHelper.collectFrame(match.get(), corePos);
            var rand = riftLevel.getRandom();
            for (BlockPos p : frameBlocks) {
                if (rand.nextFloat() <= 0.3F && riftLevel.getBlockState(p).is(ModBlocks.PORTAL_FRAME)) {
                    riftLevel.destroyBlock(p, false);
                }
            }
        }

        RiftScenarioGenerator.ensureGenerated(riftLevel, corePos);
    }

    private static PortalRegistrySavedData.PortalEntry ensureRiftPortal(ServerLevel rift, PortalRegistrySavedData reg) {
        PortalRegistrySavedData.PortalEntry existing = findRiftPortal(reg);
        if (existing != null) {
            rift.getChunk(existing.pos());
            if (!(rift.getBlockEntity(existing.pos()) instanceof PortalCoreBlockEntity)) {
                existing = null;
            } else if (!rift.getBlockState(existing.pos().below()).is(ModBlocks.VOID_BLOCK)) {
                UniverseGate.LOGGER.warn("Existing rift portal is not on void block ground; recreating.");
                existing = null;
            }
        }

        if (existing == null) {
            BlockPos corePos = findRiftCorePos(rift, RIFT_CORE_ANCHOR);
            UniverseGate.LOGGER.info("Creating rift portal at {}", corePos);
            rift.getChunk(corePos);
            placeRiftFrame(rift, corePos, Direction.EAST);
            if (!(rift.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)) return null;
            core.onPlaced();
            core.renamePortal(RIFT_PORTAL_NAME);
            reg.setHidden(core.getPortalId(), true);
            existing = reg.get(core.getPortalId());
        } else {
            UniverseGate.LOGGER.info("Using existing rift portal at {}", existing.pos());
            reg.setHidden(existing.id(), true);
        }

        return existing;
    }

    private static BlockPos findRiftCorePos(ServerLevel rift, BlockPos anchor) {
        BlockPos surface = findTopmostSurfaceNear(rift, anchor, 128);
        if (surface != null) {
            return surface.above();
        }
        UniverseGate.LOGGER.warn("No surface found near {} in rift; using fallback height", anchor);
        int y = rift.getMinBuildHeight() + 1;
        return new BlockPos(anchor.getX(), y, anchor.getZ());
    }

    private static BlockPos findTopmostSurfaceNear(ServerLevel level, BlockPos anchor, int radius) {
        BlockPos best = null;
        int bestY = level.getMinBuildHeight();
        double bestDist = Double.MAX_VALUE;
        int maxY = level.getMaxBuildHeight() - 2;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos column = anchor.offset(dx, 0, dz);
                int minY = level.getMinBuildHeight() + 1;
                for (int scanY = maxY; scanY >= minY; scanY--) {
                    BlockPos pos = new BlockPos(column.getX(), scanY, column.getZ());
                    if (level.getBlockState(pos).isAir()) continue;
                    if (!level.getBlockState(pos).is(ModBlocks.VOID_BLOCK)) continue;
                    if (!level.getBlockState(pos.above()).isAir()) continue;

                    double dist = pos.distSqr(anchor);
                    if (scanY > bestY || (scanY == bestY && dist < bestDist)) {
                        bestY = scanY;
                        bestDist = dist;
                        best = pos;
                    }
                    break;
                }
            }
        }

        if (best != null) {
            UniverseGate.LOGGER.info("Rift surface chosen at {}", best);
        }
        return best;
    }

    private static PortalRegistrySavedData.PortalEntry findRiftPortal(PortalRegistrySavedData reg) {
        for (PortalRegistrySavedData.PortalEntry entry : reg.listAll()) {
            if (entry.dim().equals(UniverseGateDimensions.RIFT)) {
                return entry;
            }
        }
        return null;
    }

    static void placeRiftFrame(ServerLevel level, BlockPos corePos, Direction right) {
        int halfWidth = 2;
        int topY = 5;

        for (int dy = 0; dy <= topY; dy++) {
            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                BlockPos p = corePos.offset(right.getStepX() * dx, dy, right.getStepZ() * dx);
                boolean isBorder = dy == 0 || dy == topY || dx == -halfWidth || dx == halfWidth;

                if (dx == 0 && dy == 0) {
                    level.setBlock(p, ModBlocks.PORTAL_CORE.defaultBlockState(), 3);
                    continue;
                }

                if (isBorder) {
                    level.setBlock(p, ModBlocks.PORTAL_FRAME.defaultBlockState(), 3);
                } else {
                    level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static BlockPos findCoreNear(ServerLevel level, BlockPos center, int rXZ, int rY) {
        for (int dy = -rY; dy <= rY; dy++) {
            for (int dx = -rXZ; dx <= rXZ; dx++) {
                for (int dz = -rXZ; dz <= rXZ; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(ModBlocks.PORTAL_CORE)) return p;
                }
            }
        }
        return null;
    }
}
