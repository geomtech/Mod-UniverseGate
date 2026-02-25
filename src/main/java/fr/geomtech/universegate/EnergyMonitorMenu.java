package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class EnergyMonitorMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 11;

    private final BlockPos monitorPos;
    private final ContainerData data;

    public EnergyMonitorMenu(int syncId, Inventory inv, BlockPos pos) {
        this(syncId, inv, pos, new SimpleContainerData(DATA_COUNT));
    }

    public EnergyMonitorMenu(int syncId, Inventory inv, EnergyMonitorBlockEntity monitor) {
        this(syncId, inv, monitor.getBlockPos(), monitor.dataAccess());
    }

    private EnergyMonitorMenu(int syncId,
                              Inventory inv,
                              BlockPos monitorPos,
                              ContainerData data) {
        super(ModMenuTypes.ENERGY_MONITOR, syncId);
        this.monitorPos = monitorPos.immutable();
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);
        this.addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(monitorPos).is(ModBlocks.ENERGY_MONITOR)
                && player.distanceToSqr(monitorPos.getX() + 0.5D, monitorPos.getY() + 0.5D, monitorPos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public long storedEnergy() {
        return combineLong(data.get(0), data.get(1), data.get(2), data.get(3));
    }

    public long capacity() {
        return combineLong(data.get(4), data.get(5), data.get(6), data.get(7));
    }

    public int condenserCount() {
        return Math.max(0, data.get(8));
    }

    public int panelCount() {
        return Math.max(0, data.get(9));
    }

    public int activePanelCount() {
        return Math.max(0, data.get(10));
    }

    public int chargePercent() {
        long cap = capacity();
        if (cap <= 0) return 0;
        return Mth.clamp((int) Math.round((storedEnergy() * 100.0D) / (double) cap), 0, 100);
    }

    private static long combineLong(int a, int b, int c, int d) {
        return ((long) (d & 0xFFFF) << 48)
                | ((long) (c & 0xFFFF) << 32)
                | ((long) (b & 0xFFFF) << 16)
                | ((long) (a & 0xFFFF));
    }
}
