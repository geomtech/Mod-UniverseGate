package fr.geomtech.universegate.neoforge;

import fr.geomtech.universegate.ExternalEnergyPort;
import fr.geomtech.universegate.ModBlockEntities;
import fr.geomtech.universegate.ModBlocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
//? if >=1.21.11 {
/*import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
*///?} else {
import net.neoforged.neoforge.energy.IEnergyStorage;
//?}

/**
 * Exposes UniverseGate's EU blocks through NeoForge's standard energy capability (FE).
 *
 * <p>Stellaris (through its Potentials library), Mekanism, Powah... all read this capability, so their
 * cables can fill the network and draw from it. Nothing here references a specific mod: the
 * capability is part of NeoForge, so this works whether those mods are installed or not.
 * Dark energy deliberately stays on its own network.
 */
public final class NeoForgeEnergyCompat {

    //? if >=1.21.11 {
    /*private static final net.neoforged.neoforge.capabilities.BlockCapability<EnergyHandler, @Nullable Direction> ENERGY_BLOCK =
            Capabilities.Energy.BLOCK;
    *///?} else {
    private static final net.neoforged.neoforge.capabilities.BlockCapability<IEnergyStorage, @Nullable Direction> ENERGY_BLOCK =
            Capabilities.EnergyStorage.BLOCK;
    //?}

    private NeoForgeEnergyCompat() {}

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(ENERGY_BLOCK, ModBlockEntities.ENERGY_CONDENSER,
                (condenser, side) -> new PortEnergyStorage(ExternalEnergyPort.condenser(condenser)));
        event.registerBlockEntity(ENERGY_BLOCK, ModBlockEntities.ZPC_INTERFACE_CONTROLLER,
                (controller, side) -> new PortEnergyStorage(ExternalEnergyPort.zpcInterface(controller)));
        event.registerBlock(ENERGY_BLOCK, (level, pos, state, blockEntity, side) -> {
            ExternalEnergyPort port = ExternalEnergyPort.conduit(level, pos);
            return port == null ? null : new PortEnergyStorage(port);
        }, ModBlocks.ENERGY_CONDUIT);
    }

    //? if >=1.21.11 {
    /*/^*
     * Transactional handler (NeoForge transfer API, 1.21.9+): amounts are only reserved during the
     * transaction and really moved when the root transaction commits, as in the Fabric compat.
     ^/
    private static final class PortEnergyStorage extends SnapshotJournal<long[]> implements EnergyHandler {

        private final ExternalEnergyPort port;
        private long pendingInsert;
        private long pendingExtract;

        private PortEnergyStorage(ExternalEnergyPort port) {
            this.port = port;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            if (!port.canInsert() || amount == 0) return 0;
            long accepted = port.insert(pendingInsert + amount, true) - pendingInsert;
            if (accepted <= 0L) return 0;
            updateSnapshots(transaction);
            pendingInsert += accepted;
            return (int) accepted;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            if (!port.canExtract() || amount == 0) return 0;
            long extracted = port.extract(pendingExtract + amount, true) - pendingExtract;
            if (extracted <= 0L) return 0;
            updateSnapshots(transaction);
            pendingExtract += extracted;
            return (int) extracted;
        }

        @Override
        public long getAmountAsLong() {
            return Math.max(0L, port.stored() + pendingInsert - pendingExtract);
        }

        @Override
        public long getCapacityAsLong() {
            return Math.max(0L, port.capacity());
        }

        @Override
        protected long[] createSnapshot() {
            return new long[]{pendingInsert, pendingExtract};
        }

        @Override
        protected void revertToSnapshot(long[] snapshot) {
            pendingInsert = snapshot[0];
            pendingExtract = snapshot[1];
        }

        @Override
        protected void onRootCommit(long[] originalState) {
            if (pendingExtract > 0L) port.extract(pendingExtract, false);
            if (pendingInsert > 0L) port.insert(pendingInsert, false);
            pendingInsert = 0L;
            pendingExtract = 0L;
        }
    }
    *///?} else {
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
    //?}
}
