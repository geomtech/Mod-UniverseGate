package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

public class CreeperCommanderItem extends Item {
    public CreeperCommanderItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        List<AcidCreeperEntity> companions = level.getEntitiesOfClass(AcidCreeperEntity.class,
                player.getBoundingBox().inflate(32.0D), creeper -> creeper.isOwnedBy(player) && creeper.isAlive() && !creeper.isIgnited());
        if (companions.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.universegate.no_acid_creepers"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (player.isShiftKeyDown()) {
            companions.forEach(AcidCreeperEntity::followOwner);
            player.displayClientMessage(Component.translatable("message.universegate.creepers_recalled", companions.size()), true);
        } else {
            HitResult hit = LaserRifleItem.aim(player, 48.0D);
            Entity target = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
            if (hit.getType() == HitResult.Type.MISS || target != null && (!LaserRifleItem.canHit(player, target)
                    || companions.stream().anyMatch(creeper -> creeper.isAlliedTo(target)))) {
                return InteractionResultHolder.fail(stack);
            }
            BlockPos position = BlockPos.containing(hit.getLocation());
            companions.forEach(creeper -> creeper.orderAttack(position, target));
            player.displayClientMessage(Component.translatable("message.universegate.creepers_ordered", companions.size()), true);
        }
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.consume(stack);
    }
}
