package fr.geomtech.universegate;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class OverworldVillagePortalGenerator {

    private static final long CHECK_EVERY_TICKS = 20L * 30L;
    private static final int VILLAGE_SEARCH_RADIUS_CHUNKS = 24;
    private static final int MIN_ADULT_VILLAGERS = 9;
    private static final int MIN_VILLAGE_SPREAD_BLOCKS = 18;
    private static final int VILLAGER_COUNT_RADIUS = 52;
    private static final int VILLAGE_PRELOAD_RADIUS_CHUNKS = 2;
    private static final int EXISTING_PORTAL_RADIUS = 40;
    private static final int MIN_CORE_SEPARATION = 18;
    private static final int MIN_PORTAL_OFFSET = 10;
    private static final int MAX_PORTAL_OFFSET = 30;
    private static final int PORTAL_PLACEMENT_ATTEMPTS = 28;
    private static final int BOOTSTRAP_TARGET_PORTALS = 7;
    private static final int BOOTSTRAP_SEARCH_RADIUS_CHUNKS = 40;
    private static final int BOOTSTRAP_MIN_DISTANCE_FROM_SPAWN = 1200;
    private static final double BOOTSTRAP_GOLDEN_ANGLE = 2.399963229728653D;
    private static final int BOOTSTRAP_BASE_RADIUS = 900;
    private static final int BOOTSTRAP_RADIUS_STEP = 180;
    private static final String GENERATED_PORTAL_NAME = "Village Gate";

    // --- Lag guards -------------------------------------------------------------------------
    // Locating villages (like /locate) and loading/generating chunks synchronously on the main
    // thread are the most expensive things this generator does. Keep them rare.
    /** A player's nearest village is only re-located after moving this far, or after PLAYER_RESEARCH_TICKS. */
    private static final int PLAYER_RESEARCH_MOVE_CHUNKS = 8;
    private static final long PLAYER_RESEARCH_TICKS = 20L * 60L * 5L;
    /** A village that was too small (or had no room for a portal) is re-checked after this delay. */
    private static final long REJECTED_VILLAGE_RETRY_TICKS = 20L * 60L * 10L;
    /** Remote "bootstrap" villages: one probe at most every 2 minutes. */
    private static final long BOOTSTRAP_EVERY_TICKS = 20L * 60L * 2L;
    /** How many bootstrap cycles we wait for an asynchronously loaded village before giving up. */
    private static final int BOOTSTRAP_MAX_WAIT_CYCLES = 5;
    private static final long VILLAGE_TICKET_TIMEOUT = BOOTSTRAP_EVERY_TICKS * (BOOTSTRAP_MAX_WAIT_CYCLES + 1);
    //? if >=1.21.11 {
    /*// Since 1.21.5 ticket types are registry entries, see registerTicketType().
    private static TicketType VILLAGE_TICKET;
    *///?} else {
    private static final TicketType<ChunkPos> VILLAGE_TICKET = TicketType.create(
            UniverseGate.MOD_ID + ":village_portal",
            Comparator.comparingLong(ChunkPos::toLong),
            (int) VILLAGE_TICKET_TIMEOUT);
    //?}

    /** Registers the chunk ticket type; must run while the registries are unfrozen. */
    public static void registerTicketType() {
        //? if >=1.21.11 {
        /*VILLAGE_TICKET = Registry.register(
                BuiltInRegistries.TICKET_TYPE,
                ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "village_portal"),
                new TicketType(VILLAGE_TICKET_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION)
        );
        *///?}
    }

    private record PlayerSearch(ChunkPos origin, long searchedAt, @Nullable BlockPos village) {}

    private static final class LevelState {
        final Map<UUID, PlayerSearch> playerSearches = new HashMap<>();
        final Map<Long, Long> retryAfter = new HashMap<>();
        @Nullable BlockPos pendingBootstrapVillage;
        int pendingBootstrapWaitCycles;
    }

    private static final Map<ServerLevel, LevelState> STATES = new WeakHashMap<>();

    private OverworldVillagePortalGenerator() {}

    public static void tickWorld(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        long now = level.getGameTime();
        if (now % CHECK_EVERY_TICKS != 0L) return;

        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        VillagePortalSavedData data = VillagePortalSavedData.get(level.getServer());
        LevelState state = STATES.computeIfAbsent(level, ignored -> new LevelState());
        state.retryAfter.values().removeIf(retryAt -> retryAt <= now);

        Set<Long> checkedThisTick = new HashSet<>();
        Set<UUID> online = new HashSet<>();

        for (ServerPlayer player : players) {
            if (player.isSpectator()) continue;
            online.add(player.getUUID());

            BlockPos villageCenter = nearestVillageFor(level, state, player, now);
            if (villageCenter == null) continue;

            long villageKey = packVillage(villageCenter);
            if (!checkedThisTick.add(villageKey)) continue;
            if (data.isProcessed(villageCenter)) continue;
            if (state.retryAfter.containsKey(villageKey)) continue;

            // Only look at a village once its chunks are already loaded around a player:
            // never load or generate them synchronously from here.
            if (!isVillageAreaLoaded(level, villageCenter)) continue;

            processVillage(level, data, state, villageCenter, now);
        }

        state.playerSearches.keySet().retainAll(online);

        if (now % BOOTSTRAP_EVERY_TICKS == 0L) {
            bootstrapUndiscoveredVillagePortals(level, data, state, now);
        }
    }

    private static @Nullable BlockPos nearestVillageFor(ServerLevel level, LevelState state, ServerPlayer player, long now) {
        ChunkPos playerChunk = player.chunkPosition();
        PlayerSearch previous = state.playerSearches.get(player.getUUID());
        if (previous != null
                && now - previous.searchedAt() < PLAYER_RESEARCH_TICKS
                && previous.origin().getChessboardDistance(playerChunk) < PLAYER_RESEARCH_MOVE_CHUNKS) {
            return previous.village();
        }

        BlockPos village = findNearestVillageCenter(level, player.blockPosition(), VILLAGE_SEARCH_RADIUS_CHUNKS);
        state.playerSearches.put(player.getUUID(), new PlayerSearch(playerChunk, now, village));
        return village;
    }

    /** @return true when a portal was generated for this village. */
    private static boolean processVillage(ServerLevel level,
                                          VillagePortalSavedData data,
                                          LevelState state,
                                          BlockPos villageCenter,
                                          long now) {
        VillageSize villageSize = evaluateVillageSize(level, villageCenter, VILLAGER_COUNT_RADIUS);
        if (villageSize.adultVillagers() < MIN_ADULT_VILLAGERS
                || villageSize.maxHorizontalDistance() < MIN_VILLAGE_SPREAD_BLOCKS) {
            state.retryAfter.put(packVillage(villageCenter), now + REJECTED_VILLAGE_RETRY_TICKS);
            return false;
        }

        if (hasPortalCoreNear(level, villageCenter, EXISTING_PORTAL_RADIUS)) {
            data.markProcessed(villageCenter);
            return false;
        }

        if (tryPlaceVillagePortal(level, villageCenter)) {
            data.markProcessed(villageCenter);
            return true;
        }

        state.retryAfter.put(packVillage(villageCenter), now + REJECTED_VILLAGE_RETRY_TICKS);
        return false;
    }

    private static boolean isVillageAreaLoaded(ServerLevel level, BlockPos villageCenter) {
        int centerChunkX = villageCenter.getX() >> 4;
        int centerChunkZ = villageCenter.getZ() >> 4;
        for (int dx = -VILLAGE_PRELOAD_RADIUS_CHUNKS; dx <= VILLAGE_PRELOAD_RADIUS_CHUNKS; dx++) {
            for (int dz = -VILLAGE_PRELOAD_RADIUS_CHUNKS; dz <= VILLAGE_PRELOAD_RADIUS_CHUNKS; dz++) {
                if (!level.hasChunk(centerChunkX + dx, centerChunkZ + dz)) return false;
            }
        }
        return true;
    }

    private static @Nullable BlockPos findNearestVillageCenter(ServerLevel level, BlockPos from, int radiusChunks) {
        return level.findNearestMapStructure(StructureTags.VILLAGE, from, radiusChunks, false);
    }

    private static VillageSize evaluateVillageSize(ServerLevel level, BlockPos center, int radius) {
        AABB box = new AABB(
                center.getX() - radius,
                level.getMinBuildHeight(),
                center.getZ() - radius,
                center.getX() + radius + 1,
                level.getMaxBuildHeight(),
                center.getZ() + radius + 1
        );
        List<Villager> villagers = level.getEntitiesOfClass(
                Villager.class,
                box,
                Villager::isAlive
        );

        int adults = 0;
        double maxDistanceSqr = 0.0D;
        double cx = center.getX() + 0.5D;
        double cz = center.getZ() + 0.5D;

        for (Villager villager : villagers) {
            if (villager.isBaby()) continue;
            adults++;

            double dx = villager.getX() - cx;
            double dz = villager.getZ() - cz;
            double distanceSqr = (dx * dx) + (dz * dz);
            if (distanceSqr > maxDistanceSqr) {
                maxDistanceSqr = distanceSqr;
            }
        }

        return new VillageSize(adults, Math.sqrt(maxDistanceSqr));
    }

    private static void requestVillageChunks(ServerLevel level, BlockPos villageCenter) {
        ChunkPos center = new ChunkPos(villageCenter);
        //? if >=1.21.11 {
        /*level.getChunkSource().addTicketWithRadius(VILLAGE_TICKET, center, VILLAGE_PRELOAD_RADIUS_CHUNKS);
        *///?} else {
        level.getChunkSource().addRegionTicket(VILLAGE_TICKET, center, VILLAGE_PRELOAD_RADIUS_CHUNKS, center);
        //?}
    }

    private static void releaseVillageChunks(ServerLevel level, BlockPos villageCenter) {
        ChunkPos center = new ChunkPos(villageCenter);
        //? if >=1.21.11 {
        /*level.getChunkSource().removeTicketWithRadius(VILLAGE_TICKET, center, VILLAGE_PRELOAD_RADIUS_CHUNKS);
        *///?} else {
        level.getChunkSource().removeRegionTicket(VILLAGE_TICKET, center, VILLAGE_PRELOAD_RADIUS_CHUNKS, center);
        //?}
    }

    private static void bootstrapUndiscoveredVillagePortals(ServerLevel level,
                                                            VillagePortalSavedData data,
                                                            LevelState state,
                                                            long now) {
        // A remote village was requested earlier: its chunks load in the background through a
        // chunk ticket instead of being generated synchronously on the server thread.
        BlockPos pending = state.pendingBootstrapVillage;
        if (pending != null) {
            if (isVillageAreaLoaded(level, pending)) {
                if (!data.isProcessed(pending) && processVillage(level, data, state, pending, now)) {
                    data.incrementBootstrapGeneratedPortals();
                }
            } else if (++state.pendingBootstrapWaitCycles <= BOOTSTRAP_MAX_WAIT_CYCLES) {
                return;
            }
            releaseVillageChunks(level, pending);
            state.pendingBootstrapVillage = null;
            state.pendingBootstrapWaitCycles = 0;
            return;
        }

        if (!hasAnyOverworldPortal(level)) return;
        if (data.getBootstrapGeneratedPortals() >= BOOTSTRAP_TARGET_PORTALS) return;

        //? if >=1.21.11 {
        /*BlockPos spawnPos = level.getRespawnData().pos();
        *///?} else {
        BlockPos spawnPos = level.getSharedSpawnPos();
        //?}
        long minDistanceSqr = (long) BOOTSTRAP_MIN_DISTANCE_FROM_SPAWN * (long) BOOTSTRAP_MIN_DISTANCE_FROM_SPAWN;

        long index = data.nextBootstrapIndex();
        BlockPos probe = computeBootstrapProbe(spawnPos, index);

        BlockPos villageCenter = findNearestVillageCenter(level, probe, BOOTSTRAP_SEARCH_RADIUS_CHUNKS);
        if (villageCenter == null) return;
        if (horizontalDistanceSqr(villageCenter, spawnPos) < minDistanceSqr) return;
        if (data.isProcessed(villageCenter)) return;
        if (state.retryAfter.containsKey(packVillage(villageCenter))) return;

        state.pendingBootstrapVillage = villageCenter.immutable();
        state.pendingBootstrapWaitCycles = 0;
        requestVillageChunks(level, villageCenter);
    }

    private static boolean hasAnyOverworldPortal(ServerLevel level) {
        PortalRegistrySavedData registry = PortalRegistrySavedData.get(level.getServer());
        for (PortalRegistrySavedData.PortalEntry entry : registry.listAll()) {
            if (entry.dim().equals(Level.OVERWORLD)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos computeBootstrapProbe(BlockPos spawnPos, long index) {
        double angle = index * BOOTSTRAP_GOLDEN_ANGLE;
        double radius = BOOTSTRAP_BASE_RADIUS + (Math.sqrt(index + 1.0D) * BOOTSTRAP_RADIUS_STEP);
        int x = spawnPos.getX() + Mth.floor(Math.cos(angle) * radius);
        int z = spawnPos.getZ() + Mth.floor(Math.sin(angle) * radius);
        return new BlockPos(x, spawnPos.getY(), z);
    }

    private static long horizontalDistanceSqr(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return (dx * dx) + (dz * dz);
    }

    private static boolean hasPortalCoreNear(ServerLevel level, BlockPos center, int radiusXZ) {
        int radiusSqr = radiusXZ * radiusXZ;
        PortalRegistrySavedData registry = PortalRegistrySavedData.get(level.getServer());

        for (PortalRegistrySavedData.PortalEntry entry : registry.listAll()) {
            if (!entry.dim().equals(level.dimension())) continue;

            BlockPos portalPos = entry.pos();
            if (horizontalDistanceSqr(portalPos, center) > radiusSqr) continue;
            if (!level.hasChunkAt(portalPos)) return true;

            if (level.getBlockState(portalPos).is(ModBlocks.PORTAL_CORE)) {
                return true;
            }
        }

        return false;
    }

    private static boolean tryPlaceVillagePortal(ServerLevel level, BlockPos villageCenter) {
        RandomSource random = level.getRandom();

        for (int attempt = 0; attempt < PORTAL_PLACEMENT_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * (Math.PI * 2.0D);
            int distance = MIN_PORTAL_OFFSET + random.nextInt(MAX_PORTAL_OFFSET - MIN_PORTAL_OFFSET + 1);
            int x = villageCenter.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = villageCenter.getZ() + Mth.floor(Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;

            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, level.getMinBuildHeight(), z)
            );

            BlockPos corePos = surface.below();
            if (corePos.getY() <= level.getMinBuildHeight() + 1) continue;
            if (corePos.getY() + PortalFrameDetector.INNER_HEIGHT + 1 >= level.getMaxBuildHeight()) continue;
            if (!level.hasChunkAt(corePos)) continue;

            Direction first = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
            Direction second = first == Direction.EAST ? Direction.SOUTH : Direction.EAST;

            if (tryPlaceWithOrientation(level, villageCenter, corePos, first)
                    || tryPlaceWithOrientation(level, villageCenter, corePos, second)) {
                return true;
            }
        }

        return false;
    }

    private static boolean tryPlaceWithOrientation(ServerLevel level,
                                                   BlockPos villageCenter,
                                                   BlockPos corePos,
                                                   Direction right) {
        if (!level.canSeeSky(corePos.above(PortalFrameDetector.INNER_HEIGHT + 1))) return false;
        if (hasPortalCoreNear(level, corePos, MIN_CORE_SEPARATION)) return false;

        Direction normal = right == Direction.EAST ? Direction.SOUTH : Direction.EAST;
        BlockPos keyboardHeight = corePos.above();

        BlockPos keyboardA = keyboardHeight.relative(normal, 2);
        if (canPlaceVillagePortalAt(level, corePos, right, keyboardA)) {
            placeVillagePortal(level, villageCenter, corePos, right, keyboardA);
            return true;
        }

        BlockPos keyboardB = keyboardHeight.relative(normal.getOpposite(), 2);
        if (canPlaceVillagePortalAt(level, corePos, right, keyboardB)) {
            placeVillagePortal(level, villageCenter, corePos, right, keyboardB);
            return true;
        }

        BlockPos keyboardC = keyboardHeight.relative(normal, 3);
        if (canPlaceVillagePortalAt(level, corePos, right, keyboardC)) {
            placeVillagePortal(level, villageCenter, corePos, right, keyboardC);
            return true;
        }

        BlockPos keyboardD = keyboardHeight.relative(normal.getOpposite(), 3);
        if (canPlaceVillagePortalAt(level, corePos, right, keyboardD)) {
            placeVillagePortal(level, villageCenter, corePos, right, keyboardD);
            return true;
        }

        return false;
    }

    private static boolean canPlaceVillagePortalAt(ServerLevel level,
                                                   BlockPos corePos,
                                                   Direction right,
                                                   BlockPos keyboardPos) {
        if (!level.canSeeSky(keyboardPos.above())) return false;

        int halfOuterWidth = PortalFrameDetector.INNER_WIDTH / 2 + 1;
        int topY = PortalFrameDetector.INNER_HEIGHT + 1;

        for (int dy = 0; dy <= topY; dy++) {
            for (int dx = -halfOuterWidth; dx <= halfOuterWidth; dx++) {
                BlockPos p = corePos.offset(right.getStepX() * dx, dy, right.getStepZ() * dx);
                boolean border = dy == 0 || dy == topY || dx == -halfOuterWidth || dx == halfOuterWidth;
                BlockState state = level.getBlockState(p);

                if (border) {
                    if (state.is(dx == 0 && dy == 0 ? ModBlocks.PORTAL_CORE : ModBlocks.PORTAL_FRAME) || state.canBeReplaced()) continue;
                    if (dy != 0 || !isSurfaceTerrain(level, p, state)) return false;
                } else {
                    if (!state.isAir() && !state.canBeReplaced()) return false;
                    if (state.is(ModBlocks.PORTAL_CORE)
                            || state.is(ModBlocks.PORTAL_FRAME)
                            || state.is(ModBlocks.PORTAL_KEYBOARD)
                            || state.is(ModBlocks.PORTAL_NATURAL_KEYBOARD)) {
                        return false;
                    }
                }
            }
        }

        for (int dx = -halfOuterWidth; dx <= halfOuterWidth; dx++) {
            BlockPos supportPos = corePos.offset(right.getStepX() * dx, -1, right.getStepZ() * dx);
            if (!isTopSupportSolid(level, supportPos)) return false;
        }

        if (!canPlaceNaturalKeyboard(level, keyboardPos)) return false;

        return true;
    }

    private static boolean isSurfaceTerrain(ServerLevel level, BlockPos pos, BlockState state) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) == pos.getY() + 1
                && (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                    || state.is(Blocks.DIRT_PATH) || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK));
    }

    private static boolean canPlaceNaturalKeyboard(ServerLevel level, BlockPos keyboardPos) {
        BlockState state = level.getBlockState(keyboardPos);
        if (!state.is(ModBlocks.PORTAL_NATURAL_KEYBOARD) && !state.canBeReplaced()) return false;

        BlockPos support = keyboardPos.below();
        return isTopSupportSolid(level, support);
    }

    private static boolean isTopSupportSolid(ServerLevel level, BlockPos supportPos) {
        BlockState supportState = level.getBlockState(supportPos);
        return supportState.isFaceSturdy(level, supportPos, Direction.UP);
    }

    private static void placeVillagePortal(ServerLevel level,
                                           BlockPos villageCenter,
                                           BlockPos corePos,
                                           Direction right,
                                           BlockPos keyboardPos) {
        PortalRiftHelper.placeRiftFrame(level, corePos, right);

        // Le pupitre se tourne vers le joueur, qui se tient du côté opposé au portail (comme à la pose à la main).
        Direction facing = Direction.getNearest(
                keyboardPos.getX() - corePos.getX(),
                0,
                keyboardPos.getZ() - corePos.getZ()
        );
        if (!facing.getAxis().isHorizontal()) {
            facing = Direction.NORTH;
        }

        BlockState keyboardState = ModBlocks.PORTAL_NATURAL_KEYBOARD.defaultBlockState()
                .setValue(PortalNaturalKeyboardBlock.FACING, facing)
                .setValue(PortalNaturalKeyboardBlock.LIT, false);
        level.setBlock(keyboardPos, keyboardState, 3);

        if (level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core) {
            core.renamePortal(GENERATED_PORTAL_NAME);
        }

        UniverseGate.LOGGER.info("Generated village overworld portal at {} near {}", corePos, villageCenter);
    }

    private static long packVillage(BlockPos pos) {
        return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
    }

    private record VillageSize(int adultVillagers, double maxHorizontalDistance) {
    }
}
