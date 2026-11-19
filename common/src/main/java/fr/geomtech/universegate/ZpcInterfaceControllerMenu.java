package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class ZpcInterfaceControllerMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 13;

    public static final int FLAG_HAS_ZPC = 1;
    public static final int FLAG_ENGAGED = 1 << 1;
    public static final int FLAG_NETWORK_LINKED = 1 << 2;
    public static final int FLAG_ANIMATING = 1 << 3;

    private final BlockPos controllerPos;
    private final ContainerData data;

    public ZpcInterfaceControllerMenu(int syncId, Inventory inv, BlockPos pos) {
        this(syncId, inv, pos, new SimpleContainerData(DATA_COUNT));
    }

    public ZpcInterfaceControllerMenu(int syncId,
                                      Inventory inv,
                                      ZpcInterfaceControllerBlockEntity controller) {
        this(syncId, inv, controller.getBlockPos(), controller.dataAccess());
    }

    private ZpcInterfaceControllerMenu(int syncId,
                                       Inventory inv,
                                       BlockPos controllerPos,
                                       ContainerData data) {
        super(ModMenuTypes.ZPC_INTERFACE_CONTROLLER, syncId);
        this.controllerPos = controllerPos.immutable();
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);
        this.addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(controllerPos).is(ModBlocks.ZPC_INTERFACE_CONTROLLER)
                && player.distanceToSqr(controllerPos.getX() + 0.5D, controllerPos.getY() + 0.5D, controllerPos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public long storedEnergy() {
        return combineLong(data.get(0), data.get(1), data.get(2), data.get(3));
    }

    public long totalEnergyDrawn() {
        return combineLong(data.get(4), data.get(5), data.get(6), data.get(7));
    }

    public int chargePercent() {
        return Mth.clamp(data.get(8), 0, 100);
    }

    public int outputPerSecond() {
        return combineInt(data.get(9), data.get(10));
    }

    public int flags() {
        return data.get(11);
    }

    public int installedCount() {
        return Mth.clamp(data.get(12), 0, ZpcInterfaceControllerBlockEntity.MAX_ZPCS);
    }

    public long totalCapacity() {
        return (long) installedCount() * ZpcItem.CAPACITY;
    }

    public boolean hasFlag(int flag) {
        return (flags() & flag) != 0;
    }

    public boolean hasZpc() {
        return hasFlag(FLAG_HAS_ZPC);
    }

    public boolean engaged() {
        return hasFlag(FLAG_ENGAGED);
    }

    public boolean networkLinked() {
        return hasFlag(FLAG_NETWORK_LINKED);
    }

    public boolean animating() {
        return hasFlag(FLAG_ANIMATING);
    }

    private static int combineInt(int low, int high) {
        return ((high & 0xFFFF) << 16) | (low & 0xFFFF);
    }

    private static long combineLong(int a, int b, int c, int d) {
        return ((long) (d & 0xFFFF) << 48)
                | ((long) (c & 0xFFFF) << 32)
                | ((long) (b & 0xFFFF) << 16)
                | ((long) (a & 0xFFFF));
    }
}
