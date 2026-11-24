package fr.geomtech.universegate.seamless.net;

import fr.geomtech.universegate.PortalCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface PortalEndpointListener {

    void onEndpointUpdated(ServerLevel sourceLevel,
                           BlockPos sourceCorePos,
                           PortalCoreBlockEntity core,
                           ResolvedPortalEndpoint endpoint);

    void onEndpointClosed(ServerLevel sourceLevel,
                          BlockPos sourceCorePos,
                          PortalCoreBlockEntity core,
                          @Nullable ResolvedPortalEndpoint previousEndpoint);

    void onCoreUnloaded(ServerLevel sourceLevel,
                        BlockPos sourceCorePos,
                        PortalCoreBlockEntity core);

    default void onObserverUntracked(ServerLevel sourceLevel,
                                     BlockPos sourceCorePos,
                                     UUID playerId) {
    }
}
