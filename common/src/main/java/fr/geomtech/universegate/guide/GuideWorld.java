package fr.geomtech.universegate.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.util.AbortableIterationConsumer;
//? if >=1.21.11 {
/*import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
*///?} else {
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.crafting.RecipeManager;
//?}

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A tiny client-only world holding the blocks of a guide scene, so that the mod's own block entity
 * renderers (the 3D ring, the wires, the crocodile clips...) can draw exactly as they do in game.
 * It is never ticked, has no entities, full light everywhere, and only knows the blocks put in it.
 */
public final class GuideWorld extends Level {
    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
    private final ClientLevel host;
    private final GuideChunks chunks;
    private final Holder<Biome> plains;
    private final TickRateManager tickRates = new TickRateManager();
    private final Scoreboard scoreboard = new Scoreboard();
    //? if >=1.21.11 {
    /*private final WorldBorder border = new WorldBorder();
    *///?}

    /** Needs a loaded client level for its registries; see {@link #create()}. */
    private GuideWorld(ClientLevel host) {
        //? if >=1.21.11 {
        /*super(new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false), Level.OVERWORLD, host.registryAccess(),
                host.dimensionTypeRegistration(), true, false, 0L, 0);
        *///?} else {
        super(new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false), Level.OVERWORLD, host.registryAccess(),
                host.dimensionTypeRegistration(), () -> InactiveProfiler.INSTANCE, true, false, 0L, 0);
        //?}
        this.host = host;
        this.plains = host.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        this.chunks = new GuideChunks(this);
    }

    /** A new empty world, or null when no client level is loaded (main menu). */
    public static GuideWorld create() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : new GuideWorld(level);
    }

    // ------------------------------------------------------------------ content

    public void clear() {
        blocks.clear();
        blockEntities.clear();
    }

    /** Puts a block (and its block entity) without any update. */
    public void put(BlockPos pos, BlockState state) {
        pos = pos.immutable();
        if (state.isAir()) {
            blocks.remove(pos);
            blockEntities.remove(pos);
            return;
        }
        blocks.put(pos, state);
        BlockEntity existing = blockEntities.get(pos);
        if (existing != null && existing.getType().isValid(state)) {
            existing.setBlockState(state);
            return;
        }
        blockEntities.remove(pos);
        if (state.getBlock() instanceof EntityBlock entityBlock) {
            BlockEntity be = entityBlock.newBlockEntity(pos, state);
            if (be != null) {
                be.setLevel(this);
                blockEntities.put(pos, be);
            }
        }
    }

    /** Lets every block adapt to its neighbours, as after placing them in a real world (wire links...). */
    public void settle() {
        for (int pass = 0; pass < 2; pass++) {
            for (Map.Entry<BlockPos, BlockState> entry : List.copyOf(blocks.entrySet())) {
                BlockState updated = Block.updateFromNeighbourShapes(entry.getValue(), this, entry.getKey());
                // Only connections change here: a block the scene placed never vanishes.
                if (updated != entry.getValue() && !updated.isAir()) put(entry.getKey(), updated);
            }
        }
    }

    public Map<BlockPos, BlockState> blocks() {
        return blocks;
    }

    public void setTime(long ticks) {
        ((ClientLevel.ClientLevelData) getLevelData()).setGameTime(ticks);
    }

    // ------------------------------------------------------------------ block access

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(pos);
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        put(pos, state);
        return true;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return 15;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        if (!shade) return 1.0F;
        return switch (direction) {
            case DOWN -> 0.5F;
            case UP -> 1.0F;
            case NORTH, SOUTH -> 0.8F;
            default -> 0.6F;
        };
    }

    @Override
    public ChunkSource getChunkSource() {
        return chunks;
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return plains;
    }

    // ------------------------------------------------------------------ inert world services

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        return NO_ENTITIES;
    }

    @Override
    public String gatherChunkSourceStats() {
        return "guide";
    }

    @Override
    public List<? extends Player> players() {
        return List.of();
    }

    @Override
    public TickRateManager tickRateManager() {
        return tickRates;
    }

    @Override
    public Entity getEntity(int id) {
        return null;
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return host.enabledFeatures();
    }

    @Override
    public PotionBrewing potionBrewing() {
        return host.potionBrewing();
    }

    @Override
    public MapItemSavedData getMapData(MapId id) {
        return null;
    }

    @Override
    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
    }

    @Override
    public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
    }

    /** NeoForge only (its Level declares these four as abstract). */
    public float getDayTimeFraction() {
        return 0.0F;
    }

    public float getDayTimePerTick() {
        return 1.0F;
    }

    public void setDayTimeFraction(float fraction) {
    }

    public void setDayTimePerTick(float perTick) {
    }

    //? if >=1.21.11 {
    /*@Override
    public EnvironmentAttributeSystem environmentAttributes() {
        return host.environmentAttributes();
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    /^* Raw type: vanilla returns ender dragon parts, NeoForge its PartEntity type. ^/
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Collection dragonParts() {
        return List.of();
    }

    @Override
    public RecipeAccess recipeAccess() {
        return host.recipeAccess();
    }

    @Override
    public FuelValues fuelValues() {
        return host.fuelValues();
    }

    @Override
    public WorldBorder getWorldBorder() {
        return border;
    }

    @Override
    public LevelData.RespawnData getRespawnData() {
        return host.getRespawnData();
    }

    @Override
    public void setRespawnData(LevelData.RespawnData data) {
    }

    @Override
    public void explode(Entity source, DamageSource damageSource, ExplosionDamageCalculator calculator,
                        double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction interaction,
                        ParticleOptions small, ParticleOptions large, WeightedList<ExplosionParticleInfo> particles,
                        Holder<SoundEvent> sound) {
    }

    @Override
    public void playSeededSound(Entity source, double x, double y, double z, Holder<SoundEvent> sound,
                                SoundSource category, float volume, float pitch, long seed) {
    }

    @Override
    public void playSeededSound(Entity source, Entity entity, Holder<SoundEvent> sound, SoundSource category,
                                float volume, float pitch, long seed) {
    }

    @Override
    public void levelEvent(Entity source, int type, BlockPos pos, int data) {
    }
    *///?} else {
    @Override
    public RecipeManager getRecipeManager() {
        return host.getRecipeManager();
    }

    @Override
    public MapId getFreeMapId() {
        return new MapId(0);
    }

    @Override
    public void setMapData(MapId id, MapItemSavedData data) {
    }

    @Override
    public void playSeededSound(Player player, double x, double y, double z, Holder<SoundEvent> sound,
                                SoundSource category, float volume, float pitch, long seed) {
    }

    @Override
    public void playSeededSound(Player player, Entity entity, Holder<SoundEvent> sound, SoundSource category,
                                float volume, float pitch, long seed) {
    }

    @Override
    public void levelEvent(Player player, int type, BlockPos pos, int data) {
    }
    //?}

    // ------------------------------------------------------------------ helpers

    private static final LevelEntityGetter<Entity> NO_ENTITIES = new LevelEntityGetter<>() {
        @Override
        public Entity get(int id) {
            return null;
        }

        @Override
        public Entity get(UUID uuid) {
            return null;
        }

        @Override
        public Iterable<Entity> getAll() {
            return List.of();
        }

        @Override
        public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AbortableIterationConsumer<U> consumer) {
        }

        @Override
        public void get(AABB box, Consumer<Entity> consumer) {
        }

        @Override
        public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AABB box, AbortableIterationConsumer<U> consumer) {
        }
    };

    /** Empty chunks everywhere: the blocks live in the world's map, not in chunks. */
    private static final class GuideChunks extends ChunkSource {
        private final GuideWorld world;
        private final LevelLightEngine light;

        GuideChunks(GuideWorld world) {
            this.world = world;
            this.light = new LevelLightEngine(this, false, false);
        }

        @Override
        public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) {
            return new EmptyLevelChunk(world, new ChunkPos(x, z), world.plains);
        }

        @Override
        public void tick(BooleanSupplier hasTimeLeft, boolean tickChunks) {
        }

        @Override
        public String gatherStats() {
            return "guide";
        }

        @Override
        public int getLoadedChunksCount() {
            return 0;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return light;
        }

        @Override
        public net.minecraft.world.level.BlockGetter getLevel() {
            return world;
        }
    }
}
