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

    private UUID portalId;
    private String portalName = "";

    // Stargate state
    private boolean active = false;
    private UUID connectionId = null;
    private UUID targetPortalId = null;
    private long activeUntilGameTime = 0L; // fermeture auto
    private boolean riftLightningLink = false;
    private boolean outboundTravelEnabled = false;
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
        if (active) {
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
            if (!restoreActiveState(sl)) return;
        }
        if (sl.getGameTime() % 10L == 0L) {
            PortalRiftHelper.findNearestChargedRod(sl, worldPosition, ChargedLightningRodBlock.PORTAL_RADIUS)
                    .ifPresent((rod) -> spawnRiftParticles(sl));
        }
        if (!active) return;

        if (sl.getGameTime() % 40L == 0L) {
            ModSounds.playAt(sl, worldPosition,
                    riftLightningLink ? ModSounds.PORTAL_UNSTABLE : ModSounds.PORTAL_IDLE,
                    0.35F,
                    1.0F);
        }

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

        for (BlockPos p : match.get().interior()) {
            if (!sl.getBlockState(p).is(ModBlocks.PORTAL_FIELD)) {
                sl.setBlock(p, ModBlocks.PORTAL_FIELD.defaultBlockState(), 3);
            }
        }

        return true;
    }

    // ---------- State helpers ----------
    public UUID getPortalId() { return portalId; }
    public boolean isActive() { return active; }
    public UUID getTargetPortalId() { return targetPortalId; }
    public UUID getConnectionId() { return connectionId; }
    public long getActiveUntilGameTime() { return activeUntilGameTime; }
    public boolean isRiftLightningLink() { return riftLightningLink; }
    public boolean isOutboundTravelEnabled() { return outboundTravelEnabled; }

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

    void setActiveState(UUID connectionId,
                        UUID targetPortalId,
                        long activeUntilGameTime,
                        boolean riftLightningLink,
                        boolean outboundTravelEnabled) {
        this.active = true;
        this.connectionId = connectionId;
        this.targetPortalId = targetPortalId;
        this.activeUntilGameTime = activeUntilGameTime;
        this.riftLightningLink = riftLightningLink;
        this.outboundTravelEnabled = outboundTravelEnabled;
        setChanged();
    }

    void clearActiveState() {
        this.active = false;
        this.connectionId = null;
        this.targetPortalId = null;
        this.activeUntilGameTime = 0L;
        this.riftLightningLink = false;
        this.outboundTravelEnabled = false;
        setChanged();
    }

    // ---------- NBT ----------
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (portalId != null) tag.putUUID("PortalId", portalId);
        tag.putString("PortalName", portalName);

        tag.putBoolean("Active", active);
        if (connectionId != null) tag.putUUID("ConnectionId", connectionId);
        if (targetPortalId != null) tag.putUUID("TargetPortalId", targetPortalId);
        tag.putLong("ActiveUntil", activeUntilGameTime);
        tag.putBoolean("RiftLightning", riftLightningLink);
        tag.putBoolean("OutboundTravel", outboundTravelEnabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        portalId = tag.hasUUID("PortalId") ? tag.getUUID("PortalId") : null;
        portalName = tag.getString("PortalName");

        active = tag.getBoolean("Active");
        connectionId = tag.hasUUID("ConnectionId") ? tag.getUUID("ConnectionId") : null;
        targetPortalId = tag.hasUUID("TargetPortalId") ? tag.getUUID("TargetPortalId") : null;
        activeUntilGameTime = tag.getLong("ActiveUntil");
        riftLightningLink = tag.getBoolean("RiftLightning");
        outboundTravelEnabled = tag.contains("OutboundTravel") ? tag.getBoolean("OutboundTravel") : active;
        restorePending = active;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Portal Core");
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
