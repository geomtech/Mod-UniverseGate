package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

public class MobClonerControllerMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 10;
    public static final int BUTTON_CLONE = 0;

    public static final int FLAG_HAS_DNA = 1;
    public static final int FLAG_DNA_VALID = 1 << 1;
    public static final int FLAG_HAS_CLONER = 1 << 2;
    public static final int FLAG_STANDARD_LINK = 1 << 3;
    public static final int FLAG_DARK_LINK = 1 << 4;
    public static final int FLAG_SPAWN_SPACE = 1 << 5;
    public static final int FLAG_ENOUGH_ENERGY = 1 << 6;
    public static final int FLAG_READY = 1 << 7;
    public static final int FLAG_CHARGING = 1 << 8;

    private static final int SLOT_DNA = 0;
    private static final int PLAYER_INV_START = 1;
    private static final int PLAYER_INV_END = 28;
    private static final int HOTBAR_START = 28;
    private static final int HOTBAR_END = 37;

    private static final int DNA_SLOT_X = 266;
    private static final int DNA_SLOT_Y = 30;
    private static final int PLAYER_INVENTORY_X = 79;
    private static final int PLAYER_INVENTORY_Y = 106;
    private static final int HOTBAR_Y = 164;

    private final Container inventory;
    private final ContainerData data;
    private final BlockPos controllerPos;

    public MobClonerControllerMenu(int syncId, Inventory inv, BlockPos pos) {
        this(syncId, inv, getContainer(inv, pos), new SimpleContainerData(DATA_COUNT), pos);
    }

    public MobClonerControllerMenu(int syncId,
                                   Inventory inv,
                                   MobClonerControllerBlockEntity controller,
                                   ContainerData data) {
        this(syncId, inv, controller, data, controller.getBlockPos());
    }

    private MobClonerControllerMenu(int syncId,
                                    Inventory inv,
                                    Container inventory,
                                    ContainerData data,
                                    BlockPos controllerPos) {
        super(ModMenuTypes.MOB_CLONER_CONTROLLER, syncId);
        checkContainerSize(inventory, 1);
        checkContainerDataCount(data, DATA_COUNT);

        this.inventory = inventory;
        this.data = data;
        this.controllerPos = controllerPos.immutable();

        this.addSlot(new Slot(inventory, SLOT_DNA, DNA_SLOT_X, DNA_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.DNA) && !MobClonerControllerMenu.this.isCharging();
            }

            @Override
            public boolean mayPickup(Player player) {
                return !MobClonerControllerMenu.this.isCharging();
            }
        });

        this.addDataSlots(data);
        addPlayerInventory(inv);
        addPlayerHotbar(inv);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_CLONE) return false;
        if (!(player.level() instanceof ServerLevel serverLevel)) return false;
        if (!(serverLevel.getBlockEntity(controllerPos) instanceof MobClonerControllerBlockEntity controller)) return false;
        return controller.tryClone(player);
    }

    @Override
    public boolean stillValid(Player player) {
        if (inventory instanceof MobClonerControllerBlockEntity controller) {
            return controller.stillValid(player);
        }

        return player.level().getBlockState(controllerPos).is(ModBlocks.MOB_CLONER_CONTROLLER)
                && player.distanceToSqr(controllerPos.getX() + 0.5D, controllerPos.getY() + 0.5D, controllerPos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == SLOT_DNA && isCharging()) {
            return ItemStack.EMPTY;
        }

        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack originalStack = slot.getItem();
            newStack = originalStack.copy();

            if (index == SLOT_DNA) {
                if (!this.moveItemStackTo(originalStack, PLAYER_INV_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (originalStack.is(ModItems.DNA)) {
                if (!this.moveItemStackTo(originalStack, SLOT_DNA, SLOT_DNA + 1, false)) {
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

    public int cost() {
        return combineInt(data.get(0), data.get(1));
    }

    public int flags() {
        return data.get(2);
    }

    public int clonerDistanceBlocks() {
        return data.get(3);
    }

    public long availableEnergy() {
        return combineLong(data.get(4), data.get(5), data.get(6), data.get(7));
    }

    public int chargeTicks() {
        return Math.max(0, data.get(8));
    }

    public int maxChargeTicks() {
        return Math.max(1, data.get(9));
    }

    public float chargeProgress() {
        return Mth.clamp(chargeTicks() / (float) maxChargeTicks(), 0.0F, 1.0F);
    }

    public int remainingChargeSeconds() {
        int ticksLeft = Math.max(0, maxChargeTicks() - chargeTicks());
        return Mth.ceil(ticksLeft / 20.0F);
    }

    public boolean hasFlag(int flag) {
        return (flags() & flag) != 0;
    }

    public boolean ready() {
        return hasFlag(FLAG_READY);
    }

    public boolean isCharging() {
        return hasFlag(FLAG_CHARGING);
    }

    public int dnaCount() {
        return this.getSlot(SLOT_DNA).getItem().getCount();
    }

    private static int combineInt(int low, int high) {
        return Mth.clamp(((high & 0xFFFF) << 16) | (low & 0xFFFF), 0, Integer.MAX_VALUE);
    }

    private static long combineLong(int a, int b, int c, int d) {
        return ((long) (d & 0xFFFF) << 48)
                | ((long) (c & 0xFFFF) << 32)
                | ((long) (b & 0xFFFF) << 16)
                | ((long) (a & 0xFFFF));
    }

    private static Container getContainer(Inventory inv, BlockPos pos) {
        if (inv.player.level().getBlockEntity(pos) instanceof MobClonerControllerBlockEntity controller) {
            return controller;
        }
        return new SimpleContainer(1);
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(
                        playerInventory,
                        col + row * 9 + 9,
                        PLAYER_INVENTORY_X + col * 18,
                        PLAYER_INVENTORY_Y + row * 18
                ));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, PLAYER_INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }
}
