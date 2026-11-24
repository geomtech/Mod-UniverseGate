package fr.geomtech.universegate;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(UniverseGate.MOD_ID)
public class UniverseGate {
	public static final String MOD_ID = "universegate";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public UniverseGate(IEventBus modEventBus) {
		ModFluidTypes.register(modEventBus);
		modEventBus.addListener(NeoForgeRegistryBootstrap::registerEntries);
		modEventBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) -> ModEntityTypes.registerAttributes(event));
		modEventBus.addListener((net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) -> ModEntityTypes.registerSpawnPlacements(event));
		modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(UniverseGatePoiHelper::registerChargedLightningRodPoi));
		onInitialize();
		if (FMLEnvironment.dist == Dist.CLIENT) registerClient(modEventBus);
	}

	private static void registerClient(IEventBus modEventBus) {
		try {
			Class<?> clientEntrypoint = Class.forName("fr.geomtech.universegate.UniverseGateClient");
			clientEntrypoint.getMethod("register", IEventBus.class).invoke(null, modEventBus);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Could not initialize the UniverseGate client", exception);
		}
	}

	public void onInitialize() {
		RiftDeathRecoveryHandler.register();
		EngineerExpeditionManager.register();
		ZpcStructureLoot.register();
		AncientCityZpcGuarantee.register();
		WeatherMachineCommand.register();
		AcidRainManager.register();
		AcidTechnologyManager.register();
		UpdateCheckManager.register();
		ServerTickEvents.END_WORLD_TICK.register(RiftShadeSpawner::tickWorld);
		ServerTickEvents.END_WORLD_TICK.register(RiftCubeGenerator::tickWorld);
		ServerTickEvents.END_WORLD_TICK.register(PortalRiftHelper::tickWorld);
		ServerTickEvents.END_WORLD_TICK.register(PortalPursuitTracker::tickWorld);
		ServerTickEvents.END_WORLD_TICK.register(OverworldVillagePortalGenerator::tickWorld);
		ServerTickEvents.END_WORLD_TICK.register(AcidRainManager::tickWorld);
		fr.geomtech.universegate.net.UniverseGateNetwork.registerCommon();
		LOGGER.info("UniverseGate Initialized for NeoForge");
	}
}
