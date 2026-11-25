package fr.geomtech.universegate.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record PortalActivatorTargetPayload(BlockPos activatorPos,
                                           @Nullable UUID targetPortalId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PortalActivatorTargetPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("universegate", "portal_activator_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortalActivatorTargetPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buffer, payload.activatorPos());
                        buffer.writeBoolean(payload.targetPortalId() != null);
                        if (payload.targetPortalId() != null) {
                            buffer.writeLong(payload.targetPortalId().getMostSignificantBits());
                            buffer.writeLong(payload.targetPortalId().getLeastSignificantBits());
                        }
                    },
                    buffer -> new PortalActivatorTargetPayload(
                            BlockPos.STREAM_CODEC.decode(buffer),
                            buffer.readBoolean()
                                    ? new UUID(buffer.readLong(), buffer.readLong())
                                    : null
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
