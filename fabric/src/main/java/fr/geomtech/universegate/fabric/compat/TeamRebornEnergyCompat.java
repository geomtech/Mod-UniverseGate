package fr.geomtech.universegate.fabric.compat;

import fr.geomtech.universegate.ExternalEnergyPort;
import fr.geomtech.universegate.ModBlockEntities;
import fr.geomtech.universegate.ModBlocks;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

/**
 * Exposes UniverseGate's EU blocks through Team Reborn Energy, the shared energy API of Fabric mods
 * (used by Stellaris' Potentials library on Fabric, Tech Reborn, etc.). Dark energy stays separate.
 *
 * <p>Only loaded when the {@code team_reborn_energy} mod is present: see
 * {@link fr.geomtech.universegate.fabric.UniverseGateFabric}. Never reference this class otherwise,
 * or the game crashes with NoClassDefFoundError for players without the library.
 */
public final class TeamRebornEnergyCompat {

    private TeamRebornEnergyCompat() {}

    public static void register() {
        EnergyStorage.SIDED.registerForBlockEntity(
                (condenser, side) -> new PortEnergyStorage(ExternalEnergyPort.condenser(condenser)),
                ModBlockEntities.ENERGY_CONDENSER);
        EnergyStorage.SIDED.registerForBlockEntity(
                (controller, side) -> new PortEnergyStorage(ExternalEnergyPort.zpcInterface(controller)),
                ModBlockEntities.ZPC_INTERFACE_CONTROLLER);
        EnergyStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) -> {
            ExternalEnergyPort port = ExternalEnergyPort.conduit(level, pos);
            return port == null ? null : new PortEnergyStorage(port);
        }, ModBlocks.ENERGY_CONDUIT);
    }

    /**
     * Transactional wrapper: amounts are only reserved during the transaction and really moved
     * when the outermost transaction commits, so an aborted transfer never touches the block.
     */
    private static final class PortEnergyStorage extends SnapshotParticipant<long[]> implements EnergyStorage {

        private final ExternalEnergyPort port;
        private long pendingInsert;
        private long pendingExtract;

        private PortEnergyStorage(ExternalEnergyPort port) {
            this.port = port;
        }

        @Override
        public boolean supportsInsertion() {
            return port.canInsert();
        }

        @Override
        public boolean supportsExtraction() {
            return port.canExtract();
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            if (!port.canInsert() || maxAmount <= 0L) return 0L;
            long accepted = port.insert(pendingInsert + maxAmount, true) - pendingInsert;
            if (accepted <= 0L) return 0L;
            updateSnapshots(transaction);
            pendingInsert += accepted;
            return accepted;
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            if (!port.canExtract() || maxAmount <= 0L) return 0L;
            long extracted = port.extract(pendingExtract + maxAmount, true) - pendingExtract;
            if (extracted <= 0L) return 0L;
            updateSnapshots(transaction);
            pendingExtract += extracted;
            return extracted;
        }

        @Override
        public long getAmount() {
            return Math.max(0L, port.stored() + pendingInsert - pendingExtract);
        }

        @Override
        public long getCapacity() {
            return Math.max(0L, port.capacity());
        }

        @Override
        protected long[] createSnapshot() {
            return new long[]{pendingInsert, pendingExtract};
        }

        @Override
        protected void readSnapshot(long[] snapshot) {
            pendingInsert = snapshot[0];
            pendingExtract = snapshot[1];
        }

        @Override
        protected void onFinalCommit() {
            if (pendingExtract > 0L) port.extract(pendingExtract, false);
            if (pendingInsert > 0L) port.insert(pendingInsert, false);
            pendingInsert = 0L;
            pendingExtract = 0L;
        }
    }
}
