package fr.geomtech.universegate.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DisconnectPortalPayload(BlockPos keyboardPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DisconnectPortalPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("universegate", "disconnect_portal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DisconnectPortalPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DisconnectPortalPayload::keyboardPos,
                    DisconnectPortalPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
