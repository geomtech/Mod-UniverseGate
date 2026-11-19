package fr.geomtech.universegate.platform;

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

    void registerEntityAttributes(EntityType<? extends LivingEntity> type, AttributeSupplier.Builder attributes);

    <T extends Mob> void registerSpawnPlacement(EntityType<T> type,
                                                SpawnPlacementType placementType,
                                                Heightmap.Types heightmap,
                                                SpawnPlacements.SpawnPredicate<T> predicate);

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
