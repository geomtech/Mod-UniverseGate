package fr.geomtech.universegate.seamless.net;

import fr.geomtech.universegate.UniverseGate;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.Objects;

public record RemoteChunkDataPayload(
        ResourceKey<Level> dim,
        int chunkX,
        int chunkZ,
        byte[] rawChunkWithLightPacketBytes
) implements CustomPacketPayload {

    public static final int MAX_PACKET_BYTES = 4 * 1024 * 1024;

    public static final CustomPacketPayload.Type<RemoteChunkDataPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "remote_chunk_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteChunkDataPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceKey.streamCodec(Registries.DIMENSION), RemoteChunkDataPayload::dim,
                    ByteBufCodecs.INT, RemoteChunkDataPayload::chunkX,
                    ByteBufCodecs.INT, RemoteChunkDataPayload::chunkZ,
                    ByteBufCodecs.byteArray(MAX_PACKET_BYTES), RemoteChunkDataPayload::rawChunkWithLightPacketBytes,
                    RemoteChunkDataPayload::new
            );

    public RemoteChunkDataPayload {
        Objects.requireNonNull(dim, "dim");
        Objects.requireNonNull(rawChunkWithLightPacketBytes, "rawChunkWithLightPacketBytes");
        if (rawChunkWithLightPacketBytes.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException(
                    "Chunk payload size " + rawChunkWithLightPacketBytes.length + " exceeds MAX_PACKET_BYTES (" + MAX_PACKET_BYTES + ")"
            );
        }
        rawChunkWithLightPacketBytes = rawChunkWithLightPacketBytes.clone();
    }

    @Override
    public byte[] rawChunkWithLightPacketBytes() {
        return rawChunkWithLightPacketBytes.clone();
    }

    public byte[] copyBytes() {
        return rawChunkWithLightPacketBytes.clone();
    }

    public static RemoteChunkDataPayload fromPacket(ResourceKey<Level> dim,
                                                    RegistryAccess registryAccess,
                                                    ClientboundLevelChunkWithLightPacket packet) {
        Objects.requireNonNull(dim, "dim");
        Objects.requireNonNull(registryAccess, "registryAccess");
        Objects.requireNonNull(packet, "packet");

        ByteBuf raw = Unpooled.buffer();
        try {
            RegistryFriendlyByteBuf friendly = new RegistryFriendlyByteBuf(raw, registryAccess);
            ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(friendly, packet);
            byte[] encoded = new byte[friendly.readableBytes()];
            friendly.readBytes(encoded);
            return new RemoteChunkDataPayload(dim, packet.getX(), packet.getZ(), encoded);
        } finally {
            raw.release();
        }
    }

    public ClientboundLevelChunkWithLightPacket decodePacket(RegistryAccess registryAccess) {
        Objects.requireNonNull(registryAccess, "registryAccess");

        ByteBuf raw = Unpooled.wrappedBuffer(this.rawChunkWithLightPacketBytes);
        try {
            RegistryFriendlyByteBuf friendly = new RegistryFriendlyByteBuf(raw, registryAccess);
            return ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(friendly);
        } finally {
            raw.release();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RemoteChunkDataPayload other)) return false;
        return chunkX == other.chunkX
                && chunkZ == other.chunkZ
                && dim.equals(other.dim)
                && Arrays.equals(rawChunkWithLightPacketBytes, other.rawChunkWithLightPacketBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(dim, chunkX, chunkZ);
        result = 31 * result + Arrays.hashCode(rawChunkWithLightPacketBytes);
        return result;
    }

    @Override
    public String toString() {
        return "RemoteChunkDataPayload[dim=" + dim
                + ", chunkX=" + chunkX
                + ", chunkZ=" + chunkZ
                + ", byteLength=" + rawChunkWithLightPacketBytes.length + "]";
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
