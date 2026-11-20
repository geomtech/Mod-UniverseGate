package fr.geomtech.universegate.fabric;

import fr.geomtech.universegate.AncientCityZpcGuarantee;
import fr.geomtech.universegate.CrocodileClipEntity;
import fr.geomtech.universegate.RiftDeathRecoveryHandler;
import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.UpdateCheckManager;
import fr.geomtech.universegate.ZpcStructureLoot;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.level.storage.loot.LootPool;

public class UniverseGateFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		UniverseGate.registerContent();
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> CrocodileClipEntity.restoreMissing(chunk));

		// Optional energy compatibility (Stellaris and other Team Reborn Energy mods).
		if (FabricLoader.getInstance().isModLoaded("team_reborn_energy")) {
			fr.geomtech.universegate.fabric.compat.TeamRebornEnergyCompat.register();
		}

		ServerPlayerEvents.ALLOW_DEATH.register((player, source, amount) -> {
			RiftDeathRecoveryHandler.onPlayerDeath(player);
			return true;
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> RiftDeathRecoveryHandler.onPlayerRespawn(newPlayer));

		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!source.isBuiltin()) return;
			LootPool.Builder pool = ZpcStructureLoot.createExtraPool(key.location());
			if (pool != null) {
				tableBuilder.withPool(pool);
			}
		});

		UseBlockCallback.EVENT.register(AncientCityZpcGuarantee::onUseBlock);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> UniverseGate.registerCommands(dispatcher));

		ServerLifecycleEvents.SERVER_STARTING.register(UpdateCheckManager::onServerStarting);
		ServerLifecycleEvents.SERVER_STARTED.register(UpdateCheckManager::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(UpdateCheckManager::onServerStopped);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> UpdateCheckManager.onPlayerJoin(handler.player));

		ServerTickEvents.END_SERVER_TICK.register(UniverseGate::onServerTickEnd);
		ServerTickEvents.END_WORLD_TICK.register(UniverseGate::onServerLevelTickEnd);

		UniverseGate.init();
	}
}
