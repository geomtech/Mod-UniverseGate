package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class RiftRefinerMenu extends AbstractContainerMenu {

    private final Container inventory;
    private final ContainerData data;
    protected final Level level;

    // Client Constructor
    public RiftRefinerMenu(int syncId, Inventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, new SimpleContainer(3), new SimpleContainerData(2));
    }

    // Server Constructor
    public RiftRefinerMenu(int syncId, Inventory playerInventory, Container inventory, ContainerData data) {
        super(ModMenuTypes.RIFT_REFINER, syncId);
        checkContainerSize(inventory, 3);
        this.inventory = inventory;
        this.data = data;
        this.level = playerInventory.player.level();

        addSlot(new Slot(inventory, 0, 56, 17)); // Crystal
        addSlot(new Slot(inventory, 1, 56, 53)); // Bucket
        addSlot(new Slot(inventory, 2, 116, 35)); // Output

        addDataSlots(data);

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }
    
    public boolean isCrafting() {
        return data.get(0) > 0;
    }

    public int getScaledProgress() {
        int progress = this.data.get(0);
        int maxProgress = this.data.get(1);  // Max Progress
        int progressArrowSize = 26; // This is the width in pixels of your arrow

        return maxProgress != 0 && progress != 0 ? progress * progressArrowSize / maxProgress : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot != null && slot.hasItem()) {
            ItemStack originalStack = slot.getItem();
            newStack = originalStack.copy();
            if (invSlot < this.inventory.getContainerSize()) {
                if (!this.moveItemStackTo(originalStack, this.inventory.getContainerSize(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(originalStack, 0, this.inventory.getContainerSize(), false)) {
                return ItemStack.EMPTY;
            }

            if (originalStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return newStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.inventory.stillValid(player);
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 84 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }
}
