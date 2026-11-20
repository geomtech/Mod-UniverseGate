package fr.geomtech.universegate;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-independent entry point. The Fabric and NeoForge modules call these methods
 * from their own initializers and event hooks.
 */
public final class UniverseGate {
	public static final String MOD_ID = "universegate";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static boolean contentRegistered = false;

	private UniverseGate() {}

	/**
	 * Registers every block, item, entity, menu... into the vanilla registries.
	 * Must run while the registries are unfrozen (Fabric: mod init, NeoForge: RegisterEvent).
	 */
	public static void registerContent() {
		if (contentRegistered) return;
		contentRegistered = true;

		ModBlocks.register();
		ModFluids.register();
		ModEntityTypes.register();
		ModItems.register();
		ModItemGroups.register();
		ModBlockEntities.register();
		ModFeatures.register();
		ModMenuTypes.register();
		ModSounds.register();
		ModParticles.register();
		ModVillagers.register();
		UniverseGatePoiHelper.registerChargedLightningRodPoi();
	}

	/** Registers network payloads. Does not touch registries. */
	public static void init() {
		fr.geomtech.universegate.net.UniverseGateNetwork.registerCommon();
		LOGGER.info("UniverseGate Initialized");
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		EngineerExpeditionManager.registerCommands(dispatcher);
		WeatherMachineCommand.registerCommands(dispatcher);
	}

	/** Called at the end of every server tick. */
	public static void onServerTickEnd(MinecraftServer server) {
		EngineerExpeditionManager.tickServer(server);
		UpdateCheckManager.runPeriodicCheck(server);
	}

	/** Called at the end of every server level tick. */
	public static void onServerLevelTickEnd(ServerLevel level) {
		RiftShadeSpawner.tickWorld(level);
		RiftCubeGenerator.tickWorld(level);
		PortalRiftHelper.tickWorld(level);
		PortalPursuitTracker.tickWorld(level);
		OverworldVillagePortalGenerator.tickWorld(level);
	}
}
