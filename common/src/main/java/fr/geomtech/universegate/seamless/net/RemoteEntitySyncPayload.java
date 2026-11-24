package fr.geomtech.universegate.seamless.net;

import fr.geomtech.universegate.UniverseGate;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
//? if >=1.21.11 {
/*import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
*///?}
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public record RemoteEntitySyncPayload(
        ResourceKey<Level> dim,
        int entityId,
        SyncKind kind,
        byte[] encodedVanillaPacket
) implements CustomPacketPayload {

    public static final int MAX_PACKET_BYTES = 1024 * 1024;

    public static final CustomPacketPayload.Type<RemoteEntitySyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "remote_entity_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteEntitySyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceKey.streamCodec(Registries.DIMENSION), RemoteEntitySyncPayload::dim,
                    ByteBufCodecs.VAR_INT, RemoteEntitySyncPayload::entityId,
                    SyncKind.STREAM_CODEC, RemoteEntitySyncPayload::kind,
                    ByteBufCodecs.byteArray(MAX_PACKET_BYTES), RemoteEntitySyncPayload::encodedVanillaPacket,
                    RemoteEntitySyncPayload::new
            );

    public enum SyncKind {
        ADD_ENTITY,
        SET_DATA,
        UPDATE_ATTRIBUTES,
        SET_EQUIPMENT,
        MOVE_POS,
        MOVE_POS_ROT,
        MOVE_ROT,
        TELEPORT_ENTITY,
        ENTITY_POSITION_SYNC,
        SET_MOTION,
        ROTATE_HEAD,
        REMOVE_ENTITIES,
        SET_PASSENGERS,
        SET_ENTITY_LINK,
        PROJECTILE_POWER,
        MOVE_MINECART;

        private static final SyncKind[] VALUES = values();

        public static final StreamCodec<ByteBuf, SyncKind> STREAM_CODEC =
                StreamCodec.of(
                        (buf, value) -> ByteBufCodecs.VAR_INT.encode(buf, value.ordinal()),
                        buf -> fromOrdinal(ByteBufCodecs.VAR_INT.decode(buf))
                );

        public static SyncKind fromOrdinal(int ordinal) {
            if (ordinal < 0 || ordinal >= VALUES.length) {
                throw new DecoderException("Invalid SyncKind ordinal: " + ordinal);
            }
            return VALUES[ordinal];
        }
    }

    public RemoteEntitySyncPayload {
        Objects.requireNonNull(dim, "dim");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(encodedVanillaPacket, "encodedVanillaPacket");
        if (encodedVanillaPacket.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException(
                    "Entity sync payload size " + encodedVanillaPacket.length + " exceeds MAX_PACKET_BYTES (" + MAX_PACKET_BYTES + ")"
            );
        }
        encodedVanillaPacket = encodedVanillaPacket.clone();
    }

    @Override
    public byte[] encodedVanillaPacket() {
        return encodedVanillaPacket.clone();
    }

    public byte[] copyBytes() {
        return encodedVanillaPacket.clone();
    }

    public static Optional<SyncKind> kindForPacket(Packet<?> packet) {
        if (packet instanceof ClientboundAddEntityPacket) return Optional.of(SyncKind.ADD_ENTITY);
        if (packet instanceof ClientboundSetEntityDataPacket) return Optional.of(SyncKind.SET_DATA);
        if (packet instanceof ClientboundUpdateAttributesPacket) return Optional.of(SyncKind.UPDATE_ATTRIBUTES);
        if (packet instanceof ClientboundSetEquipmentPacket) return Optional.of(SyncKind.SET_EQUIPMENT);
        if (packet instanceof ClientboundMoveEntityPacket.Pos) return Optional.of(SyncKind.MOVE_POS);
        if (packet instanceof ClientboundMoveEntityPacket.PosRot) return Optional.of(SyncKind.MOVE_POS_ROT);
        if (packet instanceof ClientboundMoveEntityPacket.Rot) return Optional.of(SyncKind.MOVE_ROT);
        if (packet instanceof ClientboundTeleportEntityPacket) return Optional.of(SyncKind.TELEPORT_ENTITY);
        if (packet instanceof ClientboundSetEntityMotionPacket) return Optional.of(SyncKind.SET_MOTION);
        if (packet instanceof ClientboundRotateHeadPacket) return Optional.of(SyncKind.ROTATE_HEAD);
        if (packet instanceof ClientboundRemoveEntitiesPacket) return Optional.of(SyncKind.REMOVE_ENTITIES);
        if (packet instanceof ClientboundSetPassengersPacket) return Optional.of(SyncKind.SET_PASSENGERS);
        if (packet instanceof ClientboundSetEntityLinkPacket) return Optional.of(SyncKind.SET_ENTITY_LINK);
        if (packet instanceof ClientboundProjectilePowerPacket) return Optional.of(SyncKind.PROJECTILE_POWER);
        //? if >=1.21.11 {
        /*if (packet instanceof ClientboundEntityPositionSyncPacket) return Optional.of(SyncKind.ENTITY_POSITION_SYNC);
        if (packet instanceof ClientboundMoveMinecartPacket) return Optional.of(SyncKind.MOVE_MINECART);
        *///?}
        return Optional.empty();
    }

    public static RemoteEntitySyncPayload fromPacket(ResourceKey<Level> dim,
                                                     int entityId,
                                                     SyncKind kind,
                                                     RegistryAccess registryAccess,
                                                     Packet<?> packet) {
        byte[] encoded = encodeVanillaPacket(registryAccess, kind, packet);
        return new RemoteEntitySyncPayload(dim, entityId, kind, encoded);
    }

    public static byte[] encodeVanillaPacket(RegistryAccess registryAccess, SyncKind kind, Packet<?> packet) {
        Objects.requireNonNull(registryAccess, "registryAccess");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(packet, "packet");

        return switch (kind) {
            case ADD_ENTITY -> encodeTyped(registryAccess, ClientboundAddEntityPacket.STREAM_CODEC,
                    requireType(kind, packet, ClientboundAddEntityPacket.class));
            case SET_DATA -> encodeTyped(registryAccess, ClientboundSetEntityDataPacket.STREAM_CODEC,
                    requireType(kind, packet, ClientboundSetEntityDataPacket.class));
            case UPDATE_ATTRIBUTES -> encodeTyped(registryAccess, ClientboundUpdateAttributesPacket.STREAM_CODEC,
                    requireType(kind, packet, ClientboundUpdateAttributesPacket.class));
            case SET_EQUIPMENT -> encodeTyped(registryAccess, ClientboundSetEquipmentPacket.STREAM_CODEC,
                    requireType(kind, packet, ClientboundSetEquipmentPacket.class));
            case MOVE_POS -> encodeTyped(registryAccess, ClientboundMoveEntityPacket.Pos.STREAM_CODEC,
                    requireType(kind, packet, ClientboundMoveEntityPacket.Pos.class));
            case MOVE_POS_ROT -> encodeTyped(registryAccess, ClientboundMoveEntityPacket.PosRot.STREAM_CODEC,
                    requireType(kind, packet, ClientboundMoveEntityPacket.PosRot.class));
            case MOVE_ROT -> encodeTyped(registryAccess, ClientboundMoveEntityPacket.Rot.STREAM_CODEC,
                    requireType(kind, packet, ClientboundMoveEntityPacket.Rot.class));
            case TELEPORT_ENTITY -> encodeTyped(registryAccess, ClientboundTeleportEntityPacket.STREAM_CODEC,
                    requireType(kind, packet, ClientboundTeleportEntityPacket.class));
            case ENTITY_POSITION_SYNC -> {
                //? if >=1.21.11 {
                /*yield encodeTyped(registryAccess, ClientboundEntityPositionSyncPacket.STREAM_CODEC,
                        requireType(kind, packet, ClientboundEntityPositionSyncPacket.class));
                *///?} else {
                throw new UnsupportedOperationException("ENTITY_POSITION_SYNC is not supported on Minecraft 1.21.1");
                //?}
            }
            case SET_MOTION -> encodeTyped(registryAccess, ClientboundSetEntityMotionPacket.STREAM_CODEC,
                    requireType(kind, packet, ClientboundSetEntityMotionPacket.class));
            case ROTATE_HEAD -> encodeTyped(registryAccess, ClientboundRotateHeadPacket.STREAM_CODEC,
                    requireType(kind, packet, ClientboundRotateHeadPacket.class));
            case REMOVE_ENTITIES -> encodeTyped(registryAccess, ClientboundRemoveEntitiesPacket.STREAM_CODEC,
                    requireType(kind, packet, ClientboundRemoveEntitiesPacket.class));
            case SET_PASSENGERS -> encodeTyped(registryAccess, ClientboundSetPassengersPacket.STREAM_CODEC,
                    requireType(kind, packet, ClientboundSetPassengersPacket.class));
            case SET_ENTITY_LINK -> encodeTyped(registryAccess, ClientboundSetEntityLinkPacket.STREAM_CODEC,
                    requireType(kind, packet, ClientboundSetEntityLinkPacket.class));
            case PROJECTILE_POWER -> encodeTyped(registryAccess, ClientboundProjectilePowerPacket.STREAM_CODEC,
                    requireType(kind, packet, ClientboundProjectilePowerPacket.class));
            case MOVE_MINECART -> {
                //? if >=1.21.11 {
                /*yield encodeTyped(registryAccess, ClientboundMoveMinecartPacket.STREAM_CODEC,
                        requireType(kind, packet, ClientboundMoveMinecartPacket.class));
                *///?} else {
                throw new UnsupportedOperationException("MOVE_MINECART is not supported on Minecraft 1.21.1");
                //?}
            }
        };
    }

    public Packet<?> decodeVanillaPacket(RegistryAccess registryAccess) {
        Objects.requireNonNull(registryAccess, "registryAccess");

        return switch (kind) {
            case ADD_ENTITY -> decodeTyped(registryAccess, ClientboundAddEntityPacket.STREAM_CODEC);
            case SET_DATA -> decodeTyped(registryAccess, ClientboundSetEntityDataPacket.STREAM_CODEC);
            case UPDATE_ATTRIBUTES -> decodeTyped(registryAccess, ClientboundUpdateAttributesPacket.STREAM_CODEC);
            case SET_EQUIPMENT -> decodeTyped(registryAccess, ClientboundSetEquipmentPacket.STREAM_CODEC);
            case MOVE_POS -> decodeTyped(registryAccess, ClientboundMoveEntityPacket.Pos.STREAM_CODEC);
            case MOVE_POS_ROT -> decodeTyped(registryAccess, ClientboundMoveEntityPacket.PosRot.STREAM_CODEC);
            case MOVE_ROT -> decodeTyped(registryAccess, ClientboundMoveEntityPacket.Rot.STREAM_CODEC);
            case TELEPORT_ENTITY -> decodeTyped(registryAccess, ClientboundTeleportEntityPacket.STREAM_CODEC);
            case ENTITY_POSITION_SYNC -> {
                //? if >=1.21.11 {
                /*yield decodeTyped(registryAccess, ClientboundEntityPositionSyncPacket.STREAM_CODEC);
                *///?} else {
                throw new UnsupportedOperationException("ENTITY_POSITION_SYNC is not supported on Minecraft 1.21.1");
                //?}
            }
            case SET_MOTION -> decodeTyped(registryAccess, ClientboundSetEntityMotionPacket.STREAM_CODEC);
            case ROTATE_HEAD -> decodeTyped(registryAccess, ClientboundRotateHeadPacket.STREAM_CODEC);
            case REMOVE_ENTITIES -> decodeTyped(registryAccess, ClientboundRemoveEntitiesPacket.STREAM_CODEC);
            case SET_PASSENGERS -> decodeTyped(registryAccess, ClientboundSetPassengersPacket.STREAM_CODEC);
            case SET_ENTITY_LINK -> decodeTyped(registryAccess, ClientboundSetEntityLinkPacket.STREAM_CODEC);
            case PROJECTILE_POWER -> decodeTyped(registryAccess, ClientboundProjectilePowerPacket.STREAM_CODEC);
            case MOVE_MINECART -> {
                //? if >=1.21.11 {
                /*yield decodeTyped(registryAccess, ClientboundMoveMinecartPacket.STREAM_CODEC);
                *///?} else {
                throw new UnsupportedOperationException("MOVE_MINECART is not supported on Minecraft 1.21.1");
                //?}
            }
        };
    }

    private static <P extends Packet<?>> P requireType(SyncKind kind, Packet<?> packet, Class<P> expectedClass) {
        if (!expectedClass.isInstance(packet)) {
            throw new IllegalArgumentException(
                    "Packet " + packet.getClass().getName() + " does not match SyncKind." + kind.name()
            );
        }
        return expectedClass.cast(packet);
    }

    private static <P extends Packet<?>> byte[] encodeTyped(RegistryAccess registryAccess,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, P> codec,
                                                            P packet) {
        ByteBuf raw = Unpooled.buffer();
        try {
            RegistryFriendlyByteBuf friendly = new RegistryFriendlyByteBuf(raw, registryAccess);
            codec.encode(friendly, packet);
            byte[] bytes = new byte[friendly.readableBytes()];
            friendly.readBytes(bytes);
            if (bytes.length > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException(
                        "Encoded entity sync packet size " + bytes.length + " exceeds MAX_PACKET_BYTES (" + MAX_PACKET_BYTES + ")"
                );
            }
            return bytes;
        } finally {
            raw.release();
        }
    }

    private <P extends Packet<?>> P decodeTyped(RegistryAccess registryAccess,
                                                StreamCodec<? super RegistryFriendlyByteBuf, P> codec) {
        ByteBuf raw = Unpooled.wrappedBuffer(this.encodedVanillaPacket);
        try {
            RegistryFriendlyByteBuf friendly = new RegistryFriendlyByteBuf(raw, registryAccess);
            return codec.decode(friendly);
        } finally {
            raw.release();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RemoteEntitySyncPayload other)) return false;
        return entityId == other.entityId
                && kind == other.kind
                && dim.equals(other.dim)
                && Arrays.equals(encodedVanillaPacket, other.encodedVanillaPacket);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(dim, entityId, kind);
        result = 31 * result + Arrays.hashCode(encodedVanillaPacket);
        return result;
    }

    @Override
    public String toString() {
        return "RemoteEntitySyncPayload[dim=" + dim
                + ", entityId=" + entityId
                + ", kind=" + kind
                + ", byteLength=" + encodedVanillaPacket.length + "]";
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
