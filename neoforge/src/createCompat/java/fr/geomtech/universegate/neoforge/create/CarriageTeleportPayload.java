package fr.geomtech.universegate.neoforge.create;

import com.simibubi.create.content.trains.entity.CarriageSyncData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

public record CarriageTeleportPayload(int entityId, UUID trainId, UUID graphId, CarriageSyncData data,
                                     Optional<Controls> controls)
        implements CustomPacketPayload {
    public static final Type<CarriageTeleportPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("universegate", "create_carriage_teleport"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarriageTeleportPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId);
                buffer.writeUUID(payload.trainId);
                buffer.writeUUID(payload.graphId);
                payload.data.write(buffer);
                buffer.writeOptional(payload.controls, (buf, controls) -> {
                    buf.writeUUID(controls.playerId);
                    buf.writeBlockPos(controls.localPos);
                });
            },
            buffer -> new CarriageTeleportPayload(
                    buffer.readVarInt(), buffer.readUUID(), buffer.readUUID(), new CarriageSyncData(buffer),
                    buffer.readOptional(buf -> new Controls(buf.readUUID(), buf.readBlockPos())))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Controls(UUID playerId, BlockPos localPos) {}
}
