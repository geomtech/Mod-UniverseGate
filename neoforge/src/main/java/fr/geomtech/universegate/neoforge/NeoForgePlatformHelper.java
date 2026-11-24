package fr.geomtech.universegate.neoforge;

//? if >=1.21.11 {
/*import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
*///?}
import fr.geomtech.universegate.UniverseGate;
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
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class NeoForgePlatformHelper implements PlatformHelper {

    /** Network protocol version; bump when payloads change incompatibly. */
    private static final String NETWORK_VERSION = "2";

    private static final Map<EntityType<? extends LivingEntity>, Supplier<AttributeSupplier.Builder>> ATTRIBUTES = new LinkedHashMap<>();
    private static final List<Consumer<RegisterSpawnPlacementsEvent>> SPAWN_PLACEMENTS = new ArrayList<>();
    private static final List<Consumer<PayloadRegistrar>> PAYLOADS = new ArrayList<>();
    static final Map<CustomPacketPayload.Type<?>, Consumer<?>> CLIENT_RECEIVERS = new ConcurrentHashMap<>();

    @Override
    public String getLoaderName() {
        return "neoforge";
    }

    @Override
    public String getModVersion() {
        return ModList.get().getModContainerById(UniverseGate.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("0.0.0");
    }

    @Override
    public boolean isModLoaded(String modId) {
        return modId != null && ModList.get().isLoaded(modId);
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> createPosMenuType(PosMenuFactory<M> factory) {
        return IMenuTypeExtension.create((syncId, inventory, buf) -> factory.create(syncId, inventory, buf.readBlockPos()));
    }

    @Override
    public void openPosMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        player.openMenu(provider, buf -> buf.writeBlockPos(pos));
    }

    @Override
    public void registerEntityAttributes(EntityType<? extends LivingEntity> type, Supplier<AttributeSupplier.Builder> attributes) {
        ATTRIBUTES.put(type, attributes);
    }

    @Override
    public <T extends Mob> void registerSpawnPlacement(EntityType<T> type,
                                                       SpawnPlacementType placementType,
                                                       Heightmap.Types heightmap,
                                                       SpawnPlacements.SpawnPredicate<T> predicate) {
        SPAWN_PLACEMENTS.add(event -> event.register(type, placementType, heightmap, predicate,
                RegisterSpawnPlacementsEvent.Operation.REPLACE));
    }

    //? if >=1.21.11 {
    /*@Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block block) {
        // Public through NeoForge's access transformer
        return new BlockEntityType<>(factory::create, Set.of(block));
    }
    *///?}

    @Override
    public FlowingFluid createDarkMatterSource() {
        return new NeoForgeFluids.Source();
    }

    @Override
    public FlowingFluid createDarkMatterFlowing() {
        return new NeoForgeFluids.Flowing();
    }

    @Override
    public boolean autoRegistersPoiBlockStates() {
        return true;
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientboundPayload(CustomPacketPayload.Type<T> type,
                                                                          StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        PAYLOADS.add(registrar -> registrar.playToClient(type, codec, (payload, context) -> {
            @SuppressWarnings("unchecked")
            Consumer<T> receiver = (Consumer<T>) CLIENT_RECEIVERS.get(type);
            if (receiver != null) {
                receiver.accept(payload);
            }
        }));
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerboundPayload(CustomPacketPayload.Type<T> type,
                                                                          StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                          BiConsumer<T, ServerPlayer> handler) {
        PAYLOADS.add(registrar -> registrar.playToServer(type, codec, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                handler.accept(payload, player);
            }
        }));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendEntityPairingData(ServerEntity serverEntity,
                                      ServerPlayer player,
                                      Consumer<Packet<? super ClientGamePacketListener>> consumer) {
        //? if >=1.21.11 {
        /*serverEntity.sendPairingData(player, consumer);
        *///?} else {
        serverEntity.sendPairingData(
                player,
                new net.neoforged.neoforge.network.bundle.PacketAndPayloadAcceptor<>(consumer)
        );
        //?}
    }

    static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        ATTRIBUTES.forEach((type, attributes) -> event.put(type, attributes.get().build()));
    }

    static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        SPAWN_PLACEMENTS.forEach(action -> action.accept(event));
    }

    static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        PAYLOADS.forEach(action -> action.accept(registrar));
    }
}
