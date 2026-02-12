package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RiftShadeSpawner {

    private static final int CELL_SIZE_CHUNKS = 8;
    private static final int NATURAL_CHECK_EVERY_TICKS = 80;
    private static final int EMITTER_CHECK_EVERY_TICKS = 20;
    private static final int EMITTER_SCAN_RADIUS_CHUNKS = 5;
    private static final int EMITTER_MAX_SHADES = 10;
    private static final int EMITTER_SPAWN_BURST = 2;
    private static final int EMITTER_SPAWN_RADIUS = 8;

    private RiftShadeSpawner() {}

    public static void tickWorld(ServerLevel level) {
        if (!level.dimension().equals(UniverseGateDimensions.RIFT)) return;

        long time = level.getGameTime();

        if (time % NATURAL_CHECK_EVERY_TICKS == 0L) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) continue;

                ChunkPos cp = player.chunkPosition();
                int cellX = Math.floorDiv(cp.x, CELL_SIZE_CHUNKS) * CELL_SIZE_CHUNKS;
                int cellZ = Math.floorDiv(cp.z, CELL_SIZE_CHUNKS) * CELL_SIZE_CHUNKS;

                trySpawnInCell(level, cellX, cellZ);
            }
        }

        if (time % EMITTER_CHECK_EVERY_TICKS == 0L) {
            spawnAroundEmitters(level);
        }
    }

    private static void spawnAroundEmitters(ServerLevel level) {
        Set<BlockPos> emitters = collectLoadedEmittersNearPlayers(level);
        for (BlockPos emitterPos : emitters) {
            AABB area = new AABB(emitterPos).inflate(EMITTER_SPAWN_RADIUS, 6.0, EMITTER_SPAWN_RADIUS);
            int count = level.getEntitiesOfClass(RiftShadeEntity.class, area).size();
            if (count >= EMITTER_MAX_SHADES) continue;

            int toSpawn = Math.min(EMITTER_SPAWN_BURST, EMITTER_MAX_SHADES - count);
            for (int i = 0; i < toSpawn; i++) {
                BlockPos spawnPos = findSpawnNearEmitter(level, emitterPos, EMITTER_SPAWN_RADIUS);
                if (spawnPos == null) continue;
                spawnShade(level, spawnPos, MobSpawnType.SPAWNER, "emitter");
            }
        }
    }

    private static Set<BlockPos> collectLoadedEmittersNearPlayers(ServerLevel level) {
        Set<BlockPos> out = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            ChunkPos cp = player.chunkPosition();
            for (int cx = cp.x - EMITTER_SCAN_RADIUS_CHUNKS; cx <= cp.x + EMITTER_SCAN_RADIUS_CHUNKS; cx++) {
                for (int cz = cp.z - EMITTER_SCAN_RADIUS_CHUNKS; cz <= cp.z + EMITTER_SCAN_RADIUS_CHUNKS; cz++) {
                    if (!level.hasChunkAt(new BlockPos(cx * 16, level.getMinBuildHeight(), cz * 16))) continue;
                    LevelChunk chunk = level.getChunk(cx, cz);
                    for (var be : chunk.getBlockEntities().values()) {
                        if (be instanceof LightBeamEmitterBlockEntity) {
                            out.add(be.getBlockPos().immutable());
                        }
                    }
                }
            }
        }
        return out;
    }

    private static void trySpawnInCell(ServerLevel level, int cellChunkX, int cellChunkZ) {
        AABB cellBox = new AABB(
                cellChunkX * 16.0,
                level.getMinBuildHeight(),
                cellChunkZ * 16.0,
                (cellChunkX + CELL_SIZE_CHUNKS) * 16.0,
                level.getMaxBuildHeight(),
                (cellChunkZ + CELL_SIZE_CHUNKS) * 16.0
        );

        List<RiftShadeEntity> existing = level.getEntitiesOfClass(RiftShadeEntity.class, cellBox);
        if (!existing.isEmpty()) return;

        BlockPos spawnPos = findSpawnPosInCell(level, cellChunkX, cellChunkZ);
        if (spawnPos == null) return;

        spawnShade(level, spawnPos, MobSpawnType.NATURAL, "cell");
    }

    private static void spawnShade(ServerLevel level, BlockPos spawnPos, MobSpawnType spawnType, String source) {
        RiftShadeEntity shade = ModEntityTypes.RIFT_SHADE.create(level);
        if (shade == null) return;

        shade.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        if (!RiftShadeEntity.canSpawn(ModEntityTypes.RIFT_SHADE, level, spawnType, spawnPos, level.random)) return;
        shade.setPersistenceRequired();

        UniverseGate.LOGGER.debug("Spawn Rift Shade ({}) at {}", source, spawnPos);

        level.addFreshEntity(shade);
    }

    private static BlockPos findSpawnPosInCell(ServerLevel level, int cellChunkX, int cellChunkZ) {
        int blockX = cellChunkX * 16;
        int blockZ = cellChunkZ * 16;

        for (int i = 0; i < 24; i++) {
            int x = blockX + level.random.nextInt(CELL_SIZE_CHUNKS * 16);
            int z = blockZ + level.random.nextInt(CELL_SIZE_CHUNKS * 16);
            BlockPos probe = new BlockPos(x, level.getMinBuildHeight() + 1, z);
            if (!level.hasChunkAt(probe)) continue;

            BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
            BlockPos ground = findVoidGround(level, top);
            if (ground == null) continue;

            BlockPos pos = ground.above();
            if (!level.getBlockState(pos).isAir()) continue;
            if (!level.getBlockState(pos.above()).isAir()) continue;
            return pos;
        }

        return null;
    }

    private static BlockPos findSpawnNearEmitter(ServerLevel level, BlockPos emitterPos, int radius) {
        for (int i = 0; i < 20; i++) {
            int x = emitterPos.getX() + level.random.nextInt(radius * 2 + 1) - radius;
            int z = emitterPos.getZ() + level.random.nextInt(radius * 2 + 1) - radius;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
            BlockPos ground = findVoidGround(level, top);
            if (ground == null) continue;
            BlockPos pos = ground.above();
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) continue;
            if (pos.closerThan(emitterPos, 2.0)) continue;
            return pos;
        }
        return null;
    }

    private static BlockPos findVoidGround(ServerLevel level, BlockPos fromTop) {
        int minY = level.getMinBuildHeight();
        for (int y = fromTop.getY(); y > minY; y--) {
            BlockPos p = new BlockPos(fromTop.getX(), y, fromTop.getZ());
            if (!level.getBlockState(p).is(ModBlocks.VOID_BLOCK)) continue;
            if (!level.getBlockState(p.above()).isAir()) continue;
            return p;
        }
        return null;
    }
}
