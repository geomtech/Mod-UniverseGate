package fr.geomtech.universegate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class PortalCoreBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {

    private static final long FOLLOW_UP_WINDOW_TICKS = 20L * 10L;
    private static final long EXTENDED_OPEN_TICKS = 20L * 60L;
    private static final long OPENING_DURATION_FALLBACK_TICKS = 20L * 10L;

    private UUID portalId;
    private String portalName = "";

    // Stargate state
    private boolean active = false;
    private UUID connectionId = null;
    private UUID targetPortalId = null;
    private long activeUntilGameTime = 0L; // fermeture auto
    private long lastEntityPassGameTime = -1L;
    private boolean extendedOpenWindow = false;
    private boolean riftLightningLink = false;
    private boolean outboundTravelEnabled = false;
    private boolean opening = false;
    private long openingStartedGameTime = 0L;
    private long openingCompleteGameTime = 0L;
    private boolean restorePending = false;

    public PortalCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PORTAL_CORE, pos, state);
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

        // Si actif, ferme localement (et tente de fermer l’autre côté si possible)
        if (isActiveOrOpening()) {
            PortalConnectionManager.forceCloseOneSide(sl, worldPosition);
        }
        PortalRegistrySavedData.get(sl.getServer()).removePortal(portalId);
        setChanged();
    }

    // ---------- Ticking ----------
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (restorePending) {
            restorePending = false;
            if (active && !restoreActiveState(sl)) return;
            if (opening) {
                if (targetPortalId == null) {
                    PortalConnectionManager.forceCloseOneSide(sl, worldPosition);
                    return;
                }
                if (openingCompleteGameTime <= 0L) {
                    openingStartedGameTime = sl.getGameTime();
                    openingCompleteGameTime = openingStartedGameTime + OPENING_DURATION_FALLBACK_TICKS;
                    setChanged();
                }
                PortalConnectionManager.setNearbyKeyboardsLit(sl, worldPosition, true);
            }
        }
        if (sl.getGameTime() % 10L == 0L) {
            PortalRiftHelper.findNearestChargedRod(sl, worldPosition, ChargedLightningRodBlock.PORTAL_RADIUS)
                    .ifPresent((rod) -> spawnRiftParticles(sl));
        }
        if (opening) {
            PortalConnectionManager.tickOpeningSequence(sl, worldPosition, this);
            return;
        }
        if (!active) return;

        long now = sl.getGameTime();
        if (now >= activeUntilGameTime) {
            PortalConnectionManager.forceCloseOneSide(sl, worldPosition);
        }
    }

    private void spawnRiftParticles(ServerLevel level) {
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 1.2;
        double z = worldPosition.getZ() + 0.5;
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 4, 0.35, 0.5, 0.35, 0.0);
    }

    private boolean restoreActiveState(ServerLevel sl) {
        if (!active) return false;

        if (targetPortalId == null) {
            PortalConnectionManager.forceCloseOneSide(sl, worldPosition);
            return false;
        }

        long now = sl.getGameTime();
        if (activeUntilGameTime > 0L && now >= activeUntilGameTime) {
            PortalConnectionManager.forceCloseOneSide(sl, worldPosition);
            return false;
        }

        java.util.Optional<PortalFrameDetector.FrameMatch> match = PortalFrameDetector.find(sl, worldPosition);
        if (match.isEmpty()) {
            PortalConnectionManager.forceCloseOneSide(sl, worldPosition);
            return false;
        }

        var frameMatch = match.get();
        var axis = frameMatch.right() == net.minecraft.core.Direction.EAST
                ? net.minecraft.core.Direction.Axis.X
                : net.minecraft.core.Direction.Axis.Z;
        BlockState desiredFieldState = ModBlocks.PORTAL_FIELD.defaultBlockState()
                .setValue(PortalFieldBlock.AXIS, axis)
                .setValue(PortalFieldBlock.UNSTABLE, riftLightningLink);

        for (BlockPos p : frameMatch.interior()) {
            BlockState current = sl.getBlockState(p);
            if (!current.equals(desiredFieldState)) {
                sl.setBlock(p, desiredFieldState, 3);
            }
        }

        for (BlockPos p : PortalFrameHelper.collectFrame(frameMatch, worldPosition)) {
            BlockState state = sl.getBlockState(p);
            if (state.is(ModBlocks.PORTAL_FRAME)
                    && state.hasProperty(PortalFrameBlock.ACTIVE)
                    && state.hasProperty(PortalFrameBlock.UNSTABLE)
                    && state.hasProperty(PortalFrameBlock.BLINK_ON)) {
                BlockState updated = state
                        .setValue(PortalFrameBlock.ACTIVE, true)
                        .setValue(PortalFrameBlock.UNSTABLE, riftLightningLink)
                        .setValue(PortalFrameBlock.BLINK_ON, riftLightningLink);
                if (!updated.equals(state)) {
                    sl.setBlock(p, updated, 3);
                }
            }
        }

        PortalConnectionManager.setNearbyKeyboardsLit(sl, worldPosition, true);

        ModSounds.playPortalAmbientAt(sl, worldPosition, riftLightningLink);

        return true;
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

    void onEntityPassed(long now) {
        if (!active) return;

        boolean quickFollowUp = lastEntityPassGameTime >= 0L
                && now - lastEntityPassGameTime <= FOLLOW_UP_WINDOW_TICKS;

        if (quickFollowUp) {
            extendedOpenWindow = true;
            activeUntilGameTime = Math.max(activeUntilGameTime, now + EXTENDED_OPEN_TICKS);
        } else if (!extendedOpenWindow) {
            activeUntilGameTime = now + FOLLOW_UP_WINDOW_TICKS;
        }

        lastEntityPassGameTime = now;
        setChanged();
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
                         boolean outboundTravelEnabled) {
        this.active = false;
        this.opening = true;
        this.connectionId = connectionId;
        this.targetPortalId = targetPortalId;
        this.activeUntilGameTime = 0L;
        this.lastEntityPassGameTime = -1L;
        this.extendedOpenWindow = false;
        this.riftLightningLink = riftLightningLink;
        this.outboundTravelEnabled = outboundTravelEnabled;
        this.openingStartedGameTime = openingStartedGameTime;
        this.openingCompleteGameTime = openingCompleteGameTime;
        setChanged();
    }

    void finalizeOpeningState(long activeUntilGameTime) {
        if (!opening) return;
        this.opening = false;
        this.active = true;
        this.activeUntilGameTime = activeUntilGameTime;
        this.lastEntityPassGameTime = -1L;
        this.extendedOpenWindow = false;
        this.openingStartedGameTime = 0L;
        this.openingCompleteGameTime = 0L;
        setChanged();
    }

    void setActiveState(UUID connectionId,
                        UUID targetPortalId,
                        long activeUntilGameTime,
                        boolean riftLightningLink,
                        boolean outboundTravelEnabled) {
        this.active = true;
        this.opening = false;
        this.connectionId = connectionId;
        this.targetPortalId = targetPortalId;
        this.activeUntilGameTime = activeUntilGameTime;
        this.lastEntityPassGameTime = -1L;
        this.extendedOpenWindow = false;
        this.riftLightningLink = riftLightningLink;
        this.outboundTravelEnabled = outboundTravelEnabled;
        this.openingStartedGameTime = 0L;
        this.openingCompleteGameTime = 0L;
        setChanged();
    }

    void clearActiveState() {
        this.active = false;
        this.opening = false;
        this.connectionId = null;
        this.targetPortalId = null;
        this.activeUntilGameTime = 0L;
        this.lastEntityPassGameTime = -1L;
        this.extendedOpenWindow = false;
        this.riftLightningLink = false;
        this.outboundTravelEnabled = false;
        this.openingStartedGameTime = 0L;
        this.openingCompleteGameTime = 0L;
        setChanged();
    }

    // ---------- NBT ----------
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (portalId != null) tag.putUUID("PortalId", portalId);
        tag.putString("PortalName", portalName);

        tag.putBoolean("Active", active);
        tag.putBoolean("Opening", opening);
        if (connectionId != null) tag.putUUID("ConnectionId", connectionId);
        if (targetPortalId != null) tag.putUUID("TargetPortalId", targetPortalId);
        tag.putLong("ActiveUntil", activeUntilGameTime);
        tag.putLong("LastEntityPass", lastEntityPassGameTime);
        tag.putBoolean("ExtendedOpenWindow", extendedOpenWindow);
        tag.putBoolean("RiftLightning", riftLightningLink);
        tag.putBoolean("OutboundTravel", outboundTravelEnabled);
        tag.putLong("OpeningStart", openingStartedGameTime);
        tag.putLong("OpeningComplete", openingCompleteGameTime);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        portalId = tag.hasUUID("PortalId") ? tag.getUUID("PortalId") : null;
        portalName = tag.getString("PortalName");

        active = tag.getBoolean("Active");
        opening = tag.getBoolean("Opening");
        connectionId = tag.hasUUID("ConnectionId") ? tag.getUUID("ConnectionId") : null;
        targetPortalId = tag.hasUUID("TargetPortalId") ? tag.getUUID("TargetPortalId") : null;
        activeUntilGameTime = tag.getLong("ActiveUntil");
        lastEntityPassGameTime = tag.contains("LastEntityPass") ? tag.getLong("LastEntityPass") : -1L;
        extendedOpenWindow = tag.getBoolean("ExtendedOpenWindow");
        riftLightningLink = tag.getBoolean("RiftLightning");
        outboundTravelEnabled = tag.contains("OutboundTravel") ? tag.getBoolean("OutboundTravel") : (active || opening);
        openingStartedGameTime = tag.contains("OpeningStart") ? tag.getLong("OpeningStart") : 0L;
        openingCompleteGameTime = tag.contains("OpeningComplete") ? tag.getLong("OpeningComplete") : 0L;
        if (!active) {
            lastEntityPassGameTime = -1L;
            extendedOpenWindow = false;
        }
        if (!opening) {
            openingStartedGameTime = 0L;
            openingCompleteGameTime = 0L;
        }
        restorePending = active || opening;
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
