package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class PortalCoreMenu extends AbstractContainerMenu {

    private final PortalCoreBlockEntity core;
    private final BlockPos corePos;

    public BlockPos getCorePos() {
        return corePos;
    }

    // client constructor
    public PortalCoreMenu(int syncId, Inventory inv, BlockPos pos) {
        this(syncId, inv, getCoreAt(inv, pos));
    }

    // server constructor
    public PortalCoreMenu(int syncId, Inventory inv, PortalCoreBlockEntity core) {
        super(ModMenuTypes.PORTAL_CORE, syncId);
        this.core = core;
        this.corePos = core.getBlockPos();
    }

    private static PortalCoreBlockEntity getCoreAt(Inventory inv, BlockPos pos) {
        var be = inv.player.level().getBlockEntity(pos);
        if (be instanceof PortalCoreBlockEntity core) return core;
        throw new IllegalStateException("PortalCoreBlockEntity not found at " + pos);
    }

    @Override
    public boolean stillValid(Player player) {
        return core != null && player.distanceToSqr(
                corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
