package fr.geomtech.universegate.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Éclatement du champ d'un portail qui se ferme : un seul paquet pour tout le portail,
 * le client génère les étincelles de chaque case (voir PortalFieldCollapseParticles).
 */
public record PortalFieldCollapsePayload(List<BlockPos> cells, boolean xAxis, boolean unstable)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PortalFieldCollapsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("universegate", "portal_field_collapse"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortalFieldCollapsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(64)), PortalFieldCollapsePayload::cells,
                    ByteBufCodecs.BOOL, PortalFieldCollapsePayload::xAxis,
                    ByteBufCodecs.BOOL, PortalFieldCollapsePayload::unstable,
                    PortalFieldCollapsePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
