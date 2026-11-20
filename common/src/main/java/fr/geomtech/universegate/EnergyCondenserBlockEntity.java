package fr.geomtech.universegate;

import fr.geomtech.universegate.compat.DataReader;
import fr.geomtech.universegate.compat.DataWriter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
//? if >=1.21.11 {
/*import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?}
import net.minecraft.core.BlockPos;
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

    // --- Energy exchange with other mods (Stellaris, any FE / Team Reborn Energy mod) ---
    // Exposed through ExternalEnergyPort: NeoForge energy capability in the neoforge module,
    // Team Reborn Energy (only when installed) in the fabric module.

    public int getCapacity() {
        return CAPACITY;
    }

    /** Insert energy coming from another mod. Returns the amount accepted. */
    public int receiveExternal(long maxAmount, boolean simulate) {
        int accepted = ExternalEnergyTransfer.insertable(maxAmount, storedEnergy, CAPACITY);
        if (!simulate && accepted > 0) {
            storedEnergy += accepted;
            setChanged();
        }
        return accepted;
    }

    /** Give energy to another mod. Returns the amount extracted. */
    public int extractExternal(long maxAmount, boolean simulate) {
        int extracted = ExternalEnergyTransfer.extractable(maxAmount, storedEnergy);
        if (!simulate && extracted > 0) {
            storedEnergy -= extracted;
            setChanged();
        }
        return extracted;
    }

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
        tag.putInt("StoredEnergy", storedEnergy);
    }

    private void loadData(DataReader tag) {
        storedEnergy = Math.max(0, Math.min(CAPACITY, tag.getInt("StoredEnergy")));
    }

}
