package fr.geomtech.universegate;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class LaserRifleItem extends Item {
    private static final ResourceKey<DamageType> LASER_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "laser"));

    public LaserRifleItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack weapon = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResultHolder.success(weapon);
        AbstractHorse horse = player.getVehicle() instanceof AbstractHorse ridden
                && ((LaserMount) ridden).universegate$hasLaserHarness()
                && player.getUUID().equals(ridden.getOwnerUUID()) ? ridden : null;
        boolean mounted = horse != null;
        int cost = mounted ? 2_000 : 1_000;
        if (!consumeBatteryEnergy(player, cost)) {
            player.displayClientMessage(Component.translatable("message.universegate.laser_no_energy", cost), true);
            return InteractionResultHolder.fail(weapon);
        }

        HitResult hit = aim(player, mounted ? 96.0D : 64.0D);
        Vec3 start = player.getEyePosition();
        Vec3 end = hit.getLocation();
        if (hit instanceof EntityHitResult entityHit && canHit(player, entityHit.getEntity())) {
            DamageSource damage = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(LASER_DAMAGE), player, player);
            entityHit.getEntity().hurt(damage, mounted ? 40.0F : 18.0F);
        }
        if (mounted && hit.getType() != HitResult.Type.MISS && start.distanceToSqr(end) > 16.0D
                && (!(hit instanceof EntityHitResult entityHit) || canHit(player, entityHit.getEntity()))) {
            level.explode(player, end.x, end.y, end.z, 2.0F, false, Level.ExplosionInteraction.TNT);
        }
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.35F, 1.0F, 0.15F), mounted ? 1.6F : 1.0F);
        if (horse != null) {
            Vec3 forward = Vec3.directionFromRotation(0, horse.getYRot());
            Vec3 side = forward.cross(new Vec3(0, 1, 0)).scale(0.6D);
            Vec3 muzzle = horse.position().add(0, 1.4D, 0).add(forward.scale(1.0D));
            drawBeam(serverLevel, dust, muzzle.add(side), end);
            drawBeam(serverLevel, dust, muzzle.subtract(side), end);
        } else {
            drawBeam(serverLevel, dust, start, end);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                mounted ? 1.4F : 0.8F, mounted ? 0.55F : 1.7F);
        player.getCooldowns().addCooldown(this, mounted ? 30 : 12);
        player.awardStat(Stats.ITEM_USED.get(this));
        if (horse != null) AcidTechnologyManager.wearLaserHorse(horse, player);
        return InteractionResultHolder.consume(weapon);
    }

    private static void drawBeam(ServerLevel serverLevel, DustParticleOptions dust, Vec3 start, Vec3 end) {
        int points = Math.max(1, (int) Math.ceil(start.distanceTo(end) * 2.0D));
        for (int step = 0; step <= points; step++) {
            Vec3 point = start.lerp(end, step / (double) points);
            serverLevel.sendParticles(dust, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    public static HitResult aim(Player player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        Vec3 end = start.add(direction.scale(range));
        HitResult blockHit = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, start, blockHit.getLocation(),
                player.getBoundingBox().expandTowards(direction.scale(range)).inflate(1.0D),
                entity -> entity instanceof LivingEntity && !entity.isSpectator() && entity.canBeHitByProjectile(),
                start.distanceToSqr(blockHit.getLocation()));
        return entityHit != null ? entityHit : blockHit;
    }

    public static boolean canHit(Player player, Entity target) {
        return !(target instanceof Player other)
                || player.getServer() != null && player.getServer().isPvpAllowed() && player.canHarmPlayer(other);
    }

    private static boolean consumeBatteryEnergy(Player player, int cost) {
        if (player.isCreative()) return true;
        int available = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            available += AcidBatteryItem.getStoredEnergy(player.getInventory().getItem(slot));
        }
        if (available < cost) return false;
        int remaining = cost;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack battery = player.getInventory().getItem(slot);
            int stored = AcidBatteryItem.getStoredEnergy(battery);
            int used = Math.min(stored, remaining);
            if (used > 0) {
                AcidBatteryItem.setStoredEnergy(battery, stored - used);
                remaining -= used;
            }
        }
        player.getInventory().setChanged();
        return true;
    }
}
