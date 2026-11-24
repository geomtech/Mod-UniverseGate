package fr.geomtech.universegate;

import fr.geomtech.universegate.net.AcidRainStatePayload;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.storage.ServerLevelData;

import java.lang.reflect.Method;

public class AcidRainGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void tenPercentBoundaryAndNoRepeatedStart(GameTestHelper helper) {
        AcidRainSavedData state = new AcidRainSavedData();
        helper.assertFalse(state.beginNaturalEpisode(100L, 0.1F), "A roll at the 10% boundary must remain normal rain");
        helper.assertTrue(state.beginNaturalEpisode(100L, 0.09999F), "A roll below 10% must start the warning");
        helper.assertFalse(state.isActive(), "A successful roll must warn before acid effects start");
        helper.assertFalse(state.beginNaturalEpisode(200L, 0.0F), "An ongoing episode cannot restart its warning");
        helper.assertTrue(state.warningTicksRemaining(200L) == 500, "Repeated starts must not reset the warning clock");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void warningEpisodeAndThreeDayGapSurviveReload(GameTestHelper helper) {
        AcidRainSavedData original = new AcidRainSavedData();
        original.beginEpisode(100L);
        AcidRainSavedData warning = reload(original, helper);
        helper.assertTrue(warning.warningTicksRemaining(400L) == 300, "Reload must preserve the remaining warning time");
        warning.tick(699L, true);
        helper.assertFalse(warning.isActive(), "Acid effects must not begin before 600 warning ticks");
        warning.tick(700L, true);
        AcidRainSavedData active = reload(warning, helper);
        helper.assertTrue(active.isActive(), "An active episode must remain active after reloading");
        active.endEpisode(2_000L);
        AcidRainSavedData cooldown = reload(active, helper);
        helper.assertFalse(cooldown.canStart(73_999L), "The full 72,000 tick gap starts when the episode ends");
        helper.assertTrue(cooldown.cooldownRemaining(73_999L) == 1L, "The final cooldown tick must survive a reload");
        helper.assertTrue(cooldown.beginNaturalEpisode(74_000L, 0.0F), "The next episode becomes eligible at exactly three Minecraft days");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void stoppedRainCancelsWarningAndDoesNotReactivate(GameTestHelper helper) {
        AcidRainSavedData state = new AcidRainSavedData();
        state.beginEpisode(100L);
        state.tick(200L, false);
        state.tick(1_000L, true);
        helper.assertTrue(state.phase() == AcidRainSavedData.Phase.CLEAR, "Rain ending must cancel the warning");
        helper.assertFalse(state.isActive(), "A later normal rain cannot resume the canceled warning");
        helper.assertTrue(state.cooldownRemaining(200L) == 72_000L, "Cancellation must also prevent immediate repeated episodes");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void warningAndActivePacketsRoundTrip(GameTestHelper helper) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            AcidRainStatePayload warning = new AcidRainStatePayload(false, 300);
            AcidRainStatePayload active = new AcidRainStatePayload(true, 0);
            AcidRainStatePayload.STREAM_CODEC.encode(buffer, warning);
            AcidRainStatePayload.STREAM_CODEC.encode(buffer, active);
            helper.assertTrue(warning.equals(AcidRainStatePayload.STREAM_CODEC.decode(buffer)), "Client sync must retain the remaining warning time");
            helper.assertTrue(active.equals(AcidRainStatePayload.STREAM_CODEC.decode(buffer)), "Client sync must distinguish active acid from a warning");
            helper.assertTrue(buffer.readableBytes() == 0, "The packet codec must consume each complete payload");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "acid_weather_transition")
    public void vanillaWeatherTransitionRollsOnlyOnceAndIgnoresCommands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WeatherSnapshot snapshot = new WeatherSnapshot(level);
        try {
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true, level.getServer());
            AcidRainSavedData state = AcidRainSavedData.get(level);
            long luckySeed = seedFor(true);
            long unluckySeed = seedFor(false);

            level.setWeatherParameters(0, 20_000, true, false);
            snapshot.weather.setThunderTime(10_000);
            level.getRandom().setSeed(luckySeed);
            advanceWeather(level);
            helper.assertTrue(state.phase() == AcidRainSavedData.Phase.CLEAR, "Commanded rain must not be treated as a natural start");

            level.setWeatherParameters(0, 1, false, false);
            snapshot.weather.setThunderTime(10_000);
            level.getRandom().setSeed(unluckySeed);
            advanceWeather(level);
            helper.assertTrue(snapshot.weather.isRaining(), "Vanilla weather must actually transition to rain");
            helper.assertTrue(state.phase() == AcidRainSavedData.Phase.CLEAR, "An unsuccessful roll must leave normal rain");

            snapshot.weather.setRainTime(20_000);
            level.getRandom().setSeed(luckySeed);
            for (int tick = 0; tick < 25; tick++) advanceWeather(level);
            helper.assertTrue(state.phase() == AcidRainSavedData.Phase.CLEAR, "Ongoing rain must never roll again on subsequent ticks");

            level.setWeatherParameters(0, 1, false, false);
            snapshot.weather.setThunderTime(10_000);
            level.getRandom().setSeed(luckySeed);
            advanceWeather(level);
            helper.assertTrue(state.phase() == AcidRainSavedData.Phase.WARNING, "A successful natural start must trigger the warning through the server mixin");
            state.endEpisode(level.getGameTime());
            level.setWeatherParameters(0, 1, false, false);
            snapshot.weather.setThunderTime(10_000);
            level.getRandom().setSeed(luckySeed);
            advanceWeather(level);
            helper.assertTrue(state.phase() == AcidRainSavedData.Phase.CLEAR, "Natural weather must respect the persistent cooldown even with a winning roll");
        } finally {
            snapshot.restore();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "acid_weather_effects", timeoutTicks = 800)
    public void warningIsHarmlessThenExposedTerrainAndCreaturesCorrode(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WeatherSnapshot snapshot = new WeatherSnapshot(level);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Runnable cleanup = () -> {
            player.discard();
            snapshot.restore();
        };
        try {
            helper.setBiome(Biomes.PLAINS);
            // The empty GameTest structure has a light-transparent barrier roof that still blocks rain.
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 8; z++) {
                    for (int y = 3; y <= 12; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (helper.getBlockState(pos).is(Blocks.BARRIER)) helper.setBlock(pos, Blocks.AIR);
                    }
                }
            }
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
            level.setWeatherParameters(0, 20_000, true, false);
            level.setRainLevel(1.0F);
            level.setThunderLevel(0.0F);
            BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
            player.moveTo(center.getX(), center.getY(), center.getZ(), 0, 0);
            helper.setBlock(2, 1, 2, Blocks.DIRT);
            helper.setBlock(4, 1, 2, Blocks.FARMLAND);
            helper.setBlock(4, 2, 2, Blocks.WHEAT);
            helper.setBlock(6, 1, 2, Blocks.CAULDRON);
            helper.setBlock(2, 1, 5, Blocks.DIRT);
            helper.setBlock(2, 4, 5, Blocks.STONE);
            var golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(3, 2, 3));
            helper.assertTrue(level.players().contains(player) && !player.isSpectator(), "The effect fixture requires a nearby nonspectator server player");
            helper.assertTrue(AcidRainManager.setActive(level, true), "The controller path must begin with a warning");
            helper.runAfterDelay(590, () -> {
                try {
                    helper.assertFalse(AcidRainSavedData.get(level).isActive(), "The warning must still be harmless after 590 ticks");
                    BlockPos exposed = helper.absolutePos(new BlockPos(2, 2, 2));
                    helper.assertTrue(level.isRainingAt(exposed), "Rain fixture: raining=" + level.isRaining()
                            + ", sky=" + level.canSeeSky(exposed) + ", height="
                            + level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, exposed.getX(), exposed.getZ())
                            + ", precipitation=" + level.getBiome(exposed).value().getPrecipitationAt(exposed));
                    helper.assertBlockPresent(Blocks.DIRT, 2, 1, 2);
                    helper.assertBlockPresent(Blocks.WHEAT, 4, 2, 2);
                    helper.assertBlockPresent(Blocks.CAULDRON, 6, 1, 2);
                    helper.assertFalse(golem.isRemoved(), "Acid must not rust creatures during the warning");
                } catch (RuntimeException failure) {
                    cleanup.run();
                    throw failure;
                }
                helper.runAfterDelay(120, () -> {
                    try {
                        helper.assertTrue(AcidRainSavedData.get(level).isActive(), "Acid effects must start once the warning ends");
                        helper.assertBlockPresent(ModBlocks.MOLDY_DIRT, 2, 1, 2);
                        helper.assertBlockNotPresent(Blocks.WHEAT, 4, 2, 2);
                        helper.assertBlockPresent(ModBlocks.ACID_RAIN_CAULDRON, 6, 1, 2);
                        helper.assertBlockPresent(Blocks.DIRT, 2, 1, 5);
                        helper.assertTrue(golem.isRemoved(), "Exposed iron golems must rust after the warning");
                        level.setWeatherParameters(20_000, 0, false, false);
                        AcidRainManager.tickWorld(level);
                        helper.assertTrue(AcidRainSavedData.get(level).cooldownRemaining(level.getGameTime()) == 72_000L,
                                "The live episode must start the three-day cooldown when rain ends");
                        helper.succeed();
                    } finally {
                        cleanup.run();
                    }
                });
            });
        } catch (RuntimeException failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static AcidRainSavedData reload(AcidRainSavedData data, GameTestHelper helper) {
        return AcidRainSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
    }

    private static long seedFor(boolean lucky) {
        for (long seed = 0; seed < 100_000; seed++) {
            if ((RandomSource.create(seed).nextFloat() < AcidRainSavedData.NATURAL_CHANCE) == lucky) return seed;
        }
        throw new IllegalStateException("No suitable deterministic weather seed");
    }

    private static void advanceWeather(ServerLevel level) {
        try {
            Method method = ServerLevel.class.getDeclaredMethod("advanceWeatherCycle");
            method.setAccessible(true);
            method.invoke(level);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to exercise the vanilla weather transition", failure);
        }
    }

    private static final class WeatherSnapshot {
        private final ServerLevel level;
        private final ServerLevelData weather;
        private final AcidRainSavedData previousState;
        private final boolean weatherCycle;
        private final boolean raining;
        private final boolean thundering;
        private final int clearTime;
        private final int rainTime;
        private final int thunderTime;
        private final float rainLevel;
        private final float thunderLevel;

        private WeatherSnapshot(ServerLevel level) {
            this.level = level;
            weather = (ServerLevelData) level.getLevelData();
            previousState = AcidRainSavedData.get(level);
            weatherCycle = level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
            raining = weather.isRaining();
            thundering = weather.isThundering();
            clearTime = weather.getClearWeatherTime();
            rainTime = weather.getRainTime();
            thunderTime = weather.getThunderTime();
            rainLevel = level.getRainLevel(1.0F);
            thunderLevel = level.getThunderLevel(1.0F);
            level.getDataStorage().set("universegate_acid_rain", new AcidRainSavedData());
        }

        private void restore() {
            level.getDataStorage().set("universegate_acid_rain", previousState);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(weatherCycle, level.getServer());
            weather.setClearWeatherTime(clearTime);
            weather.setRainTime(rainTime);
            weather.setThunderTime(thunderTime);
            weather.setRaining(raining);
            weather.setThundering(thundering);
            level.setRainLevel(rainLevel);
            level.setThunderLevel(thunderLevel);
        }
    }
}
