package fr.geomtech.universegate.compat;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Stand-in for {@code net.minecraft.world.InteractionResultHolder}, removed in 1.21.2 when
 * {@code Item.use} started returning {@link InteractionResult}. Only compiled for 1.21.11:
 * Stonecutter swaps the import, so the 1.21.1 code keeps using the vanilla class.
 */
public final class InteractionResultHolder {
    private InteractionResultHolder() {
    }

    public static InteractionResult success(ItemStack stack) {
        return InteractionResult.SUCCESS.heldItemTransformedTo(stack);
    }

    public static InteractionResult consume(ItemStack stack) {
        return InteractionResult.CONSUME.heldItemTransformedTo(stack);
    }

    public static InteractionResult sidedSuccess(ItemStack stack, boolean isClientSide) {
        return (isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME).heldItemTransformedTo(stack);
    }

    public static InteractionResult pass(ItemStack stack) {
        return InteractionResult.PASS;
    }

    public static InteractionResult fail(ItemStack stack) {
        return InteractionResult.FAIL;
    }
}
