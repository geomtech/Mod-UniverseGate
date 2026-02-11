package fr.geomtech.universegate.net;

import fr.geomtech.universegate.PortalInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record PortalListPayload(BlockPos keyboardPos, List<PortalInfo> portals) implements CustomPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_STREAM_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    (buf) -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final CustomPacketPayload.Type<PortalListPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("universegate", "portal_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortalInfo> PORTAL_INFO_CODEC =
            StreamCodec.composite(
                    UUID_STREAM_CODEC, PortalInfo::id,
                    ByteBufCodecs.STRING_UTF8, PortalInfo::name,
                    ResourceLocation.STREAM_CODEC, PortalInfo::dimId,
                    BlockPos.STREAM_CODEC, PortalInfo::corePos,
                    PortalInfo::new
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, PortalListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PortalListPayload::keyboardPos,
                    PORTAL_INFO_CODEC.apply(ByteBufCodecs.list()), PortalListPayload::portals,
                    PortalListPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
