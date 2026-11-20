package fr.geomtech.universegate.neoforge;

import fr.geomtech.universegate.EnergyCondenserBlockEntity;
import fr.geomtech.universegate.ModBlockEntities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Exposes the Energy Condenser through NeoForge's standard energy capability (FE).
 *
 * <p>Stellaris (through its Potentials library) and most NeoForge tech mods read this capability, so
 * their cables can fill condensers and draw from them. Nothing here references Stellaris: the
 * capability is part of NeoForge, so this works whether Stellaris is installed or not.
 */
public final class NeoForgeEnergyCompat {

    private NeoForgeEnergyCompat() {}

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.ENERGY_CONDENSER,
                (condenser, side) -> new CondenserEnergyStorage(condenser));
    }

    private record CondenserEnergyStorage(EnergyCondenserBlockEntity condenser) implements IEnergyStorage {

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return condenser.receiveExternal(toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return condenser.extractExternal(toExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return condenser.getStoredEnergy();
        }

        @Override
        public int getMaxEnergyStored() {
            return condenser.getCapacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
