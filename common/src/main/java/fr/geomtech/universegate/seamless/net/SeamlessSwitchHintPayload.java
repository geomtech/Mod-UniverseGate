package fr.geomtech.universegate.seamless.net;

import fr.geomtech.universegate.UniverseGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Sent S2C immediately before a cross-dimension seamless portal transfer so the client can
 * suppress the dimension loading screen and atomically promote its preloaded remote {@code ClientLevel}.
 */
public record SeamlessSwitchHintPayload(
        ResourceKey<Level> targetDim,
        BlockPos targetCorePos,
        double x,
        double y,
        double z,
        float yRot,
        float xRot,
        double vx,
        double vy,
        double vz
) implements CustomPacketPayload {

    private static final StreamCodec<io.netty.buffer.ByteBuf, ResourceKey<Level>> DIMENSION_STREAM_CODEC =
            ResourceKey.streamCodec(Registries.DIMENSION);

    public static final CustomPacketPayload.Type<SeamlessSwitchHintPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "seamless_switch_hint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SeamlessSwitchHintPayload> STREAM_CODEC =
            StreamCodec.of(SeamlessSwitchHintPayload::encode, SeamlessSwitchHintPayload::decode);

    public SeamlessSwitchHintPayload {
        Objects.requireNonNull(targetDim, "targetDim");
        Objects.requireNonNull(targetCorePos, "targetCorePos");
        targetCorePos = targetCorePos.immutable();
    }

    public SeamlessSwitchHintPayload(ResourceKey<Level> targetDim,
                                     BlockPos targetCorePos,
                                     double x,
                                     double y,
                                     double z,
                                     float yRot,
                                     float xRot,
                                     Vec3 velocity) {
        this(targetDim, targetCorePos, x, y, z, yRot, xRot, velocity.x, velocity.y, velocity.z);
    }

    public Vec3 velocity() {
        return new Vec3(vx, vy, vz);
    }

    private static void encode(RegistryFriendlyByteBuf buf, SeamlessSwitchHintPayload payload) {
        DIMENSION_STREAM_CODEC.encode(buf, payload.targetDim());
        BlockPos.STREAM_CODEC.encode(buf, payload.targetCorePos());
        ByteBufCodecs.DOUBLE.encode(buf, payload.x());
        ByteBufCodecs.DOUBLE.encode(buf, payload.y());
        ByteBufCodecs.DOUBLE.encode(buf, payload.z());
        ByteBufCodecs.FLOAT.encode(buf, payload.yRot());
        ByteBufCodecs.FLOAT.encode(buf, payload.xRot());
        ByteBufCodecs.DOUBLE.encode(buf, payload.vx());
        ByteBufCodecs.DOUBLE.encode(buf, payload.vy());
        ByteBufCodecs.DOUBLE.encode(buf, payload.vz());
    }

    private static SeamlessSwitchHintPayload decode(RegistryFriendlyByteBuf buf) {
        ResourceKey<Level> targetDim = DIMENSION_STREAM_CODEC.decode(buf);
        BlockPos targetCorePos = BlockPos.STREAM_CODEC.decode(buf);
        double x = ByteBufCodecs.DOUBLE.decode(buf);
        double y = ByteBufCodecs.DOUBLE.decode(buf);
        double z = ByteBufCodecs.DOUBLE.decode(buf);
        float yRot = ByteBufCodecs.FLOAT.decode(buf);
        float xRot = ByteBufCodecs.FLOAT.decode(buf);
        double vx = ByteBufCodecs.DOUBLE.decode(buf);
        double vy = ByteBufCodecs.DOUBLE.decode(buf);
        double vz = ByteBufCodecs.DOUBLE.decode(buf);
        return new SeamlessSwitchHintPayload(targetDim, targetCorePos, x, y, z, yRot, xRot, vx, vy, vz);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
