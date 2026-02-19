package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Optional;
import java.util.UUID;

public final class PortalConnectionManager {

    private static final long ACTIVE_OPEN_DURATION_TICKS = 20L * 60L; // 1 minute
    private static final long OPENING_DURATION_TICKS = 20L * 9L;
    private static final long OPENING_BLACKOUT_TICKS = 20L * 2L;
    private static final double OPENING_PARTICLE_EXPONENT = 4.0D;
    private static final int OPENING_MAX_PARTICLES_PER_CELL = 7;
    private static final int KEYBOARD_RADIUS_XZ = 8;
    private static final int KEYBOARD_RADIUS_Y = 4;

    private PortalConnectionManager() {}

    /**
     * Ouvre A => B (sens unique) en activant les deux côtés.
     * sourceLevel/sourcePos = core A (origine, où le joueur est)
     * targetId = portail B (destination) choisi via UI
     */
    public static boolean openBothSides(ServerLevel sourceLevel, BlockPos sourceCorePos, UUID targetId) {
        return openBothSides(sourceLevel, sourceCorePos, targetId, false, false);
    }

    public static boolean openBothSides(ServerLevel sourceLevel, BlockPos sourceCorePos, UUID targetId, boolean riftLightningLink) {
        return openBothSides(sourceLevel, sourceCorePos, targetId, riftLightningLink, false);
    }

    public static boolean openBothSides(ServerLevel sourceLevel,
                                        BlockPos sourceCorePos,
                                        UUID targetId,
                                        boolean riftLightningLink,
                                        boolean sourceMaintenanceEnergyBypass) {
        MinecraftServer server = sourceLevel.getServer();
        PortalRegistrySavedData reg = PortalRegistrySavedData.get(server);

        // Récup source A
        if (!(sourceLevel.getBlockEntity(sourceCorePos) instanceof PortalCoreBlockEntity a)) return false;
        if (a.getPortalId() == null) {
            a.renamePortal(a.getPortalName());
            if (a.getPortalId() == null) return false;
        }

        // Récup B via registre
        PortalRegistrySavedData.PortalEntry bEntry = reg.get(targetId);
        if (bEntry == null) return false;

        // Interdire self link
        if (a.getPortalId().equals(targetId)) return false;

        // Refuser si déjà actif/en cours d’ouverture
        if (a.isActiveOrOpening()) return false;

        // Charger la dimension B
        ServerLevel targetLevel = server.getLevel(bEntry.dim());
        if (targetLevel == null) {
            reg.removePortal(targetId);
            return false;
        }

        // Charger chunk du core B
        targetLevel.getChunk(bEntry.pos());

        if (!(targetLevel.getBlockEntity(bEntry.pos()) instanceof PortalCoreBlockEntity b)) {
            reg.removePortal(targetId);
            return false;
        }
        UUID resolvedTargetId = targetId;
        if (b.getPortalId() == null) {
            b.renamePortal(b.getPortalName());
            if (b.getPortalId() == null) {
                reg.removePortal(targetId);
                return false;
            }
        }
        if (!targetId.equals(b.getPortalId())) {
            reg.removePortal(targetId);
            reg.upsertPortal(targetLevel, b.getPortalId(), b.getPortalName(), bEntry.pos(), bEntry.hidden());
            resolvedTargetId = b.getPortalId();
        }
        if (a.getPortalId().equals(resolvedTargetId)) {
            return false;
        }
        if (b.isActiveOrOpening()) return false;

        // Vérifier cadres (A et B)
        Optional<PortalFrameDetector.FrameMatch> frameA = PortalFrameDetector.find(sourceLevel, sourceCorePos);
        Optional<PortalFrameDetector.FrameMatch> frameB = PortalFrameDetector.find(targetLevel, bEntry.pos());
        if (frameA.isEmpty() || frameB.isEmpty()) return false;

        UUID connectionId = UUID.randomUUID();
        long nowA = sourceLevel.getGameTime();
        long nowB = targetLevel.getGameTime();
        long openingCompleteA = nowA + OPENING_DURATION_TICKS;
        long openingCompleteB = nowB + OPENING_DURATION_TICKS;

        a.setOpeningState(connectionId, b.getPortalId(), nowA, openingCompleteA, riftLightningLink, true, sourceMaintenanceEnergyBypass);
        b.setOpeningState(connectionId, a.getPortalId(), nowB, openingCompleteB, riftLightningLink, false, false);

        setFrameActive(sourceLevel, frameA.get(), sourceCorePos, true, riftLightningLink);
        setFrameActive(targetLevel, frameB.get(), bEntry.pos(), true, riftLightningLink);
        setNearbyKeyboardsLit(sourceLevel, sourceCorePos, true);
        setNearbyKeyboardsLit(targetLevel, bEntry.pos(), true);

        ModSounds.playPortalDialingAt(sourceLevel, sourceCorePos);
        ModSounds.playPortalDialingAt(targetLevel, bEntry.pos());

        a.setChanged();
        b.setChanged();
        return true;
    }

    static void tickOpeningSequence(ServerLevel level, BlockPos corePos, PortalCoreBlockEntity core) {
        if (!core.isOpening()) return;

        long now = level.getGameTime();
        Optional<PortalFrameDetector.FrameMatch> frame = PortalFrameDetector.find(level, corePos);
        if (frame.isEmpty()) {
            forceCloseOneSide(level, corePos);
            return;
        }

        spawnOpeningParticles(level, frame.get(), now, core);

        if (now < core.getOpeningCompleteGameTime()) return;

        finalizeOpening(level, corePos, core);
    }

    private static void finalizeOpening(ServerLevel sourceLevel, BlockPos sourceCorePos, PortalCoreBlockEntity sourceCore) {
        if (!sourceCore.isOpening()) return;

        UUID targetId = sourceCore.getTargetPortalId();
        if (targetId == null) {
            forceCloseOneSide(sourceLevel, sourceCorePos);
            return;
        }

        MinecraftServer server = sourceLevel.getServer();
        PortalRegistrySavedData reg = PortalRegistrySavedData.get(server);
        PortalRegistrySavedData.PortalEntry targetEntry = reg.get(targetId);
        if (targetEntry == null) {
            forceCloseOneSide(sourceLevel, sourceCorePos);
            return;
        }

        ServerLevel targetLevel = server.getLevel(targetEntry.dim());
        if (targetLevel == null) {
            forceCloseOneSide(sourceLevel, sourceCorePos);
            return;
        }

        targetLevel.getChunk(targetEntry.pos());
        if (!(targetLevel.getBlockEntity(targetEntry.pos()) instanceof PortalCoreBlockEntity targetCore)
                || !targetCore.isOpening()) {
            forceCloseOneSide(sourceLevel, sourceCorePos);
            return;
        }

        UUID connectionId = sourceCore.getConnectionId();
        if (connectionId == null || !connectionId.equals(targetCore.getConnectionId())) {
            forceCloseOneSide(sourceLevel, sourceCorePos);
            return;
        }

        if (sourceLevel.getGameTime() < sourceCore.getOpeningCompleteGameTime()
                || targetLevel.getGameTime() < targetCore.getOpeningCompleteGameTime()) {
            return;
        }

        Optional<PortalFrameDetector.FrameMatch> frameA = PortalFrameDetector.find(sourceLevel, sourceCorePos);
        Optional<PortalFrameDetector.FrameMatch> frameB = PortalFrameDetector.find(targetLevel, targetEntry.pos());
        if (frameA.isEmpty() || frameB.isEmpty()) {
            forceCloseOneSide(sourceLevel, sourceCorePos);
            return;
        }

        boolean unstableA = sourceCore.isRiftLightningLink();
        boolean unstableB = targetCore.isRiftLightningLink();

        if (!placeField(sourceLevel, frameA.get(), unstableA)) {
            forceCloseOneSide(sourceLevel, sourceCorePos);
            return;
        }
        if (!placeField(targetLevel, frameB.get(), unstableB)) {
            removeField(sourceLevel, frameA.get());
            forceCloseOneSide(sourceLevel, sourceCorePos);
            return;
        }

        long untilA = sourceLevel.getGameTime() + ACTIVE_OPEN_DURATION_TICKS;
        long untilB = targetLevel.getGameTime() + ACTIVE_OPEN_DURATION_TICKS;

        sourceCore.finalizeOpeningState(untilA, sourceLevel.getGameTime());
        targetCore.finalizeOpeningState(untilB, targetLevel.getGameTime());

        setFrameActive(sourceLevel, frameA.get(), sourceCorePos, true, unstableA);
        setFrameActive(targetLevel, frameB.get(), targetEntry.pos(), true, unstableB);
        setNearbyKeyboardsLit(sourceLevel, sourceCorePos, true);
        setNearbyKeyboardsLit(targetLevel, targetEntry.pos(), true);

        ModSounds.playPortalOpenedAt(sourceLevel, sourceCorePos, unstableA);
        ModSounds.playPortalOpenedAt(targetLevel, targetEntry.pos(), unstableB);
        triggerRiftOpeningLightning(sourceLevel, sourceCorePos);
        triggerRiftOpeningLightning(targetLevel, targetEntry.pos());
        sourceCore.onPortalAmbientStarted(sourceLevel.getGameTime());
        targetCore.onPortalAmbientStarted(targetLevel.getGameTime());

        sourceCore.setChanged();
        targetCore.setChanged();
    }

    private static void triggerRiftOpeningLightning(ServerLevel level, BlockPos corePos) {
        if (!EnergyNetworkHelper.isRiftDimension(level)) return;

        LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(level);
        if (lightningBolt == null) return;

        BlockPos strikePos = corePos.above();
        lightningBolt.moveTo(strikePos.getX() + 0.5D, strikePos.getY(), strikePos.getZ() + 0.5D);
        lightningBolt.setVisualOnly(true);
        level.addFreshEntity(lightningBolt);
    }

    private static void spawnOpeningParticles(ServerLevel level,
                                              PortalFrameDetector.FrameMatch match,
                                              long now,
                                              PortalCoreBlockEntity core) {
        long openingStart = core.getOpeningStartedGameTime();
        long openingEnd = core.getOpeningCompleteGameTime();
        if (openingEnd <= openingStart) {
            openingStart = now;
            openingEnd = now + OPENING_DURATION_TICKS;
        }

        long duration = Math.max(1L, openingEnd - openingStart);
        double progress = Mth.clamp((double) (now - openingStart) / (double) duration, 0.0D, 1.0D);
        double growth = Math.expm1(progress * OPENING_PARTICLE_EXPONENT) / Math.expm1(OPENING_PARTICLE_EXPONENT);

        int baseCount = (int) Math.floor(growth * OPENING_MAX_PARTICLES_PER_CELL);
        long blackStart = openingEnd - OPENING_BLACKOUT_TICKS;
        double blackRatio = now <= blackStart
                ? 0.0D
                : Mth.clamp((double) (now - blackStart) / (double) Math.max(1L, OPENING_BLACKOUT_TICKS), 0.0D, 1.0D);

        Direction.Axis axis = match.right() == Direction.EAST ? Direction.Axis.X : Direction.Axis.Z;
        RandomSource random = level.random;

        for (BlockPos p : match.interior()) {
            int count = baseCount;
            if (random.nextDouble() < growth) count++;
            if (count <= 0) continue;

            int blackCount = blackRatio <= 0.0D ? 0 : (int) Math.round(count * blackRatio);
            blackCount = Math.min(blackCount, count);
            int whiteCount = count - blackCount;

            if (whiteCount > 0) {
                sendOpeningParticles(level, p, axis, ParticleTypes.END_ROD, whiteCount, 0.002D);
            }
            if (blackCount > 0) {
                sendOpeningParticles(level, p, axis, ParticleTypes.SQUID_INK, blackCount, 0.01D);
            }
        }
    }

    private static void sendOpeningParticles(ServerLevel level,
                                             BlockPos pos,
                                             Direction.Axis axis,
                                             ParticleOptions particle,
                                             int count,
                                             double speed) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        double xSpread = axis == Direction.Axis.X ? 0.43D : 0.06D;
        double ySpread = 0.46D;
        double zSpread = axis == Direction.Axis.X ? 0.06D : 0.43D;

        level.sendParticles(particle, x, y, z, count, xSpread, ySpread, zSpread, speed);
    }

    /** Ferme un portail (un côté) + tente de fermer le target si chargé. */
    public static void forceCloseOneSide(ServerLevel level, BlockPos corePos) {
        if (!(level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity a)) return;

        UUID targetId = a.getTargetPortalId();

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
        ModSounds.stopPortalLifecycleNear(level, corePos);
        a.clearActiveState();
        setNearbyKeyboardsLit(level, corePos, false);

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
        ModSounds.stopPortalLifecycleNear(targetLevel, entry.pos());
        b.clearActiveState();
        setNearbyKeyboardsLit(targetLevel, entry.pos(), false);
        b.setChanged();
    }

    static void syncActivePortalInstability(ServerLevel level, BlockPos corePos, boolean unstable) {
        var match = PortalFrameDetector.find(level, corePos);
        if (match.isPresent()) {
            setFieldInstability(level, match.get(), unstable);
            setFrameActive(level, match.get(), corePos, true, unstable);
            return;
        }

        setFieldInstabilityFallback(level, corePos, unstable);
        setFrameActiveFallback(level, corePos, true, unstable);
    }

    public static void syncKeyboardLitFromNearbyCore(ServerLevel level, BlockPos keyboardPos) {
        boolean lit = hasActiveCoreNear(level, keyboardPos, KEYBOARD_RADIUS_XZ, KEYBOARD_RADIUS_Y);
        setKeyboardLit(level, keyboardPos, lit);
    }

    static void setNearbyKeyboardsLit(ServerLevel level, BlockPos corePos, boolean lit) {
        for (int dy = -KEYBOARD_RADIUS_Y; dy <= KEYBOARD_RADIUS_Y; dy++) {
            for (int dx = -KEYBOARD_RADIUS_XZ; dx <= KEYBOARD_RADIUS_XZ; dx++) {
                for (int dz = -KEYBOARD_RADIUS_XZ; dz <= KEYBOARD_RADIUS_XZ; dz++) {
                    BlockPos p = corePos.offset(dx, dy, dz);
                    var state = level.getBlockState(p);
                    if (!isKeyboardBlock(state)) continue;
                    if (lit) {
                        setKeyboardLit(level, p, true);
                    } else {
                        syncKeyboardLitFromNearbyCore(level, p);
                    }
                }
            }
        }
    }

    public static void forceCloseFromKeyboard(ServerLevel level, BlockPos keyboardPos) {
        BlockPos corePos = findActiveCoreNear(level, keyboardPos, KEYBOARD_RADIUS_XZ, KEYBOARD_RADIUS_Y);
        if (corePos == null) return;

        if (!(level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)) return;
        if (core.isRiftLightningLink()) return;
        if (!core.isOutboundTravelEnabled()) return;

        forceCloseOneSide(level, corePos);
    }

    private static void setKeyboardLit(ServerLevel level, BlockPos keyboardPos, boolean lit) {
        var state = level.getBlockState(keyboardPos);
        if (!isKeyboardBlock(state) || !state.hasProperty(BlockStateProperties.LIT)) return;
        if (state.getValue(BlockStateProperties.LIT) == lit) return;

        level.setBlock(keyboardPos, state.setValue(BlockStateProperties.LIT, lit), 3);
    }

    private static boolean isKeyboardBlock(BlockState state) {
        return state.is(ModBlocks.PORTAL_KEYBOARD) || state.is(ModBlocks.PORTAL_NATURAL_KEYBOARD);
    }

    private static boolean hasActiveCoreNear(ServerLevel level, BlockPos center, int rXZ, int rY) {
        return findActiveCoreNear(level, center, rXZ, rY) != null;
    }

    private static BlockPos findActiveCoreNear(ServerLevel level, BlockPos center, int rXZ, int rY) {
        for (int dy = -rY; dy <= rY; dy++) {
            for (int dx = -rXZ; dx <= rXZ; dx++) {
                for (int dz = -rXZ; dz <= rXZ; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(ModBlocks.PORTAL_CORE)
                            && level.getBlockEntity(p) instanceof PortalCoreBlockEntity core
                            && core.isActiveOrOpening()) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    // ----------------------------
    // Champ portal (à brancher ensuite)
    // ----------------------------
    private static boolean placeField(ServerLevel level, PortalFrameDetector.FrameMatch match, boolean unstable) {
        var axis = match.right() == net.minecraft.core.Direction.EAST
                ? net.minecraft.core.Direction.Axis.X
                : net.minecraft.core.Direction.Axis.Z;
        var baseFieldState = ModBlocks.PORTAL_FIELD.defaultBlockState()
                .setValue(PortalFieldBlock.AXIS, axis)
                .setValue(PortalFieldBlock.UNSTABLE, unstable);
        for (BlockPos p : match.interior()) {
            boolean waterlogged = level.getFluidState(p).is(FluidTags.WATER);
            level.setBlock(p, baseFieldState.setValue(PortalFieldBlock.WATERLOGGED, waterlogged), 3);
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
                boolean waterlogged = state.hasProperty(PortalFieldBlock.WATERLOGGED)
                        && state.getValue(PortalFieldBlock.WATERLOGGED);
                level.setBlockAndUpdate(
                        p,
                        waterlogged
                                ? net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()
                                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                );
            }
        }
    }

    private static void setFieldInstability(ServerLevel level, PortalFrameDetector.FrameMatch match, boolean unstable) {
        for (BlockPos p : match.interior()) {
            var state = level.getBlockState(p);
            if (!state.is(ModBlocks.PORTAL_FIELD) || !state.hasProperty(PortalFieldBlock.UNSTABLE)) continue;
            if (state.getValue(PortalFieldBlock.UNSTABLE) == unstable) continue;
            level.setBlock(p, state.setValue(PortalFieldBlock.UNSTABLE, unstable), 3);
        }
    }

    private static void setFieldInstabilityFallback(ServerLevel level, BlockPos corePos, boolean unstable) {
        setFieldAreaInstability(level, corePos, net.minecraft.core.Direction.EAST, unstable);
        setFieldAreaInstability(level, corePos, net.minecraft.core.Direction.SOUTH, unstable);
    }

    private static void setFieldAreaInstability(ServerLevel level,
                                                BlockPos corePos,
                                                net.minecraft.core.Direction right,
                                                boolean unstable) {
        for (int dy = 1; dy <= PortalFrameDetector.INNER_HEIGHT; dy++) {
            for (int dx = -PortalFrameDetector.INNER_WIDTH / 2; dx <= PortalFrameDetector.INNER_WIDTH / 2; dx++) {
                BlockPos p = corePos.offset(right.getStepX() * dx, dy, right.getStepZ() * dx);
                var state = level.getBlockState(p);
                if (!state.is(ModBlocks.PORTAL_FIELD) || !state.hasProperty(PortalFieldBlock.UNSTABLE)) continue;
                if (state.getValue(PortalFieldBlock.UNSTABLE) == unstable) continue;
                level.setBlock(p, state.setValue(PortalFieldBlock.UNSTABLE, unstable), 3);
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
                    boolean waterlogged = state.hasProperty(PortalFieldBlock.WATERLOGGED)
                            && state.getValue(PortalFieldBlock.WATERLOGGED);
                    level.setBlockAndUpdate(
                            p,
                            waterlogged
                                    ? net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()
                                    : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    );
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
