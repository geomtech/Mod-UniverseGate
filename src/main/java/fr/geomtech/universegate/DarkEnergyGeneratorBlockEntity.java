package fr.geomtech.universegate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DarkEnergyGeneratorBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {

    private int darkMatterAmount = 0;
    private int energyAmount = 0;
    public static final int MAX_DARK_MATTER = 10000; // mB
    public static final int MAX_ENERGY = 50000;

    // Production rate
    public static final int DARK_MATTER_CONSUMPTION = 250; // 250 mB per unit
    public static final int ENERGY_PRODUCTION = 1; // 1 Dark Energy unit per cycle
    public static final int OUTPUT_PER_TICK = 10;
    public static final int NETWORK_BUFFER_RESERVE = 50;

    private boolean isRunning = false;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> darkMatterAmount;
                case 1 -> energyAmount & 0xFFFF;
                case 2 -> (energyAmount >>> 16) & 0xFFFF;
                case 3 -> isRunning ? 1 : 0;
                case 4 -> hasFuel() ? 1 : 0;
                case 5 -> canSupplyNetwork() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DarkEnergyGeneratorMenu.DATA_COUNT;
        }
    };

    public DarkEnergyGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DARK_ENERGY_GENERATOR, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DarkEnergyGeneratorBlockEntity entity) {
        if (level.isClientSide) return;

        boolean wasRunning = entity.isRunning;
        boolean dirty = false;

        // Logic: consume fuel while there is room for energy.
        if (entity.darkMatterAmount >= DARK_MATTER_CONSUMPTION && entity.energyAmount < MAX_ENERGY) {
            entity.darkMatterAmount -= DARK_MATTER_CONSUMPTION;
            entity.energyAmount = Math.min(entity.energyAmount + ENERGY_PRODUCTION, MAX_ENERGY);
            entity.isRunning = true;
            dirty = true;
        } else {
            entity.isRunning = false;
        }

        if (entity.isRunning != wasRunning) {
            dirty = true;
        }

        if (entity.energyAmount > NETWORK_BUFFER_RESERVE && entity.distributeEnergy(level, pos)) {
            dirty = true;
        }
        
        if (dirty) {
            entity.setChanged();
        }
    }

    private boolean distributeEnergy(Level level, BlockPos pos) {
        int transferableEnergy = Math.max(0, energyAmount - NETWORK_BUFFER_RESERVE);
        if (transferableEnergy <= 0) {
            return false;
        }

        int before = energyAmount;
        int[] budget = new int[] { transferableEnergy };

        // Push to adjacent Portal Core or through conduits
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            if (energyAmount <= 0 || budget[0] <= 0) break;
            
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            BlockEntity neighborBe = level.getBlockEntity(neighborPos);
            
            if (neighborBe instanceof PortalCoreBlockEntity core) {
                int accepted = core.addDarkEnergy(Math.min(Math.min(energyAmount, budget[0]), OUTPUT_PER_TICK));
                energyAmount -= accepted;
                budget[0] -= accepted;
            } else if (neighborState.is(ModBlocks.DARK_ENERGY_CONDUIT)) {
                distributeThroughConduits(level, neighborPos, new java.util.HashSet<>(), budget);
            }
        }

        return energyAmount != before;
    }

    private void distributeThroughConduits(Level level,
                                           BlockPos start,
                                           java.util.Set<BlockPos> visited,
                                           int[] budget) {
        java.util.Queue<BlockPos> queue = new java.util.LinkedList<>();
        queue.add(start);
        visited.add(start);
        
        int depth = 0;
        while (!queue.isEmpty() && depth < 64 && energyAmount > 0 && budget[0] > 0) {
            BlockPos current = queue.poll();
            depth++;
            
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                if (budget[0] <= 0 || energyAmount <= 0) {
                    return;
                }

                BlockPos next = current.relative(dir);
                if (visited.contains(next)) continue;
                
                BlockState state = level.getBlockState(next);
                BlockEntity be = level.getBlockEntity(next);
                
                if (be instanceof PortalCoreBlockEntity core) {
                    int accepted = core.addDarkEnergy(Math.min(Math.min(energyAmount, budget[0]), OUTPUT_PER_TICK));
                    energyAmount -= accepted;
                    budget[0] -= accepted;
                    visited.add(next);
                    if (energyAmount <= 0 || budget[0] <= 0) return;
                } else if (state.is(ModBlocks.DARK_ENERGY_CONDUIT)
                        || state.is(ModBlocks.PORTAL_FRAME)
                        || state.is(ModBlocks.PORTAL_KEYBOARD)
                        || state.is(ModBlocks.PORTAL_NATURAL_KEYBOARD)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
    }

    public boolean isGenerating() {
        return isRunning;
    }

    public boolean hasFuel() {
        return darkMatterAmount >= DARK_MATTER_CONSUMPTION;
    }

    public boolean hasStoredEnergy() {
        return energyAmount > 0;
    }

    public boolean canSupplyNetwork() {
        return hasStoredEnergy() || hasFuel();
    }

    public ContainerData dataAccess() {
        return dataAccess;
    }

    
    // Simplistic Fluid/Energy insertion methods for now
    public int fillDarkMatter(int amount) {
        if (amount <= 0) return 0;

        int space = MAX_DARK_MATTER - darkMatterAmount;
        if (space <= 0) return 0;

        int toFill = Math.min(amount, space);
        darkMatterAmount += toFill;
        if (toFill > 0) {
            setChanged();
        }
        return toFill;
    }

    public int receiveEnergy(int amount) {
        if (amount <= 0) return 0;

        int space = MAX_ENERGY - energyAmount;
        if (space <= 0) return 0;

        int toReceive = Math.min(amount, space);
        energyAmount += toReceive;
        if (toReceive > 0) {
            setChanged();
        }
        return toReceive;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.universegate.dark_energy_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId,
                                            net.minecraft.world.entity.player.Inventory inv,
                                            Player player) {
        return new DarkEnergyGeneratorMenu(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("DarkMatter", darkMatterAmount);
        tag.putInt("Energy", energyAmount);
        tag.putBoolean("Running", isRunning);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        darkMatterAmount = Math.max(0, Math.min(tag.getInt("DarkMatter"), MAX_DARK_MATTER));
        energyAmount = Math.max(0, Math.min(tag.getInt("Energy"), MAX_ENERGY));
        isRunning = tag.getBoolean("Running");
    }
}
