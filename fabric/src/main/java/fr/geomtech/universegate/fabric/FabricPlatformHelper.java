package fr.geomtech.universegate.fabric;

//? if >=1.21.11 {
/*import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
*///?}
import fr.geomtech.universegate.DarkMatterFluid;
import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.platform.PlatformHelper;
import fr.geomtech.universegate.platform.PosMenuFactory;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class FabricPlatformHelper implements PlatformHelper {

    @Override
    public String getLoaderName() {
        return "fabric";
    }

    @Override
    public String getModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(UniverseGate.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

    @Override
    public boolean isModLoaded(String modId) {
        return modId != null && FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return FabricItemGroup.builder();
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> createPosMenuType(PosMenuFactory<M> factory) {
        return new ExtendedScreenHandlerType<>(factory::create, BlockPos.STREAM_CODEC);
    }

    @Override
    public void openPosMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        player.openMenu(new ExtendedScreenHandlerFactory<BlockPos>() {
            @Override
            public BlockPos getScreenOpeningData(ServerPlayer serverPlayer) {
                return pos;
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player menuPlayer) {
                return provider.createMenu(syncId, inventory, menuPlayer);
            }
        });
    }

    @Override
    public void registerEntityAttributes(EntityType<? extends LivingEntity> type, Supplier<AttributeSupplier.Builder> attributes) {
        FabricDefaultAttributeRegistry.register(type, attributes.get());
    }

    @Override
    public <T extends Mob> void registerSpawnPlacement(EntityType<T> type,
                                                       SpawnPlacementType placementType,
                                                       Heightmap.Types heightmap,
                                                       SpawnPlacements.SpawnPredicate<T> predicate) {
        SpawnPlacements.register(type, placementType, heightmap, predicate);
    }

    //? if >=1.21.11 {
    /*@Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block block) {
        return FabricBlockEntityTypeBuilder.<T>create(factory::create, block).build();
    }
    *///?}

    @Override
    public FlowingFluid createDarkMatterSource() {
        return new DarkMatterFluid.Source();
    }

    @Override
    public FlowingFluid createDarkMatterFlowing() {
        return new DarkMatterFluid.Flowing();
    }

    @Override
    public boolean autoRegistersPoiBlockStates() {
        return false;
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientboundPayload(CustomPacketPayload.Type<T> type,
                                                                          StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.playS2C().register(type, codec);
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerboundPayload(CustomPacketPayload.Type<T> type,
                                                                          StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                          BiConsumer<T, ServerPlayer> handler) {
        PayloadTypeRegistry.playC2S().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload, context.player()));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendEntityPairingData(ServerEntity serverEntity,
                                      ServerPlayer player,
                                      Consumer<Packet<? super ClientGamePacketListener>> consumer) {
        serverEntity.sendPairingData(player, consumer::accept);
    }
}
