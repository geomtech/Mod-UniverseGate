package fr.geomtech.universegate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class CombustionGeneratorBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedScreenHandlerFactory<BlockPos> {

    private static final int SLOT_FUEL = 0;
    private static final int[] ACCESSIBLE_SLOTS = new int[] {SLOT_FUEL};

    private static final int TICK_INTERVAL = 20;
    private static final int OUTPUT_PER_INTERVAL = 480;
    public static final int OUTPUT_PER_SECOND = OUTPUT_PER_INTERVAL * (20 / TICK_INTERVAL);
    public static final int MAX_BUFFERED_ENERGY = 64000;
    private static final int REFUEL_THRESHOLD = 16000;
    private static final double ENERGY_PER_BURN_TICK = 1.4D;
    private static final int RIFT_CRYSTAL_ENERGY = 24000;

    private final NonNullList<ItemStack> inventory = NonNullList.withSize(1, ItemStack.EMPTY);
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> bufferedEnergy & 0xFFFF;
                case 1 -> (bufferedEnergy >>> 16) & 0xFFFF;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> bufferedEnergy = (bufferedEnergy & 0xFFFF0000) | (value & 0xFFFF);
                case 1 -> bufferedEnergy = (bufferedEnergy & 0x0000FFFF) | ((value & 0xFFFF) << 16);
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return CombustionGeneratorMenu.DATA_COUNT;
        }
    };
    private int bufferedEnergy = 0;

    public CombustionGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMBUSTION_GENERATOR, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CombustionGeneratorBlockEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % TICK_INTERVAL != 0L) return;

        boolean dirty = false;

        if (entity.bufferedEnergy <= REFUEL_THRESHOLD) {
            dirty |= entity.consumeFuelIfPossible();
        }

        if (entity.bufferedEnergy > 0) {
            EnergyNetworkHelper.CondenserNetwork network = EnergyNetworkHelper.scanCondenserNetwork(serverLevel, pos);
            int toSend = Math.min(entity.bufferedEnergy, OUTPUT_PER_INTERVAL);
            int sent = EnergyNetworkHelper.distributeEnergy(serverLevel, network.condensers(), toSend);
            if (sent > 0) {
                entity.bufferedEnergy -= sent;
                dirty = true;
            }
        }

        dirty |= entity.syncLitState(state);

        if (dirty) {
            entity.setChanged();
        }
    }

    public ContainerData dataAccess() {
        return dataAccess;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.universegate.combustion_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new CombustionGeneratorMenu(syncId, inv, this, dataAccess());
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.worldPosition;
    }

    public boolean canAcceptFuel(ItemStack stack) {
        if (!isSupportedFuel(stack)) return false;

        ItemStack slotStack = inventory.get(SLOT_FUEL);
        if (slotStack.isEmpty()) return true;
        if (slotStack.getItem() != stack.getItem()) return false;
        return slotStack.getCount() < Math.min(slotStack.getMaxStackSize(), getMaxStackSize());
    }

    public int insertFuel(ItemStack stack) {
        if (!isSupportedFuel(stack)) return 0;

        ItemStack slotStack = inventory.get(SLOT_FUEL);
        int maxStackSize = Math.min(stack.getMaxStackSize(), getMaxStackSize());

        if (slotStack.isEmpty()) {
            int moved = Math.min(stack.getCount(), maxStackSize);
            if (moved <= 0) return 0;

            ItemStack movedStack = stack.copy();
            movedStack.setCount(moved);
            inventory.set(SLOT_FUEL, movedStack);
            setChanged();
            return moved;
        }

        if (slotStack.getItem() != stack.getItem()) return 0;

        int slotLimit = Math.min(slotStack.getMaxStackSize(), getMaxStackSize());
        int space = slotLimit - slotStack.getCount();
        if (space <= 0) return 0;

        int moved = Math.min(space, stack.getCount());
        slotStack.grow(moved);
        setChanged();
        return moved;
    }

    private boolean consumeFuelIfPossible() {
        ItemStack fuelStack = inventory.get(SLOT_FUEL);
        if (fuelStack.isEmpty()) return false;

        int fuelEnergy = getFuelEnergy(fuelStack);
        if (fuelEnergy <= 0) return false;
        if (bufferedEnergy + fuelEnergy > MAX_BUFFERED_ENERGY) return false;

        boolean lavaBucket = fuelStack.is(Items.LAVA_BUCKET);
        fuelStack.shrink(1);
        if (fuelStack.isEmpty()) {
            inventory.set(SLOT_FUEL, ItemStack.EMPTY);
        }
        if (lavaBucket && level != null) {
            Containers.dropItemStack(level,
                    worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D,
                    worldPosition.getZ() + 0.5D,
                    new ItemStack(Items.BUCKET));
        }
        bufferedEnergy += fuelEnergy;
        return true;
    }

    private boolean syncLitState(BlockState state) {
        if (level == null || level.isClientSide) return false;
        if (!state.hasProperty(CombustionGeneratorBlock.LIT)) return false;

        boolean shouldBeLit = bufferedEnergy > 0;
        if (state.getValue(CombustionGeneratorBlock.LIT) == shouldBeLit) return false;

        level.setBlock(worldPosition, state.setValue(CombustionGeneratorBlock.LIT, shouldBeLit), 3);
        return true;
    }

    public static boolean isSupportedFuel(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ModItems.RIFT_CRYSTAL) || AbstractFurnaceBlockEntity.isFuel(stack));
    }

    private static int getFuelEnergy(ItemStack stack) {
        if (stack.is(ModItems.RIFT_CRYSTAL)) {
            return RIFT_CRYSTAL_ENERGY;
        }

        int burnDuration = AbstractFurnaceBlockEntity.getFuel().getOrDefault(stack.getItem(), 0);
        if (burnDuration <= 0) return 0;

        return Math.max(1, (int) Math.round(burnDuration * ENERGY_PER_BURN_TICK));
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ACCESSIBLE_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return slot == SLOT_FUEL && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
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
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(inventory, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != SLOT_FUEL) return;

        if (!stack.isEmpty() && !isSupportedFuel(stack)) {
            return;
        }

        inventory.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_FUEL && isSupportedFuel(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        inventory.clear();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, inventory, registries);
        tag.putInt("BufferedEnergy", bufferedEnergy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, inventory, registries);
        bufferedEnergy = Math.max(0, Math.min(MAX_BUFFERED_ENERGY, tag.getInt("BufferedEnergy")));
    }
}
