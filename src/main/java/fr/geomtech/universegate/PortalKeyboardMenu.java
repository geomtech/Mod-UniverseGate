package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class PortalKeyboardMenu extends AbstractContainerMenu {

    private final PortalKeyboardBlockEntity keyboard;
    private final BlockPos keyboardPos;

    public BlockPos getKeyboardPos() {
        return keyboardPos;
    }

    // constructeur CLIENT
    public PortalKeyboardMenu(int syncId, Inventory inv, BlockPos pos) {
        this(syncId, inv, getKeyboardAt(inv, pos));
    }

    // constructeur SERVEUR
    public PortalKeyboardMenu(int syncId, Inventory inv, PortalKeyboardBlockEntity keyboard) {
        super(ModMenuTypes.PORTAL_KEYBOARD, syncId);
        this.keyboard = keyboard;
        this.keyboardPos = keyboard.getBlockPos();
    }

    private static PortalKeyboardBlockEntity getKeyboardAt(Inventory inv, BlockPos pos) {
        var be = inv.player.level().getBlockEntity(pos);
        if (be instanceof PortalKeyboardBlockEntity kb) return kb;
        throw new IllegalStateException("PortalKeyboardBlockEntity not found at " + pos);
    }

    @Override
    public boolean stillValid(Player player) {
        return keyboard != null && keyboard.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
