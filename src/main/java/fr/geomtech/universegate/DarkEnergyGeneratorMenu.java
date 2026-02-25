package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class DarkEnergyGeneratorMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 6;

    private final BlockPos generatorPos;
    private final ContainerData data;

    public DarkEnergyGeneratorMenu(int syncId, Inventory inv, BlockPos pos) {
        this(syncId, inv, pos, new SimpleContainerData(DATA_COUNT));
    }

    public DarkEnergyGeneratorMenu(int syncId, Inventory inv, DarkEnergyGeneratorBlockEntity generator) {
        this(syncId, inv, generator.getBlockPos(), generator.dataAccess());
    }

    private DarkEnergyGeneratorMenu(int syncId,
                                    Inventory inv,
                                    BlockPos generatorPos,
                                    ContainerData data) {
        super(ModMenuTypes.DARK_ENERGY_GENERATOR, syncId);
        this.generatorPos = generatorPos.immutable();
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);
        this.addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(generatorPos).is(ModBlocks.DARK_ENERGY_GENERATOR)
                && player.distanceToSqr(generatorPos.getX() + 0.5D, generatorPos.getY() + 0.5D, generatorPos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public int darkMatterAmount() {
        return Mth.clamp(data.get(0), 0, DarkEnergyGeneratorBlockEntity.MAX_DARK_MATTER);
    }

    public int darkMatterCapacity() {
        return DarkEnergyGeneratorBlockEntity.MAX_DARK_MATTER;
    }

    public int darkMatterPercent() {
        return Mth.clamp((int) Math.round((darkMatterAmount() * 100.0D) / (double) darkMatterCapacity()), 0, 100);
    }

    public int storedDarkEnergy() {
        return Mth.clamp(combine(data.get(1), data.get(2)), 0, DarkEnergyGeneratorBlockEntity.MAX_ENERGY);
    }

    public int darkEnergyCapacity() {
        return DarkEnergyGeneratorBlockEntity.MAX_ENERGY;
    }

    public int darkEnergyPercent() {
        return Mth.clamp((int) Math.round((storedDarkEnergy() * 100.0D) / (double) darkEnergyCapacity()), 0, 100);
    }

    public boolean isRunning() {
        return data.get(3) != 0;
    }

    public boolean hasFuel() {
        return data.get(4) != 0;
    }

    public boolean networkOnline() {
        return data.get(5) != 0;
    }

    public int outputPerTick() {
        return DarkEnergyGeneratorBlockEntity.OUTPUT_PER_TICK;
    }

    public int reserveAmount() {
        return DarkEnergyGeneratorBlockEntity.NETWORK_BUFFER_RESERVE;
    }

    private static int combine(int low, int high) {
        return ((high & 0xFFFF) << 16) | (low & 0xFFFF);
    }
}
