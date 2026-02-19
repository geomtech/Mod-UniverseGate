package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private static final int BOOTSTRAP_ATTEMPTS_PER_CYCLE = 2;
    private static final int BOOTSTRAP_SEARCH_RADIUS_CHUNKS = 40;
    private static final int BOOTSTRAP_MIN_DISTANCE_FROM_SPAWN = 1200;
    private static final double BOOTSTRAP_GOLDEN_ANGLE = 2.399963229728653D;
    private static final int BOOTSTRAP_BASE_RADIUS = 900;
    private static final int BOOTSTRAP_RADIUS_STEP = 180;
    private static final String GENERATED_PORTAL_NAME = "Village Gate";

    private OverworldVillagePortalGenerator() {}

    public static void tickWorld(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        if (level.getGameTime() % CHECK_EVERY_TICKS != 0L) return;

        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        VillagePortalSavedData data = VillagePortalSavedData.get(level.getServer());
        Set<Long> checkedThisTick = new HashSet<>();

        for (ServerPlayer player : players) {
            if (player.isSpectator()) continue;

            BlockPos villageCenter = findNearestVillageCenter(level, player.blockPosition(), VILLAGE_SEARCH_RADIUS_CHUNKS);
            if (villageCenter == null) continue;

            long villageKey = packVillage(villageCenter);
            if (!checkedThisTick.add(villageKey)) continue;
            if (data.isProcessed(villageCenter)) continue;

            preloadVillageChunks(level, villageCenter);

            VillageSize villageSize = evaluateVillageSize(level, villageCenter, VILLAGER_COUNT_RADIUS);
            if (villageSize.adultVillagers() < MIN_ADULT_VILLAGERS) continue;
            if (villageSize.maxHorizontalDistance() < MIN_VILLAGE_SPREAD_BLOCKS) continue;

            if (hasPortalCoreNear(level, villageCenter, EXISTING_PORTAL_RADIUS)) {
                data.markProcessed(villageCenter);
                continue;
            }

            if (tryPlaceVillagePortal(level, villageCenter)) {
                data.markProcessed(villageCenter);
            }
        }

        bootstrapUndiscoveredVillagePortals(level, data);
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

    private static void preloadVillageChunks(ServerLevel level, BlockPos villageCenter) {
        int centerChunkX = villageCenter.getX() >> 4;
        int centerChunkZ = villageCenter.getZ() >> 4;
        for (int dx = -VILLAGE_PRELOAD_RADIUS_CHUNKS; dx <= VILLAGE_PRELOAD_RADIUS_CHUNKS; dx++) {
            for (int dz = -VILLAGE_PRELOAD_RADIUS_CHUNKS; dz <= VILLAGE_PRELOAD_RADIUS_CHUNKS; dz++) {
                level.getChunk(centerChunkX + dx, centerChunkZ + dz);
            }
        }
    }

    private static void bootstrapUndiscoveredVillagePortals(ServerLevel level, VillagePortalSavedData data) {
        if (!hasAnyOverworldPortal(level)) return;
        if (data.getBootstrapGeneratedPortals() >= BOOTSTRAP_TARGET_PORTALS) return;

        BlockPos spawnPos = level.getSharedSpawnPos();
        long minDistanceSqr = (long) BOOTSTRAP_MIN_DISTANCE_FROM_SPAWN * (long) BOOTSTRAP_MIN_DISTANCE_FROM_SPAWN;

        for (int i = 0; i < BOOTSTRAP_ATTEMPTS_PER_CYCLE; i++) {
            long index = data.nextBootstrapIndex();
            BlockPos probe = computeBootstrapProbe(spawnPos, index);

            BlockPos villageCenter = findNearestVillageCenter(level, probe, BOOTSTRAP_SEARCH_RADIUS_CHUNKS);
            if (villageCenter == null) continue;
            if (horizontalDistanceSqr(villageCenter, spawnPos) < minDistanceSqr) continue;
            if (data.isProcessed(villageCenter)) continue;

            preloadVillageChunks(level, villageCenter);

            VillageSize villageSize = evaluateVillageSize(level, villageCenter, VILLAGER_COUNT_RADIUS);
            if (villageSize.adultVillagers() < MIN_ADULT_VILLAGERS) continue;
            if (villageSize.maxHorizontalDistance() < MIN_VILLAGE_SPREAD_BLOCKS) continue;

            if (hasPortalCoreNear(level, villageCenter, EXISTING_PORTAL_RADIUS)) {
                data.markProcessed(villageCenter);
                continue;
            }

            if (tryPlaceVillagePortal(level, villageCenter)) {
                data.markProcessed(villageCenter);
                data.incrementBootstrapGeneratedPortals();

                if (data.getBootstrapGeneratedPortals() >= BOOTSTRAP_TARGET_PORTALS) {
                    break;
                }
            }
        }
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
            int dx = portalPos.getX() - center.getX();
            int dz = portalPos.getZ() - center.getZ();
            if ((dx * dx) + (dz * dz) > radiusSqr) continue;
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

            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, level.getMinBuildHeight(), z)
            );

            BlockPos corePos = surface.immutable();
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

        BlockPos keyboardA = corePos.relative(normal, 2);
        if (canPlaceVillagePortalAt(level, corePos, right, keyboardA)) {
            placeVillagePortal(level, villageCenter, corePos, right, keyboardA);
            return true;
        }

        BlockPos keyboardB = corePos.relative(normal.getOpposite(), 2);
        if (canPlaceVillagePortalAt(level, corePos, right, keyboardB)) {
            placeVillagePortal(level, villageCenter, corePos, right, keyboardB);
            return true;
        }

        BlockPos keyboardC = corePos.relative(normal, 3);
        if (canPlaceVillagePortalAt(level, corePos, right, keyboardC)) {
            placeVillagePortal(level, villageCenter, corePos, right, keyboardC);
            return true;
        }

        BlockPos keyboardD = corePos.relative(normal.getOpposite(), 3);
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
                    if (dx == 0 && dy == 0) {
                        if (!state.is(ModBlocks.PORTAL_CORE) && !state.canBeReplaced()) return false;
                    } else {
                        if (!state.is(ModBlocks.PORTAL_FRAME) && !state.canBeReplaced()) return false;
                    }
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

        Direction facing = Direction.getNearest(
                corePos.getX() - keyboardPos.getX(),
                0,
                corePos.getZ() - keyboardPos.getZ()
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
