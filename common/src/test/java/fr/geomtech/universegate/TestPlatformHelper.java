package fr.geomtech.universegate;

import fr.geomtech.universegate.platform.PlatformHelper;
import fr.geomtech.universegate.platform.PosMenuFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
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
//? if >=1.21.11 {
/*import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
*///?}

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Only block/fluid registration is available in headless common tests. */
public final class TestPlatformHelper implements PlatformHelper {

    @Override
    public String getLoaderName() { return "test"; }

    @Override
    public String getModVersion() { return "test"; }

    @Override
    public boolean isModLoaded(String modId) { return false; }

    @Override
    public FlowingFluid createDarkMatterSource() { return new DarkMatterFluid.Source(); }

    @Override
    public FlowingFluid createDarkMatterFlowing() { return new DarkMatterFluid.Flowing(); }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() { throw unsupported(); }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> createPosMenuType(PosMenuFactory<M> factory) {
        throw unsupported();
    }

    @Override
    public void openPosMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) { throw unsupported(); }

    @Override
    public void registerEntityAttributes(EntityType<? extends LivingEntity> type,
                                         Supplier<AttributeSupplier.Builder> attributes) { throw unsupported(); }

    @Override
    public <T extends Mob> void registerSpawnPlacement(EntityType<T> type, SpawnPlacementType placementType,
                                                      Heightmap.Types heightmap,
                                                      SpawnPlacements.SpawnPredicate<T> predicate) { throw unsupported(); }

    //? if >=1.21.11 {
    /*@Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block block) {
        throw unsupported();
    }
    *///?}

    @Override
    public boolean autoRegistersPoiBlockStates() { throw unsupported(); }

    @Override
    public <T extends CustomPacketPayload> void registerClientboundPayload(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) { throw unsupported(); }

    @Override
    public <T extends CustomPacketPayload> void registerServerboundPayload(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        throw unsupported();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) { throw unsupported(); }

    @Override
    public void sendEntityPairingData(ServerEntity serverEntity, ServerPlayer player,
                                      Consumer<Packet<? super ClientGamePacketListener>> consumer) { throw unsupported(); }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Loader operation unavailable in headless common tests");
    }
}
