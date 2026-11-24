package fr.geomtech.universegate.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AcidRainStatePayload(boolean active, int warningTicks) implements CustomPacketPayload {

    public static final Type<AcidRainStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("universegate", "acid_rain_state")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AcidRainStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            AcidRainStatePayload::active,
            ByteBufCodecs.VAR_INT,
            AcidRainStatePayload::warningTicks,
            AcidRainStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
