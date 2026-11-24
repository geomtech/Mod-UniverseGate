package fr.geomtech.universegate;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class AcidExplosiveEntity extends ThrowableItemProjectile {

    private static final float EXPLOSION_POWER = 7.0F;
    private boolean exploded;

    public AcidExplosiveEntity(EntityType<? extends AcidExplosiveEntity> type, Level level) {
        super(type, level);
    }

    public AcidExplosiveEntity(Level level, LivingEntity owner) {
        super(ModEntityTypes.NITRO_ACID_EXPLOSIVE, owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.NITRO_ACID_EXPLOSIVE;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        explode();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= 100) {
            explode();
        }
    }

    private void explode() {
        if (exploded || level().isClientSide) return;
        exploded = true;
        level().explode(this, getX(), getY(), getZ(), EXPLOSION_POWER, false, Level.ExplosionInteraction.TNT);
        discard();
    }
}
