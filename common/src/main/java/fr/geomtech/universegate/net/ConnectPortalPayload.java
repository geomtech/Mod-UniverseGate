package fr.geomtech.universegate.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;


public record ConnectPortalPayload(BlockPos keyboardPos, UUID targetPortalId) implements CustomPacketPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_STREAM_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    (buf) -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final CustomPacketPayload.Type<ConnectPortalPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("universegate", "connect_portal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectPortalPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ConnectPortalPayload::keyboardPos,
                    UUID_STREAM_CODEC, ConnectPortalPayload::targetPortalId,
                    ConnectPortalPayload::new
            );


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
