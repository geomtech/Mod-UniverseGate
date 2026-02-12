package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

import java.util.Optional;

public final class RiftScenarioGenerator {

    private static final ResourceLocation OUTPOST_TEMPLATE = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_outpost");
    private static final int OUTPOST_MIN_DISTANCE = 10;
    private static final int OUTPOST_MAX_DISTANCE = 24;
    private static final int WORKING_PORTAL_DISTANCE = 2000;
    private static final Rotation[] HORIZONTAL_ROTATIONS = {
            Rotation.NONE,
            Rotation.CLOCKWISE_90,
            Rotation.CLOCKWISE_180,
            Rotation.COUNTERCLOCKWISE_90
    };

    private RiftScenarioGenerator() {}

    public static void ensureGenerated(ServerLevel riftLevel, BlockPos brokenPortalPos) {
        if (!riftLevel.dimension().equals(UniverseGateDimensions.RIFT)) return;

        RiftScenarioSavedData data = RiftScenarioSavedData.get(riftLevel.getServer());
        if (data.isGenerated()) return;

        UniverseGate.LOGGER.info("Generating Rift scenario near broken portal {}", brokenPortalPos);

        var outpost = placeOutpostNearBrokenPortal(riftLevel, brokenPortalPos);
        if (outpost.isEmpty()) {
            UniverseGate.LOGGER.warn("Could not place rift outpost near {}", brokenPortalPos);
            return;
        }
        UniverseGate.LOGGER.info("Placed rift outpost at {}", outpost.get().center());

        BlockPos workingPortalCore = placeWorkingPortalFarFromOutpost(riftLevel, outpost.get().center());
        if (workingPortalCore == null) {
            UniverseGate.LOGGER.warn("Could not place working rift portal near outpost {}", outpost.get().center());
            return;
        }

        BlockPos outpostBedPos = findOutpostBedPos(riftLevel, outpost.get());
        if (outpostBedPos == null) {
            UniverseGate.LOGGER.warn("No bed found in outpost at {}, using fallback respawn point", outpost.get().center());
            outpostBedPos = outpost.get().center().above();
        }

        addCompassToOutpostContainers(riftLevel, outpost.get(), workingPortalCore);
        data.setGenerated(outpost.get().center(), workingPortalCore, outpostBedPos);

        UniverseGate.LOGGER.info("Rift scenario generated: outpost at {}, outpost bed at {}, working portal at {}", outpost.get().center(), outpostBedPos, workingPortalCore);
    }

    private static Optional<PlacedTemplate> placeOutpostNearBrokenPortal(ServerLevel level, BlockPos brokenPortalPos) {
        var templateOpt = level.getStructureManager().get(OUTPOST_TEMPLATE);
        if (templateOpt.isEmpty()) {
            UniverseGate.LOGGER.warn("Missing template {} (expected data/{}/structure/rift_outpost.nbt)", OUTPOST_TEMPLATE, UniverseGate.MOD_ID);
            return Optional.empty();
        }

        var template = templateOpt.get();

        // Prioritize a very visible ring around the broken portal.
        int[] ringDistances = {12, 14, 16, 18, 20, 22, 24};
        int[][] dirs = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        for (int dist : ringDistances) {
            for (int[] d : dirs) {
                int x = brokenPortalPos.getX() + d[0] * dist;
                int z = brokenPortalPos.getZ() + d[1] * dist;
                Optional<PlacedTemplate> placed = tryPlaceOutpostAt(level, template, x, z);
                if (placed.isPresent()) return placed;
            }
        }

        // Fallback random attempts in the same near range.
        for (int attempt = 0; attempt < 64; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            int dist = OUTPOST_MIN_DISTANCE + level.random.nextInt(OUTPOST_MAX_DISTANCE - OUTPOST_MIN_DISTANCE + 1);

            int x = brokenPortalPos.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = brokenPortalPos.getZ() + (int) Math.round(Math.sin(angle) * dist);
            Optional<PlacedTemplate> placed = tryPlaceOutpostAt(level, template, x, z);
            if (placed.isPresent()) return placed;
        }

        return Optional.empty();
    }

    private static Optional<PlacedTemplate> tryPlaceOutpostAt(ServerLevel level,
                                                              net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template,
                                                              int x,
                                                              int z) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
        BlockPos ground = findVoidGround(level, top);
        if (ground == null) return Optional.empty();

        for (Rotation rotation : HORIZONTAL_ROTATIONS) {
            Vec3i size = template.getSize(rotation);
            BlockPos center = ground;
            BlockPos start = center.offset(-size.getX() / 2, 0, -size.getZ() / 2);

            loadChunksForTemplate(level, start, size);

            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(rotation)
                    .setMirror(Mirror.NONE)
                    .setIgnoreEntities(true)
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);

            boolean ok = template.placeInWorld(level, start, start, settings, level.random, 2);
            if (ok) {
                UniverseGate.LOGGER.info("Rift outpost placed at {} (rotation {})", start, rotation);
                return Optional.of(new PlacedTemplate(start, size));
            }
        }

        return Optional.empty();
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

    private static BlockPos placeWorkingPortalFarFromOutpost(ServerLevel level, BlockPos outpostCenter) {
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            int x = outpostCenter.getX() + (int) Math.round(Math.cos(angle) * WORKING_PORTAL_DISTANCE);
            int z = outpostCenter.getZ() + (int) Math.round(Math.sin(angle) * WORKING_PORTAL_DISTANCE);

            BlockPos desired = new BlockPos(x, 0, z);
            BlockPos corePos = findPortalCoreNear(level, desired, 32);
            if (corePos == null) {
                if (attempt % 8 == 0) {
                    UniverseGate.LOGGER.info("Rift waypoint attempt {}: no valid ground near {}", attempt, desired);
                }
                continue;
            }

            loadChunksForPortal(level, corePos);
            clearPortalAirVolume(level, corePos);

            var right = level.random.nextBoolean() ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.SOUTH;
            PortalRiftHelper.placeRiftFrame(level, corePos, right);

            if (!(level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)) continue;

            core.onPlaced();
            core.renamePortal("Rift Waypoint");
            PortalRegistrySavedData.get(level.getServer()).setHidden(core.getPortalId(), true);
            UniverseGate.LOGGER.info("Placed working rift portal at {} (target around {})", corePos, desired);
            return corePos;
        }

        return null;
    }

    private static BlockPos findPortalCoreNear(ServerLevel level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                level.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));

                BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
                BlockPos ground = findVoidGround(level, top);
                if (ground == null) continue;

                BlockPos corePos = ground.above();
                double dist = corePos.distSqr(center);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = corePos;
                }
            }
        }

        return best;
    }

    private static void loadChunksForPortal(ServerLevel level, BlockPos corePos) {
        int minChunkX = Math.floorDiv(corePos.getX() - 4, 16);
        int minChunkZ = Math.floorDiv(corePos.getZ() - 4, 16);
        int maxChunkX = Math.floorDiv(corePos.getX() + 4, 16);
        int maxChunkZ = Math.floorDiv(corePos.getZ() + 4, 16);

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    private static void clearPortalAirVolume(ServerLevel level, BlockPos corePos) {
        for (int dy = 1; dy <= 6; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos p = corePos.offset(dx, dy, dz);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static void addCompassToOutpostContainers(ServerLevel level, PlacedTemplate outpost, BlockPos targetCorePos) {
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.set(DataComponents.CUSTOM_NAME, Component.literal("Rift Compass"));
        compass.set(
                DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(UniverseGateDimensions.RIFT, targetCorePos)), false)
        );
        ItemStack craftingTable = new ItemStack(Items.CRAFTING_TABLE);

        BlockPos start = outpost.start();
        Vec3i size = outpost.size();
        BlockPos end = start.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        boolean inserted = false;

        for (BlockPos pos : BlockPos.betweenClosed(start, end)) {
            var be = level.getBlockEntity(pos);
            if (!(be instanceof Container container)) continue;

            if (be instanceof RandomizableContainer randomizable) {
                randomizable.unpackLootTable(null);
            }

            for (int i = 0; i < container.getContainerSize(); i++) {
                if (!container.getItem(i).isEmpty()) continue;
                container.setItem(i, compass.copy());
                inserted = true;
                UniverseGate.LOGGER.info("Inserted Rift Compass in outpost container at {} targeting core {} (tracked=false)", pos, targetCorePos);

                if (tryInsertInEmptySlot(container, craftingTable.copy())) {
                    UniverseGate.LOGGER.info("Inserted Crafting Table in outpost container at {}", pos);
                } else {
                    UniverseGate.LOGGER.warn("No empty slot for Crafting Table in outpost container at {}", pos);
                }
                break;
            }

            if (inserted) break;
        }

        if (!inserted) {
            UniverseGate.LOGGER.warn("No container found in rift outpost for Rift Compass at {}", outpost.center());
        }
    }

    private static BlockPos findOutpostBedPos(ServerLevel level, PlacedTemplate outpost) {
        BlockPos start = outpost.start();
        Vec3i size = outpost.size();
        BlockPos end = start.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        for (BlockPos pos : BlockPos.betweenClosed(start, end)) {
            if (level.getBlockState(pos).is(BlockTags.BEDS)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static boolean tryInsertInEmptySlot(Container container, ItemStack stack) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) continue;
            container.setItem(i, stack);
            return true;
        }
        return false;
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

    private record PlacedTemplate(BlockPos start, Vec3i size) {
        BlockPos center() {
            return start.offset(size.getX() / 2, 0, size.getZ() / 2);
        }
    }
}
