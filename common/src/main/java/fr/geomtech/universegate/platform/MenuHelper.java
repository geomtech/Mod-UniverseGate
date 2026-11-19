package fr.geomtech.universegate.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;

public final class MenuHelper {

    private MenuHelper() {}

    /** Opens {@code provider} for {@code player}, sending the screen data required by {@link PosMenuProvider}. */
    public static void open(Player player, MenuProvider provider) {
        if (!(player instanceof ServerPlayer serverPlayer) || provider == null) return;
        if (provider instanceof PosMenuProvider posProvider) {
            Services.PLATFORM.openPosMenu(serverPlayer, posProvider, posProvider.getScreenOpeningData(serverPlayer));
        } else {
            serverPlayer.openMenu(provider);
        }
    }
}
