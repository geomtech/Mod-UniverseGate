package fr.geomtech.universegate.neoforge;

import fr.geomtech.universegate.ExternalEnergyPort;
import fr.geomtech.universegate.ModBlockEntities;
import fr.geomtech.universegate.ModBlocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Exposes UniverseGate's EU blocks through NeoForge's standard energy capability (FE).
 *
 * <p>Stellaris (through its Potentials library), Mekanism, Powah... all read this capability, so their
 * cables can fill the network and draw from it. Nothing here references a specific mod: the
 * capability is part of NeoForge, so this works whether those mods are installed or not.
 * Dark energy deliberately stays on its own network.
 */
public final class NeoForgeEnergyCompat {

    private NeoForgeEnergyCompat() {}

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.ENERGY_CONDENSER,
                (condenser, side) -> new PortEnergyStorage(ExternalEnergyPort.condenser(condenser)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.ZPC_INTERFACE_CONTROLLER,
                (controller, side) -> new PortEnergyStorage(ExternalEnergyPort.zpcInterface(controller)));
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, blockEntity, side) -> {
            ExternalEnergyPort port = ExternalEnergyPort.conduit(level, pos);
            return port == null ? null : new PortEnergyStorage(port);
        }, ModBlocks.ENERGY_CONDUIT);
    }

    private record PortEnergyStorage(ExternalEnergyPort port) implements IEnergyStorage {

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return port.canInsert() ? port.insert(toReceive, simulate) : 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return port.canExtract() ? port.extract(toExtract, simulate) : 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, port.stored()));
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, port.capacity()));
        }

        @Override
        public boolean canExtract() {
            return port.canExtract();
        }

        @Override
        public boolean canReceive() {
            return port.canInsert();
        }
    }
}
