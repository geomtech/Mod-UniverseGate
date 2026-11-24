package fr.geomtech.universegate.seamless.net;

import fr.geomtech.universegate.UniverseGate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record PortalViewSubscriptionPayload(
        BlockPos sourceCorePos,
        boolean subscribe
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PortalViewSubscriptionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_view_subscription"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortalViewSubscriptionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PortalViewSubscriptionPayload::sourceCorePos,
                    ByteBufCodecs.BOOL, PortalViewSubscriptionPayload::subscribe,
                    PortalViewSubscriptionPayload::new
            );

    public PortalViewSubscriptionPayload {
        Objects.requireNonNull(sourceCorePos, "sourceCorePos");
        sourceCorePos = sourceCorePos.immutable();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
