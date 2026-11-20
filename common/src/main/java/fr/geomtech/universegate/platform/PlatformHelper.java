package fr.geomtech.universegate.platform;

//? if >=1.21.11 {
/*import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Everything the common code needs from the mod loader (Fabric or NeoForge).
 */
public interface PlatformHelper {

    /** "fabric" or "neoforge". */
    String getLoaderName();

    /** Version of the UniverseGate mod as declared in the loader metadata. */
    String getModVersion();

    CreativeModeTab.Builder creativeTabBuilder();

    /** Creates a menu type whose client-side menu is built from a {@link BlockPos} sent by the server. */
    <M extends AbstractContainerMenu> MenuType<M> createPosMenuType(PosMenuFactory<M> factory);

    /** Opens a menu created by {@link #createPosMenuType} and sends {@code pos} to the client. */
    void openPosMenu(ServerPlayer player, MenuProvider provider, BlockPos pos);

    /** The supplier is called lazily on NeoForge (attributes must not be built while registries are being filled). */
    void registerEntityAttributes(EntityType<? extends LivingEntity> type, Supplier<AttributeSupplier.Builder> attributes);

    <T extends Mob> void registerSpawnPlacement(EntityType<T> type,
                                                SpawnPlacementType placementType,
                                                Heightmap.Types heightmap,
                                                SpawnPlacements.SpawnPredicate<T> predicate);

    //? if >=1.21.11 {
    /*/^* Block entity constructor, as {@code PortalCoreBlockEntity::new}. ^/
    @FunctionalInterface
    interface BlockEntityFactory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }

    /^*
     * A block entity type for one block. Since 1.21.2 there is no BlockEntityType.Builder and the
     * constructor is only opened by the loaders (NeoForge access transformer, Fabric API builder).
     ^/
    <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block block);
    *///?}

    FlowingFluid createDarkMatterSource();

    FlowingFluid createDarkMatterFlowing();

    /**
     * NeoForge automatically maps the block states of a newly registered {@code PoiType};
     * vanilla / Fabric need {@code PoiTypes.registerBlockStates} to be called by hand.
     */
    boolean autoRegistersPoiBlockStates();

    <T extends CustomPacketPayload> void registerClientboundPayload(CustomPacketPayload.Type<T> type,
                                                                   StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

    /** The handler is invoked with the sending player; it may run on the network thread. */
    <T extends CustomPacketPayload> void registerServerboundPayload(CustomPacketPayload.Type<T> type,
                                                                   StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                   BiConsumer<T, ServerPlayer> handler);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
}
