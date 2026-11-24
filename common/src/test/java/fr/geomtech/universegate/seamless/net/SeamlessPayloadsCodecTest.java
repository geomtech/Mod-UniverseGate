package fr.geomtech.universegate.seamless.net;

import com.mojang.datafixers.util.Pair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
//? if >=1.21.11 {
/*import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.PositionMoveRotation;
*///?}
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeamlessPayloadsCodecTest {

    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static <T> T roundtrip(StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T value) {
        ByteBuf raw = Unpooled.buffer();
        try {
            RegistryFriendlyByteBuf out = new RegistryFriendlyByteBuf(raw, registries);
            codec.encode(out, value);
            RegistryFriendlyByteBuf in = new RegistryFriendlyByteBuf(raw, registries);
            T decoded = codec.decode(in);
            assertEquals(0, raw.readableBytes(), "Codec should consume all encoded bytes");
            return decoded;
        } finally {
            raw.release();
        }
    }

    @Test
    void portalRemoteEndpointPayloadRoundtripsAndEvaluatesStateFlags() {
        ResourceKey<Level> riftDim = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("universegate", "rift")
        );
        byte flags = (byte) (PortalRemoteEndpointPayload.FLAG_ACTIVE | PortalRemoteEndpointPayload.FLAG_UNSTABLE);
        PortalRemoteEndpointPayload original = new PortalRemoteEndpointPayload(
                new BlockPos(-120, 64, 512),
                riftDim,
                new BlockPos(32, 70, -48),
                Direction.SOUTH,
                Direction.WEST,
                true,
                flags
        );

        PortalRemoteEndpointPayload decoded = roundtrip(PortalRemoteEndpointPayload.STREAM_CODEC, original);
        assertEquals(original, decoded);
        assertFalse(decoded.isOpening());
        assertTrue(decoded.isActive());
        assertTrue(decoded.isUnstable());
        assertFalse(decoded.isClosed());

        PortalRemoteEndpointPayload opening = new PortalRemoteEndpointPayload(
                BlockPos.ZERO,
                Level.NETHER,
                new BlockPos(1, 2, 3),
                Direction.EAST,
                Direction.NORTH,
                false,
                PortalRemoteEndpointPayload.FLAG_OPENING
        );
        assertTrue(opening.isOpening());
        assertFalse(opening.isActive());
        assertFalse(opening.isUnstable());
        assertFalse(opening.isClosed());

        PortalRemoteEndpointPayload closed = new PortalRemoteEndpointPayload(
                BlockPos.ZERO,
                Level.OVERWORLD,
                BlockPos.ZERO,
                Direction.EAST,
                Direction.SOUTH,
                false,
                PortalRemoteEndpointPayload.FLAG_CLOSED
        );
        assertFalse(closed.isOpening());
        assertFalse(closed.isActive());
        assertFalse(closed.isUnstable());
        assertTrue(closed.isClosed());
    }

    @Test
    void remoteChunkDataPayloadRoundtripsAndDefensivelyCopiesBytes() {
        byte[] source = new byte[]{1, 2, 3, 4, 5, (byte) 0xFF};
        RemoteChunkDataPayload payload = new RemoteChunkDataPayload(Level.NETHER, -7, 19, source);
        source[0] = 99;

        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, (byte) 0xFF}, payload.rawChunkWithLightPacketBytes());
        byte[] copied = payload.copyBytes();
        copied[1] = 88;
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, (byte) 0xFF}, payload.copyBytes());

        RemoteChunkDataPayload decoded = roundtrip(RemoteChunkDataPayload.STREAM_CODEC, payload);
        assertEquals(payload, decoded);
        assertNotSame(payload.rawChunkWithLightPacketBytes(), decoded.rawChunkWithLightPacketBytes());
    }

    @Test
    void remoteChunkDataPayloadEnforcesMaxBytesLimit() {
        byte[] oversized = new byte[RemoteChunkDataPayload.MAX_PACKET_BYTES + 1];
        assertThrows(
                IllegalArgumentException.class,
                () -> new RemoteChunkDataPayload(Level.OVERWORLD, 0, 0, oversized)
        );

        ByteBuf raw = Unpooled.buffer();
        try {
            RegistryFriendlyByteBuf out = new RegistryFriendlyByteBuf(raw, registries);
            ResourceKey.streamCodec(Registries.DIMENSION).encode(out, Level.OVERWORLD);
            ByteBufCodecs.INT.encode(out, 1);
            ByteBufCodecs.INT.encode(out, 2);
            ByteBufCodecs.BYTE_ARRAY.encode(out, new byte[RemoteChunkDataPayload.MAX_PACKET_BYTES + 1]);

            RegistryFriendlyByteBuf in = new RegistryFriendlyByteBuf(raw, registries);
            assertThrows(DecoderException.class, () -> RemoteChunkDataPayload.STREAM_CODEC.decode(in));
        } finally {
            raw.release();
        }
    }

    @Test
    void remoteChunkForgetPayloadRoundtrips() {
        RemoteChunkForgetPayload original = new RemoteChunkForgetPayload(Level.END, -45, 88);
        RemoteChunkForgetPayload decoded = roundtrip(RemoteChunkForgetPayload.STREAM_CODEC, original);
        assertEquals(original, decoded);
    }

    @Test
    void remoteBlockDeltaPayloadRoundtripsBlockStatesNbtAndUnknownIdFallback() {
        RemoteBlockDeltaPayload airStone = new RemoteBlockDeltaPayload(
                Level.OVERWORLD,
                new BlockPos(10, 64, -20),
                Blocks.STONE.defaultBlockState(),
                Optional.empty()
        );
        assertEquals(airStone, roundtrip(RemoteBlockDeltaPayload.STREAM_CODEC, airStone));

        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
        CompoundTag tag = new CompoundTag();
        tag.putString("CustomName", "GateChest");
        tag.putInt("SlotCount", 27);
        RemoteBlockDeltaPayload withTag = new RemoteBlockDeltaPayload(
                Level.NETHER,
                new BlockPos(5, 70, 5),
                stairs,
                Optional.of(tag)
        );
        tag.putString("CustomName", "MutatedAfterCreate");

        RemoteBlockDeltaPayload decoded = roundtrip(RemoteBlockDeltaPayload.STREAM_CODEC, withTag);
        assertEquals(stairs, decoded.state());
        assertTrue(decoded.blockEntityTag().isPresent());
        //? if >=1.21.11 {
        /*assertEquals("GateChest", decoded.blockEntityTag().get().getString("CustomName").orElseThrow());

        CompoundTag returnedTag = decoded.blockEntityTag().get();
        returnedTag.putInt("SlotCount", 999);
        assertEquals(27, decoded.blockEntityTag().get().getInt("SlotCount").orElseThrow());
        *///?} else {
        assertEquals("GateChest", decoded.blockEntityTag().get().getString("CustomName"));

        CompoundTag returnedTag = decoded.blockEntityTag().get();
        returnedTag.putInt("SlotCount", 999);
        assertEquals(27, decoded.blockEntityTag().get().getInt("SlotCount"));
        //?}

        ByteBuf raw = Unpooled.buffer();
        try {
            RegistryFriendlyByteBuf out = new RegistryFriendlyByteBuf(raw, registries);
            ResourceKey.streamCodec(Registries.DIMENSION).encode(out, Level.OVERWORLD);
            BlockPos.STREAM_CODEC.encode(out, BlockPos.ZERO);
            ByteBufCodecs.VAR_INT.encode(out, Integer.MAX_VALUE);
            ByteBufCodecs.OPTIONAL_COMPOUND_TAG.encode(out, Optional.empty());

            RegistryFriendlyByteBuf in = new RegistryFriendlyByteBuf(raw, registries);
            RemoteBlockDeltaPayload unknownDecoded = RemoteBlockDeltaPayload.STREAM_CODEC.decode(in);
            assertEquals(Blocks.AIR.defaultBlockState(), unknownDecoded.state());
        } finally {
            raw.release();
        }
    }

    @Test
    void remoteEntitySyncPayloadRoundtripsDefensiveCopiesAndSyncKinds() {
        byte[] bytes = new byte[]{10, 20, 30};
        RemoteEntitySyncPayload payload = new RemoteEntitySyncPayload(
                Level.OVERWORLD,
                77,
                RemoteEntitySyncPayload.SyncKind.SET_MOTION,
                bytes
        );
        bytes[0] = 0;

        assertArrayEquals(new byte[]{10, 20, 30}, payload.copyBytes());
        assertEquals(payload, roundtrip(RemoteEntitySyncPayload.STREAM_CODEC, payload));

        for (RemoteEntitySyncPayload.SyncKind kind : RemoteEntitySyncPayload.SyncKind.values()) {
            ByteBuf raw = Unpooled.buffer();
            try {
                RemoteEntitySyncPayload.SyncKind.STREAM_CODEC.encode(raw, kind);
                assertEquals(kind, RemoteEntitySyncPayload.SyncKind.STREAM_CODEC.decode(raw));
            } finally {
                raw.release();
            }
        }

        assertThrows(DecoderException.class, () -> RemoteEntitySyncPayload.SyncKind.fromOrdinal(-1));
        assertThrows(
                DecoderException.class,
                () -> RemoteEntitySyncPayload.SyncKind.fromOrdinal(RemoteEntitySyncPayload.SyncKind.values().length)
        );

        ByteBuf badKindBuf = Unpooled.buffer();
        try {
            ByteBufCodecs.VAR_INT.encode(badKindBuf, 999);
            assertThrows(DecoderException.class, () -> RemoteEntitySyncPayload.SyncKind.STREAM_CODEC.decode(badKindBuf));
        } finally {
            badKindBuf.release();
        }

        byte[] oversized = new byte[RemoteEntitySyncPayload.MAX_PACKET_BYTES + 1];
        assertThrows(
                IllegalArgumentException.class,
                () -> new RemoteEntitySyncPayload(Level.OVERWORLD, 1, RemoteEntitySyncPayload.SyncKind.MOVE_POS, oversized)
        );
        assertThrows(
                EncoderException.class,
                () -> {
                    ByteBuf buf = Unpooled.buffer();
                    try {
                        ByteBufCodecs.byteArray(RemoteEntitySyncPayload.MAX_PACKET_BYTES).encode(buf, oversized);
                    } finally {
                        buf.release();
                    }
                }
        );
    }

    @Test
    void remoteEntitySyncPayloadEncodesAndDecodesVanillaPackets() {
        UUID entityUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        ClientboundAddEntityPacket addEntity = new ClientboundAddEntityPacket(
                42, entityUuid, 10.5D, 64.0D, -3.25D, 15.0F, 90.0F,
                EntityType.ZOMBIE, 0, new Vec3(0.1D, -0.2D, 0.3D), 45.0D
        );
        RemoteEntitySyncPayload wrappedAdd = RemoteEntitySyncPayload.fromPacket(
                Level.OVERWORLD, 42, RemoteEntitySyncPayload.SyncKind.ADD_ENTITY, registries, addEntity
        );
        RemoteEntitySyncPayload roundtrippedAdd = roundtrip(RemoteEntitySyncPayload.STREAM_CODEC, wrappedAdd);
        ClientboundAddEntityPacket decodedAdd = assertInstanceOf(
                ClientboundAddEntityPacket.class,
                roundtrippedAdd.decodeVanillaPacket(registries)
        );
        assertEquals(42, decodedAdd.getId());
        assertEquals(entityUuid, decodedAdd.getUUID());
        assertEquals(EntityType.ZOMBIE, decodedAdd.getType());

        ClientboundSetEntityDataPacket setData = new ClientboundSetEntityDataPacket(
                42,
                List.of(new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, (byte) 0x05))
        );
        RemoteEntitySyncPayload wrappedData = RemoteEntitySyncPayload.fromPacket(
                Level.OVERWORLD, 42, RemoteEntitySyncPayload.SyncKind.SET_DATA, registries, setData
        );
        ClientboundSetEntityDataPacket decodedData = assertInstanceOf(
                ClientboundSetEntityDataPacket.class,
                roundtrip(RemoteEntitySyncPayload.STREAM_CODEC, wrappedData).decodeVanillaPacket(registries)
        );
        assertEquals(42, decodedData.id());
        assertEquals(1, decodedData.packedItems().size());

        AttributeInstance healthAttr = new AttributeInstance(Attributes.MAX_HEALTH, ignored -> {});
        healthAttr.setBaseValue(40.0D);
        ClientboundUpdateAttributesPacket updateAttr = new ClientboundUpdateAttributesPacket(42, List.of(healthAttr));
        RemoteEntitySyncPayload wrappedAttr = RemoteEntitySyncPayload.fromPacket(
                Level.OVERWORLD, 42, RemoteEntitySyncPayload.SyncKind.UPDATE_ATTRIBUTES, registries, updateAttr
        );
        ClientboundUpdateAttributesPacket decodedAttr = assertInstanceOf(
                ClientboundUpdateAttributesPacket.class,
                roundtrip(RemoteEntitySyncPayload.STREAM_CODEC, wrappedAttr).decodeVanillaPacket(registries)
        );
        assertEquals(42, decodedAttr.getEntityId());
        assertEquals(1, decodedAttr.getValues().size());

        ClientboundSetEquipmentPacket equipment = new ClientboundSetEquipmentPacket(
                42,
                List.of(Pair.of(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD)))
        );
        RemoteEntitySyncPayload wrappedEquip = RemoteEntitySyncPayload.fromPacket(
                Level.OVERWORLD, 42, RemoteEntitySyncPayload.SyncKind.SET_EQUIPMENT, registries, equipment
        );
        ClientboundSetEquipmentPacket decodedEquip = assertInstanceOf(
                ClientboundSetEquipmentPacket.class,
                roundtrip(RemoteEntitySyncPayload.STREAM_CODEC, wrappedEquip).decodeVanillaPacket(registries)
        );
        assertEquals(42, decodedEquip.getEntity());
        assertEquals(1, decodedEquip.getSlots().size());
        assertTrue(decodedEquip.getSlots().getFirst().getSecond().is(Items.DIAMOND_SWORD));

        Packet<?>[] simplePackets = new Packet<?>[]{
                new ClientboundMoveEntityPacket.Pos(42, (short) 100, (short) -50, (short) 25, true),
                new ClientboundMoveEntityPacket.PosRot(42, (short) 10, (short) 20, (short) 30, (byte) 64, (byte) -32, false),
                new ClientboundMoveEntityPacket.Rot(42, (byte) 120, (byte) 15, true),
                new ClientboundSetEntityMotionPacket(42, new Vec3(0.5D, 0.25D, -0.75D)),
                new ClientboundRemoveEntitiesPacket(1, 2, 42),
                new ClientboundProjectilePowerPacket(42, 0.15D)
        };
        for (Packet<?> packet : simplePackets) {
            RemoteEntitySyncPayload.SyncKind kind = RemoteEntitySyncPayload.kindForPacket(packet).orElseThrow();
            RemoteEntitySyncPayload wrapped = RemoteEntitySyncPayload.fromPacket(Level.NETHER, 42, kind, registries, packet);
            Packet<?> decoded = roundtrip(RemoteEntitySyncPayload.STREAM_CODEC, wrapped).decodeVanillaPacket(registries);
            assertEquals(packet.getClass(), decoded.getClass());
        }

        //? if >=1.21.11 {
        /*ClientboundTeleportEntityPacket teleportPacket = ClientboundTeleportEntityPacket.teleport(
                42,
                new PositionMoveRotation(new Vec3(1.0D, 2.0D, 3.0D), Vec3.ZERO, 90.0F, 15.0F),
                Set.of(),
                true
        );
        RemoteEntitySyncPayload wrappedTp = RemoteEntitySyncPayload.fromPacket(
                Level.END, 42, RemoteEntitySyncPayload.SyncKind.TELEPORT_ENTITY, registries, teleportPacket
        );
        ClientboundTeleportEntityPacket decodedTp = assertInstanceOf(
                ClientboundTeleportEntityPacket.class,
                roundtrip(RemoteEntitySyncPayload.STREAM_CODEC, wrappedTp).decodeVanillaPacket(registries)
        );
        assertEquals(42, decodedTp.id());

        ClientboundEntityPositionSyncPacket posSyncPacket = new ClientboundEntityPositionSyncPacket(
                42,
                new PositionMoveRotation(new Vec3(4.0D, 5.0D, 6.0D), new Vec3(0.1D, 0.2D, 0.3D), 45.0F, -10.0F),
                false
        );
        RemoteEntitySyncPayload wrappedPosSync = RemoteEntitySyncPayload.fromPacket(
                Level.END, 42, RemoteEntitySyncPayload.SyncKind.ENTITY_POSITION_SYNC, registries, posSyncPacket
        );
        ClientboundEntityPositionSyncPacket decodedPosSync = assertInstanceOf(
                ClientboundEntityPositionSyncPacket.class,
                roundtrip(RemoteEntitySyncPayload.STREAM_CODEC, wrappedPosSync).decodeVanillaPacket(registries)
        );
        assertEquals(42, decodedPosSync.id());
        *///?} else {
        RemoteEntitySyncPayload unsupported1211 = new RemoteEntitySyncPayload(
                Level.OVERWORLD, 42, RemoteEntitySyncPayload.SyncKind.ENTITY_POSITION_SYNC, new byte[]{1}
        );
        assertThrows(UnsupportedOperationException.class, () -> unsupported1211.decodeVanillaPacket(registries));
        //?}

        assertThrows(
                IllegalArgumentException.class,
                () -> RemoteEntitySyncPayload.encodeVanillaPacket(
                        registries,
                        RemoteEntitySyncPayload.SyncKind.MOVE_POS,
                        new ClientboundRemoveEntitiesPacket(42)
                )
        );
    }

    @Test
    void portalViewSubscriptionPayloadRoundtrips() {
        PortalViewSubscriptionPayload subscribe = new PortalViewSubscriptionPayload(new BlockPos(12, 65, -90), true);
        assertEquals(subscribe, roundtrip(PortalViewSubscriptionPayload.STREAM_CODEC, subscribe));

        PortalViewSubscriptionPayload unsubscribe = new PortalViewSubscriptionPayload(new BlockPos(-4, 120, 88), false);
        assertEquals(unsubscribe, roundtrip(PortalViewSubscriptionPayload.STREAM_CODEC, unsubscribe));
    }
}
