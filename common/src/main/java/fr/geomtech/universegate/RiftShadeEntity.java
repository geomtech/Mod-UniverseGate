package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;

public class RiftShadeEntity extends Monster {

    private static final String TAG_GUARDIAN = "RiftGuardian";
    private static final int GUARDIAN_SUMMON_INTERVAL_TICKS = 20 * 9;
    private static final int GUARDIAN_MAX_MINIONS = 6;

    /** True for the Rift Guardian, the boss that protects each Rift cube. */
    private boolean guardian = false;
    private ServerBossEvent bossEvent;
    private int summonCooldown = GUARDIAN_SUMMON_INTERVAL_TICKS;

    public RiftShadeEntity(EntityType<? extends Monster> type, net.minecraft.world.level.Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2D);
    }

    public static boolean canSpawn(EntityType<RiftShadeEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!level.getLevel().dimension().equals(UniverseGateDimensions.RIFT)) return false;
        if (level.getLevel().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return false;
        if (!level.getBlockState(pos.below()).is(ModBlocks.VOID_BLOCK)) return false;
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) return false;
        return level.getLevel().noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.05D, false));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.RIFT_SHADE_SOUND;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource damageSource) {
        return ModSounds.RIFT_SHADE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.RIFT_SHADE_DEATH;
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return type != ModEntityTypes.RIFT_SHADE && super.canAttackType(type);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isSensitiveToWater() {
        return false;
    }

    // ------------------------------------------------------------------
    // Rift Guardian (boss variant)
    // ------------------------------------------------------------------

    /** Turns a freshly created shade into the Rift Guardian boss. */
    public void makeGuardian() {
        guardian = true;
        setAttributeBase(Attributes.MAX_HEALTH, 220.0D);
        setAttributeBase(Attributes.ATTACK_DAMAGE, 11.0D);
        setAttributeBase(Attributes.ARMOR, 8.0D);
        setAttributeBase(Attributes.KNOCKBACK_RESISTANCE, 0.9D);
        setAttributeBase(Attributes.MOVEMENT_SPEED, 0.27D);
        setAttributeBase(Attributes.SCALE, 1.75D);
        setHealth(getMaxHealth());
        setCustomName(Component.translatable("entity.universegate.rift_guardian"));
        setCustomNameVisible(true);
        setPersistenceRequired();
        xpReward = 60;
    }

    public boolean isGuardian() {
        return guardian;
    }

    private void setAttributeBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        var instance = getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private ServerBossEvent bossEvent() {
        if (bossEvent == null) {
            bossEvent = new ServerBossEvent(
                    Component.translatable("entity.universegate.rift_guardian"),
                    BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.NOTCHED_10);
            bossEvent.setDarkenScreen(true);
        }
        return bossEvent;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!guardian) return;

        bossEvent().setProgress(getHealth() / getMaxHealth());

        if (summonCooldown > 0) {
            summonCooldown--;
            return;
        }
        summonCooldown = GUARDIAN_SUMMON_INTERVAL_TICKS;

        if (getTarget() == null || getHealth() > getMaxHealth() * 0.6F) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;

        AABB area = getBoundingBox().inflate(16.0D, 6.0D, 16.0D);
        int minions = serverLevel.getEntitiesOfClass(RiftShadeEntity.class, area, shade -> !shade.isGuardian()).size();
        int toSpawn = Math.min(2, GUARDIAN_MAX_MINIONS - minions);
        for (int i = 0; i < toSpawn; i++) {
            RiftShadeEntity minion = ModEntityTypes.RIFT_SHADE.create(serverLevel);
            if (minion == null) return;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            minion.moveTo(getX() + Math.cos(angle) * 2.5D, getY(), getZ() + Math.sin(angle) * 2.5D, random.nextFloat() * 360.0F, 0.0F);
            if (!serverLevel.noCollision(minion)) continue;
            minion.setTarget(getTarget());
            serverLevel.addFreshEntity(minion);
        }
        playSound(ModSounds.RIFT_SHADE_SOUND, 2.0F, 0.6F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && guardian && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 4, 0), this);
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 2, 1), this);
        }
        return hit;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (guardian) bossEvent().addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (bossEvent != null) bossEvent.removePlayer(player);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        if (!guardian) return;
        spawnAtLocation(new ItemStack(ModItems.RIFT_CORE));
        spawnAtLocation(new ItemStack(ModItems.RIFT_CRYSTAL, 8 + random.nextInt(9)));
        spawnAtLocation(new ItemStack(ModItems.ENERGY_CRYSTAL, 2 + random.nextInt(3)));
    }

    @Override
    public boolean canChangeDimensions(net.minecraft.world.level.Level from, net.minecraft.world.level.Level to) {
        return !guardian && super.canChangeDimensions(from, to);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (guardian) tag.putBoolean(TAG_GUARDIAN, true);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        guardian = tag.getBoolean(TAG_GUARDIAN);
        if (guardian) {
            xpReward = 60;
            if (hasCustomName()) bossEvent().setName(getCustomName());
        }
    }

}
