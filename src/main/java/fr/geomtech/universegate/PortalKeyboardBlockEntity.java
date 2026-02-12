package fr.geomtech.universegate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

public class PortalKeyboardBlockEntity extends BlockEntity implements Container, ExtendedScreenHandlerFactory<BlockPos> {

    private static final int SLOT_FUEL = 0;
    private static final int SIZE = 1;

    private final net.minecraft.core.NonNullList<ItemStack> items =
            net.minecraft.core.NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public PortalKeyboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PORTAL_KEYBOARD, pos, state);
    }

    // --- fuel API ---
    public boolean consumeOneFuel() {
        ItemStack s = items.get(SLOT_FUEL);
        if (s.isEmpty() || !s.is(ModItems.RIFT_ASH)) return false;
        s.shrink(1);
        setChanged();
        return true;
    }

    public int fuelCount() {
        ItemStack s = items.get(SLOT_FUEL);
        return s.is(ModItems.RIFT_ASH) ? s.getCount() : 0;
    }

    // --- ExtendedScreenHandlerFactory ---
    @Override
    public Component getDisplayName() {
        return Component.literal("Portal Keyboard");
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
        // n'accepte que la rift ash
        if (!stack.isEmpty() && !stack.is(ModItems.RIFT_ASH)) return;

        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
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
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.worldPosition;
    }

}
