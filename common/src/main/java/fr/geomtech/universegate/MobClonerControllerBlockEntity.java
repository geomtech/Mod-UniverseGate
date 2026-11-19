package fr.geomtech.universegate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class MobClonerControllerBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements Container, ExtendedScreenHandlerFactory<BlockPos> {

    public static final long BASE_CLONE_COST = 1_000_000_000L;

    private static final int SLOT_DNA = 0;
    private static final int RESCAN_INTERVAL_TICKS = 10;
    private static final int CHARGE_DURATION_TICKS = 20 * 8;

    private static final double BOSS_MULTIPLIER = 6.0D;
    private static final float BOSS_HEALTH_THRESHOLD = 150.0F;

    private final NonNullList<ItemStack> inventory = NonNullList.withSize(1, ItemStack.EMPTY);

    private int cachedCost = 0;
    private int cachedFlags = 0;
    private int cachedClonerDistance = -1;
    private long cachedAvailableEnergy = 0L;
    private int rescanCooldown = 0;
    private boolean redstonePoweredLatch = false;

    private boolean chargeActive = false;
    private int chargeTicks = 0;

    @Nullable
    private String pendingEntityTypeId = null;
    @Nullable
    private String pendingCustomName = null;
    @Nullable
    private BlockPos pendingClonerPos = null;
    @Nullable
    private UUID chargingPlayerId = null;
    private int pendingCost = 0;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> cachedCost & 0xFFFF;
                case 1 -> (cachedCost >>> 16) & 0xFFFF;
                case 2 -> cachedFlags;
                case 3 -> cachedClonerDistance;
                case 4 -> segment(cachedAvailableEnergy, 0);
                case 5 -> segment(cachedAvailableEnergy, 1);
                case 6 -> segment(cachedAvailableEnergy, 2);
                case 7 -> segment(cachedAvailableEnergy, 3);
                case 8 -> chargeTicks;
                case 9 -> CHARGE_DURATION_TICKS;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return MobClonerControllerMenu.DATA_COUNT;
        }
    };

    public MobClonerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOB_CLONER_CONTROLLER, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MobClonerControllerBlockEntity entity) {
        if (level.isClientSide) return;
        entity.serverTick();
    }

    public ContainerData dataAccess() {
        return dataAccess;
    }

    public boolean isChargeActive() {
        return chargeActive;
    }

    public void forceRescan() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        refreshStatus(serverLevel, true);
    }

    public boolean tryInsertDna(Player player, ItemStack heldStack) {
        if (chargeActive) return false;
        if (!heldStack.is(ModItems.DNA)) return false;
        if (!inventory.get(SLOT_DNA).isEmpty()) return false;

        ItemStack inserted = heldStack.copy();
        inserted.setCount(1);
        inventory.set(SLOT_DNA, inserted);

        if (!player.getAbilities().instabuild) {
            heldStack.shrink(1);
        }

        setChanged();
        forceRescan();
        syncClient();
        return true;
    }

    public boolean tryClone(@Nullable Player player) {
        if (!(level instanceof ServerLevel serverLevel)) return false;

        if (chargeActive) {
            notifyPlayer(player, Component.translatable("message.universegate.mob_cloner_controller_charge_running"));
            return false;
        }

        ItemStack dnaStack = inventory.get(SLOT_DNA);
        if (dnaStack.isEmpty()) {
            notifyPlayer(player, Component.translatable("message.universegate.mob_cloner_controller_insert_dna"));
            return false;
        }

        if (DnaSampleItem.getRemainingUses(dnaStack) <= 0) {
            notifyPlayer(player, Component.translatable("message.universegate.mob_cloner_controller_dna_depleted"));
            return false;
        }

        @Nullable EntityType<?> mobType = resolveMobType(serverLevel, dnaStack);
        if (mobType == null) {
            if (DnaSampleItem.getEntityType(dnaStack).isEmpty()) {
                notifyPlayer(player, Component.translatable("message.universegate.mob_cloner_controller_invalid_dna"));
            } else {
                notifyPlayer(player, Component.translatable("message.universegate.mob_cloner_controller_non_mob"));
            }
            return false;
        }

        @Nullable Mob mob = createMobFromType(serverLevel, mobType);
        if (mob == null) {
            notifyPlayer(player, Component.translatable("message.universegate.mob_cloner_controller_non_mob"));
            return false;
        }

        BlockPos clonerPos = EnergyNetworkHelper.findNearestMobClonerOnNetwork(serverLevel, worldPosition);
        if (clonerPos == null) {
            notifyPlayer(player, Component.translatable("message.universegate.mob_cloner_controller_no_cloner"));
            return false;
        }

        if (!EnergyNetworkHelper.isNodeLinkedToEnergyConduit(serverLevel, clonerPos)) {
            notifyPlayer(player, Component.translatable("message.universegate.mob_cloner_controller_no_standard_link"));
            return false;
        }

        if (!DarkEnergyNetworkHelper.isMachinePowered(serverLevel, clonerPos)) {
            notifyPlayer(player, Component.translatable("message.universegate.mob_cloner_controller_no_dark_power"));
            return false;
        }

        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mobType);
        if (typeId == null) {
            notifyPlayer(player, Component.translatable("message.universegate.mob_cloner_controller_invalid_dna"));
            return false;
        }

        int energyCost = computeCloneCost(mobType, mobType.getCategory(), mob.getMaxHealth());
        long availableEnergy = EnergyNetworkHelper.getAvailableEnergyLongFromNetwork(serverLevel, clonerPos);
        if (availableEnergy < energyCost) {
            notifyPlayer(
                    player,
                    Component.translatable("message.universegate.mob_cloner_controller_no_energy", ZpcItem.formatEnergy(energyCost))
            );
            return false;
        }

        if (!canSpawnOnTopOfCloner(serverLevel, clonerPos, mob)) {
            notifyPlayer(player, Component.translatable("message.universegate.mob_cloner_controller_spawn_blocked"));
            return false;
        }

        chargeActive = true;
        chargeTicks = 0;
        pendingEntityTypeId = typeId.toString();
        pendingCustomName = DnaSampleItem.getCustomName(dnaStack).orElse(null);
        pendingClonerPos = clonerPos.immutable();
        pendingCost = energyCost;
        chargingPlayerId = player != null ? player.getUUID() : null;

        notifyPlayer(
                player,
                Component.translatable("message.universegate.mob_cloner_controller_charge_started", CHARGE_DURATION_TICKS / 20)
        );

        setChanged();
        forceRescan();
        syncClient();
        return true;
    }

    public void dropContents() {
        if (level == null) return;
        Containers.dropContents(level, worldPosition, this);
        inventory.clear();
        chargeActive = false;
        chargeTicks = 0;
        clearPendingCloneData();
        setChanged();
    }

    private void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        boolean redstonePowered = serverLevel.hasNeighborSignal(worldPosition);
        if (redstonePowered && !redstonePoweredLatch && !chargeActive) {
            tryClone(null);
        }
        redstonePoweredLatch = redstonePowered;

        if (chargeActive) {
            if (pendingClonerPos != null) {
                emitCloningParticles(serverLevel, pendingClonerPos, chargeTicks);
            }

            chargeTicks = Math.min(CHARGE_DURATION_TICKS, chargeTicks + 1);
            if (chargeTicks >= CHARGE_DURATION_TICKS) {
                completePendingClone(serverLevel);
                chargeActive = false;
                chargeTicks = 0;
                clearPendingCloneData();
                setChanged();
                syncClient();
                forceRescan();
            }
        }

        if (rescanCooldown > 0) {
            rescanCooldown--;
            return;
        }

        refreshStatus(serverLevel, false);
    }

    private void refreshStatus(ServerLevel serverLevel, boolean force) {
        if (!force && rescanCooldown > 0) {
            return;
        }
        rescanCooldown = RESCAN_INTERVAL_TICKS;

        ItemStack dnaStack = inventory.get(SLOT_DNA);

        int flags = 0;
        int cost = 0;
        int distance = -1;
        long availableEnergy = 0L;

        if (!dnaStack.isEmpty()) {
            flags |= MobClonerControllerMenu.FLAG_HAS_DNA;
        }

        BlockPos clonerPos = pendingClonerPos;
        if (clonerPos == null || !chargeActive) {
            clonerPos = EnergyNetworkHelper.findNearestMobClonerOnNetwork(serverLevel, worldPosition);
        }

        if (clonerPos != null) {
            flags |= MobClonerControllerMenu.FLAG_HAS_CLONER;
            distance = (int) Math.round(Math.sqrt(worldPosition.distSqr(clonerPos)));

            if (EnergyNetworkHelper.isNodeLinkedToEnergyConduit(serverLevel, clonerPos)) {
                flags |= MobClonerControllerMenu.FLAG_STANDARD_LINK;
                availableEnergy = EnergyNetworkHelper.getAvailableEnergyLongFromNetwork(serverLevel, clonerPos);
            }

            if (DarkEnergyNetworkHelper.isMachinePowered(serverLevel, clonerPos)) {
                flags |= MobClonerControllerMenu.FLAG_DARK_LINK;
            }
        }

        int remainingUses = DnaSampleItem.getRemainingUses(dnaStack);
        @Nullable EntityType<?> mobType = resolveMobType(serverLevel, dnaStack);
        if (mobType != null && remainingUses > 0) {
            flags |= MobClonerControllerMenu.FLAG_DNA_VALID;
            float maxHealth = estimateMobMaxHealth(serverLevel, mobType);
            cost = computeCloneCost(mobType, mobType.getCategory(), maxHealth);

            if (clonerPos != null) {
                @Nullable Mob preview = createMobFromType(serverLevel, mobType);
                if (preview != null && canSpawnOnTopOfCloner(serverLevel, clonerPos, preview)) {
                    flags |= MobClonerControllerMenu.FLAG_SPAWN_SPACE;
                }
            }
        }

        if (chargeActive) {
            flags |= MobClonerControllerMenu.FLAG_CHARGING;
            if (pendingCost > 0) {
                cost = pendingCost;
            }
            if (pendingClonerPos != null) {
                distance = (int) Math.round(Math.sqrt(worldPosition.distSqr(pendingClonerPos)));
            }
        }

        if (availableEnergy >= cost && cost > 0) {
            flags |= MobClonerControllerMenu.FLAG_ENOUGH_ENERGY;
        }

        boolean ready = (flags & MobClonerControllerMenu.FLAG_DNA_VALID) != 0
                && (flags & MobClonerControllerMenu.FLAG_HAS_CLONER) != 0
                && (flags & MobClonerControllerMenu.FLAG_STANDARD_LINK) != 0
                && (flags & MobClonerControllerMenu.FLAG_DARK_LINK) != 0
                && (flags & MobClonerControllerMenu.FLAG_SPAWN_SPACE) != 0
                && (flags & MobClonerControllerMenu.FLAG_ENOUGH_ENERGY) != 0
                && (flags & MobClonerControllerMenu.FLAG_CHARGING) == 0;
        if (ready) {
            flags |= MobClonerControllerMenu.FLAG_READY;
        }

        boolean changed = flags != cachedFlags
                || cost != cachedCost
                || distance != cachedClonerDistance
                || availableEnergy != cachedAvailableEnergy;

        cachedFlags = flags;
        cachedCost = cost;
        cachedClonerDistance = distance;
        cachedAvailableEnergy = availableEnergy;

        if (changed) {
            setChanged();
        }
    }

    @Nullable
    private static EntityType<?> resolveMobType(ServerLevel level, ItemStack dnaStack) {
        if (dnaStack.isEmpty()) return null;

        @Nullable EntityType<?> entityType = DnaSampleItem.getEntityType(dnaStack).orElse(null);
        if (entityType == null) return null;

        @Nullable Mob mob = createMobFromType(level, entityType);
        if (mob == null) return null;
        return entityType;
    }

    private static float estimateMobMaxHealth(ServerLevel level, EntityType<?> entityType) {
        @Nullable Mob mob = createMobFromType(level, entityType);
        if (mob == null) {
            return 20.0F;
        }
        return mob.getMaxHealth();
    }

    private void completePendingClone(ServerLevel serverLevel) {
        Player requester = getChargingPlayer(serverLevel);

        if (pendingEntityTypeId == null || pendingClonerPos == null || pendingCost <= 0) {
            return;
        }

        ResourceLocation entityLocation = ResourceLocation.tryParse(pendingEntityTypeId);
        if (entityLocation == null) {
            notifyPlayer(requester, Component.translatable("message.universegate.mob_cloner_controller_invalid_dna"));
            return;
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityLocation).orElse(null);
        if (entityType == null) {
            notifyPlayer(requester, Component.translatable("message.universegate.mob_cloner_controller_non_mob"));
            return;
        }

        if (!serverLevel.getBlockState(pendingClonerPos).is(ModBlocks.MOB_CLONER)) {
            notifyPlayer(requester, Component.translatable("message.universegate.mob_cloner_controller_no_cloner"));
            return;
        }

        if (!EnergyNetworkHelper.isNodeLinkedToEnergyConduit(serverLevel, pendingClonerPos)) {
            notifyPlayer(requester, Component.translatable("message.universegate.mob_cloner_controller_no_standard_link"));
            return;
        }

        if (!DarkEnergyNetworkHelper.isMachinePowered(serverLevel, pendingClonerPos)) {
            notifyPlayer(requester, Component.translatable("message.universegate.mob_cloner_controller_no_dark_power"));
            return;
        }

        Mob mob = createMobFromType(serverLevel, entityType);
        if (mob == null) {
            notifyPlayer(requester, Component.translatable("message.universegate.mob_cloner_controller_non_mob"));
            return;
        }

        if (!canSpawnOnTopOfCloner(serverLevel, pendingClonerPos, mob)) {
            notifyPlayer(requester, Component.translatable("message.universegate.mob_cloner_controller_spawn_blocked"));
            return;
        }

        if (!EnergyNetworkHelper.consumeEnergyFromNetwork(serverLevel, pendingClonerPos, pendingCost)) {
            notifyPlayer(
                    requester,
                    Component.translatable("message.universegate.mob_cloner_controller_no_energy", ZpcItem.formatEnergy(pendingCost))
            );
            return;
        }

        BlockPos spawnPos = pendingClonerPos.above();
        mob.finalizeSpawn(
                serverLevel,
                serverLevel.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.MOB_SUMMONED,
                null
        );
        mob.setPersistenceRequired();

        if (pendingCustomName != null && !pendingCustomName.isBlank()) {
            mob.setCustomName(Component.literal(pendingCustomName));
        }

        if (!consumeDnaCharge()) {
            notifyPlayer(requester, Component.translatable("message.universegate.mob_cloner_controller_dna_depleted"));
            return;
        }

        if (!serverLevel.addFreshEntity(mob)) {
            notifyPlayer(requester, Component.translatable("message.universegate.mob_cloner_controller_spawn_blocked"));
            return;
        }

        emitCloneMaterializationBurst(serverLevel, pendingClonerPos);

        serverLevel.playSound(null, pendingClonerPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7F, 1.3F);
        notifyPlayer(
                requester,
                Component.translatable(
                        "message.universegate.mob_cloner_controller_spawn_success",
                        mob.getType().getDescription(),
                        ZpcItem.formatEnergy(pendingCost)
                )
        );
    }

    private boolean consumeDnaCharge() {
        ItemStack dnaStack = inventory.get(SLOT_DNA);
        if (dnaStack.isEmpty() || !dnaStack.is(ModItems.DNA)) {
            return false;
        }

        if (!DnaSampleItem.consumeUse(dnaStack)) {
            return false;
        }

        if (DnaSampleItem.getRemainingUses(dnaStack) <= 0) {
            inventory.set(SLOT_DNA, ItemStack.EMPTY);
        }
        return true;
    }

    @Nullable
    private static Mob createMobFromType(ServerLevel level, EntityType<?> entityType) {
        Entity entity = entityType.create(level);
        if (entity instanceof Mob mob) {
            return mob;
        }
        return null;
    }

    private static boolean canSpawnOnTopOfCloner(ServerLevel level, BlockPos clonerPos, Mob mob) {
        BlockPos spawnPos = clonerPos.above();
        float yaw = level.random.nextFloat() * 360.0F;
        mob.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, yaw, 0.0F);
        return level.noCollision(mob);
    }

    private static int computeCloneCost(EntityType<?> entityType, MobCategory category, float maxHealth) {
        double categoryMultiplier = switch (category) {
            case MONSTER -> 1.25D;
            case AMBIENT -> 1.10D;
            case WATER_CREATURE -> 1.15D;
            case WATER_AMBIENT -> 1.05D;
            case UNDERGROUND_WATER_CREATURE -> 1.30D;
            case AXOLOTLS -> 1.20D;
            case MISC -> 1.35D;
            default -> 1.0D;
        };

        double healthMultiplier = 1.0D + Math.max(0.0D, (maxHealth - 20.0D) / 20.0D) * 0.5D;
        double bossMultiplier = isBossEntity(entityType, maxHealth) ? BOSS_MULTIPLIER : 1.0D;

        long cost = Math.round(BASE_CLONE_COST * categoryMultiplier * healthMultiplier * bossMultiplier);
        cost = Math.max(BASE_CLONE_COST, Math.min(Integer.MAX_VALUE, cost));
        return (int) cost;
    }

    private static boolean isBossEntity(EntityType<?> entityType, float maxHealth) {
        if (entityType == EntityType.WITHER
                || entityType == EntityType.ENDER_DRAGON
                || entityType == EntityType.WARDEN
                || entityType == EntityType.ELDER_GUARDIAN) {
            return true;
        }

        return maxHealth >= BOSS_HEALTH_THRESHOLD;
    }

    private static void emitCloningParticles(ServerLevel level, BlockPos clonerPos, int currentChargeTicks) {
        double centerX = clonerPos.getX() + 0.5D;
        double centerZ = clonerPos.getZ() + 0.5D;
        float progress = Mth.clamp(currentChargeTicks / (float) CHARGE_DURATION_TICKS, 0.0F, 1.0F);

        int baseCount = 18 + Mth.floor(progress * 26.0F);
        for (int layer = 0; layer < 3; layer++) {
            double y = clonerPos.getY() + 1.2D + layer;
            double spread = 0.52D - layer * 0.06D;

            level.sendParticles(ParticleTypes.PORTAL, centerX, y, centerZ, baseCount + 12, spread, 0.24D, spread, 0.08D);
            level.sendParticles(ParticleTypes.ENCHANT, centerX, y, centerZ, baseCount + 8, spread, 0.18D, spread, 0.03D);
            level.sendParticles(ParticleTypes.END_ROD, centerX, y, centerZ, (baseCount / 2) + 8, spread * 0.85D, 0.16D, spread * 0.85D, 0.0D);
            level.sendParticles(ParticleTypes.GLOW, centerX, y, centerZ, (baseCount / 2) + 6, spread * 0.75D, 0.16D, spread * 0.75D, 0.0D);

            if (((currentChargeTicks + layer) & 1) == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centerX, y, centerZ, 8 + Mth.floor(progress * 10.0F), spread * 0.9D, 0.22D, spread * 0.9D, 0.02D);
            }

            int ringPoints = 14 + Mth.floor(progress * 16.0F);
            double ringRadius = 0.66D - layer * 0.08D;
            for (int i = 0; i < ringPoints; i++) {
                double angle = ((Math.PI * 2.0D) / ringPoints) * i + currentChargeTicks * 0.24D + layer * 0.35D;
                double px = centerX + Math.cos(angle) * ringRadius;
                double py = y - 0.22D + (i % 3) * 0.14D;
                double pz = centerZ + Math.sin(angle) * ringRadius;
                level.sendParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.0D, 0.015D, 0.0D, 0.0D);
            }
        }
    }

    private static void emitCloneMaterializationBurst(ServerLevel level, BlockPos clonerPos) {
        double centerX = clonerPos.getX() + 0.5D;
        double centerZ = clonerPos.getZ() + 0.5D;

        level.sendParticles(ParticleTypes.FLASH, centerX, clonerPos.getY() + 2.3D, centerZ, 2, 0.15D, 0.15D, 0.15D, 0.0D);
        level.sendParticles(ParticleTypes.EXPLOSION, centerX, clonerPos.getY() + 2.0D, centerZ, 4, 0.25D, 0.45D, 0.25D, 0.02D);

        for (int layer = 0; layer < 3; layer++) {
            double y = clonerPos.getY() + 1.15D + layer;
            double spread = 0.58D - layer * 0.08D;

            level.sendParticles(ParticleTypes.PORTAL, centerX, y, centerZ, 70, spread, 0.30D, spread, 0.11D);
            level.sendParticles(ParticleTypes.ENCHANT, centerX, y, centerZ, 64, spread, 0.22D, spread, 0.04D);
            level.sendParticles(ParticleTypes.END_ROD, centerX, y, centerZ, 42, spread * 0.85D, 0.20D, spread * 0.85D, 0.0D);
            level.sendParticles(ParticleTypes.GLOW, centerX, y, centerZ, 48, spread * 0.75D, 0.20D, spread * 0.75D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centerX, y, centerZ, 24, spread * 0.9D, 0.25D, spread * 0.9D, 0.04D);
        }
    }

    @Nullable
    private Player getChargingPlayer(ServerLevel serverLevel) {
        if (chargingPlayerId == null) return null;
        return serverLevel.getServer().getPlayerList().getPlayer(chargingPlayerId);
    }

    private void notifyPlayer(@Nullable Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    private void clearPendingCloneData() {
        pendingEntityTypeId = null;
        pendingCustomName = null;
        pendingClonerPos = null;
        chargingPlayerId = null;
        pendingCost = 0;
    }

    private void syncClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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
        if (chargeActive) {
            return ItemStack.EMPTY;
        }

        ItemStack result = ContainerHelper.removeItem(inventory, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
            forceRescan();
            syncClient();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (chargeActive) {
            return ItemStack.EMPTY;
        }

        ItemStack result = ContainerHelper.takeItem(inventory, slot);
        if (!result.isEmpty()) {
            setChanged();
            forceRescan();
            syncClient();
        }
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != SLOT_DNA) return;
        if (chargeActive) return;

        if (!stack.isEmpty() && !stack.is(ModItems.DNA)) {
            return;
        }

        inventory.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
        forceRescan();
        syncClient();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return !chargeActive && slot == SLOT_DNA && stack.is(ModItems.DNA);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        if (chargeActive) return;

        inventory.clear();
        setChanged();
        forceRescan();
        syncClient();
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, inventory, registries);

        tag.putBoolean("ChargeActive", chargeActive);
        tag.putInt("ChargeTicks", chargeTicks);
        tag.putBoolean("RedstonePoweredLatch", redstonePoweredLatch);
        tag.putInt("PendingCost", pendingCost);
        if (pendingEntityTypeId != null) {
            tag.putString("PendingEntityType", pendingEntityTypeId);
        }
        if (pendingCustomName != null && !pendingCustomName.isBlank()) {
            tag.putString("PendingCustomName", pendingCustomName);
        }
        if (pendingClonerPos != null) {
            tag.putLong("PendingClonerPos", pendingClonerPos.asLong());
        }
        if (chargingPlayerId != null) {
            tag.putUUID("ChargingPlayer", chargingPlayerId);
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, inventory, registries);
        cachedCost = 0;
        cachedFlags = 0;
        cachedClonerDistance = -1;
        cachedAvailableEnergy = 0L;
        rescanCooldown = 0;

        chargeActive = tag.getBoolean("ChargeActive");
        chargeTicks = Mth.clamp(tag.getInt("ChargeTicks"), 0, CHARGE_DURATION_TICKS);
        redstonePoweredLatch = tag.getBoolean("RedstonePoweredLatch");
        pendingCost = Math.max(0, tag.getInt("PendingCost"));
        pendingEntityTypeId = tag.contains("PendingEntityType") ? tag.getString("PendingEntityType") : null;
        pendingCustomName = tag.contains("PendingCustomName") ? tag.getString("PendingCustomName") : null;
        pendingClonerPos = tag.contains("PendingClonerPos") ? BlockPos.of(tag.getLong("PendingClonerPos")) : null;
        chargingPlayerId = tag.hasUUID("ChargingPlayer") ? tag.getUUID("ChargingPlayer") : null;

        if (!chargeActive) {
            clearPendingCloneData();
            chargeTicks = 0;
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.universegate.mob_cloner_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv, Player player) {
        forceRescan();
        return new MobClonerControllerMenu(syncId, inv, this, dataAccess());
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    private static int segment(long value, int index) {
        return (int) ((value >>> (index * 16)) & 0xFFFFL);
    }
}
