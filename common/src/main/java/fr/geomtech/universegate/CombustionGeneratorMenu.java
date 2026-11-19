package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CombustionGeneratorMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 2;

    private static final int SLOT_FUEL = 0;
    private static final int PLAYER_INV_START = 1;
    private static final int PLAYER_INV_END = 28;
    private static final int HOTBAR_START = 28;
    private static final int HOTBAR_END = 37;

    private final Container inventory;
    private final ContainerData data;
    private final BlockPos generatorPos;

    public CombustionGeneratorMenu(int syncId, Inventory inv, BlockPos pos) {
        this(syncId, inv, getContainer(inv, pos), new SimpleContainerData(DATA_COUNT), pos);
    }

    public CombustionGeneratorMenu(int syncId,
                                   Inventory inv,
                                   CombustionGeneratorBlockEntity generator,
                                   ContainerData data) {
        this(syncId, inv, generator, data, generator.getBlockPos());
    }

    private CombustionGeneratorMenu(int syncId,
                                    Inventory inv,
                                    Container inventory,
                                    ContainerData data,
                                    BlockPos generatorPos) {
        super(ModMenuTypes.COMBUSTION_GENERATOR, syncId);
        checkContainerSize(inventory, 1);
        checkContainerDataCount(data, DATA_COUNT);

        this.inventory = inventory;
        this.data = data;
        this.generatorPos = generatorPos.immutable();

        addSlot(new Slot(inventory, SLOT_FUEL, 8, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CombustionGeneratorBlockEntity.isSupportedFuel(stack);
            }
        });

        addDataSlots(data);
        addPlayerInventory(inv);
        addPlayerHotbar(inv);
    }

    @Override
    public boolean stillValid(Player player) {
        if (inventory instanceof CombustionGeneratorBlockEntity generator) {
            return generator.stillValid(player);
        }

        return player.level().getBlockState(generatorPos).is(ModBlocks.COMBUSTION_GENERATOR)
                && player.distanceToSqr(generatorPos.getX() + 0.5D, generatorPos.getY() + 0.5D, generatorPos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack originalStack = slot.getItem();
            newStack = originalStack.copy();

            if (index == SLOT_FUEL) {
                if (!this.moveItemStackTo(originalStack, PLAYER_INV_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (CombustionGeneratorBlockEntity.isSupportedFuel(originalStack)) {
                if (!this.moveItemStackTo(originalStack, SLOT_FUEL, SLOT_FUEL + 1, false)) {
                    if (index >= PLAYER_INV_START && index < PLAYER_INV_END) {
                        if (!this.moveItemStackTo(originalStack, HOTBAR_START, HOTBAR_END, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (index >= HOTBAR_START && index < HOTBAR_END) {
                        if (!this.moveItemStackTo(originalStack, PLAYER_INV_START, PLAYER_INV_END, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (index >= PLAYER_INV_START && index < PLAYER_INV_END) {
                if (!this.moveItemStackTo(originalStack, HOTBAR_START, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= HOTBAR_START && index < HOTBAR_END) {
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

    public int bufferedEnergy() {
        return Mth.clamp(combine(data.get(0), data.get(1)), 0, CombustionGeneratorBlockEntity.MAX_BUFFERED_ENERGY);
    }

    public int capacity() {
        return CombustionGeneratorBlockEntity.MAX_BUFFERED_ENERGY;
    }

    public int chargePercent() {
        return Mth.clamp((int) Math.round((bufferedEnergy() * 100.0D) / (double) capacity()), 0, 100);
    }

    public int outputPerSecond() {
        return CombustionGeneratorBlockEntity.OUTPUT_PER_SECOND;
    }

    public int fuelCount() {
        return this.getSlot(SLOT_FUEL).getItem().getCount();
    }

    private static int combine(int low, int high) {
        return ((high & 0xFFFF) << 16) | (low & 0xFFFF);
    }

    private static Container getContainer(Inventory inv, BlockPos pos) {
        if (inv.player.level().getBlockEntity(pos) instanceof CombustionGeneratorBlockEntity generator) {
            return generator;
        }
        return new SimpleContainer(1);
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }
}
