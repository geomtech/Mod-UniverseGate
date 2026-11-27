package fr.geomtech.universegate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** One sixth of the 3D portal ring, placed around a portal core (see {@link PortalRingSegments}). */
public class PortalRingSegmentItem extends Item {

    public PortalRingSegmentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        return tryPlace(context.getLevel(), player, context.getItemInHand())
                ? (context.getLevel().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME)
                : InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return PortalRingSegments.find(level, player) != null
                    ? InteractionResultHolder.success(stack) : InteractionResultHolder.pass(stack);
        }
        return PortalRingSegments.place((ServerLevel) level, player, stack)
                ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    /** Clicking a block only places a segment when a ring is aimed at; otherwise {@link #use} reports why. */
    private static boolean tryPlace(Level level, Player player, ItemStack stack) {
        if (PortalRingSegments.find(level, player) == null) return false;
        return level.isClientSide() || PortalRingSegments.place((ServerLevel) level, player, stack);
    }
}
