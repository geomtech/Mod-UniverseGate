package fr.geomtech.universegate;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

public class RiftCubeFeature extends Feature<NoneFeatureConfiguration> {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_cube");
    private static final int SPACING_CHUNKS = 40;
    private static boolean loggedMissingTemplate = false;

    public RiftCubeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();

        ChunkPos currentChunk = new ChunkPos(context.origin());
        if (!isTargetChunkForGrid(level.getSeed(), currentChunk)) return false;

        var templateOpt = level.getLevel().getStructureManager().get(TEMPLATE_ID);
        if (templateOpt.isEmpty()) {
            if (!loggedMissingTemplate) {
                loggedMissingTemplate = true;
                UniverseGate.LOGGER.warn("Missing structure template {}. Expected at data/{}/structure/rift_cube.nbt", TEMPLATE_ID, UniverseGate.MOD_ID);
            }
            return false;
        }

        BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, context.origin());
        BlockPos ground = findVoidGround(level, top);
        if (ground == null) return false;

        Rotation rotation = Rotation.values()[context.random().nextInt(Rotation.values().length)];
        var template = templateOpt.get();
        Vec3i size = template.getSize(rotation);

        BlockPos origin = ground.above();
        BlockPos start = origin.offset(-size.getX() / 2, 0, -size.getZ() / 2);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);

        return template.placeInWorld(level, start, start, settings, context.random(), 2);
    }

    private static boolean isTargetChunkForGrid(long seed, ChunkPos chunkPos) {
        int cellX = Math.floorDiv(chunkPos.x, SPACING_CHUNKS);
        int cellZ = Math.floorDiv(chunkPos.z, SPACING_CHUNKS);

        long mix = seed ^ (long) cellX * 341873128712L ^ (long) cellZ * 132897987541L;
        java.util.Random rand = new java.util.Random(mix);

        int targetChunkX = cellX * SPACING_CHUNKS + rand.nextInt(SPACING_CHUNKS);
        int targetChunkZ = cellZ * SPACING_CHUNKS + rand.nextInt(SPACING_CHUNKS);

        return chunkPos.x == targetChunkX && chunkPos.z == targetChunkZ;
    }

    private static BlockPos findVoidGround(WorldGenLevel level, BlockPos fromTop) {
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
