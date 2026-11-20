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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ParabolaBlockEntity extends BlockEntity {

    private boolean isPowered = false;

    public ParabolaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARABOLA, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ParabolaBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        
        if (level.getGameTime() % 20L != 0L) return;

        // Consume 120 energy per interval (assumed 20 ticks based on solar panel interval)
        boolean nowPowered = EnergyNetworkHelper.consumeEnergyFromNetwork(serverLevel, pos, 120);
        
        if (nowPowered != blockEntity.isPowered) {
            blockEntity.isPowered = nowPowered;
            blockEntity.setChanged();
            blockEntity.syncToClient();
        }
    }

    public boolean isPowered() {
        return isPowered;
    }

    private void syncToClient() {
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
        tag.putBoolean("IsPowered", isPowered);
    }

    private void loadData(DataReader tag) {
        isPowered = tag.getBoolean("IsPowered");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("IsPowered", isPowered);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
