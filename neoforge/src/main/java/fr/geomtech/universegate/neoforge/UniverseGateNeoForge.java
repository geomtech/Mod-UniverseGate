package fr.geomtech.universegate.neoforge;

import fr.geomtech.universegate.AncientCityZpcGuarantee;
import fr.geomtech.universegate.RiftDeathRecoveryHandler;
import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.UpdateCheckManager;
import fr.geomtech.universegate.ZpcStructureLoot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootPool;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(UniverseGate.MOD_ID)
public final class UniverseGateNeoForge {

    public UniverseGateNeoForge(IEventBus modBus) {
        // NeoForge unfreezes every registry while RegisterEvent is being fired, so the
        // common (vanilla-style) registration code runs once, on the BLOCK RegisterEvent:
        // by then attributes, data components and NeoForge's own early entries are bound.
        modBus.addListener(EventPriority.HIGH, RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(Registries.BLOCK)) {
                NeoForgeFluids.register();
                UniverseGate.registerContent();
            }
        });
        modBus.addListener(EntityAttributeCreationEvent.class, NeoForgePlatformHelper::onEntityAttributeCreation);
        modBus.addListener(RegisterSpawnPlacementsEvent.class, NeoForgePlatformHelper::onRegisterSpawnPlacements);
        modBus.addListener(RegisterPayloadHandlersEvent.class, NeoForgePlatformHelper::onRegisterPayloadHandlers);
        // FE energy capability on condensers, conduits and ZPC interfaces (Stellaris and other energy mods, all optional).
        modBus.addListener(RegisterCapabilitiesEvent.class, NeoForgeEnergyCompat::onRegisterCapabilities);

        IEventBus gameBus = NeoForge.EVENT_BUS;

        gameBus.addListener(EventPriority.LOWEST, LivingDeathEvent.class, event -> {
            if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
                RiftDeathRecoveryHandler.onPlayerDeath(player);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                RiftDeathRecoveryHandler.onPlayerRespawn(player);
            }
        });

        gameBus.addListener(LootTableLoadEvent.class, event -> {
            LootPool.Builder pool = ZpcStructureLoot.createExtraPool(event.getName());
            if (pool != null) {
                event.getTable().addPool(pool.build());
            }
        });

        gameBus.addListener(PlayerInteractEvent.RightClickBlock.class, event ->
                AncientCityZpcGuarantee.onUseBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec()));

        gameBus.addListener(RegisterCommandsEvent.class, event -> UniverseGate.registerCommands(event.getDispatcher()));

        gameBus.addListener(ServerStartingEvent.class, event -> UpdateCheckManager.onServerStarting(event.getServer()));
        gameBus.addListener(ServerStartedEvent.class, event -> UpdateCheckManager.onServerStarted(event.getServer()));
        gameBus.addListener(ServerStoppedEvent.class, event -> UpdateCheckManager.onServerStopped(event.getServer()));
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                UpdateCheckManager.onPlayerJoin(player);
            }
        });

        gameBus.addListener(ServerTickEvent.Post.class, event -> UniverseGate.onServerTickEnd(event.getServer()));
        gameBus.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                UniverseGate.onServerLevelTickEnd(level);
            }
        });

        UniverseGate.init();
    }
}
