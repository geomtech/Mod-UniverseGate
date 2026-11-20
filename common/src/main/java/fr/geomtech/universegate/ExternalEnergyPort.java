package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;

/**
 * Loader-neutral view of a UniverseGate block as seen by other mods' energy APIs (NeoForge FE
 * capability, Team Reborn Energy on Fabric). Stellaris, Mekanism, Tech Reborn... all go through it.
 *
 * <ul>
 *     <li>Energy Condenser: in and out.</li>
 *     <li>Energy Conduit: in and out, spread over every condenser of the conduit network, so a
 *     foreign cable can plug in anywhere on the network.</li>
 *     <li>ZPC Interface: out only (ZPCs are only recharged with dark energy).</li>
 * </ul>
 * Each port moves at most {@link ExternalEnergyTransfer#MAX_TRANSFER_PER_CALL} per call. 1 FE = 1 E.
 */
public interface ExternalEnergyPort {

    /** Returns the amount accepted (or that would be accepted when {@code simulate}). */
    int insert(long maxAmount, boolean simulate);

    /** Returns the amount extracted (or that would be extracted when {@code simulate}). */
    int extract(long maxAmount, boolean simulate);

    long stored();

    long capacity();

    default boolean canInsert() {
        return true;
    }

    default boolean canExtract() {
        return true;
    }

    // --- Factories ---

    static ExternalEnergyPort condenser(EnergyCondenserBlockEntity condenser) {
        return new ExternalEnergyPort() {
            @Override
            public int insert(long maxAmount, boolean simulate) {
                return condenser.receiveExternal(maxAmount, simulate);
            }

            @Override
            public int extract(long maxAmount, boolean simulate) {
                return condenser.extractExternal(maxAmount, simulate);
            }

            @Override
            public long stored() {
                return condenser.getStoredEnergy();
            }

            @Override
            public long capacity() {
                return condenser.getCapacity();
            }
        };
    }

    static ExternalEnergyPort zpcInterface(ZpcInterfaceControllerBlockEntity controller) {
        return new ExternalEnergyPort() {
            @Override
            public int insert(long maxAmount, boolean simulate) {
                return 0;
            }

            @Override
            public int extract(long maxAmount, boolean simulate) {
                if (!controller.canSupplyEnergy()) return 0;
                long available = Math.min(controller.getStoredEnergy(), Integer.MAX_VALUE);
                int amount = ExternalEnergyTransfer.extractable(maxAmount, (int) available);
                if (simulate || amount <= 0) return amount;
                return controller.extractEnergy(amount);
            }

            @Override
            public long stored() {
                return controller.canSupplyEnergy() ? controller.getStoredEnergy() : 0L;
            }

            @Override
            public long capacity() {
                return controller.getTotalCapacity();
            }

            @Override
            public boolean canInsert() {
                return false;
            }
        };
    }

    /** Port for an Energy Conduit, or {@code null} on the client. */
    static ExternalEnergyPort conduit(Level level, BlockPos conduitPos) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        BlockPos pos = conduitPos.immutable();
        return new ExternalEnergyPort() {
            private List<EnergyCondenserBlockEntity> condensers() {
                return EnergyNetworkHelper.getCondensersOnConduitNetwork(serverLevel, pos);
            }

            @Override
            public int insert(long maxAmount, boolean simulate) {
                long remaining = Math.min(maxAmount, ExternalEnergyTransfer.MAX_TRANSFER_PER_CALL);
                if (remaining <= 0L) return 0;
                List<EnergyCondenserBlockEntity> list = condensers();
                list.sort(Comparator.comparingInt(EnergyCondenserBlockEntity::getStoredEnergy));
                int total = 0;
                for (EnergyCondenserBlockEntity condenser : list) {
                    if (remaining <= 0L) break;
                    int moved = condenser.receiveExternal(remaining, simulate);
                    total += moved;
                    remaining -= moved;
                }
                return total;
            }

            @Override
            public int extract(long maxAmount, boolean simulate) {
                long remaining = Math.min(maxAmount, ExternalEnergyTransfer.MAX_TRANSFER_PER_CALL);
                if (remaining <= 0L) return 0;
                List<EnergyCondenserBlockEntity> list = condensers();
                list.sort(Comparator.comparingInt(EnergyCondenserBlockEntity::getStoredEnergy).reversed());
                int total = 0;
                for (EnergyCondenserBlockEntity condenser : list) {
                    if (remaining <= 0L) break;
                    int moved = condenser.extractExternal(remaining, simulate);
                    total += moved;
                    remaining -= moved;
                }
                return total;
            }

            @Override
            public long stored() {
                long sum = 0L;
                for (EnergyCondenserBlockEntity condenser : condensers()) sum += condenser.getStoredEnergy();
                return sum;
            }

            @Override
            public long capacity() {
                long sum = 0L;
                for (EnergyCondenserBlockEntity condenser : condensers()) sum += condenser.getCapacity();
                return sum;
            }
        };
    }
}
