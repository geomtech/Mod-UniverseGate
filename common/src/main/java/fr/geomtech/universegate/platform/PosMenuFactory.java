package fr.geomtech.universegate.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

@FunctionalInterface
public interface PosMenuFactory<M extends AbstractContainerMenu> {
    M create(int syncId, Inventory inventory, BlockPos pos);
}
