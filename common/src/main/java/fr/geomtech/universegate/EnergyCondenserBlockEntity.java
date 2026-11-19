package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class EnergyCondenserBlockEntity extends BlockEntity {

    public static final int CAPACITY = 20000;
    private static final int SOLAR_TICK_INTERVAL = 20;
    private static final int ENERGY_PER_PANEL_PER_INTERVAL = 60;

    private int storedEnergy = 0;

    public EnergyCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_CONDENSER, pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % SOLAR_TICK_INTERVAL != 0L) return;

        EnergyNetworkHelper.CondenserNetwork network = EnergyNetworkHelper.scanCondenserNetwork(serverLevel, worldPosition);
        if (network.condensers().isEmpty() || !network.condensers().contains(worldPosition)) return;

        BlockPos leader = EnergyNetworkHelper.findNetworkLeader(network.condensers());
        if (leader == null || !leader.equals(worldPosition)) return;

        int activePanels = 0;
        for (BlockPos panelPos : network.solarPanels()) {
            if (EnergyNetworkHelper.isSolarPanelGenerating(serverLevel, panelPos)) {
                activePanels++;
            }
        }
        if (activePanels <= 0) return;

        int generated = activePanels * ENERGY_PER_PANEL_PER_INTERVAL;
        EnergyNetworkHelper.distributeEnergy(serverLevel, network.condensers(), generated);
    }

    public int getStoredEnergy() {
        return storedEnergy;
    }

    public int getFreeSpace() {
        return Math.max(0, CAPACITY - storedEnergy);
    }

    public int addEnergy(int amount) {
        if (amount <= 0) return 0;
        int accepted = Math.min(amount, getFreeSpace());
        if (accepted <= 0) return 0;
        storedEnergy += accepted;
        setChanged();
        return accepted;
    }

    public int extractEnergy(int amount) {
        if (amount <= 0) return 0;
        int extracted = Math.min(amount, storedEnergy);
        if (extracted <= 0) return 0;
        storedEnergy -= extracted;
        setChanged();
        return extracted;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("StoredEnergy", storedEnergy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storedEnergy = Math.max(0, Math.min(CAPACITY, tag.getInt("StoredEnergy")));
    }
}
