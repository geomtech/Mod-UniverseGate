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

    private static final int CRYSTAL_SLOT = 0;
    private static final int PLAYER_INV_START = 1;
    private static final int PLAYER_INV_END = 28;
    private static final int HOTBAR_START = 28;
    private static final int HOTBAR_END = 37;

    private final Container inventory;
    private final ContainerData data;
    protected final Level level;

    // Client Constructor
    public RiftRefinerMenu(int syncId, Inventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, new SimpleContainer(1), new SimpleContainerData(4));
    }

    // Server Constructor
    public RiftRefinerMenu(int syncId, Inventory playerInventory, Container inventory, ContainerData data) {
        super(ModMenuTypes.RIFT_REFINER, syncId);
        checkContainerSize(inventory, 1); // Reduced to 1
        this.inventory = inventory;
        this.data = data;
        this.level = playerInventory.player.level();

        // Only one slot now: Crystal Input
        addSlot(new Slot(inventory, CRYSTAL_SLOT, 56, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.RIFT_CRYSTAL);
            }
        });

        addDataSlots(data);

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }
    
    public boolean isCrafting() {
        return data.get(0) > 0;
    }

    public int getScaledProgress() {
        int progress = this.data.get(0);
        int maxProgress = this.data.get(1);
        int progressArrowSize = 26;

        return maxProgress != 0 && progress != 0 ? progress * progressArrowSize / maxProgress : 0;
    }

    public int getFluidAmount() {
        return this.data.get(2);
    }

    public int getMaxFluid() {
        return this.data.get(3);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);

        if (slot != null && slot.hasItem()) {
            ItemStack originalStack = slot.getItem();
            newStack = originalStack.copy();

            if (invSlot == CRYSTAL_SLOT) {
                if (!this.moveItemStackTo(originalStack, PLAYER_INV_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (originalStack.is(ModItems.RIFT_CRYSTAL)) {
                if (!this.moveItemStackTo(originalStack, CRYSTAL_SLOT, CRYSTAL_SLOT + 1, false)) {
                    if (invSlot >= PLAYER_INV_START && invSlot < PLAYER_INV_END) {
                        if (!this.moveItemStackTo(originalStack, HOTBAR_START, HOTBAR_END, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (invSlot >= HOTBAR_START && invSlot < HOTBAR_END) {
                        if (!this.moveItemStackTo(originalStack, PLAYER_INV_START, PLAYER_INV_END, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (invSlot >= PLAYER_INV_START && invSlot < PLAYER_INV_END) {
                if (!this.moveItemStackTo(originalStack, HOTBAR_START, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (invSlot >= HOTBAR_START && invSlot < HOTBAR_END) {
                if (!this.moveItemStackTo(originalStack, PLAYER_INV_START, PLAYER_INV_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }

            if (originalStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            slot.onTake(player, originalStack);
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
