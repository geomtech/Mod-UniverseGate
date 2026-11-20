package fr.geomtech.universegate;

import fr.geomtech.universegate.compat.DataReader;
import fr.geomtech.universegate.compat.DataWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.11 {
/*import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?}

import java.util.List;

/**
 * Charge stored by a lightning antenna (on its base). A lightning strike adds one charge and
 * immediately tries to open the Rift through every portal wired to the antenna; the charge is
 * spent when a portal opens and kept otherwise, up to {@link #MAX_CHARGE}.
 */
public class LightningAntennaBlockEntity extends BlockEntity {

    public static final int MAX_CHARGE = 5;
    private static final long SCAN_INTERVAL_TICKS = 40L;

    private int charge = 0;

    public LightningAntennaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIGHTNING_ANTENNA, pos, state);
    }

    public int getCharge() {
        return charge;
    }

    public boolean hasCharge() {
        return charge > 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LightningAntennaBlockEntity antenna) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!antenna.hasCharge()) return;
        if (serverLevel.getGameTime() % SCAN_INTERVAL_TICKS != 0L) return;

        // A charged antenna shows where its charge would go: a few sparks on the wired portals and along the wires.
        WireNetwork.Scan scan = WireNetwork.scan(serverLevel, pos);
        for (BlockPos core : scan.cores()) {
            serverLevel.sendParticles(ModParticles.SPARK,
                    core.getX() + 0.5D, core.getY() + 1.2D, core.getZ() + 0.5D,
                    4, 0.35D, 0.5D, 0.35D, 0.0D);
        }
        sparkAlongWires(serverLevel, scan.wires(), 1);
    }

    /** Called by the lightning bolt that struck this antenna. */
    public void onLightningStrike(ServerLevel level) {
        setCharge(Math.min(MAX_CHARGE, charge + 1));

        WireNetwork.Scan scan = WireNetwork.scan(level, worldPosition);
        sparkAlongWires(level, scan.wires(), 24);
        for (BlockPos core : scan.cores()) {
            if (PortalRiftHelper.tryOpenRiftFromAntenna(level, core)) {
                setCharge(charge - 1);
                return;
            }
        }
    }

    private static void sparkAlongWires(ServerLevel level, List<BlockPos> wires, int count) {
        if (wires.isEmpty()) return;
        var random = level.getRandom();
        for (int i = 0; i < count; i++) {
            BlockPos wire = wires.get(random.nextInt(wires.size()));
            level.sendParticles(ModParticles.SPARK,
                    wire.getX() + 0.5D, wire.getY() + 0.3D, wire.getZ() + 0.5D,
                    1, 0.2D, 0.1D, 0.2D, 0.0D);
        }
    }

    private void setCharge(int value) {
        int clamped = Math.max(0, Math.min(MAX_CHARGE, value));
        if (charge == clamped) return;
        boolean wasCharged = charge > 0;
        charge = clamped;
        setChanged();
        if (level != null && !level.isClientSide()) {
            if (wasCharged != (charge > 0)) {
                LightningAntennaBlock.setCharged(level, worldPosition, charge > 0);
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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
        tag.putInt("Charge", charge);
    }

    private void loadData(DataReader tag) {
        charge = tag.getInt("Charge");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Charge", charge);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
