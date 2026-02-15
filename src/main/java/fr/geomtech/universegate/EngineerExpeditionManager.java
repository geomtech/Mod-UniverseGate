package fr.geomtech.universegate;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class EngineerExpeditionManager {

    private static final long MIN_EVENT_INTERVAL_TICKS = 24_000L * 4L;
    private static final long MAX_EVENT_INTERVAL_TICKS = 24_000L * 5L;
    private static final long RETRY_INTERVAL_TICKS = 24_000L;

    private static final long DESTINATION_WAIT_TICKS = 20L * 120L;
    private static final long PORTAL_OPEN_TIMEOUT_TICKS = 20L * 45L;
    private static final long CROSSING_TIMEOUT_TICKS = 20L * 60L;
    private static final long MAX_EXPEDITION_DURATION_TICKS = 20L * 60L * 8L;

    private static final int CORE_SEARCH_RADIUS_XZ = 8;
    private static final int CORE_SEARCH_RADIUS_Y = 4;
    private static final double PARTY_SEARCH_RADIUS = 40.0D;
    private static final double PLAYER_DETECTION_RADIUS = 28.0D;
    private static final double PARTY_MOVE_SPEED = 0.95D;
    private static final double HOME_ARRIVAL_RADIUS_SQR = 24.0D * 24.0D;

    private static final int MIN_COMPANIONS = 2;
    private static final int MAX_COMPANIONS = 3;

    private static final long FORCE_TELEPORT_AFTER_TICKS = 20L * 20L;

    private static ExpeditionState activeExpedition;

    private EngineerExpeditionManager() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EngineerExpeditionManager::tickServer);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("universegate")
                        .requires(source -> source.getEntity() instanceof ServerPlayer && source.hasPermission(2))
                        .then(Commands.literal("engineer_event")
                                .then(Commands.literal("force").executes(EngineerExpeditionManager::forceEventCommand))
                                .then(Commands.literal("status").executes(EngineerExpeditionManager::statusCommand))
                                .then(Commands.literal("cancel").executes(EngineerExpeditionManager::cancelCommand))
                        )
        ));
    }

    private static void tickServer(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        EngineerExpeditionSavedData data = EngineerExpeditionSavedData.get(server);

        if (data.getNextEventTick() < 0L) {
            data.setNextEventTick(now + randomEventInterval(overworld.getRandom()));
        }

        if (activeExpedition != null) {
            tickActiveExpedition(server, now);
            return;
        }

        if (now < data.getNextEventTick()) return;

        boolean started = tryStartExpedition(server, now, false);
        if (!started) {
            data.setNextEventTick(now + RETRY_INTERVAL_TICKS);
        }
    }

    private static void tickActiveExpedition(MinecraftServer server, long now) {
        ExpeditionState expedition = activeExpedition;
        if (expedition == null) return;

        if (now - expedition.startedAtTick > MAX_EXPEDITION_DURATION_TICKS) {
            abortExpedition(server, "timeout global");
            return;
        }

        ServerLevel sourceLevel = server.getLevel(expedition.sourceDim);
        ServerLevel targetLevel = server.getLevel(expedition.targetDim);
        if (sourceLevel == null || targetLevel == null) {
            abortExpedition(server, "dimension manquante");
            return;
        }

        sourceLevel.getChunk(expedition.sourceCorePos);
        targetLevel.getChunk(expedition.targetCorePos);

        Villager engineer = findVillager(server, expedition, expedition.engineerId);
        if (engineer == null || !engineer.isAlive()) {
            abortExpedition(server, "ingenieur introuvable");
            return;
        }

        switch (expedition.phase) {
            case OUTBOUND_CROSSING -> tickOutboundCrossing(server, expedition, sourceLevel, targetLevel, now);
            case WAITING_ON_DESTINATION -> tickDestinationWait(server, expedition, targetLevel, now, engineer);
            case OPENING_RETURN -> tickOpeningReturn(server, expedition, targetLevel, now);
            case RETURN_CROSSING -> tickReturnCrossing(server, expedition, sourceLevel, targetLevel, now);
        }
    }

    private static void tickOutboundCrossing(MinecraftServer server,
                                             ExpeditionState expedition,
                                             ServerLevel sourceLevel,
                                             ServerLevel targetLevel,
                                             long now) {
        PortalCoreBlockEntity sourceCore = getCore(sourceLevel, expedition.sourceCorePos);
        if (sourceCore == null || !sourceCore.isActiveOrOpening()) {
            abortExpedition(server, "portail source ferme pendant depart");
            return;
        }

        boolean forceTeleport = now - expedition.phaseStartedAtTick > FORCE_TELEPORT_AFTER_TICKS;
        guidePartyTowardPortal(sourceLevel, expedition.sourceCorePos, expedition.partyIds, forceTeleport);

        if (isEngineerNearCore(targetLevel, expedition.engineerId, expedition.targetCorePos)) {
            expedition.phase = ExpeditionPhase.WAITING_ON_DESTINATION;
            expedition.phaseStartedAtTick = now;
            expedition.waitUntilTick = now + DESTINATION_WAIT_TICKS;
            return;
        }

        if (now - expedition.phaseStartedAtTick > CROSSING_TIMEOUT_TICKS) {
            abortExpedition(server, "timeout trajet aller");
        }
    }

    private static void tickDestinationWait(MinecraftServer server,
                                            ExpeditionState expedition,
                                            ServerLevel targetLevel,
                                            long now,
                                            Villager engineer) {
        guidePartyAroundCore(targetLevel, expedition.targetCorePos, expedition.partyIds);

        if (!expedition.giftGiven) {
            ServerPlayer nearbyPlayer = findNearbyPlayer(targetLevel, engineer, expedition.targetCorePos, PLAYER_DETECTION_RADIUS);
            if (nearbyPlayer != null) {
                giveThanksGift(nearbyPlayer, targetLevel.getRandom());
                expedition.giftGiven = true;
                beginReturnPhase(expedition, now);
                return;
            }
        }

        if (now < expedition.waitUntilTick) return;
        beginReturnPhase(expedition, now);
    }

    private static void beginReturnPhase(ExpeditionState expedition, long now) {
        expedition.phase = ExpeditionPhase.OPENING_RETURN;
        expedition.phaseStartedAtTick = now;
    }

    private static void tickOpeningReturn(MinecraftServer server,
                                          ExpeditionState expedition,
                                          ServerLevel targetLevel,
                                          long now) {
        PortalCoreBlockEntity targetCore = getCore(targetLevel, expedition.targetCorePos);
        if (targetCore == null) {
            abortExpedition(server, "core destination introuvable");
            return;
        }

        if (targetCore.isActiveOrOpening()) {
            expedition.phaseStartedAtTick = now;
            guidePartyAroundCore(targetLevel, expedition.targetCorePos, expedition.partyIds);
            return;
        }

        if ((now - expedition.phaseStartedAtTick) % 20L == 0L) {
            BlockPos targetKeyboard = findKeyboardNear(targetLevel, expedition.targetCorePos, CORE_SEARCH_RADIUS_XZ, CORE_SEARCH_RADIUS_Y);
            boolean opened = tryOpenPortalForExpedition(
                    targetLevel,
                    expedition.targetCorePos,
                    expedition.sourcePortalId,
                    targetKeyboard
            );
            if (opened) {
                expedition.phase = ExpeditionPhase.RETURN_CROSSING;
                expedition.phaseStartedAtTick = now;
                return;
            }
        }

        if (now - expedition.phaseStartedAtTick > PORTAL_OPEN_TIMEOUT_TICKS) {
            abortExpedition(server, "timeout ouverture retour");
            return;
        }

        guidePartyAroundCore(targetLevel, expedition.targetCorePos, expedition.partyIds);
    }

    private static void tickReturnCrossing(MinecraftServer server,
                                           ExpeditionState expedition,
                                           ServerLevel sourceLevel,
                                           ServerLevel targetLevel,
                                           long now) {
        PortalCoreBlockEntity targetCore = getCore(targetLevel, expedition.targetCorePos);
        if (targetCore == null || !targetCore.isActiveOrOpening()) {
            abortExpedition(server, "portail retour ferme");
            return;
        }

        boolean forceTeleport = now - expedition.phaseStartedAtTick > FORCE_TELEPORT_AFTER_TICKS;
        guidePartyTowardPortal(targetLevel, expedition.targetCorePos, expedition.partyIds, forceTeleport);

        boolean engineerHome = isEngineerNearCore(sourceLevel, expedition.engineerId, expedition.sourceCorePos);
        int membersStillAway = countPartyMembersAwayFromHome(
                sourceLevel,
                targetLevel,
                expedition.sourceCorePos,
                expedition.partyIds
        );

        if (engineerHome && membersStillAway <= 0) {
            finishExpedition(server, "succes");
            return;
        }

        if (now - expedition.phaseStartedAtTick > CROSSING_TIMEOUT_TICKS) {
            if (engineerHome) {
                finishExpedition(server, "retour partiel");
            } else {
                abortExpedition(server, "timeout trajet retour");
            }
        }
    }

    private static boolean tryStartExpedition(MinecraftServer server, long now, boolean forced) {
        if (activeExpedition != null) return false;

        ExpeditionPlan plan = buildExpeditionPlan(server);
        if (plan == null) return false;

        ServerLevel sourceLevel = server.getLevel(plan.sourceDim);
        if (sourceLevel == null) return false;

        Villager engineer = getVillagerInLevel(sourceLevel, plan.engineerId);
        if (engineer == null) return false;
        ensureEngineerProfession(engineer);

        boolean opened = tryOpenPortalForExpedition(
                sourceLevel,
                plan.sourceCorePos,
                plan.targetPortalId,
                plan.sourceKeyboardPos
        );
        if (!opened) return false;

        List<UUID> partyIds = new ArrayList<>();
        partyIds.add(plan.engineerId);
        partyIds.addAll(plan.companionIds);

        activeExpedition = new ExpeditionState(
                plan.sourcePortalId,
                plan.sourceDim,
                plan.sourceCorePos,
                plan.sourceKeyboardPos,
                plan.targetPortalId,
                plan.targetDim,
                plan.targetCorePos,
                plan.engineerId,
                plan.companionIds,
                partyIds,
                now,
                now,
                forced
        );

        EngineerExpeditionSavedData.get(server).setNextEventTick(now + randomEventInterval(sourceLevel.getRandom()));

        UniverseGate.LOGGER.info(
                "Engineer expedition started: source={} {} target={} {} party={} forced={}",
                plan.sourceDim.location(),
                plan.sourceCorePos,
                plan.targetDim.location(),
                plan.targetCorePos,
                partyIds.size(),
                forced
        );

        return true;
    }

    private static void finishExpedition(MinecraftServer server, String reason) {
        ExpeditionState expedition = activeExpedition;
        if (expedition == null) return;

        ServerLevel sourceLevel = server.getLevel(expedition.sourceDim);
        ServerLevel targetLevel = server.getLevel(expedition.targetDim);

        if (sourceLevel != null) {
            closePortalIfOpen(sourceLevel, expedition.sourceCorePos);
            sendPartyHome(sourceLevel, expedition.sourceKeyboardPos, expedition.partyIds);
        }
        if (targetLevel != null && targetLevel != sourceLevel) {
            closePortalIfOpen(targetLevel, expedition.targetCorePos);
        }

        UniverseGate.LOGGER.info("Engineer expedition finished ({})", reason);
        activeExpedition = null;
    }

    private static void abortExpedition(MinecraftServer server, String reason) {
        finishExpedition(server, "abort: " + reason);
    }

    private static void closePortalIfOpen(ServerLevel level, BlockPos corePos) {
        PortalCoreBlockEntity core = getCore(level, corePos);
        if (core == null) return;
        if (!core.isActiveOrOpening()) return;
        PortalConnectionManager.forceCloseOneSide(level, corePos);
    }

    private static void sendPartyHome(ServerLevel level, BlockPos keyboardPos, List<UUID> partyIds) {
        double x = keyboardPos.getX() + 0.5;
        double y = keyboardPos.getY() + 0.5;
        double z = keyboardPos.getZ() + 0.5;

        for (UUID id : partyIds) {
            Villager villager = getVillagerInLevel(level, id);
            if (villager == null || !villager.isAlive()) continue;
            villager.getNavigation().moveTo(x, y, z, PARTY_MOVE_SPEED);
        }
    }

    private static void guidePartyAroundCore(ServerLevel level, BlockPos corePos, List<UUID> partyIds) {
        double x = corePos.getX() + 0.5;
        double y = corePos.getY() + 0.5;
        double z = corePos.getZ() + 0.5;

        for (UUID id : partyIds) {
            Villager villager = getVillagerInLevel(level, id);
            if (villager == null || !villager.isAlive()) continue;
            villager.getNavigation().moveTo(x, y, z, PARTY_MOVE_SPEED);
        }
    }

    private static void guidePartyTowardPortal(ServerLevel level,
                                               BlockPos corePos,
                                               List<UUID> partyIds,
                                               boolean forceTeleport) {
        List<BlockPos> fieldBlocks = collectPortalFields(level, corePos);
        if (fieldBlocks.isEmpty()) {
            guidePartyAroundCore(level, corePos, partyIds);
            return;
        }

        for (UUID id : partyIds) {
            Villager villager = getVillagerInLevel(level, id);
            if (villager == null || !villager.isAlive()) continue;

            BlockPos nearestField = findNearest(fieldBlocks, villager);
            if (nearestField == null) continue;

            double x = nearestField.getX() + 0.5;
            double y = nearestField.getY() + 0.5;
            double z = nearestField.getZ() + 0.5;
            villager.getNavigation().moveTo(x, y, z, PARTY_MOVE_SPEED);

            double distanceSqr = villager.distanceToSqr(x, y, z);
            if (forceTeleport || distanceSqr < 9.0D) {
                PortalTeleportHandler.tryTeleport(villager, nearestField);
            }
        }
    }

    private static List<BlockPos> collectPortalFields(ServerLevel level, BlockPos corePos) {
        List<BlockPos> fields = new ArrayList<>();
        var frame = PortalFrameDetector.find(level, corePos);
        if (frame.isEmpty()) return fields;

        for (BlockPos p : frame.get().interior()) {
            if (level.getBlockState(p).is(ModBlocks.PORTAL_FIELD)) {
                fields.add(p.immutable());
            }
        }
        return fields;
    }

    private static @Nullable BlockPos findNearest(List<BlockPos> positions, Entity entity) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : positions) {
            double dist = entity.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }
        return best;
    }

    private static boolean isEngineerNearCore(ServerLevel level, UUID engineerId, BlockPos corePos) {
        Villager villager = getVillagerInLevel(level, engineerId);
        if (villager == null || !villager.isAlive()) return false;
        return villager.distanceToSqr(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5) <= HOME_ARRIVAL_RADIUS_SQR;
    }

    private static int countPartyMembersAwayFromHome(ServerLevel sourceLevel,
                                                     ServerLevel targetLevel,
                                                     BlockPos sourceCorePos,
                                                     List<UUID> partyIds) {
        int away = 0;
        boolean sameDimension = sourceLevel == targetLevel;
        double homeX = sourceCorePos.getX() + 0.5;
        double homeY = sourceCorePos.getY() + 0.5;
        double homeZ = sourceCorePos.getZ() + 0.5;

        for (UUID id : partyIds) {
            Villager inSource = getVillagerInLevel(sourceLevel, id);
            if (inSource != null && inSource.isAlive()) {
                if (sameDimension && inSource.distanceToSqr(homeX, homeY, homeZ) > HOME_ARRIVAL_RADIUS_SQR) {
                    away++;
                }
                continue;
            }

            Villager inTarget = getVillagerInLevel(targetLevel, id);
            if (inTarget != null && inTarget.isAlive()) {
                away++;
            }
        }
        return away;
    }

    private static @Nullable Villager findVillager(MinecraftServer server, ExpeditionState expedition, UUID id) {
        ServerLevel source = server.getLevel(expedition.sourceDim);
        if (source != null) {
            Villager sourceVillager = getVillagerInLevel(source, id);
            if (sourceVillager != null) return sourceVillager;
        }

        ServerLevel target = server.getLevel(expedition.targetDim);
        if (target != null) {
            Villager targetVillager = getVillagerInLevel(target, id);
            if (targetVillager != null) return targetVillager;
        }

        return null;
    }

    private static @Nullable Villager getVillagerInLevel(ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        if (!(entity instanceof Villager villager)) return null;
        return villager;
    }

    private static @Nullable PortalCoreBlockEntity getCore(ServerLevel level, BlockPos corePos) {
        if (!(level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)) return null;
        return core;
    }

    private static @Nullable ExpeditionPlan buildExpeditionPlan(MinecraftServer server) {
        PortalRegistrySavedData registry = PortalRegistrySavedData.get(server);
        List<PortalRegistrySavedData.PortalEntry> possibleSources = new ArrayList<>(registry.listVisible());
        if (possibleSources.isEmpty()) return null;

        RandomSource random = server.overworld().getRandom();
        Collections.shuffle(possibleSources, new java.util.Random(random.nextLong()));

        for (PortalRegistrySavedData.PortalEntry sourceEntry : possibleSources) {
            if (sourceEntry.dim().equals(UniverseGateDimensions.RIFT)) continue;

            ServerLevel sourceLevel = server.getLevel(sourceEntry.dim());
            if (sourceLevel == null) continue;
            sourceLevel.getChunk(sourceEntry.pos());

            PortalCoreBlockEntity sourceCore = getCore(sourceLevel, sourceEntry.pos());
            if (sourceCore == null || sourceCore.isActiveOrOpening()) continue;

            BlockPos keyboardPos = findKeyboardNear(sourceLevel, sourceEntry.pos(), CORE_SEARCH_RADIUS_XZ, CORE_SEARCH_RADIUS_Y);
            if (keyboardPos == null) continue;

            List<Villager> nearbyVillagers = sourceLevel.getEntitiesOfClass(
                    Villager.class,
                    new AABB(keyboardPos).inflate(PARTY_SEARCH_RADIUS, 12.0D, PARTY_SEARCH_RADIUS),
                    villager -> villager.isAlive() && !villager.isBaby()
            );
            if (nearbyVillagers.size() < MIN_COMPANIONS + 1) continue;

            Villager engineer = selectEngineer(nearbyVillagers);
            if (engineer == null) continue;

            List<UUID> companionIds = pickCompanions(random, nearbyVillagers, engineer.getUUID());
            if (companionIds.size() < MIN_COMPANIONS) continue;

            PortalRegistrySavedData.PortalEntry targetEntry = pickTargetPortal(server, registry, sourceEntry.id(), random);
            if (targetEntry == null) continue;

            return new ExpeditionPlan(
                    sourceEntry.id(),
                    sourceEntry.dim(),
                    sourceEntry.pos().immutable(),
                    keyboardPos.immutable(),
                    targetEntry.id(),
                    targetEntry.dim(),
                    targetEntry.pos().immutable(),
                    engineer.getUUID(),
                    companionIds
            );
        }

        return null;
    }

    private static @Nullable Villager selectEngineer(List<Villager> villagers) {
        for (Villager villager : villagers) {
            if (!villager.isAlive()) continue;
            if (villager.getVillagerData().getProfession().equals(ModVillagers.ENGINEER)) {
                villager.setPersistenceRequired();
                return villager;
            }
        }

        for (Villager villager : villagers) {
            if (!villager.isAlive()) continue;
            VillagerProfession profession = villager.getVillagerData().getProfession();
            if (profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT) continue;
            ensureEngineerProfession(villager);
            return villager;
        }

        return null;
    }

    private static void ensureEngineerProfession(Villager villager) {
        if (villager.getVillagerData().getProfession().equals(ModVillagers.ENGINEER)) return;
        villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.ENGINEER));
        villager.setPersistenceRequired();
    }

    private static List<UUID> pickCompanions(RandomSource random, List<Villager> villagers, UUID engineerId) {
        List<UUID> pool = new ArrayList<>();
        for (Villager villager : villagers) {
            if (!villager.isAlive()) continue;
            if (villager.getUUID().equals(engineerId)) continue;
            pool.add(villager.getUUID());
        }

        Collections.shuffle(pool, new java.util.Random(random.nextLong()));
        int desired = MIN_COMPANIONS + random.nextInt(MAX_COMPANIONS - MIN_COMPANIONS + 1);
        int size = Math.min(desired, pool.size());
        if (size < MIN_COMPANIONS) return List.of();
        return new ArrayList<>(pool.subList(0, size));
    }

    private static @Nullable PortalRegistrySavedData.PortalEntry pickTargetPortal(MinecraftServer server,
                                                                                   PortalRegistrySavedData registry,
                                                                                   UUID sourcePortalId,
                                                                                   RandomSource random) {
        List<PortalRegistrySavedData.PortalEntry> candidates = new ArrayList<>();

        for (PortalRegistrySavedData.PortalEntry entry : registry.listAll()) {
            if (entry.id().equals(sourcePortalId)) continue;
            if (entry.dim().equals(UniverseGateDimensions.RIFT)) continue;
            ServerLevel level = server.getLevel(entry.dim());
            if (level == null) continue;
            level.getChunk(entry.pos());

            PortalCoreBlockEntity core = getCore(level, entry.pos());
            if (core == null || core.isActiveOrOpening()) continue;

            candidates.add(entry);
        }

        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static @Nullable BlockPos findKeyboardNear(ServerLevel level, BlockPos center, int radiusXZ, int radiusY) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dy = -radiusY; dy <= radiusY; dy++) {
            for (int dx = -radiusXZ; dx <= radiusXZ; dx++) {
                for (int dz = -radiusXZ; dz <= radiusXZ; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!level.getBlockState(p).is(ModBlocks.PORTAL_KEYBOARD)) continue;
                    double dist = p.distSqr(center);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = p;
                    }
                }
            }
        }

        return best;
    }

    private static boolean tryOpenPortalForExpedition(ServerLevel level,
                                                      BlockPos corePos,
                                                      UUID targetPortalId,
                                                      @Nullable BlockPos keyboardPos) {
        if (!prepareVillagerEnergyNetwork(level, corePos, keyboardPos)) {
            return false;
        }

        boolean opened = PortalConnectionManager.openBothSides(level, corePos, targetPortalId);
        if (!opened) return false;

        if (!EnergyNetworkHelper.isRiftDimension(level)) {
            boolean consumed = EnergyNetworkHelper.consumePortalEnergy(
                    level,
                    corePos,
                    EnergyNetworkHelper.PORTAL_OPEN_ENERGY_COST
            );
            if (!consumed) {
                PortalConnectionManager.forceCloseOneSide(level, corePos);
                return false;
            }
        }

        return true;
    }

    private static boolean prepareVillagerEnergyNetwork(ServerLevel level,
                                                        BlockPos corePos,
                                                        @Nullable BlockPos keyboardPos) {
        if (EnergyNetworkHelper.isRiftDimension(level)) {
            return true;
        }

        int required = EnergyNetworkHelper.PORTAL_OPEN_ENERGY_COST;
        int available = EnergyNetworkHelper.getAvailableEnergyForPortal(level, corePos);
        if (available >= required) {
            return true;
        }

        BlockPos condenserPos = buildVillageEnergyInfrastructure(level, corePos, keyboardPos);
        if (condenserPos == null) {
            return false;
        }

        available = EnergyNetworkHelper.getAvailableEnergyForPortal(level, corePos);
        if (available >= required) {
            return true;
        }

        EnergyNetworkHelper.CondenserNetwork network = EnergyNetworkHelper.scanCondenserNetwork(level, condenserPos);
        int missing = required - available;
        if (missing > 0 && !network.condensers().isEmpty()) {
            EnergyNetworkHelper.distributeEnergy(level, network.condensers(), missing);
        }

        return EnergyNetworkHelper.getAvailableEnergyForPortal(level, corePos) >= required;
    }

    private static @Nullable BlockPos buildVillageEnergyInfrastructure(ServerLevel level,
                                                                       BlockPos corePos,
                                                                       @Nullable BlockPos keyboardPos) {
        for (Direction direction : preferredEnergyDirections(level, corePos, keyboardPos)) {
            EnergyLayout layout = new EnergyLayout(
                    direction,
                    corePos.relative(direction, 1),
                    corePos.relative(direction, 2),
                    corePos.relative(direction, 3),
                    corePos.relative(direction, 4)
            );

            if (!canPlaceEnergyLayout(level, layout)) {
                continue;
            }

            placeEnergyLayout(level, layout);
            return layout.condenserPos();
        }

        return null;
    }

    private static List<Direction> preferredEnergyDirections(ServerLevel level,
                                                             BlockPos corePos,
                                                             @Nullable BlockPos keyboardPos) {
        List<Direction> directions = new ArrayList<>();

        PortalFrameDetector.find(level, corePos).ifPresent(match -> {
            Direction normalA = match.right() == Direction.EAST ? Direction.SOUTH : Direction.EAST;
            Direction normalB = normalA.getOpposite();
            directions.add(normalA);
            directions.add(normalB);
            directions.add(match.right());
            directions.add(match.right().getOpposite());
        });

        if (keyboardPos != null) {
            Direction awayFromKeyboard = Direction.getNearest(
                    corePos.getX() - keyboardPos.getX(),
                    0,
                    corePos.getZ() - keyboardPos.getZ()
            );
            if (awayFromKeyboard.getAxis().isHorizontal()) {
                directions.add(0, awayFromKeyboard);
            }
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!directions.contains(direction)) {
                directions.add(direction);
            }
        }

        return directions;
    }

    private static boolean canPlaceEnergyLayout(ServerLevel level, EnergyLayout layout) {
        Direction panelFacing = layout.direction();

        if (!canPlaceOrMatch(level.getBlockState(layout.conduitNearCore()), ModBlocks.ENERGY_CONDUIT)) return false;
        if (!canPlaceOrMatch(level.getBlockState(layout.condenserPos()), ModBlocks.ENERGY_CONDENSER)) return false;
        if (!canPlaceOrMatch(level.getBlockState(layout.conduitNearPanel()), ModBlocks.ENERGY_CONDUIT)) return false;

        BlockPos panelBase = layout.panelBasePos();
        BlockState base = level.getBlockState(panelBase);
        BlockState lower = level.getBlockState(panelBase.above());
        BlockState upper = level.getBlockState(panelBase.above(2));
        BlockState top = level.getBlockState(panelBase.above(3));

        boolean baseOk = isSolarPart(base, SolarPanelBlock.PanelPart.BASE, panelFacing) || base.canBeReplaced();
        boolean lowerOk = isSolarPart(lower, SolarPanelBlock.PanelPart.LOWER, panelFacing) || lower.canBeReplaced();
        boolean upperOk = isSolarPart(upper, SolarPanelBlock.PanelPart.UPPER, panelFacing) || upper.canBeReplaced();
        boolean topOk = isSolarPart(top, SolarPanelBlock.PanelPart.TOP, panelFacing) || top.canBeReplaced();
        if (!baseOk || !lowerOk || !upperOk || !topOk) return false;

        BlockState belowPanel = level.getBlockState(panelBase.below());
        return belowPanel.isFaceSturdy(level, panelBase.below(), Direction.UP)
                || belowPanel.is(ModBlocks.ENERGY_CONDUIT);
    }

    private static void placeEnergyLayout(ServerLevel level, EnergyLayout layout) {
        level.setBlock(layout.conduitNearCore(), ModBlocks.ENERGY_CONDUIT.defaultBlockState(), 3);
        level.setBlock(layout.condenserPos(), ModBlocks.ENERGY_CONDENSER.defaultBlockState(), 3);
        level.setBlock(layout.conduitNearPanel(), ModBlocks.ENERGY_CONDUIT.defaultBlockState(), 3);

        Direction panelFacing = layout.direction();
        BlockPos panelBase = layout.panelBasePos();
        level.setBlock(panelBase, ModBlocks.SOLAR_PANEL.defaultBlockState()
                .setValue(SolarPanelBlock.FACING, panelFacing)
                .setValue(SolarPanelBlock.PART, SolarPanelBlock.PanelPart.BASE), 3);
        level.setBlock(panelBase.above(), ModBlocks.SOLAR_PANEL.defaultBlockState()
                .setValue(SolarPanelBlock.FACING, panelFacing)
                .setValue(SolarPanelBlock.PART, SolarPanelBlock.PanelPart.LOWER), 3);
        level.setBlock(panelBase.above(2), ModBlocks.SOLAR_PANEL.defaultBlockState()
                .setValue(SolarPanelBlock.FACING, panelFacing)
                .setValue(SolarPanelBlock.PART, SolarPanelBlock.PanelPart.UPPER), 3);
        level.setBlock(panelBase.above(3), ModBlocks.SOLAR_PANEL.defaultBlockState()
                .setValue(SolarPanelBlock.FACING, panelFacing)
                .setValue(SolarPanelBlock.PART, SolarPanelBlock.PanelPart.TOP), 3);
    }

    private static boolean canPlaceOrMatch(BlockState state, net.minecraft.world.level.block.Block target) {
        return state.is(target) || state.canBeReplaced();
    }

    private static boolean isSolarPart(BlockState state, SolarPanelBlock.PanelPart part, Direction facing) {
        return state.is(ModBlocks.SOLAR_PANEL)
                && state.hasProperty(SolarPanelBlock.PART)
                && state.hasProperty(SolarPanelBlock.FACING)
                && state.getValue(SolarPanelBlock.PART) == part
                && state.getValue(SolarPanelBlock.FACING) == facing;
    }

    private record EnergyLayout(Direction direction,
                                BlockPos conduitNearCore,
                                BlockPos condenserPos,
                                BlockPos conduitNearPanel,
                                BlockPos panelBasePos) {
    }

    private static @Nullable ServerPlayer findNearbyPlayer(ServerLevel level,
                                                           Villager engineer,
                                                           BlockPos fallbackPos,
                                                           double radius) {
        double cx;
        double cy;
        double cz;
        if (engineer.level() == level) {
            cx = engineer.getX();
            cy = engineer.getY();
            cz = engineer.getZ();
        } else {
            cx = fallbackPos.getX() + 0.5;
            cy = fallbackPos.getY() + 0.5;
            cz = fallbackPos.getZ() + 0.5;
        }

        AABB box = new AABB(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius);
        List<ServerPlayer> players = level.getEntitiesOfClass(
                ServerPlayer.class,
                box,
                player -> player.isAlive() && !player.isSpectator()
        );

        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer player : players) {
            double dist = player.distanceToSqr(cx, cy, cz);
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    private static void giveThanksGift(ServerPlayer player, RandomSource random) {
        List<ItemStack> gifts = createGiftBundle(random);
        for (ItemStack gift : gifts) {
            ItemStack stack = gift.copy();
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        }

        player.displayClientMessage(Component.translatable("message.universegate.engineer_gift"), false);
    }

    private static List<ItemStack> createGiftBundle(RandomSource random) {
        List<ItemStack> gifts = new ArrayList<>();

        gifts.add(new ItemStack(ModItems.SOLAR_PANEL_ITEM, 1));
        gifts.add(new ItemStack(ModItems.ENERGY_CONDENSER_ITEM, 1));
        gifts.add(new ItemStack(ModItems.ENERGY_CONDUIT_ITEM, randomBetween(random, 12, 20)));
        gifts.add(new ItemStack(Items.EMERALD, randomBetween(random, 1, 3)));

        switch (random.nextInt(6)) {
            case 0 -> gifts.add(new ItemStack(Items.IRON_INGOT, randomBetween(random, 6, 12)));
            case 1 -> gifts.add(new ItemStack(Items.REDSTONE, randomBetween(random, 10, 20)));
            case 2 -> gifts.add(new ItemStack(Items.QUARTZ, randomBetween(random, 8, 14)));
            case 3 -> gifts.add(new ItemStack(Items.GLOWSTONE_DUST, randomBetween(random, 8, 14)));
            case 4 -> gifts.add(new ItemStack(Items.OBSIDIAN, randomBetween(random, 2, 4)));
            default -> gifts.add(new ItemStack(Items.ENDER_PEARL, randomBetween(random, 1, 2)));
        }

        if (random.nextFloat() < 0.30F) {
            switch (random.nextInt(5)) {
                case 0 -> gifts.add(new ItemStack(ModItems.KELO_LOG_ITEM, randomBetween(random, 8, 16)));
                case 1 -> gifts.add(new ItemStack(ModItems.KELO_PLANKS_ITEM, randomBetween(random, 16, 32)));
                case 2 -> gifts.add(new ItemStack(ModItems.WHITE_PURPUR_BLOCK_ITEM, randomBetween(random, 6, 12)));
                case 3 -> gifts.add(new ItemStack(ModItems.WHITE_PURPUR_PILLAR_ITEM, randomBetween(random, 6, 12)));
                default -> gifts.add(new ItemStack(ModItems.LIGHT_BLOCK_ITEM, randomBetween(random, 4, 8)));
            }
        }

        if (random.nextFloat() < 0.10F) {
            gifts.add(new ItemStack(Items.DIAMOND, 1));
        }

        return gifts;
    }

    private static int randomBetween(RandomSource random, int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) return minInclusive;
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    private static long randomEventInterval(RandomSource random) {
        return randomBetween(random, (int) MIN_EVENT_INTERVAL_TICKS, (int) MAX_EVENT_INTERVAL_TICKS);
    }

    private static int forceEventCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        long now = server.overworld().getGameTime();

        if (activeExpedition != null) {
            source.sendFailure(Component.literal("Un evenement ingenieur est deja en cours."));
            return 0;
        }

        boolean started = tryStartExpedition(server, now, true);
        if (!started) {
            source.sendFailure(Component.literal("Aucune expedition ingenieur n'a pu etre lancee (village/portail introuvable)."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Evenement ingenieur force."), true);
        return 1;
    }

    private static int statusCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        long now = server.overworld().getGameTime();
        long next = EngineerExpeditionSavedData.get(server).getNextEventTick();

        if (activeExpedition == null) {
            long remaining = next < 0L ? -1L : Math.max(0L, next - now);
            source.sendSuccess(() -> Component.literal(
                    "Evenement ingenieur: inactif, prochain dans " + formatTicks(remaining)
            ), false);
            return 1;
        }

        ExpeditionState expedition = activeExpedition;
        long phaseElapsed = Math.max(0L, now - expedition.phaseStartedAtTick);
        long waitRemaining = expedition.phase == ExpeditionPhase.WAITING_ON_DESTINATION
                ? Math.max(0L, expedition.waitUntilTick - now)
                : 0L;

        source.sendSuccess(() -> Component.literal(
                "Evenement ingenieur: actif phase=" + expedition.phase +
                        ", elapsed=" + formatTicks(phaseElapsed) +
                        (expedition.phase == ExpeditionPhase.WAITING_ON_DESTINATION
                                ? ", attente restante=" + formatTicks(waitRemaining)
                                : "")
        ), false);
        return 1;
    }

    private static int cancelCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        if (activeExpedition == null) {
            source.sendFailure(Component.literal("Aucun evenement ingenieur actif."));
            return 0;
        }

        abortExpedition(server, "annule via commande");
        source.sendSuccess(() -> Component.literal("Evenement ingenieur annule."), true);
        return 1;
    }

    private static String formatTicks(long ticks) {
        if (ticks < 0L) return "n/a";
        long seconds = ticks / 20L;
        long minutes = seconds / 60L;
        long remSeconds = seconds % 60L;
        return minutes + "m" + remSeconds + "s";
    }

    private enum ExpeditionPhase {
        OUTBOUND_CROSSING,
        WAITING_ON_DESTINATION,
        OPENING_RETURN,
        RETURN_CROSSING
    }

    private record ExpeditionPlan(UUID sourcePortalId,
                                  ResourceKey<Level> sourceDim,
                                  BlockPos sourceCorePos,
                                  BlockPos sourceKeyboardPos,
                                  UUID targetPortalId,
                                  ResourceKey<Level> targetDim,
                                  BlockPos targetCorePos,
                                  UUID engineerId,
                                  List<UUID> companionIds) {}

    private static final class ExpeditionState {
        private final UUID sourcePortalId;
        private final ResourceKey<Level> sourceDim;
        private final BlockPos sourceCorePos;
        private final BlockPos sourceKeyboardPos;
        private final UUID targetPortalId;
        private final ResourceKey<Level> targetDim;
        private final BlockPos targetCorePos;
        private final UUID engineerId;
        private final List<UUID> companionIds;
        private final List<UUID> partyIds;
        private final long startedAtTick;
        private long phaseStartedAtTick;
        private long waitUntilTick;
        private ExpeditionPhase phase;
        private boolean giftGiven;
        @SuppressWarnings("unused")
        private final boolean forced;

        private ExpeditionState(UUID sourcePortalId,
                                ResourceKey<Level> sourceDim,
                                BlockPos sourceCorePos,
                                BlockPos sourceKeyboardPos,
                                UUID targetPortalId,
                                ResourceKey<Level> targetDim,
                                BlockPos targetCorePos,
                                UUID engineerId,
                                List<UUID> companionIds,
                                List<UUID> partyIds,
                                long startedAtTick,
                                long phaseStartedAtTick,
                                boolean forced) {
            this.sourcePortalId = sourcePortalId;
            this.sourceDim = sourceDim;
            this.sourceCorePos = sourceCorePos;
            this.sourceKeyboardPos = sourceKeyboardPos;
            this.targetPortalId = targetPortalId;
            this.targetDim = targetDim;
            this.targetCorePos = targetCorePos;
            this.engineerId = engineerId;
            this.companionIds = List.copyOf(companionIds);
            this.partyIds = List.copyOf(partyIds);
            this.startedAtTick = startedAtTick;
            this.phaseStartedAtTick = phaseStartedAtTick;
            this.phase = ExpeditionPhase.OUTBOUND_CROSSING;
            this.waitUntilTick = 0L;
            this.giftGiven = false;
            this.forced = forced;
        }
    }
}
