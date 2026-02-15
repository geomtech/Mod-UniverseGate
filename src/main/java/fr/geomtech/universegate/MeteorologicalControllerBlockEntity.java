package fr.geomtech.universegate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class MeteorologicalControllerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {

    public enum ForceChargeResult {
        CHARGED,
        ALREADY_CHARGED,
        SEQUENCE_RUNNING
    }

    public static final int CHARGE_FULL_TICKS = 20 * 60 * 5;
    public static final int SPINUP_TICKS = 20 * 5;
    public static final int PREPARATION_TICKS = 20 * 4;
    public static final int BEAM_TICKS = 20 * 32;
    public static final int SPINDOWN_TICKS = 20 * 8;
    private static final int BEAM_LOOP_INTERVAL_TICKS = 20 * 2;
    public static final int BEAM_START_TICKS = SPINUP_TICKS + PREPARATION_TICKS;
    public static final int BEAM_END_TICKS = BEAM_START_TICKS + BEAM_TICKS;
    public static final int TOTAL_SEQUENCE_TICKS = BEAM_END_TICKS + SPINDOWN_TICKS;

    private static final int RESCAN_INTERVAL_TICKS = 20;

    private int chargeTicks = 0;
    private int sequenceTicks = 0;
    private boolean sequenceActive = false;
    private boolean finaleTriggered = false;
    private int rescanCooldown = 0;
    @Nullable
    private WeatherSelection pendingWeather = null;
    @Nullable
    private BlockPos activeCatalystPos = null;

    private WeatherMachineHelper.MachineStatus machineStatus = WeatherMachineHelper.MachineStatus.empty();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> chargeTicks;
                case 1 -> CHARGE_FULL_TICKS;
                case 2 -> buildStatusFlags();
                case 3 -> sequenceTicks;
                case 4 -> TOTAL_SEQUENCE_TICKS;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> chargeTicks = Mth.clamp(value, 0, CHARGE_FULL_TICKS);
                case 3 -> sequenceTicks = Mth.clamp(value, 0, TOTAL_SEQUENCE_TICKS);
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return MeteorologicalControllerMenu.DATA_COUNT;
        }
    };

    public MeteorologicalControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.METEOROLOGICAL_CONTROLLER, pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        refreshMachineStatus(serverLevel, false);

        if (sequenceActive) {
            tickWeatherSequence(serverLevel);
            return;
        }

        if (machineStatus.canCharge() && chargeTicks < CHARGE_FULL_TICKS) {
            chargeTicks++;
            setChanged();
        }
    }

    public boolean tryStartWeatherSequence(WeatherSelection weatherSelection) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        refreshMachineStatus(serverLevel, true);

        if (sequenceActive) return false;
        if (!machineStatus.structureReady()) return false;
        if (!machineStatus.parabolaPowered()) return false;
        if (!machineStatus.catalystHasCrystal()) return false;
        if (chargeTicks < CHARGE_FULL_TICKS) return false;

        BlockPos catalystPos = machineStatus.catalystPos();
        if (catalystPos == null) return false;

        if (!EnergyNetworkHelper.consumeEnergyFromNetwork(serverLevel, machineStatus.condenserBasePos(), 2500)) {
            return false;
        }

        pendingWeather = weatherSelection;
        activeCatalystPos = catalystPos.immutable();
        sequenceActive = true;
        finaleTriggered = false;
        sequenceTicks = 0;
        chargeTicks = 0;
        ModSounds.playMeteoMachineAt(serverLevel, activeCatalystPos);
        setChanged();
        syncToClient();
        return true;
    }

    public ContainerData dataAccess() {
        return dataAccess;
    }

    public ForceChargeResult forceFullCharge() {
        if (sequenceActive) {
            return ForceChargeResult.SEQUENCE_RUNNING;
        }
        if (chargeTicks >= CHARGE_FULL_TICKS) {
            return ForceChargeResult.ALREADY_CHARGED;
        }

        chargeTicks = CHARGE_FULL_TICKS;
        setChanged();
        syncToClient();
        return ForceChargeResult.CHARGED;
    }

    private void refreshMachineStatus(ServerLevel level, boolean force) {
        if (!force && rescanCooldown > 0) {
            rescanCooldown--;
            return;
        }
        machineStatus = WeatherMachineHelper.scan(level, worldPosition);
        rescanCooldown = RESCAN_INTERVAL_TICKS;
    }

    private void tickWeatherSequence(ServerLevel level) {
        if (pendingWeather == null) {
            resetSequence();
            return;
        }

        BlockPos catalystPos = activeCatalystPos;
        if (catalystPos == null || !isCatalystPresent(level, catalystPos)) {
            resetSequence();
            return;
        }

        if (!finaleTriggered && !isCatalystCharged(level, catalystPos)) {
            resetSequence();
            return;
        }

        if (sequenceTicks < SPINUP_TICKS) {
            // Spin-up phase: rotating rods animation only (client renderer).
        } else if (sequenceTicks < BEAM_START_TICKS) {
            spawnPreparationParticles(level, catalystPos);
        } else if (sequenceTicks < BEAM_END_TICKS) {
            int beamTick = sequenceTicks - BEAM_START_TICKS;
            if (beamTick % BEAM_LOOP_INTERVAL_TICKS == 0) {
                ModSounds.playBeamAt(level, catalystPos);
            }
            spawnBeamParticles(level, catalystPos);
        }

        if (!finaleTriggered && sequenceTicks == BEAM_END_TICKS) {
            ModSounds.stopBeamNear(level, catalystPos);
            applyWeather(level, pendingWeather);
            strikeCatalystWithLightning(level, catalystPos);
            playCrystalStrikeEffects(level, catalystPos);
            consumeCatalystCrystal(level, catalystPos);
            finaleTriggered = true;
            syncToClient();
        }

        sequenceTicks++;
        setChanged();

        if ((sequenceTicks & 1) == 0
                || sequenceTicks == SPINUP_TICKS
                || sequenceTicks == BEAM_START_TICKS
                || sequenceTicks == BEAM_END_TICKS
                || sequenceTicks >= TOTAL_SEQUENCE_TICKS) {
            syncToClient();
        }

        if (sequenceTicks < TOTAL_SEQUENCE_TICKS) return;

        resetSequence();
        rescanCooldown = 0;
    }

    private int buildStatusFlags() {
        int flags = 0;
        if (machineStatus.towerPresent()) flags |= MeteorologicalControllerMenu.FLAG_TOWER_PRESENT;
        if (machineStatus.parabolaPresent()) flags |= MeteorologicalControllerMenu.FLAG_PARABOLA_PRESENT;
        if (machineStatus.catalystPresent()) flags |= MeteorologicalControllerMenu.FLAG_CATALYST_PRESENT;
        if (machineStatus.catalystHasCrystal()) flags |= MeteorologicalControllerMenu.FLAG_CATALYST_HAS_CRYSTAL;
        if (machineStatus.structureReady()) flags |= MeteorologicalControllerMenu.FLAG_STRUCTURE_READY;
        if (machineStatus.energyLinked()) flags |= MeteorologicalControllerMenu.FLAG_ENERGY_LINKED;
        if (machineStatus.parabolaPowered()) flags |= MeteorologicalControllerMenu.FLAG_PARABOLA_POWERED;
        if (chargeTicks >= CHARGE_FULL_TICKS) flags |= MeteorologicalControllerMenu.FLAG_FULLY_CHARGED;
        if (sequenceActive) flags |= MeteorologicalControllerMenu.FLAG_SEQUENCE_ACTIVE;
        if (canTriggerWeatherChange()) flags |= MeteorologicalControllerMenu.FLAG_WEATHER_UNLOCKED;
        return flags;
    }

    private boolean canTriggerWeatherChange() {
        return !sequenceActive && machineStatus.structureReady() && chargeTicks >= CHARGE_FULL_TICKS;
    }

    private boolean isCatalystCharged(ServerLevel level, BlockPos catalystPos) {
        BlockState state = level.getBlockState(catalystPos);
        return state.is(ModBlocks.METEOROLOGICAL_CATALYST)
                && state.hasProperty(CrystalCondenserBlock.HAS_CRYSTAL)
                && state.getValue(CrystalCondenserBlock.HAS_CRYSTAL);
    }

    private boolean isCatalystPresent(ServerLevel level, BlockPos catalystPos) {
        return level.getBlockState(catalystPos).is(ModBlocks.METEOROLOGICAL_CATALYST);
    }

    private void consumeCatalystCrystal(ServerLevel level, BlockPos catalystPos) {
        BlockState state = level.getBlockState(catalystPos);
        if (!state.is(ModBlocks.METEOROLOGICAL_CATALYST)) return;
        if (!state.hasProperty(CrystalCondenserBlock.HAS_CRYSTAL)) return;
        if (!state.getValue(CrystalCondenserBlock.HAS_CRYSTAL)) return;
        level.setBlock(catalystPos, state.setValue(CrystalCondenserBlock.HAS_CRYSTAL, false), 3);
    }

    private void applyWeather(ServerLevel level, WeatherSelection weatherSelection) {
        int duration = 20 * 60 * 10;
        switch (weatherSelection) {
            case CLEAR -> level.setWeatherParameters(duration, 0, false, false);
            case RAIN -> level.setWeatherParameters(0, duration, true, false);
            case THUNDER -> level.setWeatherParameters(0, duration, true, true);
        }
    }

    private void strikeCatalystWithLightning(ServerLevel level, BlockPos catalystPos) {
        LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(level);
        if (lightningBolt == null) return;
        lightningBolt.moveTo(catalystPos.getX() + 0.5D, catalystPos.getY() + 1.0D, catalystPos.getZ() + 0.5D);
        lightningBolt.setVisualOnly(true);
        level.addFreshEntity(lightningBolt);
    }

    private void playCrystalStrikeEffects(ServerLevel level, BlockPos catalystPos) {
        double x = catalystPos.getX() + 0.5D;
        double y = catalystPos.getY() + 1.0D;
        double z = catalystPos.getZ() + 0.5D;

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 12, 0.25D, 0.20D, 0.25D, 0.03D);

        level.playSound(null, catalystPos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.1F, 0.95F);
        level.playSound(null, catalystPos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.9F, 1.2F);
    }

    private void spawnPreparationParticles(ServerLevel level, BlockPos catalystPos) {
        double centerX = catalystPos.getX() + 0.5;
        double centerY = catalystPos.getY() + 0.8;
        double centerZ = catalystPos.getZ() + 0.5;

        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0D * i / 12.0D) + level.random.nextDouble() * 0.35D;
            double radius = 0.8D + level.random.nextDouble() * 0.55D;
            double x = centerX + Math.cos(angle) * radius;
            double y = centerY + level.random.nextDouble() * 0.9D;
            double z = centerZ + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0D, 0.015D, 0.0D, 0.0D);
        }
    }

    private void spawnBeamParticles(ServerLevel level, BlockPos catalystPos) {
        if (sequenceTicks % 2 != 0) return;

        double centerX = catalystPos.getX() + 0.5;
        double centerZ = catalystPos.getZ() + 0.5;
        int startY = catalystPos.getY() + 1;
        int maxY = Math.min(level.getMaxBuildHeight() - 1, startY + 110);

        for (int y = startY; y <= maxY; y += 2) {
            level.sendParticles(ParticleTypes.END_ROD, centerX, y + 0.1D, centerZ, 1, 0.02D, 0.0D, 0.02D, 0.0D);
        }

        for (int y = startY; y <= maxY; y += 5) {
            spawnParticleRing(level, centerX, y + 0.2D, centerZ, 2.4D, 14);
        }
    }

    private void spawnParticleRing(ServerLevel level,
                                   double centerX,
                                   double y,
                                   double centerZ,
                                   double radius,
                                   int points) {
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D * i / points) + level.random.nextDouble() * 0.08D;
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void resetSequence() {
        stopWeatherSounds();
        sequenceActive = false;
        finaleTriggered = false;
        sequenceTicks = 0;
        pendingWeather = null;
        activeCatalystPos = null;
        setChanged();
        syncToClient();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ChargeTicks", chargeTicks);
        tag.putBoolean("SequenceActive", sequenceActive);
        tag.putBoolean("FinaleTriggered", finaleTriggered);
        tag.putInt("SequenceTicks", sequenceTicks);
        if (pendingWeather != null) {
            tag.putString("PendingWeather", pendingWeather.serializedName());
        }
        if (activeCatalystPos != null) {
            tag.putLong("ActiveCatalystPos", activeCatalystPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        chargeTicks = Mth.clamp(tag.getInt("ChargeTicks"), 0, CHARGE_FULL_TICKS);
        sequenceActive = tag.getBoolean("SequenceActive");
        finaleTriggered = tag.getBoolean("FinaleTriggered");
        sequenceTicks = Mth.clamp(tag.getInt("SequenceTicks"), 0, TOTAL_SEQUENCE_TICKS);
        pendingWeather = WeatherSelection.fromSerializedName(tag.getString("PendingWeather"));
        activeCatalystPos = tag.contains("ActiveCatalystPos")
                ? BlockPos.of(tag.getLong("ActiveCatalystPos"))
                : null;

        if (!sequenceActive) {
            finaleTriggered = false;
            sequenceTicks = 0;
            pendingWeather = null;
            activeCatalystPos = null;
        }
        rescanCooldown = 0;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("SequenceActive", sequenceActive);
        tag.putBoolean("FinaleTriggered", finaleTriggered);
        tag.putInt("SequenceTicks", sequenceTicks);
        if (activeCatalystPos != null) {
            tag.putLong("ActiveCatalystPos", activeCatalystPos.asLong());
        }
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public boolean isBeamPhaseActive() {
        return sequenceActive && sequenceTicks >= BEAM_START_TICKS && sequenceTicks < BEAM_END_TICKS;
    }

    public boolean isSequenceActiveForRender() {
        return sequenceActive;
    }

    public int getSequenceTicksForRender() {
        return sequenceTicks;
    }

    public boolean isAnimatingCatalyst(BlockPos catalystPos) {
        return sequenceActive && activeCatalystPos != null && activeCatalystPos.equals(catalystPos);
    }

    @Nullable
    public BlockPos getActiveCatalystPosForRender() {
        return activeCatalystPos;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.universegate.meteorological_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv, Player player) {
        return new MeteorologicalControllerMenu(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.worldPosition;
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void stopWeatherSounds() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos soundPos = activeCatalystPos != null ? activeCatalystPos : worldPosition;
        ModSounds.stopBeamNear(serverLevel, soundPos);
        ModSounds.stopMeteoMachineNear(serverLevel, soundPos);
    }
}
