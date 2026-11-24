package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

public class AcidCreeperEntity extends Creeper implements OwnableEntity {
    private UUID acidOwner;
    private BlockPos orderedPosition;
    private UUID orderedEntity;
    private int orderTicks;

    public AcidCreeperEntity(EntityType<? extends Creeper> type, Level level) { super(type, level); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CompanionGoal());
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override public @Nullable UUID getOwnerUUID() { return acidOwner; }
    public void setAcidOwner(UUID owner) { acidOwner = owner; }
    public boolean isOwnedBy(Player player) { return player.getUUID().equals(acidOwner); }

    public void orderAttack(BlockPos position, @Nullable Entity target) {
        if (isIgnited()) return;
        orderedPosition = position.immutable();
        orderedEntity = target != null ? target.getUUID() : null;
        orderTicks = 0;
    }

    public void followOwner() {
        if (isIgnited()) return;
        orderedPosition = null;
        orderedEntity = null;
        orderTicks = 0;
        getNavigation().stop();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && orderedPosition != null && !isIgnited() && ++orderTicks > 1200) followOwner();
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        return entity.getUUID().equals(acidOwner)
                || entity instanceof AcidCreeperEntity companion && acidOwner != null && acidOwner.equals(companion.acidOwner)
                || super.isAlliedTo(entity);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (acidOwner != null) tag.putUUID("AcidOwner", acidOwner);
        if (orderedPosition != null) {
            tag.putLong("AcidOrderPos", orderedPosition.asLong());
            tag.putInt("AcidOrderTicks", orderTicks);
        }
        if (orderedEntity != null) tag.putUUID("AcidOrderTarget", orderedEntity);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        acidOwner = tag.hasUUID("AcidOwner") ? tag.getUUID("AcidOwner") : null;
        orderedPosition = tag.contains("AcidOrderPos") ? BlockPos.of(tag.getLong("AcidOrderPos")) : null;
        orderedEntity = tag.hasUUID("AcidOrderTarget") ? tag.getUUID("AcidOrderTarget") : null;
        orderTicks = Math.max(0, tag.getInt("AcidOrderTicks"));
        if (acidOwner != null) setPersistenceRequired();
    }

    private final class CompanionGoal extends Goal {
        private CompanionGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override public boolean canUse() {
            if (isIgnited()) return false;
            Player owner = acidOwner != null ? level().getPlayerByUUID(acidOwner) : null;
            return orderedPosition != null || owner != null && !owner.isSpectator() && distanceToSqr(owner) > 16.0D;
        }

        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void stop() { getNavigation().stop(); }

        @Override public void tick() {
            if (orderedPosition != null && level() instanceof ServerLevel serverLevel) {
                Entity target = orderedEntity != null ? serverLevel.getEntity(orderedEntity) : null;
                if (orderedEntity != null && (target == null || !target.isAlive())) {
                    followOwner();
                    return;
                }
                Player owner = acidOwner != null ? serverLevel.getPlayerByUUID(acidOwner) : null;
                if (target instanceof Player && (owner == null || isAlliedTo(target) || !LaserRifleItem.canHit(owner, target))) {
                    followOwner();
                    return;
                }
                if (target != null) orderedPosition = target.blockPosition();
                if (distanceToSqr(orderedPosition.getX() + 0.5D, orderedPosition.getY(), orderedPosition.getZ() + 0.5D) < 6.25D
                        && (target == null || getSensing().hasLineOfSight(target))) {
                    ignite();
                    getNavigation().stop();
                } else if (tickCount % 10 == 0) {
                    getNavigation().moveTo(orderedPosition.getX() + 0.5D, orderedPosition.getY(), orderedPosition.getZ() + 0.5D, 1.25D);
                }
            } else if (acidOwner != null) {
                Player owner = level().getPlayerByUUID(acidOwner);
                if (owner != null && distanceToSqr(owner) < 96.0D * 96.0D && tickCount % 10 == 0) {
                    getNavigation().moveTo(owner, 1.1D);
                }
            }
        }
    }
}
