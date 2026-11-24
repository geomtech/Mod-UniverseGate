package fr.geomtech.universegate.seamless.server;

import fr.geomtech.universegate.seamless.net.PortalViewSubscriptionPayload;
import fr.geomtech.universegate.seamless.net.RemoteBlockDeltaPayload;
import fr.geomtech.universegate.seamless.net.RemoteChunkForgetPayload;
import fr.geomtech.universegate.seamless.net.RemoteEntitySyncPayload;
import fr.geomtech.universegate.seamless.net.SeamlessNetwork;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
//? if >=1.21.11 {
/*import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.PositionMoveRotation;

import java.util.Set;
*///?}
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerStreamPayloadIntegrationTest {

    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @AfterEach
    void resetNetworkHandlers() {
        SeamlessNetwork.resetHandlers();
    }

    private static <T> T roundtrip(StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T value) {
        ByteBuf raw = Unpooled.buffer();
        try {
            RegistryFriendlyByteBuf out = new RegistryFriendlyByteBuf(raw, registries);
            codec.encode(out, value);
            RegistryFriendlyByteBuf in = new RegistryFriendlyByteBuf(raw, registries);
            T decoded = codec.decode(in);
            assertEquals(0, raw.readableBytes(), "Codec should consume all bytes");
            return decoded;
        } finally {
            raw.release();
        }
    }

    @Test
    void seamlessNetworkSubscriptionHandlerRegistersAndResetsCleanly() {
        AtomicReference<PortalViewSubscriptionPayload> received = new AtomicReference<>();
        SeamlessNetwork.setViewSubscriptionHandler((payload, player) -> received.set(payload));
        SeamlessNetwork.setViewSubscriptionHandler(null);
        SeamlessNetwork.resetHandlers();

        PortalViewSubscriptionPayload payload = new PortalViewSubscriptionPayload(new BlockPos(4, 64, -9), true);
        PortalViewSubscriptionPayload decoded = roundtrip(PortalViewSubscriptionPayload.STREAM_CODEC, payload);
        assertEquals(payload, decoded);
        assertNull(received.get());
    }

    @Test
    void unwrapAndWrapEntityPacketsHandlesBundlePacketsAndVersionSpecificSyncKinds() {
        List<Packet<? super ClientGamePacketListener>> bundled = new ArrayList<>();
        bundled.add(new ClientboundMoveEntityPacket.Pos(42, (short) 12, (short) -4, (short) 8, true));
        bundled.add(new ClientboundSetEntityMotionPacket(42, new Vec3(0.25D, 0.5D, -0.25D)));
        bundled.add(new ClientboundSetTimePacket(100L, 200L, true));
        bundled.add(new ClientboundSetEntityDataPacket(
                42,
                List.of(new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, (byte) 0x02))
        ));
        //? if >=1.21.11 {
        /*bundled.add(new ClientboundEntityPositionSyncPacket(
                42,
                new PositionMoveRotation(new Vec3(1.0D, 2.0D, 3.0D), Vec3.ZERO, 45.0F, 0.0F),
                true
        ));
        bundled.add(ClientboundTeleportEntityPacket.teleport(
                42,
                new PositionMoveRotation(new Vec3(4.0D, 5.0D, 6.0D), Vec3.ZERO, 90.0F, 10.0F),
                Set.of(),
                false
        ));
        *///?} else {
        bundled.add(new ClientboundMoveEntityPacket.PosRot(42, (short) 1, (short) 2, (short) 3, (byte) 64, (byte) 0, false));
        //?}

        ClientboundBundlePacket bundlePacket = new ClientboundBundlePacket(bundled);
        List<RemoteEntitySyncPayload> wrapped = new ArrayList<>();
        ServerPortalStreamManager.unwrapPackets(bundlePacket, packet ->
                ServerPortalStreamManager.wrapEntityPacket(Level.NETHER, 42, registries, packet)
                        .ifPresent(wrapped::add)
        );

        //? if >=1.21.11 {
        /*assertEquals(5, wrapped.size());
        assertEquals(RemoteEntitySyncPayload.SyncKind.ENTITY_POSITION_SYNC, wrapped.get(3).kind());
        assertEquals(RemoteEntitySyncPayload.SyncKind.TELEPORT_ENTITY, wrapped.get(4).kind());
        *///?} else {
        assertEquals(4, wrapped.size());
        assertEquals(RemoteEntitySyncPayload.SyncKind.MOVE_POS_ROT, wrapped.get(3).kind());
        //?}

        assertEquals(RemoteEntitySyncPayload.SyncKind.MOVE_POS, wrapped.get(0).kind());
        assertEquals(RemoteEntitySyncPayload.SyncKind.SET_MOTION, wrapped.get(1).kind());
        assertEquals(RemoteEntitySyncPayload.SyncKind.SET_DATA, wrapped.get(2).kind());

        for (RemoteEntitySyncPayload payload : wrapped) {
            RemoteEntitySyncPayload decodedPayload = roundtrip(RemoteEntitySyncPayload.STREAM_CODEC, payload);
            Packet<?> decodedVanilla = decodedPayload.decodeVanillaPacket(registries);
            assertEquals(payload.kind(), RemoteEntitySyncPayload.kindForPacket(decodedVanilla).orElseThrow());
        }
    }

    @Test
    void chunkForgetBlockDeltaAndEntityRemovalRoundtripThroughPayloads() {
        RemoteChunkForgetPayload forget = new RemoteChunkForgetPayload(Level.END, -12, 34);
        assertEquals(forget, roundtrip(RemoteChunkForgetPayload.STREAM_CODEC, forget));

        CompoundTag beTag = new CompoundTag();
        beTag.putInt("Energy", 4000);
        RemoteBlockDeltaPayload delta = new RemoteBlockDeltaPayload(
                Level.NETHER,
                new BlockPos(16, 72, -16),
                Blocks.DIAMOND_BLOCK.defaultBlockState(),
                Optional.of(beTag)
        );
        RemoteBlockDeltaPayload decodedDelta = roundtrip(RemoteBlockDeltaPayload.STREAM_CODEC, delta);
        assertEquals(delta, decodedDelta);
        assertTrue(decodedDelta.blockEntityTag().isPresent());

        ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(10, 20, 30);
        RemoteEntitySyncPayload removeWrapped = ServerPortalStreamManager.wrapEntityPacket(
                Level.NETHER,
                10,
                registries,
                removePacket
        ).orElseThrow();
        assertEquals(RemoteEntitySyncPayload.SyncKind.REMOVE_ENTITIES, removeWrapped.kind());

        Packet<?> decodedRemove = roundtrip(RemoteEntitySyncPayload.STREAM_CODEC, removeWrapped)
                .decodeVanillaPacket(registries);
        ClientboundRemoveEntitiesPacket typedRemove = assertInstanceOf(ClientboundRemoveEntitiesPacket.class, decodedRemove);
        assertEquals(3, typedRemove.getEntityIds().size());
    }
}
