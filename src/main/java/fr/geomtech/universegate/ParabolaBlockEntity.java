package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("IsPowered", isPowered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
