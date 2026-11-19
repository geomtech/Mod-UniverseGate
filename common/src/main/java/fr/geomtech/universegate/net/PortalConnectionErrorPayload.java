package fr.geomtech.universegate.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


public record PortalConnectionErrorPayload(String errorMessage) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PortalConnectionErrorPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("universegate", "portal_connection_error"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortalConnectionErrorPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PortalConnectionErrorPayload::errorMessage,
                    PortalConnectionErrorPayload::new
            );


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
