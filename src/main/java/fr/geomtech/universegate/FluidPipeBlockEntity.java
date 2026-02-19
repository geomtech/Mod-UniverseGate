package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FluidPipeBlockEntity extends BlockEntity {

    // Capacity of a single pipe segment (e.g., 1000mB = 1 Bucket)
    public static final int CAPACITY = 1000;
    private int fluidAmount = 0;
    
    // We only transport Dark Matter for now, but good to be explicit
    // Could store Fluid type here if we want multi-fluid support later

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_PIPE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FluidPipeBlockEntity entity) {
        if (level.isClientSide) return;

        if (entity.fluidAmount > 0) {
            entity.tryDistributeFluid(level, pos);
        }
    }

    private void tryDistributeFluid(Level level, BlockPos pos) {
        int fluidToDistribute = this.fluidAmount;
        if (fluidToDistribute <= 0) return;

        // Find valid targets (pipes with space or consumers)
        // We can optimize by caching connections, but for now scan neighbors
        
        java.util.List<Direction> validOutputs = new java.util.ArrayList<>();
        
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockEntity neighborBe = level.getBlockEntity(neighborPos);

            if (neighborBe instanceof FluidPipeBlockEntity pipe) {
                if (pipe.fluidAmount < CAPACITY) {
                    validOutputs.add(dir);
                }
            } else if (neighborBe instanceof DarkEnergyGeneratorBlockEntity generator) {
                // Check if generator needs fuel
                // Assuming generator has a simplistic fill method for now
                 validOutputs.add(dir);
            }
        }

        if (validOutputs.isEmpty()) return;

        // Split fluid evenly or push as much as possible? 
        // Let's try to equalize or push forward. Simple push for now.
        int amountPerTarget = Math.max(1, fluidToDistribute / validOutputs.size());
        int remaining = fluidToDistribute;

        for (Direction dir : validOutputs) {
            if (remaining <= 0) break;
            
            BlockPos neighborPos = pos.relative(dir);
            BlockEntity neighborBe = level.getBlockEntity(neighborPos);
            
            int moved = 0;
            if (neighborBe instanceof FluidPipeBlockEntity pipe) {
                // Don't backflow easily - crude logic: only push if they have LESS than us?
                // Or just push if they have space. "Diffusion" model.
                if (pipe.fluidAmount < this.fluidAmount) {
                     int space = CAPACITY - pipe.fluidAmount;
                     // Move half the difference to equalize
                     int transfer = Math.min(remaining, Math.min(space, (this.fluidAmount - pipe.fluidAmount) / 2));
                     if (transfer > 0) {
                         pipe.fluidAmount += transfer;
                         moved = transfer;
                         pipe.setChanged();
                         pipe.sync();
                     }
                }
            } else if (neighborBe instanceof DarkEnergyGeneratorBlockEntity generator) {
                // Always push to consumers
                int accepted = generator.fillDarkMatter(Math.min(remaining, 100)); // Rate limit 100mB/tick
                moved = accepted;
            }
            
            remaining -= moved;
        }
        
        if (remaining != this.fluidAmount) {
            this.fluidAmount = remaining;
            setChanged();
            sync();
        }
    }

    // Public method for other blocks (like Refiner) to insert fluid
    public int fill(int amount) {
        if (amount <= 0) return 0;

        int space = CAPACITY - fluidAmount;
        if (space <= 0) return 0;

        int toFill = Math.min(amount, space);
        fluidAmount += toFill;
        if (toFill > 0) {
            setChanged();
            sync();
        }
        return toFill;
    }

    public int getFluidAmount() {
        return fluidAmount;
    }

    // Syncing
    public void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("FluidAmount", fluidAmount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fluidAmount = tag.getInt("FluidAmount");
    }
}
