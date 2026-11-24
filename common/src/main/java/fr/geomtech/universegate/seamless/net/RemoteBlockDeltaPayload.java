package fr.geomtech.universegate.seamless.net;

import fr.geomtech.universegate.UniverseGate;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Optional;

public record RemoteBlockDeltaPayload(
        ResourceKey<Level> dim,
        BlockPos pos,
        BlockState state,
        Optional<CompoundTag> blockEntityTag
) implements CustomPacketPayload {

    public static final StreamCodec<ByteBuf, BlockState> BLOCK_STATE_STREAM_CODEC =
            ByteBufCodecs.idMapper(Block::stateById, Block::getId);

    public static final CustomPacketPayload.Type<RemoteBlockDeltaPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "remote_block_delta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteBlockDeltaPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceKey.streamCodec(Registries.DIMENSION), RemoteBlockDeltaPayload::dim,
                    BlockPos.STREAM_CODEC, RemoteBlockDeltaPayload::pos,
                    BLOCK_STATE_STREAM_CODEC, RemoteBlockDeltaPayload::state,
                    ByteBufCodecs.OPTIONAL_COMPOUND_TAG, RemoteBlockDeltaPayload::blockEntityTag,
                    RemoteBlockDeltaPayload::new
            );

    public RemoteBlockDeltaPayload {
        Objects.requireNonNull(dim, "dim");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        pos = pos.immutable();
        blockEntityTag = blockEntityTag == null ? Optional.empty() : blockEntityTag.map(CompoundTag::copy);
    }

    @Override
    public Optional<CompoundTag> blockEntityTag() {
        return blockEntityTag.map(CompoundTag::copy);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
