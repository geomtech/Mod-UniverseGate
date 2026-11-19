package fr.geomtech.universegate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ZpcInterfaceControllerBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements ExtendedScreenHandlerFactory<BlockPos> {

    private static final int INSERTION_TICKS = 40;
    private static final int NETWORK_SCAN_INTERVAL = 20;

    public static final int MAX_ZPCS = 3;

    private final boolean[] slotOccupied = new boolean[MAX_ZPCS];
    private final long[] slotEnergy = new long[MAX_ZPCS];
    private final boolean[] slotAnimating = new boolean[MAX_ZPCS];
    private final int[] slotProgress = new int[MAX_ZPCS];

    private boolean networkLinked = false;
    private int networkScanCooldown = 0;

    private long totalEnergyDrawn = 0L;
    private long outputAccumulator = 0L;
    private int outputPerSecond = 0;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            long stored = getStoredEnergy();
            return switch (index) {
                case 0 -> segment(stored, 0);
                case 1 -> segment(stored, 1);
                case 2 -> segment(stored, 2);
                case 3 -> segment(stored, 3);
                case 4 -> segment(totalEnergyDrawn, 0);
                case 5 -> segment(totalEnergyDrawn, 1);
                case 6 -> segment(totalEnergyDrawn, 2);
                case 7 -> segment(totalEnergyDrawn, 3);
                case 8 -> getChargePercent();
                case 9 -> outputPerSecond & 0xFFFF;
                case 10 -> (outputPerSecond >>> 16) & 0xFFFF;
                case 11 -> buildFlags();
                case 12 -> getInstalledCount();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return ZpcInterfaceControllerMenu.DATA_COUNT;
        }
    };

    public ZpcInterfaceControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ZPC_INTERFACE_CONTROLLER, pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        boolean changed = false;
        boolean finishedAnimation = false;

        for (int slot = 0; slot < MAX_ZPCS; slot++) {
            if (!slotOccupied[slot] || !slotAnimating[slot]) continue;

            slotProgress[slot] = Math.min(INSERTION_TICKS, slotProgress[slot] + 1);
            changed = true;

            if (slotProgress[slot] >= INSERTION_TICKS) {
                slotAnimating[slot] = false;
                finishedAnimation = true;
            }
        }

        if (finishedAnimation) {
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.75F, 1.1F);
        }

        if (serverLevel.getGameTime() % 20L == 0L) {
            int sampledOutput = (int) Math.min(Integer.MAX_VALUE, outputAccumulator);
            if (sampledOutput != outputPerSecond) {
                outputPerSecond = sampledOutput;
                changed = true;
            }
            outputAccumulator = 0L;
        }

        if (networkScanCooldown > 0) {
            networkScanCooldown--;
        } else {
            boolean linked = isAdjacentToConduit(serverLevel);
            if (linked != networkLinked) {
                networkLinked = linked;
                changed = true;
            }
            networkScanCooldown = NETWORK_SCAN_INTERVAL;
        }

        if (syncBlockStateVisuals()) {
            changed = true;
        }

        if (changed) {
            setChanged();
            syncClient();
        }
    }

    public ContainerData dataAccess() {
        return dataAccess;
    }

    public boolean tryInsertZpc(Player player, ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ModItems.ZPC)) return false;

        int slot = firstEmptySlot();
        if (slot < 0) return false;

        ItemStack single = stack.copy();
        single.setCount(1);

        slotOccupied[slot] = true;
        slotEnergy[slot] = ZpcItem.getStoredEnergy(single);
        slotAnimating[slot] = false;
        slotProgress[slot] = 0;
        outputPerSecond = 0;
        outputAccumulator = 0L;

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.9F, 1.15F);
        }

        syncBlockStateVisuals();
        setChanged();
        syncClient();
        return true;
    }

    public boolean startEngageAnimation() {
        boolean started = false;

        for (int slot = 0; slot < MAX_ZPCS; slot++) {
            if (!slotOccupied[slot]) continue;
            if (slotAnimating[slot]) continue;
            if (slotProgress[slot] >= INSERTION_TICKS) continue;

            slotAnimating[slot] = true;
            started = true;
        }

        if (!started) return false;

        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6F, 0.95F);
        }

        setChanged();
        syncClient();
        return true;
    }

    public boolean ejectZpc(Player player) {
        int slot = findEjectSlot();
        if (slot < 0) return false;

        ItemStack stack = createSlotStack(slot);
        clearSlot(slot);

        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.8F, 0.85F);
        }

        if (player != null) {
            if (!player.addItem(stack) && level != null) {
                Containers.dropItemStack(level,
                        worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 1.0D,
                        worldPosition.getZ() + 0.5D,
                        stack);
            }
        }

        syncBlockStateVisuals();
        setChanged();
        syncClient();
        return true;
    }

    public void dropInstalledZpc() {
        if (level == null) return;

        double[][] offsets = {
                {-0.22D, 0.18D},
                {0.00D, -0.20D},
                {0.22D, 0.18D}
        };

        for (int slot = 0; slot < MAX_ZPCS; slot++) {
            if (!slotOccupied[slot]) continue;

            ItemStack stack = createSlotStack(slot);
            Containers.dropItemStack(level,
                    worldPosition.getX() + 0.5D + offsets[slot][0],
                    worldPosition.getY() + 0.95D,
                    worldPosition.getZ() + 0.5D + offsets[slot][1],
                    stack);
            clearSlot(slot);
        }

        setChanged();
    }

    public boolean hasZpcInstalled() {
        return getInstalledCount() > 0;
    }

    public int getInstalledCount() {
        int count = 0;
        for (boolean occupied : slotOccupied) {
            if (occupied) count++;
        }
        return count;
    }

    public boolean hasUnengagedZpc() {
        for (int slot = 0; slot < MAX_ZPCS; slot++) {
            if (!slotOccupied[slot]) continue;
            if (slotProgress[slot] < INSERTION_TICKS) return true;
        }
        return false;
    }

    public boolean hasEngagedZpc() {
        for (int slot = 0; slot < MAX_ZPCS; slot++) {
            if (!slotOccupied[slot]) continue;
            if (!slotAnimating[slot] && slotProgress[slot] >= INSERTION_TICKS) return true;
        }
        return false;
    }

    public boolean isAnimatingInsertion() {
        for (boolean animating : slotAnimating) {
            if (animating) return true;
        }
        return false;
    }

    public boolean isEngaged() {
        return hasEngagedZpc();
    }

    public boolean isNetworkLinked() {
        return networkLinked;
    }

    public long getStoredEnergy() {
        long total = 0L;
        for (int slot = 0; slot < MAX_ZPCS; slot++) {
            if (!slotOccupied[slot]) continue;
            total += slotEnergy[slot];
        }
        return total;
    }

    public long getTotalCapacity() {
        return (long) getInstalledCount() * ZpcItem.CAPACITY;
    }

    public int getChargePercent() {
        long capacity = getTotalCapacity();
        if (capacity <= 0L) return 0;
        return (int) Math.max(0L, Math.min(100L, Math.round((getStoredEnergy() * 100.0D) / (double) capacity)));
    }

    public long getTotalEnergyDrawn() {
        return totalEnergyDrawn;
    }

    public int getOutputPerSecond() {
        return outputPerSecond;
    }

    public int getInsertStage() {
        if (!hasZpcInstalled()) return 0;

        int maxStage = 0;
        for (int slot = 0; slot < MAX_ZPCS; slot++) {
            if (!slotOccupied[slot]) continue;
            maxStage = Math.max(maxStage, Math.max(0, Math.min(4, slotProgress[slot] / 10)));
        }
        return maxStage;
    }

    public int getRenderSlotCount() {
        int installed = getInstalledCount();
        if (installed > 0) return installed;

        BlockState state = getBlockState();
        if (state.hasProperty(ZpcInterfaceControllerBlock.HAS_ZPC)
                && state.getValue(ZpcInterfaceControllerBlock.HAS_ZPC)) {
            return 1;
        }
        return 0;
    }

    public ItemStack getRenderZpcStack(int slot) {
        if (slot >= 0 && slot < MAX_ZPCS && slotOccupied[slot]) {
            return createSlotStack(slot);
        }

        if (slot == 0) {
            BlockState state = getBlockState();
            if (state.hasProperty(ZpcInterfaceControllerBlock.HAS_ZPC)
                    && state.getValue(ZpcInterfaceControllerBlock.HAS_ZPC)) {
                ItemStack fallback = new ItemStack(ModItems.ZPC);
                long total = getStoredEnergy();
                if (total > 0L) {
                    ZpcItem.setStoredEnergy(fallback, Math.min(ZpcItem.CAPACITY, total));
                }
                return fallback;
            }
        }

        return ItemStack.EMPTY;
    }

    public float getInsertionProgressForRender(int slot, float partialTick) {
        if (slot >= 0 && slot < MAX_ZPCS && slotOccupied[slot]) {
            float progress = slotAnimating[slot]
                    ? (slotProgress[slot] + partialTick) / (float) INSERTION_TICKS
                    : slotProgress[slot] / (float) INSERTION_TICKS;
            return Mth.clamp(progress, 0.0F, 1.0F);
        }

        if (slot == 0) {
            BlockState state = getBlockState();
            if (state.hasProperty(ZpcInterfaceControllerBlock.HAS_ZPC)
                    && state.hasProperty(ZpcInterfaceControllerBlock.INSERT_STAGE)
                    && state.getValue(ZpcInterfaceControllerBlock.HAS_ZPC)) {
                int stage = state.getValue(ZpcInterfaceControllerBlock.INSERT_STAGE);
                return Mth.clamp(stage / 4.0F, 0.0F, 1.0F);
            }
        }

        return 0.0F;
    }

    public boolean canSupplyEnergy() {
        return hasEngagedZpc() && getStoredEnergy() > 0L;
    }

    public int extractEnergy(int amount) {
        if (amount <= 0 || !canSupplyEnergy()) return 0;

        int remaining = amount;
        while (remaining > 0) {
            int slot = findHighestEnergyEngagedSlot();
            if (slot < 0) break;

            int extracted = (int) Math.min((long) remaining, slotEnergy[slot]);
            if (extracted <= 0) break;

            slotEnergy[slot] -= extracted;
            remaining -= extracted;
        }

        int extractedTotal = amount - remaining;
        if (extractedTotal > 0) {
            totalEnergyDrawn += extractedTotal;
            outputAccumulator += extractedTotal;

            syncBlockStateVisuals();
            setChanged();
            syncClient();
        }
        return extractedTotal;
    }

    private int firstEmptySlot() {
        for (int slot = 0; slot < MAX_ZPCS; slot++) {
            if (!slotOccupied[slot]) return slot;
        }
        return -1;
    }

    private int findEjectSlot() {
        for (int slot = MAX_ZPCS - 1; slot >= 0; slot--) {
            if (slotOccupied[slot] && !slotAnimating[slot]) return slot;
        }
        return -1;
    }

    private int findHighestEnergyEngagedSlot() {
        int bestSlot = -1;
        long bestEnergy = 0L;

        for (int slot = 0; slot < MAX_ZPCS; slot++) {
            if (!slotOccupied[slot]) continue;
            if (slotAnimating[slot]) continue;
            if (slotProgress[slot] < INSERTION_TICKS) continue;
            if (slotEnergy[slot] <= bestEnergy) continue;

            bestEnergy = slotEnergy[slot];
            bestSlot = slot;
        }
        return bestSlot;
    }

    private ItemStack createSlotStack(int slot) {
        ItemStack stack = new ItemStack(ModItems.ZPC);
        ZpcItem.setStoredEnergy(stack, slotEnergy[slot]);
        return stack;
    }

    private void clearSlot(int slot) {
        slotOccupied[slot] = false;
        slotEnergy[slot] = 0L;
        slotAnimating[slot] = false;
        slotProgress[slot] = 0;
    }

    private int buildFlags() {
        int flags = 0;
        if (hasZpcInstalled()) flags |= ZpcInterfaceControllerMenu.FLAG_HAS_ZPC;
        if (isEngaged()) flags |= ZpcInterfaceControllerMenu.FLAG_ENGAGED;
        if (isNetworkLinked()) flags |= ZpcInterfaceControllerMenu.FLAG_NETWORK_LINKED;
        if (isAnimatingInsertion()) flags |= ZpcInterfaceControllerMenu.FLAG_ANIMATING;
        return flags;
    }

    private boolean syncBlockStateVisuals() {
        if (level == null || level.isClientSide) return false;

        BlockState state = getBlockState();
        if (!state.hasProperty(ZpcInterfaceControllerBlock.HAS_ZPC)
                || !state.hasProperty(ZpcInterfaceControllerBlock.ACTIVE)
                || !state.hasProperty(ZpcInterfaceControllerBlock.INSERT_STAGE)) {
            return false;
        }

        boolean hasAny = hasZpcInstalled();
        int stage = getInsertStage();
        boolean shouldGlow = hasAny && hasEngagedZpc() && getStoredEnergy() > 0L;

        if (state.getValue(ZpcInterfaceControllerBlock.HAS_ZPC) == hasAny
                && state.getValue(ZpcInterfaceControllerBlock.INSERT_STAGE) == stage
                && state.getValue(ZpcInterfaceControllerBlock.ACTIVE) == shouldGlow) {
            return false;
        }

        BlockState updated = state
                .setValue(ZpcInterfaceControllerBlock.HAS_ZPC, hasAny)
                .setValue(ZpcInterfaceControllerBlock.INSERT_STAGE, stage)
                .setValue(ZpcInterfaceControllerBlock.ACTIVE, shouldGlow);
        level.setBlock(worldPosition, updated, 3);
        return true;
    }

    private boolean isAdjacentToConduit(ServerLevel level) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(worldPosition.relative(direction)).is(ModBlocks.ENERGY_CONDUIT)) {
                return true;
            }
        }
        return false;
    }

    private void syncClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLongArray("SlotEnergy", slotEnergy);
        tag.putIntArray("SlotProgress", slotProgress);
        tag.putByteArray("SlotOccupied", boolArrayToByteArray(slotOccupied));
        tag.putByteArray("SlotAnimating", boolArrayToByteArray(slotAnimating));
        tag.putLong("TotalEnergyDrawn", totalEnergyDrawn);
        tag.putInt("OutputPerSecond", outputPerSecond);
        tag.putLong("OutputAccumulator", outputAccumulator);
        tag.putBoolean("NetworkLinked", networkLinked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        resetSlots();

        if (tag.contains("SlotEnergy") || tag.contains("SlotOccupied")) {
            long[] energyArray = tag.getLongArray("SlotEnergy");
            int[] progressArray = tag.getIntArray("SlotProgress");
            byte[] occupiedArray = tag.getByteArray("SlotOccupied");
            byte[] animatingArray = tag.getByteArray("SlotAnimating");

            for (int slot = 0; slot < MAX_ZPCS; slot++) {
                boolean occupied = slot < occupiedArray.length && occupiedArray[slot] != 0;
                slotOccupied[slot] = occupied;

                if (!occupied) {
                    slotEnergy[slot] = 0L;
                    slotProgress[slot] = 0;
                    slotAnimating[slot] = false;
                    continue;
                }

                long energy = slot < energyArray.length ? energyArray[slot] : 0L;
                slotEnergy[slot] = Math.max(0L, Math.min(ZpcItem.CAPACITY, energy));

                int progress = slot < progressArray.length ? progressArray[slot] : 0;
                slotProgress[slot] = Math.max(0, Math.min(INSERTION_TICKS, progress));

                boolean animating = slot < animatingArray.length && animatingArray[slot] != 0;
                slotAnimating[slot] = animating && slotProgress[slot] < INSERTION_TICKS;
            }
        } else {
            boolean legacyHasZpc = tag.getBoolean("HasZpc");
            if (legacyHasZpc) {
                slotOccupied[0] = true;
                slotEnergy[0] = Math.max(0L, Math.min(ZpcItem.CAPACITY, tag.getLong("StoredEnergy")));
                slotProgress[0] = Math.max(0, Math.min(INSERTION_TICKS, tag.getInt("InsertionProgress")));
                slotAnimating[0] = tag.getBoolean("InsertionAnimating") && slotProgress[0] < INSERTION_TICKS;
            }
        }

        totalEnergyDrawn = Math.max(0L, tag.getLong("TotalEnergyDrawn"));
        outputPerSecond = Math.max(0, tag.getInt("OutputPerSecond"));
        outputAccumulator = Math.max(0L, tag.getLong("OutputAccumulator"));
        networkLinked = tag.getBoolean("NetworkLinked");
        networkScanCooldown = 0;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.universegate.zpc_interface_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv, Player player) {
        return new ZpcInterfaceControllerMenu(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    private void resetSlots() {
        for (int slot = 0; slot < MAX_ZPCS; slot++) {
            slotOccupied[slot] = false;
            slotEnergy[slot] = 0L;
            slotAnimating[slot] = false;
            slotProgress[slot] = 0;
        }
    }

    private static byte[] boolArrayToByteArray(boolean[] values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) (values[i] ? 1 : 0);
        }
        return out;
    }

    private static int segment(long value, int segmentIndex) {
        return (int) ((value >>> (segmentIndex * 16)) & 0xFFFFL);
    }
}
