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
            StreamCodec.of(
                    (buf, info) -> {
                        UUID_STREAM_CODEC.encode(buf, info.id());
                        ByteBufCodecs.STRING_UTF8.encode(buf, info.name());
                        ResourceLocation.STREAM_CODEC.encode(buf, info.dimId());
                        BlockPos.STREAM_CODEC.encode(buf, info.corePos());
                        ByteBufCodecs.VAR_INT.encode(buf, info.openEnergyCost());
                    },
                    (buf) -> new PortalInfo(
                            UUID_STREAM_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ResourceLocation.STREAM_CODEC.decode(buf),
                            BlockPos.STREAM_CODEC.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)
                    )
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
