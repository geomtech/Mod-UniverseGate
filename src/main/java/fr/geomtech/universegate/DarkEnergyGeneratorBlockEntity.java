package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DarkEnergyGeneratorBlockEntity extends BlockEntity {

    private int darkMatterAmount = 0;
    private int energyAmount = 0;
    public static final int MAX_DARK_MATTER = 10000; // mB
    public static final int MAX_ENERGY = 50000;

    // Production rate
    public static final int DARK_MATTER_CONSUMPTION = 5; // per tick
    public static final int ENERGY_CONSUMPTION = 100; // per tick

    private boolean isRunning = false;

    public DarkEnergyGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DARK_ENERGY_GENERATOR, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DarkEnergyGeneratorBlockEntity entity) {
        if (level.isClientSide) return;

        // Logic: If has fuel + energy, run.
        if (entity.darkMatterAmount >= DARK_MATTER_CONSUMPTION && entity.energyAmount >= ENERGY_CONSUMPTION) {
            entity.darkMatterAmount -= DARK_MATTER_CONSUMPTION;
            entity.energyAmount -= ENERGY_CONSUMPTION;
            entity.isRunning = true;
        } else {
            entity.isRunning = false;
        }
    }

    public boolean isGenerating() {
        return isRunning;
    }
    
    // Simplistic Fluid/Energy insertion methods for now
    public int fillDarkMatter(int amount) {
        int space = MAX_DARK_MATTER - darkMatterAmount;
        int toFill = Math.min(amount, space);
        darkMatterAmount += toFill;
        return toFill;
    }

    public int receiveEnergy(int amount) {
        int space = MAX_ENERGY - energyAmount;
        int toReceive = Math.min(amount, space);
        energyAmount += toReceive;
        return toReceive;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("DarkMatter", darkMatterAmount);
        tag.putInt("Energy", energyAmount);
        tag.putBoolean("Running", isRunning);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        darkMatterAmount = tag.getInt("DarkMatter");
        energyAmount = tag.getInt("Energy");
        isRunning = tag.getBoolean("Running");
    }
}
