package fr.geomtech.universegate.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PortalKeyboardStatusPayload(BlockPos keyboardPos, boolean active) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PortalKeyboardStatusPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("universegate", "portal_keyboard_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortalKeyboardStatusPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PortalKeyboardStatusPayload::keyboardPos,
                    ByteBufCodecs.BOOL, PortalKeyboardStatusPayload::active,
                    PortalKeyboardStatusPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
