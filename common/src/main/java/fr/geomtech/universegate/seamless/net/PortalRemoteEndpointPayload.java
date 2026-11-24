package fr.geomtech.universegate.seamless.net;

import fr.geomtech.universegate.UniverseGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record PortalRemoteEndpointPayload(
        BlockPos corePos,
        ResourceKey<Level> targetDim,
        BlockPos targetCorePos,
        Direction targetRight,
        Direction exitNormal,
        boolean outboundEnabled,
        byte stateFlags
) implements CustomPacketPayload {

    public static final byte FLAG_OPENING = 1 << 0;
    public static final byte FLAG_ACTIVE = 1 << 1;
    public static final byte FLAG_UNSTABLE = 1 << 2;
    public static final byte FLAG_CLOSED = 1 << 3;

    private static final StreamCodec<io.netty.buffer.ByteBuf, ResourceKey<Level>> DIMENSION_STREAM_CODEC =
            ResourceKey.streamCodec(Registries.DIMENSION);

    public static final CustomPacketPayload.Type<PortalRemoteEndpointPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_remote_endpoint"));

    // 1.21.1 StreamCodec.composite supports at most 6 fields; use StreamCodec.of for 7 fields.
    public static final StreamCodec<RegistryFriendlyByteBuf, PortalRemoteEndpointPayload> STREAM_CODEC =
            StreamCodec.of(PortalRemoteEndpointPayload::encode, PortalRemoteEndpointPayload::decode);

    public PortalRemoteEndpointPayload {
        Objects.requireNonNull(corePos, "corePos");
        Objects.requireNonNull(targetDim, "targetDim");
        Objects.requireNonNull(targetCorePos, "targetCorePos");
        Objects.requireNonNull(targetRight, "targetRight");
        Objects.requireNonNull(exitNormal, "exitNormal");
        corePos = corePos.immutable();
        targetCorePos = targetCorePos.immutable();
    }

    private static void encode(RegistryFriendlyByteBuf buf, PortalRemoteEndpointPayload payload) {
        BlockPos.STREAM_CODEC.encode(buf, payload.corePos());
        DIMENSION_STREAM_CODEC.encode(buf, payload.targetDim());
        BlockPos.STREAM_CODEC.encode(buf, payload.targetCorePos());
        Direction.STREAM_CODEC.encode(buf, payload.targetRight());
        Direction.STREAM_CODEC.encode(buf, payload.exitNormal());
        ByteBufCodecs.BOOL.encode(buf, payload.outboundEnabled());
        ByteBufCodecs.BYTE.encode(buf, payload.stateFlags());
    }

    private static PortalRemoteEndpointPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos corePos = BlockPos.STREAM_CODEC.decode(buf);
        ResourceKey<Level> targetDim = DIMENSION_STREAM_CODEC.decode(buf);
        BlockPos targetCorePos = BlockPos.STREAM_CODEC.decode(buf);
        Direction targetRight = Direction.STREAM_CODEC.decode(buf);
        Direction exitNormal = Direction.STREAM_CODEC.decode(buf);
        boolean outboundEnabled = ByteBufCodecs.BOOL.decode(buf);
        byte stateFlags = ByteBufCodecs.BYTE.decode(buf);
        return new PortalRemoteEndpointPayload(
                corePos,
                targetDim,
                targetCorePos,
                targetRight,
                exitNormal,
                outboundEnabled,
                stateFlags
        );
    }

    public boolean isOpening() {
        return (stateFlags & FLAG_OPENING) != 0;
    }

    public boolean isActive() {
        return (stateFlags & FLAG_ACTIVE) != 0;
    }

    public boolean isUnstable() {
        return (stateFlags & FLAG_UNSTABLE) != 0;
    }

    public boolean isClosed() {
        return (stateFlags & FLAG_CLOSED) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
