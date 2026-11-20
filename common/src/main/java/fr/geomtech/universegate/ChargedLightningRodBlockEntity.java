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
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ChargedLightningRodBlockEntity extends BlockEntity {

    public static final int MAX_CHARGE = 5;
    private int charge = 0;

    public ChargedLightningRodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHARGED_LIGHTNING_ROD, pos, state);
    }

    public int getCharge() {
        return charge;
    }

    public boolean hasCharge() {
        return charge > 0;
    }

    public void addCharge(int amount) {
        if (amount <= 0) return;
        setCharge(Math.min(MAX_CHARGE, charge + amount));
    }

    public boolean consumeCharge(int amount) {
        if (amount <= 0) return true;
        if (charge < amount) return false;
        setCharge(charge - amount);
        return true;
    }

    private void setCharge(int value) {
        if (charge == value) return;
        charge = value;
        setChanged();
        if (level != null && !level.isClientSide()) {
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
