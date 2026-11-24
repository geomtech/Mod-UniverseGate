package fr.geomtech.universegate;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

public final class AcidTechnologyManager {
    public static final int HORSE_TRANSFORMATION_SHOTS = 24;
    private AcidTechnologyManager() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player.isSpectator()) return InteractionResult.PASS;
            ItemStack held = player.getItemInHand(hand);
            if (held.is(ModItems.HORSE_LASER_HARNESS) && entity instanceof LivingEntity living) {
                return ModItems.HORSE_LASER_HARNESS.interactLivingEntity(held, player, living, hand);
            }
            if (entity instanceof AbstractHorse horse && ((LaserMount) horse).universegate$hasLaserHarness()
                    && held.isEmpty() && player.isShiftKeyDown()) {
                if (level.isClientSide) return InteractionResult.SUCCESS;
                if (!player.getUUID().equals(horse.getOwnerUUID())) return InteractionResult.FAIL;
                ((LaserMount) horse).universegate$setLaserHarness(false);
                giveItem(player, new ItemStack(ModItems.HORSE_LASER_HARNESS));
                return InteractionResult.CONSUME;
            }
            if (!held.is(ModItems.ACID_RAIN_BUCKET)
                    || !(entity instanceof IronGolem && !(entity instanceof RustedGolemEntity)
                    || entity instanceof Creeper && !(entity instanceof AcidCreeperEntity))) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide) return InteractionResult.SUCCESS;
            boolean converted;
            if (entity instanceof IronGolem golem) {
                converted = rustGolem(golem);
            } else {
                Creeper creeper = (Creeper) entity;
                float healthFraction = creeper.getHealth() / creeper.getMaxHealth();
                AcidCreeperEntity companion = creeper.convertTo(ModEntityTypes.ACID_CREEPER, true);
                converted = companion != null;
                if (companion != null) {
                    companion.setAcidOwner(player.getUUID());
                    companion.setHealth(Math.max(1.0F, healthFraction * companion.getMaxHealth()));
                    companion.setPersistenceRequired();
                }
            }
            if (!converted) return InteractionResult.FAIL;
            if (!player.isCreative()) {
                held.shrink(1);
                if (held.isEmpty()) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                else giveItem(player, new ItemStack(Items.BUCKET));
            }
            level.playSound(null, entity.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1.0F, 0.7F);
            return InteractionResult.CONSUME;
        });
    }

    public static boolean onAcidExposure(LivingEntity entity) {
        if (entity instanceof AcidCreeperEntity) return true;
        return entity instanceof IronGolem golem && !(golem instanceof RustedGolemEntity) && rustGolem(golem);
    }

    private static boolean rustGolem(IronGolem source) {
        float fraction = source.getHealth() / source.getMaxHealth();
        boolean playerCreated = source.isPlayerCreated();
        RustedGolemEntity rusty = source.convertTo(ModEntityTypes.RUSTED_GOLEM, true);
        if (rusty == null) return false;
        rusty.setPlayerCreated(playerCreated);
        rusty.setHealth(Math.max(1.0F, fraction * rusty.getMaxHealth()));
        return true;
    }

    public static void wearLaserHorse(AbstractHorse horse, Player rider) {
        if (!(horse instanceof Horse) || !(horse.level() instanceof ServerLevel level)) return;
        LaserMount mount = (LaserMount) horse;
        int shots = mount.universegate$getLaserShots() + 1;
        mount.universegate$setLaserShots(shots);
        if (shots >= 8) horse.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, shots >= 16 ? 1 : 0));
        level.sendParticles(ParticleTypes.SMOKE, horse.getX(), horse.getY() + 1.2D, horse.getZ(),
                4 + shots / 4, 0.4D, 0.25D, 0.4D, 0.01D);
        rider.displayClientMessage(Component.translatable("message.universegate.horse_corrosion", shots, HORSE_TRANSFORMATION_SHOTS), true);
        if (shots < HORSE_TRANSFORMATION_SHOTS) return;

        ItemStack saddle = mount.universegate$getSaddle();
        ItemStack armor = horse.getBodyArmorItem().copy();
        UUID owner = horse.getOwnerUUID();
        int age = horse.getAge();
        float health = horse.getHealth();
        List<Entity> passengers = List.copyOf(horse.getPassengers());
        horse.ejectPassengers();
        SkeletonHorse skeleton = horse.convertTo(EntityType.SKELETON_HORSE, false);
        if (skeleton == null) {
            for (Entity passenger : passengers) passenger.startRiding(horse, true);
            return;
        }
        skeleton.setTamed(true);
        skeleton.setOwnerUUID(owner);
        skeleton.setAge(age);
        skeleton.setHealth(Math.min(health, skeleton.getMaxHealth()));
        if (!saddle.isEmpty()) skeleton.equipSaddle(saddle, SoundSource.NEUTRAL);
        ((LaserMount) skeleton).universegate$setLaserHarness(true);
        ((LaserMount) skeleton).universegate$setLaserShots(HORSE_TRANSFORMATION_SHOTS);
        if (!armor.isEmpty()) skeleton.spawnAtLocation(armor);
        for (Entity passenger : passengers) passenger.startRiding(skeleton, true);
        level.playSound(null, skeleton.blockPosition(), SoundEvents.SKELETON_HORSE_AMBIENT, SoundSource.NEUTRAL, 1.0F, 0.6F);
        rider.displayClientMessage(Component.translatable("message.universegate.horse_transformed"), false);
    }

    private static void giveItem(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
