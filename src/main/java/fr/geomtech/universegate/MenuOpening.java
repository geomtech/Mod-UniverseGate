package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.server.level.ServerPlayer;

public final class MenuOpening {
    private MenuOpening() {}

    public static void openAt(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        player.openMenu(provider, buffer -> BlockPos.STREAM_CODEC.encode(buffer, pos));
    }
}
