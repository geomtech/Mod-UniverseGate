package fr.geomtech.universegate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

public class PortalNaturalKeyboardBlockEntity extends PortalKeyboardBlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {

    public PortalNaturalKeyboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PORTAL_NATURAL_KEYBOARD, pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.universegate.portal_natural_keyboard");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv, Player player) {
        return new PortalNaturalKeyboardMenu(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.worldPosition;
    }

}
