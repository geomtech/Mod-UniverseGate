package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Charge", charge);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
