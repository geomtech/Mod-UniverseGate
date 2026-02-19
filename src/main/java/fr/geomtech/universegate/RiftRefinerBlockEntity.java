package fr.geomtech.universegate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class RiftRefinerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, WorldlyContainer {

    private final NonNullList<ItemStack> inventory = NonNullList.withSize(1, ItemStack.EMPTY);
    // Slot 0: Crystal Input
    // Output is now Fluid, stored internally

    private static final int CRYSTAL_SLOT = 0;
    private static final int PROCESS_TIME = 40; // Faster refining (2 seconds)
    
    // Fluid Storage
    private int fluidAmount = 0;
    public static final int MAX_FLUID = 10000; // 10 Buckets
    public static final int FLUID_PER_CRYSTAL = 156; // 64 crystals = ~10000 mB

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
                    case 2 -> RiftRefinerBlockEntity.this.fluidAmount; // Sync fluid amount
                    case 3 -> RiftRefinerBlockEntity.this.MAX_FLUID;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> RiftRefinerBlockEntity.this.progress = value;
                    case 1 -> RiftRefinerBlockEntity.this.maxProgress = value;
                    case 2 -> RiftRefinerBlockEntity.this.fluidAmount = value;
                }
            }

            @Override
            public int getCount() {
                return 4;
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

        boolean dirty = false;

        // Crafting Logic
        if (entity.hasRecipe()) {
            entity.progress++;
            if (entity.progress >= entity.maxProgress) {
                entity.craftItem();
                entity.progress = 0;
                dirty = true;
            }
        } else {
            entity.progress = 0;
        }

        // Fluid Distribution Logic (Push to connected pipes/consumers)
        if (entity.fluidAmount > 0 && entity.distributeFluid(level, pos)) {
            dirty = true;
        }

        if (dirty) {
            entity.setChanged();
        }
    }

    private boolean hasRecipe() {
        ItemStack crystalStack = inventory.get(CRYSTAL_SLOT);
        boolean hasInput = crystalStack.getItem() == ModItems.RIFT_CRYSTAL;
        boolean hasSpace = fluidAmount + FLUID_PER_CRYSTAL <= MAX_FLUID;

        return hasInput && hasSpace;
    }

    // Removed canInsertItemIntoOutputSlot and canInsertAmountIntoOutputSlot as they are for items

    private void craftItem() {
        inventory.get(CRYSTAL_SLOT).shrink(1);
        fluidAmount += FLUID_PER_CRYSTAL;
    }

    private boolean distributeFluid(Level level, BlockPos pos) {
        int before = fluidAmount;

        // Push to adjacent pipes or consumers directly
        int transferRate = 100; // mB per tick

        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            if (fluidAmount <= 0) break;

            BlockPos neighborPos = pos.relative(dir);
            BlockEntity neighborBe = level.getBlockEntity(neighborPos);

            if (neighborBe instanceof FluidPipeBlockEntity pipe) {
                // Push to pipe
                int toPush = Math.min(fluidAmount, transferRate);
                if (toPush > 0) {
                    int pushed = pipe.fill(toPush);
                    fluidAmount -= pushed;
                }
            } else if (neighborBe instanceof DarkEnergyGeneratorBlockEntity generator) {
                // Direct connection to generator
                int accepted = generator.fillDarkMatter(Math.min(fluidAmount, transferRate));
                fluidAmount -= accepted;
            }
        }

        return fluidAmount != before;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, inventory, registries);
        tag.putInt("rift_refiner.progress", progress);
        tag.putInt("rift_refiner.fluid", fluidAmount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, inventory, registries);
        progress = tag.getInt("rift_refiner.progress");
        fluidAmount = Math.max(0, Math.min(tag.getInt("rift_refiner.fluid"), MAX_FLUID));
    }

    // WorldlyContainer Implementation
    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction side) {
        return new int[]{CRYSTAL_SLOT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable net.minecraft.core.Direction dir) {
        if (slot == CRYSTAL_SLOT) return stack.getItem() == ModItems.RIFT_CRYSTAL;
        return false;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == CRYSTAL_SLOT && stack.is(ModItems.RIFT_CRYSTAL);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction dir) {
        return false;
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
        if (slot != CRYSTAL_SLOT) {
            return;
        }

        if (!stack.isEmpty() && !stack.is(ModItems.RIFT_CRYSTAL)) {
            return;
        }

        inventory.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }

        progress = 0;
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
