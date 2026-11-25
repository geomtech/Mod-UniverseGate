package fr.geomtech.universegate;

import fr.geomtech.universegate.compat.DataReader;
import fr.geomtech.universegate.compat.DataWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
//? if >=1.21.11 {
/*import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PortalActivatorBlockEntity extends PortalKeyboardBlockEntity {

    private @Nullable UUID targetPortalId;
    private boolean redstonePowered;

    public PortalActivatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PORTAL_ACTIVATOR, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortalActivatorBlockEntity activator) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(PortalKeyboardBlock.LIT) != powered) {
            level.setBlock(pos, state.setValue(PortalKeyboardBlock.LIT, powered), 3);
        }

        if (powered && !activator.redstonePowered) {
            activator.activate(serverLevel);
        }
        if (powered != activator.redstonePowered) {
            activator.redstonePowered = powered;
            activator.setChanged();
        }
    }

    private void activate(ServerLevel level) {
        if (targetPortalId == null) {
            ModSounds.playAt(level, worldPosition, ModSounds.PORTAL_ERROR, 0.9F, 1.0F);
            return;
        }

        BlockPos corePos = WireNetwork.findWiredCore(level, worldPosition);
        if (corePos == null) {
            ModSounds.playAt(level, worldPosition, ModSounds.PORTAL_ERROR, 0.9F, 1.0F);
            return;
        }

        PortalDialResult result = PortalDialService.dial(level, corePos, targetPortalId, false);
        if (!result.success()) {
            ModSounds.playAt(level, corePos, ModSounds.PORTAL_ERROR, 0.9F, 1.0F);
        }
    }

    public @Nullable UUID getTargetPortalId() {
        return targetPortalId;
    }

    public void setTargetPortalId(UUID targetPortalId) {
        this.targetPortalId = targetPortalId;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.universegate.portal_activator");
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

    private void saveData(DataWriter data) {
        if (targetPortalId != null) data.putUUID("TargetPortalId", targetPortalId);
        data.putBoolean("RedstonePowered", redstonePowered);
    }

    private void loadData(DataReader data) {
        targetPortalId = data.getUUID("TargetPortalId");
        redstonePowered = data.getBoolean("RedstonePowered");
    }
}
