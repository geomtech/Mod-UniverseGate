package fr.geomtech.universegate;

import fr.geomtech.universegate.compat.DataReader;
import fr.geomtech.universegate.compat.DataWriter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
//? if >=1.21.11 {
/*import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?}
import fr.geomtech.universegate.platform.PosMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PortalCoreBlockEntity extends BlockEntity implements PosMenuProvider {

    private static final long PER_ENTITY_EXTENSION_TICKS = 20L * 10L;
    private static final long UNSTABLE_THRESHOLD_TICKS = 20L * 60L;
    private static final long POWER_LOSS_COLLAPSE_TICKS = 20L * 45L;
    private static final long POWER_LOSS_ENTITY_PENALTY_TICKS = 20L * 5L;
    private static final long POWER_LOSS_ENTITY_PENALTY_COOLDOWN_TICKS = 20L;
    private static final long POWER_LOSS_MIN_REMAINING_AFTER_PENALTY_TICKS = 20L;
    private static final long POWER_LOSS_RECOVERY_MIN_ACTIVE_TICKS = 20L * 15L;
    private static final long OPENING_DURATION_FALLBACK_TICKS = 20L * 9L;
    private static final long PLAYER_PASS_CHUNK_FORCE_TICKS = 20L * 15L;
    private static final long TICK_DURATION_MILLIS = 50L;

    private UUID portalId;
    private String portalName = "";

    // Stargate state
    private boolean active = false;
    private UUID connectionId = null;
    private UUID targetPortalId = null;
    private long activeUntilGameTime = 0L; // fermeture auto
    private long activeUntilEpochMillis = 0L;
    private long activeStartedGameTime = 0L;
    private boolean visualUnstable = false;
    private boolean powerLossUnstable = false;
    private long lastPowerLossPenaltyGameTime = Long.MIN_VALUE;
    private boolean riftLightningLink = false;
    private boolean outboundTravelEnabled = false;
    private boolean maintenanceEnergyBypass = false;
    private boolean opening = false;
    private long openingStartedGameTime = 0L;
    private long openingCompleteGameTime = 0L;
    private long keepChunkForcedUntilGameTime = 0L;
    private boolean restorePending = false;
    private final List<BlockPos> wiredFieldCells = new ArrayList<>();
    private boolean outboundChunkForceClaimed = false;
    private int darkEnergyAmount = 0;
    public static final int DARK_ENERGY_THRESHOLD = 400;

    public PortalCoreBlockEntity(BlockPos pos, BlockState state) {

        super(ModBlockEntities.PORTAL_CORE, pos, state);
    }

    public int addDarkEnergy(int amount) {
        if (amount <= 0) return 0;

        int space = Math.max(0, DARK_ENERGY_THRESHOLD - darkEnergyAmount);
        int accepted = Math.min(amount, space);
        darkEnergyAmount += accepted;
        if (accepted > 0) setChanged();
        return accepted;
    }

    public boolean isDarkEnergyComplete() {
        return darkEnergyAmount >= DARK_ENERGY_THRESHOLD;
    }

    public int getDarkEnergyAmount() {
        return darkEnergyAmount;
    }

    // ---------- Lifecycle ----------

    public void onPlaced() {
        if (!(level instanceof ServerLevel sl)) return;

        if (portalId == null) portalId = UUID.randomUUID();
        PortalRegistrySavedData.get(sl.getServer()).upsertPortal(sl, portalId, portalName, worldPosition);
        setChanged();
    }

    public void onBroken() {
        if (!(level instanceof ServerLevel sl)) return;
        // Le Core part : le cadre redevient visible (sans toucher au Core, en cours de retrait).
        applyFormed(sl, net.minecraft.core.Direction.EAST, false);
        applyFormed(sl, net.minecraft.core.Direction.SOUTH, false);

        // Si actif, ferme localement (et tente de fermer l’autre côté si possible)
        if (isActiveOrOpening()) {
            PortalConnectionManager.forceCloseOneSide(sl, worldPosition);
        }
        keepChunkForcedUntilGameTime = 0L;
        syncOutboundChunkForce(sl);
        PortalRegistrySavedData.get(sl.getServer()).removePortal(portalId);
        setChanged();
    }

    // ---------- Portail construit (anneau 3D) ----------
    private net.minecraft.core.Direction formedRight;
    // Quand le cadre est complet, le Core et les blocs du cadre passent en FORMED : ils deviennent invisibles
    // et PortalGateRenderer dessine l'anneau 3D à leur place.
    private void refreshFormation(ServerLevel sl) {
        net.minecraft.core.Direction right = PortalFrameDetector.find(sl, worldPosition)
                .map(PortalFrameDetector.FrameMatch::right)
                .orElse(null);
        boolean wasFormed = getBlockState().is(ModBlocks.PORTAL_CORE) && getBlockState().getValue(PortalCoreBlock.FORMED);
        if (right == null) {
            if (wasFormed) clearFormation(sl);
            return;
        }
        // Clearing the perpendicular footprint every tick can unform the neighbouring gate's clip support.
        if (formedRight != null && formedRight != right) applyFormed(sl, formedRight, false);
        formedRight = right;
        applyFormed(sl, right, true);
        setCoreFormed(sl, true);
    }

    private void clearFormation(ServerLevel sl) {
        formedRight = null;
        applyFormed(sl, net.minecraft.core.Direction.EAST, false);
        applyFormed(sl, net.minecraft.core.Direction.SOUTH, false);
        setCoreFormed(sl, false);
    }

    private void applyFormed(ServerLevel sl, net.minecraft.core.Direction right, boolean formed) {
        PortalFrameDetector.FrameMatch match = new PortalFrameDetector.FrameMatch(right, java.util.List.of());
        for (BlockPos p : PortalFrameHelper.collectFrame(match, worldPosition)) {
            BlockState state = sl.getBlockState(p);
            if (!state.is(ModBlocks.PORTAL_FRAME)) continue;
            BlockState updated = state
                    .setValue(PortalFrameBlock.FORMED, formed)
                    .setValue(PortalFrameBlock.BLINK_ON, false);
            if (!updated.equals(state)) {
                sl.setBlock(p, updated, 3);
            }
        }
    }

    private void setCoreFormed(ServerLevel sl, boolean formed) {
        BlockState state = getBlockState();
        if (state.is(ModBlocks.PORTAL_CORE) && state.getValue(PortalCoreBlock.FORMED) != formed) {
            sl.setBlock(worldPosition, state.setValue(PortalCoreBlock.FORMED, formed), 3);
        }
    }

    // ---------- Ticking ----------
    // Côté client uniquement : PortalAmbientSoundClient (type Object pour ne pas charger
    // de classe client sur un serveur dédié).
    private Object clientAmbientSound;
    private PortalCinematics.State clientCinematics;
    // Animation de l'anneau 3D (rotation, vapeur) : PortalGateAnimator, côté client uniquement.
    private Object clientGateAnimator;
    // Valeurs synchronisées pour la mise en scène côté client (PortalCinematics).
    private long clientActiveUntil;
    private int linkSteps = 4;

    public void clientTick() {
        if (level == null || !level.isClientSide()) return;
        if (opening) {
            PortalOpeningParticles.clientTick(level, worldPosition, this);
        }
        if (opening || active) {
            if (clientCinematics == null) clientCinematics = new PortalCinematics.State();
            PortalCinematics.clientTick(level, this, clientCinematics);
        } else {
            clientCinematics = null;
        }
        if (getBlockState().is(ModBlocks.PORTAL_CORE) && getBlockState().getValue(PortalCoreBlock.FORMED)) {
            if (clientGateAnimator == null) clientGateAnimator = new PortalGateAnimator();
            ((PortalGateAnimator) clientGateAnimator).tick(level, this);
        } else {
            clientGateAnimator = null;
        }
        if (active) {
            if (clientAmbientSound == null) clientAmbientSound = new PortalAmbientSoundClient();
            ((PortalAmbientSoundClient) clientAmbientSound).tick(this, level.getGameTime());
        } else {
            stopClientAmbientSound();
        }
    }

    private void stopClientAmbientSound() {
        if (clientAmbientSound == null) return;
        ((PortalAmbientSoundClient) clientAmbientSound).stop();
        clientAmbientSound = null;
    }

    boolean isClientAmbientUnstable() { return visualUnstable; }
    /** Animation de l'anneau 3D, ou null si le portail n'est pas construit (côté client). */
    PortalGateAnimator getClientGateAnimator() { return (PortalGateAnimator) clientGateAnimator; }
    long getClientActiveUntil() { return clientActiveUntil; }
    int getClientLinkSteps() { return linkSteps; }

    /** Nombre de fissures de l'accrochage : dépend du trajet (même dimension, autre dimension, Faille). */
    void setLinkSteps(int steps) {
        if (linkSteps == steps) return;
        linkSteps = steps;
        syncOpeningToClients();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide()) {
            stopClientAmbientSound();
        }
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        syncOutboundChunkForce(sl, now);
        if ((now + (worldPosition.asLong() & 7L)) % 10L == 0L) {
            refreshFormation(sl);
        }
        if (restorePending) {
            restorePending = false;
            if (active && !restoreActiveState(sl)) return;
            if (opening) {
                if (targetPortalId == null) {
                    PortalConnectionManager.forceCloseOneSide(sl, worldPosition);
                    return;
                }
                if (openingCompleteGameTime <= 0L) {
                    openingStartedGameTime = now;
                    openingCompleteGameTime = openingStartedGameTime + OPENING_DURATION_FALLBACK_TICKS;
                    setChanged();
                    syncOpeningToClients();
                }
                PortalConnectionManager.setNearbyKeyboardsLit(sl, worldPosition, true);
            }
        }
        if (now % 10L == 0L) {
            PortalRiftHelper.findNearestChargedRod(sl, worldPosition, ChargedLightningRodBlock.PORTAL_RADIUS)
                    .ifPresent((rod) -> spawnRiftParticles(sl));
        }
        if (opening) {
            PortalConnectionManager.tickOpeningSequence(sl, worldPosition, this);
            return;
        }
        if (!active) return;

        if (activeStartedGameTime <= 0L || activeStartedGameTime > now) {
            activeStartedGameTime = now;
            setChanged();
        }
        if (closeIfExpired(sl, now)) {
            return;
        }

        if (!drainActivePortalEnergy(sl, now)) {
            return;
        }

        refreshInstabilityVisuals(sl, now);
        // These cells keep their real blocks, so they cannot trigger PortalFieldBlock.entityInside.
        for (BlockPos cell : wiredFieldCells) {
            for (var entity : sl.getEntities(null, new AABB(cell).deflate(.001))) {
                PortalTeleportHandler.tryTeleport(entity, cell, this);
                if (!active) return;
            }
        }
    }

    void placeField(ServerLevel level, PortalFrameDetector.FrameMatch frame, boolean unstable) {
        BlockState field = ModBlocks.PORTAL_FIELD.defaultBlockState()
                .setValue(PortalFieldBlock.AXIS, frame.right().getAxis())
                .setValue(PortalFieldBlock.UNSTABLE, unstable);
        wiredFieldCells.clear();
        for (BlockPos pos : frame.interior()) {
            BlockState current = level.getBlockState(pos);
            if (hasWiring(level, pos, current)) {
                wiredFieldCells.add(pos);
                continue;
            }
            BlockState desired = field.setValue(PortalFieldBlock.WATERLOGGED, level.getFluidState(pos).is(FluidTags.WATER));
            if (desired != current) level.setBlock(pos, desired, 3);
        }
    }

    private static boolean hasWiring(ServerLevel level, BlockPos pos, BlockState state) {
        if (ElectricWireBlock.isWire(state) || state.is(ModBlocks.CROCODILE_CLIP)
                || state.is(ModBlocks.ENERGY_CONDUIT) || state.is(ModBlocks.DARK_ENERGY_CONDUIT)) return true;
        if (state.isAir()) return false;
        // Replacing a support would detach a cable even when the cable itself is outside the opening.
        for (Direction side : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(side));
            if ((ElectricWireBlock.isWire(neighbor) && neighbor.getValue(ElectricWireBlock.FACING) == side.getOpposite())
                    || (neighbor.is(ModBlocks.CROCODILE_CLIP) && neighbor.getValue(CrocodileClipBlock.FACING) == side.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    private void spawnRiftParticles(ServerLevel level) {
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 1.2;
        double z = worldPosition.getZ() + 0.5;
        level.sendParticles(ModParticles.SPARK, x, y, z, 4, 0.35, 0.5, 0.35, 0.0);
    }

    private boolean restoreActiveState(ServerLevel sl) {
        if (!active) return false;

        if (targetPortalId == null) {
            PortalConnectionManager.forceCloseOneSide(sl, worldPosition);
            return false;
        }

        long now = sl.getGameTime();
        if (closeIfExpired(sl, now)) {
            return false;
        }

        java.util.Optional<PortalFrameDetector.FrameMatch> match = PortalFrameDetector.find(sl, worldPosition);
        if (match.isEmpty()) {
            PortalConnectionManager.forceCloseOneSide(sl, worldPosition);
            return false;
        }

        var frameMatch = match.get();
        boolean unstableVisual = shouldUseUnstableVisuals(now);
        placeField(sl, frameMatch, unstableVisual);

        for (BlockPos p : PortalFrameHelper.collectFrame(frameMatch, worldPosition)) {
            BlockState state = sl.getBlockState(p);
            if (state.is(ModBlocks.PORTAL_FRAME)
                    && state.hasProperty(PortalFrameBlock.ACTIVE)
                    && state.hasProperty(PortalFrameBlock.UNSTABLE)) {
                BlockState updated = state
                        .setValue(PortalFrameBlock.ACTIVE, true)
                        .setValue(PortalFrameBlock.UNSTABLE, unstableVisual);
                if (!updated.equals(state)) {
                    sl.setBlock(p, updated, 3);
                }
            }
        }

        visualUnstable = unstableVisual;

        PortalConnectionManager.setNearbyKeyboardsLit(sl, worldPosition, true);

        syncOpeningToClients();

        return true;
    }


    private boolean emitsRedstoneSignal() {
        return active || opening;
    }

    private void updateRedstoneSignal(boolean previouslyEmitting) {
        if (previouslyEmitting == emitsRedstoneSignal()) return;
        if (level == null || level.isClientSide()) return;
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    private boolean shouldForceOutboundChunk(long now) {
        return active || opening || now < keepChunkForcedUntilGameTime;
    }

    private void syncOutboundChunkForce(ServerLevel level) {
        syncOutboundChunkForce(level, level.getGameTime());
    }

    private void syncOutboundChunkForce(ServerLevel level, long now) {
        boolean shouldForce = shouldForceOutboundChunk(now);
        if (shouldForce == outboundChunkForceClaimed) return;

        ChunkPos chunkPos = new ChunkPos(worldPosition);
        level.setChunkForced(chunkPos.x, chunkPos.z, shouldForce);
        outboundChunkForceClaimed = shouldForce;
    }

    // ---------- State helpers ----------
    public UUID getPortalId() { return portalId; }
    public boolean isActive() { return active; }
    public boolean isOpening() { return opening; }
    public boolean isActiveOrOpening() { return active || opening; }
    public UUID getTargetPortalId() { return targetPortalId; }
    public UUID getConnectionId() { return connectionId; }
    public long getActiveUntilGameTime() { return activeUntilGameTime; }
    public boolean isRiftLightningLink() { return riftLightningLink; }
    public boolean isOutboundTravelEnabled() { return outboundTravelEnabled; }
    public long getOpeningStartedGameTime() { return openingStartedGameTime; }
    public long getOpeningCompleteGameTime() { return openingCompleteGameTime; }
    public boolean isVortexUnstable(long now) {
        return active
                && (powerLossUnstable
                || (activeStartedGameTime > 0L
                && (now - activeStartedGameTime) > UNSTABLE_THRESHOLD_TICKS));
    }

    public boolean isPowerLossUnstable() {
        return active && powerLossUnstable;
    }

    void onEntityPassed(long now) {
        if (!active) return;

        if (powerLossUnstable) {
            if (!outboundTravelEnabled) return;
            if (lastPowerLossPenaltyGameTime != Long.MIN_VALUE
                    && (now - lastPowerLossPenaltyGameTime) < POWER_LOSS_ENTITY_PENALTY_COOLDOWN_TICKS) {
                return;
            }
            lastPowerLossPenaltyGameTime = now;
            long minUntil = now + POWER_LOSS_MIN_REMAINING_AFTER_PENALTY_TICKS;
            long shortened = Math.max(minUntil, activeUntilGameTime - POWER_LOSS_ENTITY_PENALTY_TICKS);
            if (shortened < activeUntilGameTime) {
                activeUntilGameTime = shortened;
                activeUntilEpochMillis = computeEpochDeadlineFromGameTime(now, activeUntilGameTime);
                setChanged();
                syncOpeningToClients();
                if (level instanceof ServerLevel sl) {
                    long remainingTicks = Math.max(0L, activeUntilGameTime - now);
                    PortalConnectionManager.syncPowerLossStateToOtherSide(sl, this, true, remainingTicks);
                }
            }
            return;
        }

        activeUntilGameTime = now + PER_ENTITY_EXTENSION_TICKS;
        activeUntilEpochMillis = computeEpochDeadlineFromGameTime(now, activeUntilGameTime);
        setChanged();
        // Le client cale l'effondrement sur cette échéance ; au plus une mise à jour par seconde.
        if (Math.abs(activeUntilGameTime - lastSyncedActiveUntil) >= 20L) {
            syncOpeningToClients();
        }
    }

    void onPlayerPassedThrough(long now) {
        onEntityPassed(now);

        long keepUntil = now + PLAYER_PASS_CHUNK_FORCE_TICKS;
        if (keepUntil > keepChunkForcedUntilGameTime) {
            keepChunkForcedUntilGameTime = keepUntil;
            setChanged();
        }
    }

    boolean closeIfExpired(ServerLevel level) {
        return closeIfExpired(level, level.getGameTime());
    }

    private boolean closeIfExpired(ServerLevel level, long nowGameTime) {
        if (!active) return false;
        if (!hasReachedActiveDeadline(nowGameTime)) return false;
        boolean triggerShockwave = powerLossUnstable && outboundTravelEnabled;
        PortalConnectionManager.forceCloseOneSide(level, worldPosition);
        if (triggerShockwave) {
            PortalConnectionManager.triggerPowerLossShockwave(level, worldPosition);
        }
        return true;
    }

    private boolean hasReachedActiveDeadline(long nowGameTime) {
        if (activeUntilEpochMillis <= 0L && activeUntilGameTime > 0L) {
            activeUntilEpochMillis = computeEpochDeadlineFromGameTime(nowGameTime, activeUntilGameTime);
            setChanged();
        }
        if (activeUntilGameTime > 0L && nowGameTime >= activeUntilGameTime) {
            return true;
        }
        return activeUntilEpochMillis > 0L && System.currentTimeMillis() >= activeUntilEpochMillis;
    }

    private static long computeEpochDeadlineFromGameTime(long nowGameTime, long untilGameTime) {
        long remainingTicks = Math.max(0L, untilGameTime - nowGameTime);
        long remainingMillis = ticksToMillis(remainingTicks);
        long nowEpochMillis = System.currentTimeMillis();
        long maxAddition = Long.MAX_VALUE - nowEpochMillis;
        if (remainingMillis >= maxAddition) {
            return Long.MAX_VALUE;
        }
        return nowEpochMillis + remainingMillis;
    }

    private static long ticksToMillis(long ticks) {
        if (ticks <= 0L) return 0L;
        if (ticks >= Long.MAX_VALUE / TICK_DURATION_MILLIS) {
            return Long.MAX_VALUE;
        }
        return ticks * TICK_DURATION_MILLIS;
    }

    private boolean shouldUseUnstableVisuals(long now) {
        return riftLightningLink || isVortexUnstable(now);
    }

    private boolean drainActivePortalEnergy(ServerLevel level, long now) {
        if (!outboundTravelEnabled || riftLightningLink || maintenanceEnergyBypass) return true;
        if (EnergyNetworkHelper.isRiftDimension(level)) return true;

        long phase = Math.floorMod(worldPosition.asLong(), 20L);
        if ((now + phase) % 20L != 0L) return true;

        int energyCost = EnergyNetworkHelper.getPortalActiveEnergyCostPerSecond(isVortexUnstable(now));

        boolean consumed = EnergyNetworkHelper.consumePortalEnergy(
                level,
                worldPosition,
                energyCost
        );
        if (consumed) {
            if (powerLossUnstable) {
                recoverFromPowerLoss(level, now);
            }
            return true;
        }

        if (!powerLossUnstable) {
            enterPowerLossInstability(level, now);
        }
        return true;
    }

    private void enterPowerLossInstability(ServerLevel level, long now) {
        powerLossUnstable = true;
        lastPowerLossPenaltyGameTime = Long.MIN_VALUE;
        activeUntilGameTime = now + POWER_LOSS_COLLAPSE_TICKS;
        activeUntilEpochMillis = computeEpochDeadlineFromGameTime(now, activeUntilGameTime);
        ModSounds.playAt(level, worldPosition, ModSounds.PORTAL_ERROR, 0.9F, 1.0F);
        refreshInstabilityVisuals(level, now);
        setChanged();
        syncOpeningToClients();
        PortalConnectionManager.syncPowerLossStateToOtherSide(level, this, true, POWER_LOSS_COLLAPSE_TICKS);
    }

    private void recoverFromPowerLoss(ServerLevel level, long now) {
        powerLossUnstable = false;
        lastPowerLossPenaltyGameTime = Long.MIN_VALUE;
        long minUntil = now + POWER_LOSS_RECOVERY_MIN_ACTIVE_TICKS;
        if (activeUntilGameTime < minUntil) {
            activeUntilGameTime = minUntil;
            activeUntilEpochMillis = computeEpochDeadlineFromGameTime(now, activeUntilGameTime);
        }
        long remainingTicks = Math.max(0L, activeUntilGameTime - now);
        refreshInstabilityVisuals(level, now);
        setChanged();
        syncOpeningToClients();
        PortalConnectionManager.syncPowerLossStateToOtherSide(level, this, false, remainingTicks);
    }

    void applyRemotePowerLossState(ServerLevel level, boolean powerLoss, long remainingTicks) {
        if (!active || outboundTravelEnabled) return;
        long now = level.getGameTime();
        powerLossUnstable = powerLoss;
        // Garde 1 s de marge côté destination pour que la source déclenche toujours le souffle en premier.
        long targetRemaining = Math.max(20L, remainingTicks + (powerLoss ? 20L : 0L));
        activeUntilGameTime = now + targetRemaining;
        activeUntilEpochMillis = computeEpochDeadlineFromGameTime(now, activeUntilGameTime);
        refreshInstabilityVisuals(level, now);
        setChanged();
        syncOpeningToClients();
    }

    private void refreshInstabilityVisuals(ServerLevel level, long now) {
        boolean unstableNow = shouldUseUnstableVisuals(now);
        if (visualUnstable == unstableNow) return;

        visualUnstable = unstableNow;
        PortalConnectionManager.syncActivePortalInstability(level, worldPosition, unstableNow);
        setChanged();
        syncOpeningToClients();
    }

    public void setPortalName(String name) { this.portalName = name == null ? "" : name; setChanged(); }
    public String getPortalName() { return portalName; }

    public void renamePortal(String name) {
        if (!(level instanceof ServerLevel sl)) return;

        if (portalId == null) portalId = java.util.UUID.randomUUID();

        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > 32) trimmed = trimmed.substring(0, 32);

        portalName = trimmed;
        PortalRegistrySavedData.get(sl.getServer()).upsertPortal(sl, portalId, portalName, worldPosition);
        setChanged();
    }

    void setOpeningState(UUID connectionId,
                         UUID targetPortalId,
                         long openingStartedGameTime,
                         long openingCompleteGameTime,
                         boolean riftLightningLink,
                         boolean outboundTravelEnabled,
                         boolean maintenanceEnergyBypass) {
        boolean previouslyEmitting = emitsRedstoneSignal();
        this.active = false;
        this.opening = true;
        this.connectionId = connectionId;
        this.targetPortalId = targetPortalId;
        this.activeUntilGameTime = 0L;
        this.activeUntilEpochMillis = 0L;
        this.activeStartedGameTime = 0L;
        this.visualUnstable = false;
        this.powerLossUnstable = false;
        this.lastPowerLossPenaltyGameTime = Long.MIN_VALUE;
        this.riftLightningLink = riftLightningLink;
        this.outboundTravelEnabled = outboundTravelEnabled;
        this.maintenanceEnergyBypass = maintenanceEnergyBypass;
        this.openingStartedGameTime = openingStartedGameTime;
        this.openingCompleteGameTime = openingCompleteGameTime;
        setChanged();
        updateRedstoneSignal(previouslyEmitting);
        if (level instanceof ServerLevel sl) {
            syncOutboundChunkForce(sl);
        }
        syncOpeningToClients();
    }

    void finalizeOpeningState(long activeUntilGameTime, long activeStartedGameTime) {
        if (!opening) return;
        boolean previouslyEmitting = emitsRedstoneSignal();
        this.opening = false;
        this.active = true;
        this.activeUntilGameTime = activeUntilGameTime;
        this.activeUntilEpochMillis = computeEpochDeadlineFromGameTime(activeStartedGameTime, activeUntilGameTime);
        this.activeStartedGameTime = Math.max(0L, activeStartedGameTime);
        this.visualUnstable = riftLightningLink;
        this.powerLossUnstable = false;
        this.lastPowerLossPenaltyGameTime = Long.MIN_VALUE;
        this.openingStartedGameTime = 0L;
        this.openingCompleteGameTime = 0L;
        setChanged();
        updateRedstoneSignal(previouslyEmitting);
        if (level instanceof ServerLevel sl) {
            syncOutboundChunkForce(sl);
        }
        syncOpeningToClients();
    }

    void setActiveState(UUID connectionId,
                        UUID targetPortalId,
                        long activeUntilGameTime,
                        boolean riftLightningLink,
                        boolean outboundTravelEnabled,
                        boolean maintenanceEnergyBypass) {
        boolean previouslyEmitting = emitsRedstoneSignal();
        this.active = true;
        this.opening = false;
        this.connectionId = connectionId;
        this.targetPortalId = targetPortalId;
        this.activeUntilGameTime = activeUntilGameTime;
        this.activeUntilEpochMillis = level instanceof ServerLevel sl
                ? computeEpochDeadlineFromGameTime(sl.getGameTime(), activeUntilGameTime)
                : 0L;
        this.activeStartedGameTime = 0L;
        this.visualUnstable = riftLightningLink;
        this.powerLossUnstable = false;
        this.lastPowerLossPenaltyGameTime = Long.MIN_VALUE;
        this.riftLightningLink = riftLightningLink;
        this.outboundTravelEnabled = outboundTravelEnabled;
        this.maintenanceEnergyBypass = maintenanceEnergyBypass;
        this.openingStartedGameTime = 0L;
        this.openingCompleteGameTime = 0L;
        setChanged();
        updateRedstoneSignal(previouslyEmitting);
        if (level instanceof ServerLevel sl) {
            syncOutboundChunkForce(sl);
        }
        syncOpeningToClients();
    }

    void clearActiveState() {
        wiredFieldCells.clear();
        boolean previouslyEmitting = emitsRedstoneSignal();
        this.active = false;
        this.opening = false;
        this.connectionId = null;
        this.targetPortalId = null;
        this.activeUntilGameTime = 0L;
        this.activeUntilEpochMillis = 0L;
        this.activeStartedGameTime = 0L;
        this.visualUnstable = false;
        this.powerLossUnstable = false;
        this.lastPowerLossPenaltyGameTime = Long.MIN_VALUE;
        this.riftLightningLink = false;
        this.outboundTravelEnabled = false;
        this.maintenanceEnergyBypass = false;
        this.openingStartedGameTime = 0L;
        this.openingCompleteGameTime = 0L;
        setChanged();
        updateRedstoneSignal(previouslyEmitting);
        if (level instanceof ServerLevel sl) {
            syncOutboundChunkForce(sl);
        }
        syncOpeningToClients();
    }

    // ---------- NBT ----------
    //? if >=1.21.11 {
    /*// Called before the block entity is removed (replaces Block.onRemove since 1.21.5).
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        onBroken();
        super.preRemoveSideEffects(pos, state);
    }
    *///?}

    //? if >=1.21.11 {
    /*@Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        saveData(DataWriter.of(output));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        loadData(DataReader.of(input));
    }
    *///?} else {
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveData(DataWriter.of(tag, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadData(DataReader.of(tag, registries));
    }
    //?}

    private void saveData(DataWriter tag) {
        if (portalId != null) tag.putUUID("PortalId", portalId);
        tag.putString("PortalName", portalName);

        tag.putBoolean("Active", active);
        tag.putBoolean("Opening", opening);
        if (connectionId != null) tag.putUUID("ConnectionId", connectionId);
        if (targetPortalId != null) tag.putUUID("TargetPortalId", targetPortalId);
        tag.putLong("ActiveUntil", activeUntilGameTime);
        tag.putLong("ActiveUntilEpoch", activeUntilEpochMillis);
        tag.putLong("ActiveStarted", activeStartedGameTime);
        tag.putBoolean("PowerLossUnstable", powerLossUnstable);
        tag.putBoolean("RiftLightning", riftLightningLink);
        tag.putBoolean("OutboundTravel", outboundTravelEnabled);
        tag.putBoolean("MaintenanceEnergyBypass", maintenanceEnergyBypass);
        tag.putLong("OpeningStart", openingStartedGameTime);
        tag.putLong("OpeningComplete", openingCompleteGameTime);
        tag.putLong("ChunkForceUntil", keepChunkForcedUntilGameTime);
        tag.putInt("DarkEnergyAmount", darkEnergyAmount);
    }

    private void loadData(DataReader tag) {
        if (tag.getBoolean(CLIENT_SYNC_KEY)) {
            opening = tag.getBoolean("Opening");
            openingStartedGameTime = opening ? tag.getLong("OpeningStart") : 0L;
            openingCompleteGameTime = opening ? tag.getLong("OpeningComplete") : 0L;
            active = tag.getBoolean("Active");
            visualUnstable = active && tag.getBoolean("VisualUnstable");
            clientActiveUntil = active ? tag.getLong("ActiveUntil") : 0L;
            linkSteps = tag.contains("LinkSteps") ? tag.getInt("LinkSteps") : 4;
            return;
        }

        portalId = tag.getUUID("PortalId");
        portalName = tag.getString("PortalName");

        active = tag.getBoolean("Active");
        opening = tag.getBoolean("Opening");
        connectionId = tag.getUUID("ConnectionId");
        targetPortalId = tag.getUUID("TargetPortalId");
        activeUntilGameTime = tag.getLong("ActiveUntil");
        activeUntilEpochMillis = tag.contains("ActiveUntilEpoch") ? tag.getLong("ActiveUntilEpoch") : 0L;
        activeStartedGameTime = tag.contains("ActiveStarted") ? tag.getLong("ActiveStarted") : 0L;
        visualUnstable = false;
        powerLossUnstable = active && tag.getBoolean("PowerLossUnstable");
        lastPowerLossPenaltyGameTime = Long.MIN_VALUE;
        riftLightningLink = tag.getBoolean("RiftLightning");
        outboundTravelEnabled = tag.contains("OutboundTravel") ? tag.getBoolean("OutboundTravel") : (active || opening);
        maintenanceEnergyBypass = tag.getBoolean("MaintenanceEnergyBypass");
        openingStartedGameTime = tag.contains("OpeningStart") ? tag.getLong("OpeningStart") : 0L;
        openingCompleteGameTime = tag.contains("OpeningComplete") ? tag.getLong("OpeningComplete") : 0L;
        keepChunkForcedUntilGameTime = tag.contains("ChunkForceUntil") ? tag.getLong("ChunkForceUntil") : 0L;
        darkEnergyAmount = Math.max(0, Math.min(tag.getInt("DarkEnergyAmount"), DARK_ENERGY_THRESHOLD));
        outboundChunkForceClaimed = false;

        if (!active) {
            activeUntilEpochMillis = 0L;
            activeStartedGameTime = 0L;
            visualUnstable = false;
        }
        if (!opening) {
            openingStartedGameTime = 0L;
            openingCompleteGameTime = 0L;
        }
        restorePending = active || opening;

        if (level instanceof ServerLevel sl && portalId != null) {
            PortalRegistrySavedData.get(sl.getServer()).upsertPortal(sl, portalId, portalName, worldPosition);
        }
    }

    // ---------- Client sync ----------
    // Seul l'état d'ouverture/activité est envoyé aux clients : il leur suffit pour animer la séquence
    // d'ouverture (PortalOpeningParticles) et jouer l'ambiance (PortalAmbientSoundClient) eux-mêmes,
    // sans paquet de particules ni de son à chaque tick.
    private static final String CLIENT_SYNC_KEY = "UniverseGateClientSync";

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(CLIENT_SYNC_KEY, true);
        tag.putBoolean("Opening", opening);
        tag.putLong("OpeningStart", openingStartedGameTime);
        tag.putLong("OpeningComplete", openingCompleteGameTime);
        tag.putBoolean("Active", active);
        tag.putBoolean("VisualUnstable", visualUnstable);
        tag.putLong("ActiveUntil", activeUntilGameTime);
        tag.putInt("LinkSteps", linkSteps);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private long lastSyncedActiveUntil;

    private void syncOpeningToClients() {
        if (!(level instanceof ServerLevel sl)) return;
        lastSyncedActiveUntil = activeUntilGameTime;
        BlockState state = getBlockState();
        sl.sendBlockUpdated(worldPosition, state, state, 3);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.universegate.portal_core");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv, Player player) {
        return new PortalCoreMenu(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.worldPosition;
    }
}
