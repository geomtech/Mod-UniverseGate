package fr.geomtech.universegate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class EnergyMonitorBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {

    private static final int RESCAN_INTERVAL_TICKS = 10;

    private int storedEnergy = 0;
    private int capacity = 0;
    private int condenserCount = 0;
    private int panelCount = 0;
    private int activePanelCount = 0;
    private int rescanCooldown = 0;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> storedEnergy & 0xFFFF;
                case 1 -> (storedEnergy >>> 16) & 0xFFFF;
                case 2 -> capacity & 0xFFFF;
                case 3 -> (capacity >>> 16) & 0xFFFF;
                case 4 -> condenserCount;
                case 5 -> panelCount;
                case 6 -> activePanelCount;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return EnergyMonitorMenu.DATA_COUNT;
        }
    };

    public EnergyMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_MONITOR, pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (rescanCooldown > 0) {
            rescanCooldown--;
            return;
        }
        refreshNetworkSnapshot(serverLevel);
    }

    public ContainerData dataAccess() {
        return dataAccess;
    }

    public void forceRescan() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        refreshNetworkSnapshot(serverLevel);
    }

    private void refreshNetworkSnapshot(ServerLevel level) {
        rescanCooldown = RESCAN_INTERVAL_TICKS;

        BlockPos attachedCondenserPos = getAttachedCondenserPos();
        EnergyNetworkHelper.EnergyNetworkSnapshot snapshot = attachedCondenserPos == null
                ? EnergyNetworkHelper.EnergyNetworkSnapshot.EMPTY
                : EnergyNetworkHelper.getNetworkSnapshot(level, attachedCondenserPos);

        boolean changed = snapshot.storedEnergy() != storedEnergy
                || snapshot.capacity() != capacity
                || snapshot.condenserCount() != condenserCount
                || snapshot.panelCount() != panelCount
                || snapshot.activePanelCount() != activePanelCount;

        storedEnergy = snapshot.storedEnergy();
        capacity = snapshot.capacity();
        condenserCount = snapshot.condenserCount();
        panelCount = snapshot.panelCount();
        activePanelCount = snapshot.activePanelCount();

        if (changed) {
            setChanged();
        }
    }

    private BlockPos getAttachedCondenserPos() {
        if (level == null) return null;

        BlockState state = getBlockState();
        if (!state.is(ModBlocks.ENERGY_MONITOR) || !state.hasProperty(EnergyMonitorBlock.FACING)) {
            return null;
        }

        BlockPos supportPos = worldPosition.relative(state.getValue(EnergyMonitorBlock.FACING).getOpposite());
        return level.getBlockState(supportPos).is(ModBlocks.ENERGY_CONDENSER) ? supportPos : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("StoredEnergy", storedEnergy);
        tag.putInt("Capacity", capacity);
        tag.putInt("CondenserCount", condenserCount);
        tag.putInt("PanelCount", panelCount);
        tag.putInt("ActivePanelCount", activePanelCount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storedEnergy = Math.max(0, tag.getInt("StoredEnergy"));
        capacity = Math.max(0, tag.getInt("Capacity"));
        condenserCount = Math.max(0, tag.getInt("CondenserCount"));
        panelCount = Math.max(0, tag.getInt("PanelCount"));
        activePanelCount = Math.max(0, tag.getInt("ActivePanelCount"));
        rescanCooldown = 0;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.universegate.energy_monitor");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv, Player player) {
        forceRescan();
        return new EnergyMonitorMenu(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.worldPosition;
    }
}
