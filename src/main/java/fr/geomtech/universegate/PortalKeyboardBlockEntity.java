package fr.geomtech.universegate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

public class PortalKeyboardBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedScreenHandlerFactory<BlockPos> {

    private static final int SLOT_FUEL = 0;
    private static final int SIZE = 1;
    private static final int[] ACCESSIBLE_SLOTS = new int[] {SLOT_FUEL};

    private final net.minecraft.core.NonNullList<ItemStack> items =
            net.minecraft.core.NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean suppressPortalCloseOnFuelConsumption = false;
    private int lastKnownFuelCount = 0;

    public PortalKeyboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PORTAL_KEYBOARD, pos, state);
    }

    // --- fuel API ---
    public boolean consumeOneFuel() {
        ItemStack s = items.get(SLOT_FUEL);
        if (s.isEmpty() || !s.is(ModItems.RIFT_ASH)) return false;
        suppressPortalCloseOnFuelConsumption = true;
        try {
            s.shrink(1);
            setChanged();
        } finally {
            suppressPortalCloseOnFuelConsumption = false;
        }
        return true;
    }

    public int fuelCount() {
        ItemStack s = items.get(SLOT_FUEL);
        return s.is(ModItems.RIFT_ASH) ? s.getCount() : 0;
    }

    private void updateRedstoneSignal(boolean hadFuel, boolean hasFuel) {
        if (hadFuel == hasFuel) return;
        if (level == null || level.isClientSide) return;
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Override
    public void setChanged() {
        int previousFuelCount = lastKnownFuelCount;
        int currentFuelCount = fuelCount();
        boolean hadFuel = previousFuelCount > 0;
        boolean hasFuel = currentFuelCount > 0;

        super.setChanged();
        lastKnownFuelCount = currentFuelCount;
        updateRedstoneSignal(hadFuel, hasFuel);

        if (suppressPortalCloseOnFuelConsumption) return;
        if (currentFuelCount >= previousFuelCount) return;
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return;

        PortalConnectionManager.forceCloseFromKeyboard(sl, worldPosition);
    }

    // --- ExtendedScreenHandlerFactory ---
    @Override
    public Component getDisplayName() {
        return Component.translatable("container.universegate.portal_keyboard");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv, Player player) {
        return new PortalKeyboardMenu(syncId, inv, this);
    }

    // --- Container ---
    @Override public int getContainerSize() { return SIZE; }
    @Override public boolean isEmpty() { return items.get(0).isEmpty(); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_FUEL && stack.is(ModItems.RIFT_ASH);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ACCESSIBLE_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return false;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!stack.isEmpty() && !canPlaceItem(slot, stack)) return;

        if (!stack.isEmpty() && stack.getCount() == 1) {
            ItemStack existing = items.get(slot);
            if (!existing.isEmpty()
                    && ItemStack.isSameItemSameComponents(existing, stack)) {
                if (existing.getCount() < getMaxStackSize(existing)) {
                    existing.grow(1);
                    setChanged();
                }
                return;
            }
        }

        ItemStack stackToStore = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        stackToStore.limitSize(getMaxStackSize(stackToStore));

        items.set(slot, stackToStore);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Objects.equals(player.level(), this.level)
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.set(0, ItemStack.EMPTY);
        setChanged();
    }

    // --- NBT (1.21+) ---
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        lastKnownFuelCount = fuelCount();
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.worldPosition;
    }

}
