package fr.geomtech.universegate.seamless.net;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record ResolvedPortalEndpoint(
        ResourceKey<Level> targetDim,
        BlockPos targetCorePos,
        Direction targetRight,
        Direction exitNormal,
        boolean outboundEnabled,
        byte stateFlags
) {

    public ResolvedPortalEndpoint {
        Objects.requireNonNull(targetDim, "targetDim");
        Objects.requireNonNull(targetCorePos, "targetCorePos");
        Objects.requireNonNull(targetRight, "targetRight");
        Objects.requireNonNull(exitNormal, "exitNormal");
        targetCorePos = targetCorePos.immutable();
    }

    public PortalRemoteEndpointPayload toPayload(BlockPos sourceCorePos) {
        Objects.requireNonNull(sourceCorePos, "sourceCorePos");
        return new PortalRemoteEndpointPayload(
                sourceCorePos.immutable(),
                targetDim,
                targetCorePos,
                targetRight,
                exitNormal,
                outboundEnabled,
                stateFlags
        );
    }
}
