package fr.geomtech.universegate.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RenamePortalPayload(BlockPos corePos, String name) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RenamePortalPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("universegate", "rename_portal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenamePortalPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RenamePortalPayload::corePos,
                    ByteBufCodecs.STRING_UTF8, RenamePortalPayload::name,
                    RenamePortalPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
