package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class AcidNeonLampBlockEntity extends BlockEntity {

    public static final int CAPACITY = AcidBatteryItem.CAPACITY * 4;
    private int storedEnergy;

    public AcidNeonLampBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.ACID_NEON_LAMP, pos, state);
    }

    protected AcidNeonLampBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AcidNeonLampBlockEntity entity) {
        if (!level.isClientSide) entity.serverTick(level, pos, state);
    }

    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        boolean operating = storedEnergy > 0 && canOperate(level, pos);
        if (operating) {
            storedEnergy = Math.max(0, storedEnergy - energyPerTick());
            if (level.getGameTime() % 20L == 0L || storedEnergy == 0) setChanged();
        }

        boolean shouldBeLit = operating && storedEnergy > 0;
        if (state.getValue(AcidNeonLampBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(AcidNeonLampBlock.LIT, shouldBeLit), 3);
        }
    }

    protected boolean canOperate(Level level, BlockPos pos) { return true; }
    protected int energyPerTick() { return 1; }

    public int getStoredEnergy() {
        return storedEnergy;
    }

    public int addEnergy(int energy) {
        int accepted = Math.min(Math.max(energy, 0), CAPACITY - storedEnergy);
        if (accepted <= 0) return 0;
        storedEnergy += accepted;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (!state.getValue(AcidNeonLampBlock.LIT)) {
                level.setBlock(worldPosition, state.setValue(AcidNeonLampBlock.LIT, true), 3);
            }
        }
        return accepted;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("StoredEnergy", storedEnergy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storedEnergy = Math.max(0, Math.min(CAPACITY, tag.getInt("StoredEnergy")));
    }
}
