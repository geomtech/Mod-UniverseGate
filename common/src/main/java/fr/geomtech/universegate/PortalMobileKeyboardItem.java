package fr.geomtech.universegate;

import fr.geomtech.universegate.net.UniverseGateNetwork;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PortalMobileKeyboardItem extends Item {

    private static final int CORE_SEARCH_RADIUS_XZ = 8;
    private static final int CORE_SEARCH_RADIUS_Y = 4;

    public PortalMobileKeyboardItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        BlockPos openingPos = player.blockPosition().immutable();
        if (findCoreNear(level, openingPos) == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.universegate.mobile_keyboard_no_portal"),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        serverPlayer.openMenu(new ExtendedScreenHandlerFactory<BlockPos>() {
            @Override
            public BlockPos getScreenOpeningData(ServerPlayer player) {
                return openingPos;
            }

            @Override
            public Component getDisplayName() {
                return Component.translatable("container.universegate.portal_mobile_keyboard");
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv, Player player) {
                return new PortalMobileKeyboardMenu(syncId, inv, openingPos);
            }
        });

        UniverseGateNetwork.sendPortalListToPlayer(serverPlayer, openingPos);
        UniverseGateNetwork.sendPortalKeyboardStatusToPlayer(serverPlayer, openingPos);
        return InteractionResultHolder.consume(stack);
    }

    private static BlockPos findCoreNear(Level level, BlockPos center) {
        for (int y = -CORE_SEARCH_RADIUS_Y; y <= CORE_SEARCH_RADIUS_Y; y++) {
            for (int x = -CORE_SEARCH_RADIUS_XZ; x <= CORE_SEARCH_RADIUS_XZ; x++) {
                for (int z = -CORE_SEARCH_RADIUS_XZ; z <= CORE_SEARCH_RADIUS_XZ; z++) {
                    BlockPos p = center.offset(x, y, z);
                    if (!level.hasChunkAt(p)) continue;
                    if (level.getBlockState(p).is(ModBlocks.PORTAL_CORE)) return p.immutable();
                }
            }
        }
        return null;
    }
}
