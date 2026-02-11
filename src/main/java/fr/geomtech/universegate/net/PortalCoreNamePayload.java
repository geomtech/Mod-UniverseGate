package fr.geomtech.universegate.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PortalCoreNamePayload(BlockPos corePos, String name) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PortalCoreNamePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("universegate", "portal_core_name"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortalCoreNamePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PortalCoreNamePayload::corePos,
                    ByteBufCodecs.STRING_UTF8, PortalCoreNamePayload::name,
                    PortalCoreNamePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
