package fr.geomtech.universegate.fabric.compat;

import fr.geomtech.universegate.EnergyCondenserBlockEntity;
import fr.geomtech.universegate.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

/**
 * Exposes the Energy Condenser through Team Reborn Energy, the shared energy API of Fabric mods
 * (used by Stellaris' Potentials library on Fabric, Tech Reborn, etc.).
 *
 * <p>Only loaded when the {@code team_reborn_energy} mod is present: see
 * {@link fr.geomtech.universegate.fabric.UniverseGateFabric}. Never reference this class otherwise,
 * or the game crashes with NoClassDefFoundError for players without the library.
 */
public final class TeamRebornEnergyCompat {

    private TeamRebornEnergyCompat() {}

    public static void register() {
        EnergyStorage.SIDED.registerForBlockEntity(
                (condenser, side) -> new CondenserEnergyStorage(condenser),
                ModBlockEntities.ENERGY_CONDENSER);
    }

    private static final class CondenserEnergyStorage extends SnapshotParticipant<Integer> implements EnergyStorage {

        private final EnergyCondenserBlockEntity condenser;

        private CondenserEnergyStorage(EnergyCondenserBlockEntity condenser) {
            this.condenser = condenser;
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            int accepted = condenser.receiveExternal(maxAmount, true);
            if (accepted <= 0) return 0L;
            updateSnapshots(transaction);
            condenser.restoreStoredEnergy(condenser.getStoredEnergy() + accepted);
            return accepted;
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            int extracted = condenser.extractExternal(maxAmount, true);
            if (extracted <= 0) return 0L;
            updateSnapshots(transaction);
            condenser.restoreStoredEnergy(condenser.getStoredEnergy() - extracted);
            return extracted;
        }

        @Override
        public long getAmount() {
            return condenser.getStoredEnergy();
        }

        @Override
        public long getCapacity() {
            return condenser.getCapacity();
        }

        @Override
        protected Integer createSnapshot() {
            return condenser.getStoredEnergy();
        }

        @Override
        protected void readSnapshot(Integer snapshot) {
            condenser.restoreStoredEnergy(snapshot);
        }

        @Override
        protected void onFinalCommit() {
            condenser.setChanged();
        }
    }
}
