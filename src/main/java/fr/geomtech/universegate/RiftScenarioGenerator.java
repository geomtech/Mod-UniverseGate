package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
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

        var outpost = placeOutpostNearBrokenPortal(riftLevel, brokenPortalPos);
        if (outpost.isEmpty()) {
            UniverseGate.LOGGER.warn("Could not place rift outpost near {}", brokenPortalPos);
            return;
        }

        BlockPos workingPortalCore = placeWorkingPortalFarFromOutpost(riftLevel, outpost.get().center());
        if (workingPortalCore == null) {
            UniverseGate.LOGGER.warn("Could not place working rift portal near outpost {}", outpost.get().center());
            return;
        }

        addCompassToOutpostContainers(riftLevel, outpost.get(), workingPortalCore.below());
        data.setGenerated(outpost.get().center(), workingPortalCore);

        UniverseGate.LOGGER.info("Rift scenario generated: outpost at {}, working portal at {}", outpost.get().center(), workingPortalCore);
    }

    private static Optional<PlacedTemplate> placeOutpostNearBrokenPortal(ServerLevel level, BlockPos brokenPortalPos) {
        var templateOpt = level.getStructureManager().get(OUTPOST_TEMPLATE);
        if (templateOpt.isEmpty()) {
            UniverseGate.LOGGER.warn("Missing template {} (expected data/{}/structure/rift_outpost.nbt)", OUTPOST_TEMPLATE, UniverseGate.MOD_ID);
            return Optional.empty();
        }

        var template = templateOpt.get();

        for (int attempt = 0; attempt < 80; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            int dist = OUTPOST_MIN_DISTANCE + level.random.nextInt(OUTPOST_MAX_DISTANCE - OUTPOST_MIN_DISTANCE + 1);

            int x = brokenPortalPos.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = brokenPortalPos.getZ() + (int) Math.round(Math.sin(angle) * dist);

            BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
            BlockPos ground = findVoidGround(level, top);
            if (ground == null) continue;

            Rotation rotation = HORIZONTAL_ROTATIONS[level.random.nextInt(HORIZONTAL_ROTATIONS.length)];
            Vec3i size = template.getSize(rotation);

            BlockPos center = ground.above();
            BlockPos start = center.offset(-size.getX() / 2, 0, -size.getZ() / 2);

            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(rotation)
                    .setMirror(Mirror.NONE)
                    .setIgnoreEntities(false)
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);

            boolean ok = template.placeInWorld(level, start, start, settings, level.random, 2);
            if (ok) {
                return Optional.of(new PlacedTemplate(start, size));
            }
        }

        return Optional.empty();
    }

    private static BlockPos placeWorkingPortalFarFromOutpost(ServerLevel level, BlockPos outpostCenter) {
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            int x = outpostCenter.getX() + (int) Math.round(Math.cos(angle) * WORKING_PORTAL_DISTANCE);
            int z = outpostCenter.getZ() + (int) Math.round(Math.sin(angle) * WORKING_PORTAL_DISTANCE);

            BlockPos near = findHighestVoidGroundNear(level, new BlockPos(x, 0, z), 96);
            if (near == null) continue;

            BlockPos corePos = near.above();
            level.setBlock(corePos.below(), Blocks.LODESTONE.defaultBlockState(), 3);

            var right = level.random.nextBoolean() ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.SOUTH;
            PortalRiftHelper.placeRiftFrame(level, corePos, right);

            if (!(level.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)) continue;

            core.onPlaced();
            core.renamePortal("Rift Waypoint");
            PortalRegistrySavedData.get(level.getServer()).setHidden(core.getPortalId(), true);
            return corePos;
        }

        return null;
    }

    private static void addCompassToOutpostContainers(ServerLevel level, PlacedTemplate outpost, BlockPos lodestonePos) {
        ItemStack compass = new ItemStack(ModItems.RIFT_COMPASS);
        compass.set(DataComponents.CUSTOM_NAME, Component.literal("Rift Compass"));
        compass.set(
                DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(UniverseGateDimensions.RIFT, lodestonePos)), true)
        );

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
                break;
            }

            if (inserted) break;
        }

        if (!inserted) {
            UniverseGate.LOGGER.warn("No container found in rift outpost for Rift Compass at {}", outpost.center());
        }
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

    private static BlockPos findHighestVoidGroundNear(ServerLevel level, BlockPos center, int radius) {
        BlockPos best = null;
        int bestY = level.getMinBuildHeight();
        double bestDist = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, center.offset(dx, 0, dz));
                BlockPos ground = findVoidGround(level, top);
                if (ground == null) continue;

                int y = ground.getY();
                double dist = ground.distSqr(center);
                if (y > bestY || (y == bestY && dist < bestDist)) {
                    bestY = y;
                    bestDist = dist;
                    best = ground;
                }
            }
        }

        return best;
    }

    private record PlacedTemplate(BlockPos start, Vec3i size) {
        BlockPos center() {
            return start.offset(size.getX() / 2, 0, size.getZ() / 2);
        }
    }
}
