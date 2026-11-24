package fr.geomtech.universegate.seamless.net;

import fr.geomtech.universegate.UniverseGate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record RemoteChunkForgetPayload(
        ResourceKey<Level> dim,
        int chunkX,
        int chunkZ
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RemoteChunkForgetPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "remote_chunk_forget"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteChunkForgetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceKey.streamCodec(Registries.DIMENSION), RemoteChunkForgetPayload::dim,
                    ByteBufCodecs.INT, RemoteChunkForgetPayload::chunkX,
                    ByteBufCodecs.INT, RemoteChunkForgetPayload::chunkZ,
                    RemoteChunkForgetPayload::new
            );

    public RemoteChunkForgetPayload {
        Objects.requireNonNull(dim, "dim");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
