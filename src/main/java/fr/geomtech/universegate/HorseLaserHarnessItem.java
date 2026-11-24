package fr.geomtech.universegate;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class HorseLaserHarnessItem extends Item {
    public HorseLaserHarnessItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Horse || target instanceof SkeletonHorse)) return InteractionResult.PASS;
        AbstractHorse horse = (AbstractHorse) target;
        if (!horse.isTamed() || horse.isBaby()
                || horse.getOwnerUUID() != null && !player.getUUID().equals(horse.getOwnerUUID())) {
            if (!player.level().isClientSide) player.displayClientMessage(Component.translatable("message.universegate.horse_not_owned"), true);
            return InteractionResult.FAIL;
        }
        LaserMount mount = (LaserMount) horse;
        if (mount.universegate$hasLaserHarness()) return InteractionResult.PASS;
        if (!player.level().isClientSide) {
            mount.universegate$setLaserHarness(true);
            horse.setOwnerUUID(player.getUUID());
            if (!player.isCreative()) stack.shrink(1);
            player.displayClientMessage(Component.translatable("message.universegate.horse_equipped"), true);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
