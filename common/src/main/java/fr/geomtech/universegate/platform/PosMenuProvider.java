package fr.geomtech.universegate.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

/**
 * A {@link MenuProvider} whose menu needs a {@link BlockPos} on the client side.
 * Open it with {@link MenuHelper#open}.
 */
public interface PosMenuProvider extends MenuProvider {
    BlockPos getScreenOpeningData(ServerPlayer player);
}
