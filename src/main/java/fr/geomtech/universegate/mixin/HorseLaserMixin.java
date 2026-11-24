package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.LaserMount;
import fr.geomtech.universegate.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractHorse.class)
public abstract class HorseLaserMixin implements LaserMount {
    @Unique
    private static final EntityDataAccessor<Boolean> UNIVERSEGATE_LASER_HARNESS =
            SynchedEntityData.defineId(AbstractHorse.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final EntityDataAccessor<Integer> UNIVERSEGATE_LASER_SHOTS =
            SynchedEntityData.defineId(AbstractHorse.class, EntityDataSerializers.INT);

    @Shadow protected SimpleContainer inventory;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void universegate$defineLaserData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(UNIVERSEGATE_LASER_HARNESS, false);
        builder.define(UNIVERSEGATE_LASER_SHOTS, 0);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void universegate$saveLaserData(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("UniverseGateLaserHarness", universegate$hasLaserHarness());
        tag.putInt("UniverseGateLaserShots", universegate$getLaserShots());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void universegate$loadLaserData(CompoundTag tag, CallbackInfo ci) {
        universegate$setLaserHarness(tag.getBoolean("UniverseGateLaserHarness"));
        universegate$setLaserShots(tag.getInt("UniverseGateLaserShots"));
    }

    @Inject(method = "dropEquipment", at = @At("TAIL"))
    private void universegate$dropLaserHarness(CallbackInfo ci) {
        AbstractHorse horse = (AbstractHorse) (Object) this;
        if (!horse.level().isClientSide && universegate$hasLaserHarness()) {
            horse.spawnAtLocation(new ItemStack(ModItems.HORSE_LASER_HARNESS));
            universegate$setLaserHarness(false);
        }
    }

    @Override public boolean universegate$hasLaserHarness() {
        return ((AbstractHorse) (Object) this).getEntityData().get(UNIVERSEGATE_LASER_HARNESS);
    }

    @Override public void universegate$setLaserHarness(boolean equipped) {
        ((AbstractHorse) (Object) this).getEntityData().set(UNIVERSEGATE_LASER_HARNESS, equipped);
    }

    @Override public int universegate$getLaserShots() {
        return ((AbstractHorse) (Object) this).getEntityData().get(UNIVERSEGATE_LASER_SHOTS);
    }

    @Override public void universegate$setLaserShots(int shots) {
        ((AbstractHorse) (Object) this).getEntityData().set(UNIVERSEGATE_LASER_SHOTS, Math.max(0, Math.min(24, shots)));
    }

    @Override public ItemStack universegate$getSaddle() {
        return inventory.getItem(AbstractHorse.INV_SLOT_SADDLE).copy();
    }
}
