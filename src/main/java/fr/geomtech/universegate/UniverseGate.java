package fr.geomtech.universegate;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UniverseGate implements ModInitializer {
	public static final String MOD_ID = "universegate";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		ModBlocks.register();
		ModFluids.register();
		ModEntityTypes.register();
		ModItems.register();
		ModItemGroups.register();
		ModBlockEntities.register();
		ModFeatures.register();
		ModMenuTypes.register();
		ModSounds.register();
		ModVillagers.register();
		UniverseGatePoiHelper.registerChargedLightningRodPoi();
		RiftDeathRecoveryHandler.register();
		EngineerExpeditionManager.register();
		WeatherMachineCommand.register();
		UpdateCheckManager.register();
		ServerTickEvents.END_WORLD_TICK.register(RiftShadeSpawner::tickWorld);
		ServerTickEvents.END_WORLD_TICK.register(RiftCubeGenerator::tickWorld);
		ServerTickEvents.END_WORLD_TICK.register(PortalRiftHelper::tickWorld);
		ServerTickEvents.END_WORLD_TICK.register(OverworldVillagePortalGenerator::tickWorld);
		fr.geomtech.universegate.net.UniverseGateNetwork.registerCommon();


		LOGGER.info("UniverseGate Initialized");
	}
}
