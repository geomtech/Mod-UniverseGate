package fr.geomtech.universegate.compat;

import net.minecraft.world.InteractionResult;

/**
 * Stand-in for {@code net.minecraft.world.ItemInteractionResult}, removed in 1.21.2 when
 * {@code useItemOn} started returning {@link InteractionResult}. Only compiled for 1.21.11:
 * Stonecutter swaps the import, so the 1.21.1 code keeps using the vanilla enum.
 */
public final class ItemInteractionResult {
    public static final InteractionResult SUCCESS = InteractionResult.SUCCESS;
    public static final InteractionResult CONSUME = InteractionResult.CONSUME;
    public static final InteractionResult CONSUME_PARTIAL = InteractionResult.CONSUME;
    public static final InteractionResult PASS_TO_DEFAULT_BLOCK_INTERACTION = InteractionResult.TRY_WITH_EMPTY_HAND;
    public static final InteractionResult SKIP_DEFAULT_BLOCK_INTERACTION = InteractionResult.PASS;
    public static final InteractionResult FAIL = InteractionResult.FAIL;

    private ItemInteractionResult() {
    }

    /** Same as 1.21.1: the client swings the hand, the server just consumes the interaction. */
    public static InteractionResult sidedSuccess(boolean isClientSide) {
        return isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }
}
