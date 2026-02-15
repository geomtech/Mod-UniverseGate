package fr.geomtech.universegate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class RiftRefinerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, WorldlyContainer {

    private final NonNullList<ItemStack> inventory = NonNullList.withSize(3, ItemStack.EMPTY);
    // Slot 0: Crystal Input
    // Slot 1: Bucket Input
    // Slot 2: Output

    private static final int CRYSTAL_SLOT = 0;
    private static final int BUCKET_SLOT = 1;
    private static final int OUTPUT_SLOT = 2;
    private static final int PROCESS_TIME = 100;

    private int progress = 0;
    private int maxProgress = PROCESS_TIME;

    protected final ContainerData data;

    public RiftRefinerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RIFT_REFINER, pos, state);
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> RiftRefinerBlockEntity.this.progress;
                    case 1 -> RiftRefinerBlockEntity.this.maxProgress;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> RiftRefinerBlockEntity.this.progress = value;
                    case 1 -> RiftRefinerBlockEntity.this.maxProgress = value;
                }
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.universegate.rift_refiner");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new RiftRefinerMenu(syncId, playerInventory, this, this.data);
    }
    
    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.worldPosition;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RiftRefinerBlockEntity entity) {
        if (level.isClientSide) return;

        if (entity.hasRecipe()) {
            entity.progress++;
            if (entity.progress >= entity.maxProgress) {
                entity.craftItem();
                entity.progress = 0;
            }
            entity.setChanged();
        } else {
            entity.progress = 0;
            entity.setChanged();
        }
    }

    private boolean hasRecipe() {
        ItemStack crystalStack = inventory.get(CRYSTAL_SLOT);
        ItemStack bucketStack = inventory.get(BUCKET_SLOT);
        ItemStack resultStack = new ItemStack(ModItems.DARK_MATTER_BUCKET);

        boolean hasInput = crystalStack.getItem() == ModItems.RIFT_CRYSTAL && bucketStack.getItem() == Items.BUCKET;

        return hasInput && canInsertAmountIntoOutputSlot(resultStack) && canInsertItemIntoOutputSlot(resultStack);
    }

    private boolean canInsertItemIntoOutputSlot(ItemStack result) {
        return inventory.get(OUTPUT_SLOT).getItem() == result.getItem() || inventory.get(OUTPUT_SLOT).isEmpty();
    }

    private boolean canInsertAmountIntoOutputSlot(ItemStack result) {
        return inventory.get(OUTPUT_SLOT).getCount() + result.getCount() <= inventory.get(OUTPUT_SLOT).getMaxStackSize();
    }

    private void craftItem() {
        ItemStack result = new ItemStack(ModItems.DARK_MATTER_BUCKET);
        inventory.get(CRYSTAL_SLOT).shrink(1);
        inventory.get(BUCKET_SLOT).shrink(1);

        if (inventory.get(OUTPUT_SLOT).isEmpty()) {
            inventory.set(OUTPUT_SLOT, result);
        } else {
            inventory.get(OUTPUT_SLOT).grow(result.getCount());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, inventory, registries);
        tag.putInt("rift_refiner.progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, inventory, registries);
        progress = tag.getInt("rift_refiner.progress");
    }

    // WorldlyContainer Implementation
    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction side) {
        if (side == net.minecraft.core.Direction.DOWN) return new int[]{OUTPUT_SLOT};
        if (side == net.minecraft.core.Direction.UP) return new int[]{CRYSTAL_SLOT};
        return new int[]{BUCKET_SLOT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable net.minecraft.core.Direction dir) {
        if (slot == CRYSTAL_SLOT) return stack.getItem() == ModItems.RIFT_CRYSTAL;
        if (slot == BUCKET_SLOT) return stack.getItem() == Items.BUCKET;
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction dir) {
        return slot == OUTPUT_SLOT;
    }

    @Override
    public int getContainerSize() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return inventory.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(inventory, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(inventory, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        inventory.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }
    
    @Override
    public void clearContent() {
        inventory.clear();
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
