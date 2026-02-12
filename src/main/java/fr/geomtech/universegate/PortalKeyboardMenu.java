package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
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

        // Slot fuel (index 0)
        this.addSlot(new Slot(keyboard, 0, 220, 150) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.CATALYST);
            }
        });

        // Inventaire joueur
        int startX = 8;
        int startY = 118;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, startX + col * 18, startY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, startX + col * 18, startY + 58));
        }
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
        ItemStack empty = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return empty;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        // slot 0 = fuel
        if (index == 0) {
            if (!this.moveItemStackTo(stack, 1, this.slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (stack.is(ModItems.CATALYST)) {
                if (!this.moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return copy;
    }
}
