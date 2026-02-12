package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

import java.util.HashSet;
import java.util.Set;

public final class RiftCubeGenerator {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_cube");
    private static final int SPACING_CHUNKS = 40;
    private static final int CELL_RADIUS = 2;
    private static final int CHECK_EVERY_TICKS = 120;
    private static boolean loggedMissingTemplate = false;

    private RiftCubeGenerator() {}

    public static void tickWorld(ServerLevel level) {
        if (!level.dimension().equals(UniverseGateDimensions.RIFT)) return;
        if (level.getGameTime() % CHECK_EVERY_TICKS != 0L) return;

        var templateOpt = level.getStructureManager().get(TEMPLATE_ID);
        if (templateOpt.isEmpty()) {
            if (!loggedMissingTemplate) {
                loggedMissingTemplate = true;
                UniverseGate.LOGGER.warn("Missing template {}. Expected at data/{}/structure/rift_cube.nbt", TEMPLATE_ID, UniverseGate.MOD_ID);
            }
            return;
        }

        var template = templateOpt.get();
        RiftCubeSavedData data = RiftCubeSavedData.get(level.getServer());

        Set<Long> processedCells = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            ChunkPos cp = player.chunkPosition();
            int baseCellX = Math.floorDiv(cp.x, SPACING_CHUNKS);
            int baseCellZ = Math.floorDiv(cp.z, SPACING_CHUNKS);

            for (int cx = baseCellX - CELL_RADIUS; cx <= baseCellX + CELL_RADIUS; cx++) {
                for (int cz = baseCellZ - CELL_RADIUS; cz <= baseCellZ + CELL_RADIUS; cz++) {
                    long packed = (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
                    if (!processedCells.add(packed)) continue;
                    if (data.isGenerated(cx, cz)) continue;

                    if (tryGenerateCell(level, template, data, cx, cz)) {
                        return; // place max 1 cube per tick cycle to avoid lag spikes
                    }
                }
            }
        }
    }

    private static boolean tryGenerateCell(ServerLevel level,
                                           net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template,
                                           RiftCubeSavedData data,
                                           int cellX,
                                           int cellZ) {
        long mix = level.getSeed() ^ (long) cellX * 341873128712L ^ (long) cellZ * 132897987541L;
        java.util.Random rand = new java.util.Random(mix);

        int targetChunkX = cellX * SPACING_CHUNKS + rand.nextInt(SPACING_CHUNKS);
        int targetChunkZ = cellZ * SPACING_CHUNKS + rand.nextInt(SPACING_CHUNKS);

        BlockPos probe = new BlockPos(targetChunkX * 16 + 8, level.getMinBuildHeight() + 1, targetChunkZ * 16 + 8);
        if (!level.hasChunkAt(probe)) return false;

        Rotation rotation = Rotation.values()[rand.nextInt(Rotation.values().length)];
        Vec3i size = template.getSize(rotation);

        BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, probe);
        BlockPos ground = findVoidGround(level, top);
        if (ground == null) {
            data.markGenerated(cellX, cellZ); // avoid retry spam in impossible cells
            return false;
        }

        BlockPos origin = ground.above();
        BlockPos start = origin.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        loadChunksForTemplate(level, start, size);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);

        boolean ok = template.placeInWorld(level, start, start, settings, level.random, 2);
        if (ok) {
            applyLootToContainers(level, template, settings, start);
            data.markGenerated(cellX, cellZ);
            UniverseGate.LOGGER.info("Generated rift_cube at {} in cell {},{}", start, cellX, cellZ);
            return true;
        }

        return false;
    }

    private static void loadChunksForTemplate(ServerLevel level, BlockPos start, Vec3i size) {
        int minChunkX = Math.floorDiv(start.getX(), 16);
        int minChunkZ = Math.floorDiv(start.getZ(), 16);
        int maxChunkX = Math.floorDiv(start.getX() + size.getX(), 16);
        int maxChunkZ = Math.floorDiv(start.getZ() + size.getZ(), 16);

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    private static void applyLootToContainers(ServerLevel level,
                                              net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template,
                                              StructurePlaceSettings settings,
                                              BlockPos start) {
        BoundingBox box = template.getBoundingBox(settings, start);
        int containers = 0;
        int filled = 0;

        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            var be = level.getBlockEntity(pos);
            if (!(be instanceof Container container)) continue;
            containers++;

            if (be instanceof RandomizableContainer randomizable) {
                randomizable.unpackLootTable(null);
            }

            if (fillRiftCubeContainer(container, level.random)) {
                filled++;
            }
            be.setChanged();
        }

        UniverseGate.LOGGER.info("Rift cube loot pass: containers found={}, filled={} at {}", containers, filled, start);
    }

    private static boolean fillRiftCubeContainer(Container container, RandomSource random) {
        boolean inserted = false;

        inserted |= addItem(container, random, 0.90F, new ItemStack(Items.COAL, randBetween(random, 4, 18)));
        inserted |= addItem(container, random, 0.70F, new ItemStack(Items.CHORUS_FRUIT, randBetween(random, 2, 8)));

        // Rare valuables
        inserted |= addItem(container, random, 0.16F, new ItemStack(Items.DIAMOND, randBetween(random, 1, 3)));
        inserted |= addItem(container, random, 0.28F, new ItemStack(Items.ENDER_PEARL, randBetween(random, 1, 2)));

        // Useless / filler loot
        inserted |= addItem(container, random, 0.55F, new ItemStack(Items.STICK, randBetween(random, 3, 14)));
        inserted |= addItem(container, random, 0.50F, new ItemStack(Items.ROTTEN_FLESH, randBetween(random, 2, 10)));
        inserted |= addItem(container, random, 0.45F, new ItemStack(Items.BONE, randBetween(random, 1, 8)));
        inserted |= addItem(container, random, 0.40F, new ItemStack(Items.STRING, randBetween(random, 1, 8)));
        inserted |= addItem(container, random, 0.35F, new ItemStack(Items.FLINT, randBetween(random, 1, 4)));
        inserted |= addItem(container, random, 0.30F, new ItemStack(Items.COBBLED_DEEPSLATE, randBetween(random, 4, 16)));
        inserted |= addItem(container, random, 0.22F, new ItemStack(Items.DEAD_BUSH, randBetween(random, 1, 3)));
        inserted |= addItem(container, random, 0.20F, new ItemStack(Items.FEATHER, randBetween(random, 1, 5)));

        if (!inserted) {
            inserted = placeInRandomEmptySlot(container, random, new ItemStack(Items.COAL, randBetween(random, 4, 8)));
        }

        return inserted;
    }

    private static boolean addItem(Container container, RandomSource random, float chance, ItemStack stack) {
        if (random.nextFloat() > chance) return false;
        return placeInRandomEmptySlot(container, random, stack);
    }

    private static boolean placeInRandomEmptySlot(Container container, RandomSource random, ItemStack stack) {
        int size = container.getContainerSize();
        int start = random.nextInt(Math.max(1, size));
        for (int i = 0; i < size; i++) {
            int slot = (start + i) % size;
            if (!container.getItem(slot).isEmpty()) continue;
            container.setItem(slot, stack);
            return true;
        }
        return false;
    }

    private static int randBetween(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
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
