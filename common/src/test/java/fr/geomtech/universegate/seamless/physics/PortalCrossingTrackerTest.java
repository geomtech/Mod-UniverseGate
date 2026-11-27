package fr.geomtech.universegate.seamless.physics;

import fr.geomtech.universegate.ModBlocks;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.PortalFieldBlock;
import fr.geomtech.universegate.PortalRegistrySavedData;
import fr.geomtech.universegate.PortalTeleportHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
//? if >=1.21.11 {
/*import net.minecraft.world.entity.InsideBlockEffectApplier;
*///?}
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
//? if >=1.21.11 {
/*import static org.mockito.ArgumentMatchers.anyBoolean;
*///?}
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalCrossingTrackerTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));

    @AfterEach
    void clearTracker() {
        PortalCrossingTracker.clearAll();
    }

    @Test
    void fieldContactsAreDeferredAndConsumedOnceWithAnImmutablePosition() {
        UUID playerId = UUID.randomUUID();
        BlockPos.MutableBlockPos field = new BlockPos.MutableBlockPos(1, 65, 2);
        PortalCrossingTracker.deferPlayerCrossing(playerId, OVERWORLD, field);
        field.set(9, 70, 9);

        assertEquals(new BlockPos(1, 65, 2),
                PortalCrossingTracker.takeDeferredPlayerCrossing(playerId, OVERWORLD));
        assertNull(PortalCrossingTracker.takeDeferredPlayerCrossing(playerId, OVERWORLD));
    }

    @Test
    void dimensionChangesAndRemovalDiscardOldFieldContacts() {
        UUID playerId = UUID.randomUUID();
        PortalCrossingTracker.deferPlayerCrossing(playerId, OVERWORLD, BlockPos.ZERO);
        assertNull(PortalCrossingTracker.takeDeferredPlayerCrossing(playerId, NETHER));
        assertNull(PortalCrossingTracker.takeDeferredPlayerCrossing(playerId, OVERWORLD));

        PortalCrossingTracker.deferPlayerCrossing(playerId, OVERWORLD, BlockPos.ZERO);
        PortalCrossingTracker.onEntityRemoved(playerId);
        assertNull(PortalCrossingTracker.takeDeferredPlayerCrossing(playerId, OVERWORLD));
    }

    @ParameterizedTest
    @ValueSource(floats = {0.0F, -90.0F})
    void fieldContactWaitsForFinalMovementPositionAndLookBeforeTeleporting(float finalYaw) {
        TraversalFixture fixture = new TraversalFixture();
        try (MockedStatic<PortalRegistrySavedData> registry = fixture.openRegistry()) {
            PortalCrossingTracker.tickServerPlayers(fixture.server);

            fixture.position = new Vec3(0.5D, 65.2D, 0.5D);
            fixture.yaw = -20.0F;
            fixture.pitch = -15.0F;
            fixture.touchField();
            assertTrue(fixture.teleports.isEmpty(), "Contact inside movement must not teleport yet");

            fixture.position = new Vec3(0.65D, 65.3D, 0.46D);
            fixture.yaw = finalYaw;
            fixture.pitch = 27.0F;
            PortalCrossingTracker.tickServerPlayers(fixture.server);

            assertEquals(1, fixture.teleports.size());
            TeleportCall teleport = fixture.teleports.getFirst();
            assertEquals(new Vec3(0.65D, 65.3D, 0.46D), teleport.sourcePosition());
            assertEquals(finalYaw, teleport.sourceYaw());
            assertEquals(27.0F, teleport.sourcePitch());
            assertEquals(33.02D, fixture.position.x, 1.0E-6D);
            assertEquals(65.3D, fixture.position.y, 1.0E-6D);
            assertEquals(32.65D, fixture.position.z, 1.0E-6D);
            assertEquals(finalYaw + 90.0F, fixture.yaw, 1.0E-5F);
            assertEquals(27.0F, fixture.pitch);
            assertEquals(0.18D, fixture.velocity.x, 1.0E-6D);
            assertEquals(0.0D, fixture.velocity.z, 1.0E-6D);

            PortalCrossingTracker.tickServerPlayers(fixture.server);
            assertEquals(1, fixture.teleports.size(), "A deferred contact must be consumed once");
        }
    }

    @Test
    void fieldRemovedAfterContactDoesNotTeleportPlayer() {
        TraversalFixture fixture = new TraversalFixture();
        try (MockedStatic<PortalRegistrySavedData> registry = fixture.openRegistry()) {
            PortalCrossingTracker.tickServerPlayers(fixture.server);
            fixture.touchField();
            fixture.blocks.remove(fixture.fieldPos);
            PortalCrossingTracker.tickServerPlayers(fixture.server);

            assertTrue(fixture.teleports.isEmpty());
            assertNull(PortalCrossingTracker.takeDeferredPlayerCrossing(fixture.player.getUUID(), OVERWORLD));
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {3.0D, 8.0D, 9.5D})
    void fastMovementCannotSkipThePortalBetweenSweepSamples(double distance) {
        TraversalFixture fixture = new TraversalFixture();
        try (MockedStatic<PortalRegistrySavedData> registry = fixture.openRegistry()) {
            fixture.position = new Vec3(0.6D, 65.25D, 1.8D);
            PortalCrossingTracker.tickServerPlayers(fixture.server);
            fixture.position = new Vec3(0.6D, 65.25D, 1.8D - distance);
            PortalCrossingTracker.tickServerPlayers(fixture.server);

            assertEquals(1, fixture.teleports.size(), "Swept traversal must find the field without entityInside");
            assertEquals(33.02D, fixture.position.x, 1.0E-6D);
            assertEquals(32.6D, fixture.position.z, 1.0E-6D);
        }
    }

    @Test
    void sweptCrossingDoesNotReadBlocksInUnloadedChunks() {
        TraversalFixture fixture = new TraversalFixture();
        fixture.position = new Vec3(0.6D, 65.25D, 1.8D);
        PortalCrossingTracker.tickServerPlayers(fixture.server);
        when(fixture.level.isLoaded(any(BlockPos.class))).thenReturn(false);
        clearInvocations(fixture.level);

        fixture.position = new Vec3(0.6D, 65.25D, -6.2D);
        PortalCrossingTracker.tickServerPlayers(fixture.server);

        assertTrue(fixture.teleports.isEmpty());
        verify(fixture.level, never()).getBlockState(any(BlockPos.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedVehicleTeleportRestoresPassengerHierarchyAndVelocity(boolean nested) {
        TraversalFixture fixture = new TraversalFixture();
        Entity vehicle = mock(Entity.class);
        Entity firstPassenger = mock(Entity.class);
        Entity secondPassenger = mock(Entity.class);
        Entity nestedPassenger = mock(Entity.class);
        when(vehicle.level()).thenReturn(fixture.level);
        when(vehicle.isAlive()).thenReturn(true);
        when(vehicle.getUUID()).thenReturn(UUID.randomUUID());
        Mockito.<EntityType<?>>when(vehicle.getType()).thenReturn(EntityType.PIG);
        when(vehicle.position()).thenReturn(fixture.position);
        when(vehicle.getX()).thenReturn(fixture.position.x);
        when(vehicle.getY()).thenReturn(fixture.position.y);
        when(vehicle.getZ()).thenReturn(fixture.position.z);
        vehicle.xo = fixture.position.x;
        vehicle.zo = 0.8D;
        when(vehicle.getDeltaMovement()).thenAnswer(invocation -> fixture.velocity);
        doAnswer(invocation -> {
            fixture.velocity = invocation.getArgument(0);
            return null;
        }).when(vehicle).setDeltaMovement(any(Vec3.class));
        when(vehicle.getPassengers()).thenReturn(List.of(firstPassenger, secondPassenger));
        if (nested) {
            when(firstPassenger.getPassengers()).thenReturn(List.of(nestedPassenger));
        }
        for (Entity passenger : List.of(firstPassenger, secondPassenger, nestedPassenger)) {
            //? if >=1.21.11 {
            /*when(passenger.startRiding(any(Entity.class), anyBoolean(), anyBoolean())).thenReturn(true);
            *///?} else {
            when(passenger.startRiding(any(Entity.class), Mockito.anyBoolean())).thenReturn(true);
            //?}
        }
        Vec3 originalVelocity = fixture.velocity;

        try (MockedStatic<PortalRegistrySavedData> registry = fixture.openRegistry()) {
            PortalTeleportHandler.tryTeleport(vehicle, fixture.fieldPos);
        }

        // The mock transport rejects the move after the real handler has detached the passengers.
        //? if >=1.21.11 {
        /*verify(vehicle).teleportTo(any(ServerLevel.class), anyDouble(), anyDouble(), anyDouble(),
                anySet(), anyFloat(), anyFloat(), anyBoolean());
        *///?} else {
        verify(vehicle).teleportTo(any(ServerLevel.class), anyDouble(), anyDouble(), anyDouble(),
                anySet(), anyFloat(), anyFloat());
        //?}
        verify(firstPassenger).stopRiding();
        verify(secondPassenger).stopRiding();
        if (nested) {
            verify(nestedPassenger).stopRiding();
        }
        var remountOrder = inOrder(firstPassenger, secondPassenger, nestedPassenger);
        //? if >=1.21.11 {
        /*remountOrder.verify(firstPassenger).startRiding(vehicle, true, true);
        if (nested) {
            remountOrder.verify(nestedPassenger).startRiding(firstPassenger, true, true);
        }
        remountOrder.verify(secondPassenger).startRiding(vehicle, true, true);
        *///?} else {
        remountOrder.verify(firstPassenger).startRiding(vehicle, true);
        if (nested) {
            remountOrder.verify(nestedPassenger).startRiding(firstPassenger, true);
        }
        remountOrder.verify(secondPassenger).startRiding(vehicle, true);
        //?}
        assertEquals(originalVelocity, fixture.velocity);
        for (Entity passenger : List.of(firstPassenger, secondPassenger, nestedPassenger)) {
            //? if >=1.21.11 {
            /*verify(passenger, never()).teleportTo(any(ServerLevel.class), anyDouble(), anyDouble(), anyDouble(),
                    anySet(), anyFloat(), anyFloat(), anyBoolean());
            *///?} else {
            verify(passenger, never()).teleportTo(any(ServerLevel.class), anyDouble(), anyDouble(), anyDouble(),
                    anySet(), anyFloat(), anyFloat());
            //?}
        }
    }

    private record TeleportCall(Vec3 sourcePosition, float sourceYaw, float sourcePitch) {}

    /**
     * Real field callbacks, crossing checks and teleport handler; only world services and the
     * vanilla player transport are simulated. No live server/network is started by this test.
     */
    private static final class TraversalFixture {
        private final MinecraftServer server = mock(MinecraftServer.class);
        private final ServerLevel level = mock(ServerLevel.class);
        private final ServerPlayer player = mock(ServerPlayer.class);
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final List<TeleportCall> teleports = new ArrayList<>();
        private final PortalRegistrySavedData registry = new PortalRegistrySavedData();
        private final BlockPos fieldPos = new BlockPos(0, 66, 0);
        private Vec3 position = new Vec3(0.6D, 65.25D, 0.64D);
        private Vec3 velocity = new Vec3(0.0D, 0.0D, -0.18D);
        private float yaw;
        private float pitch;

        private TraversalFixture() {
            BlockPos sourceCore = new BlockPos(0, 64, 0);
            BlockPos targetCore = new BlockPos(32, 64, 32);
            UUID targetId = UUID.randomUUID();
            PlayerList players = mock(PlayerList.class);
            when(server.getPlayerList()).thenReturn(players);
            when(players.getPlayers()).thenReturn(List.of(player));
            when(server.getLevel(OVERWORLD)).thenReturn(level);
            when(level.getServer()).thenReturn(server);
            when(level.dimension()).thenReturn(OVERWORLD);
            when(level.getGameTime()).thenReturn(1000L);
            when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
            when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation ->
                    blocks.getOrDefault(invocation.getArgument(0), Blocks.AIR.defaultBlockState()));
            addPortal(sourceCore, Direction.EAST, targetId);
            addPortal(targetCore, Direction.SOUTH, UUID.randomUUID());
            registry.upsertPortal(level, targetId, "Target", targetCore);

            when(player.getUUID()).thenReturn(UUID.randomUUID());
            Mockito.<EntityType<?>>when(player.getType()).thenReturn(EntityType.PLAYER);
            when(player.level()).thenReturn(level);
            //? if <1.21.11 {
            when(player.serverLevel()).thenReturn(level);
            //?}
            when(player.isAlive()).thenReturn(true);
            when(player.getRootVehicle()).thenReturn(player);
            when(player.getBbWidth()).thenReturn(0.6F);
            when(player.getBbHeight()).thenReturn(1.8F);
            when(player.position()).thenAnswer(invocation -> position);
            when(player.getX()).thenAnswer(invocation -> position.x);
            when(player.getY()).thenAnswer(invocation -> position.y);
            when(player.getZ()).thenAnswer(invocation -> position.z);
            when(player.getYRot()).thenAnswer(invocation -> yaw);
            when(player.getXRot()).thenAnswer(invocation -> pitch);
            when(player.getDeltaMovement()).thenAnswer(invocation -> velocity);
            doAnswer(invocation -> {
                velocity = invocation.getArgument(0);
                return null;
            }).when(player).setDeltaMovement(any(Vec3.class));
            doAnswer(invocation -> {
                yaw = invocation.getArgument(0);
                return null;
            }).when(player).setYRot(anyFloat());
            doAnswer(invocation -> {
                pitch = invocation.getArgument(0);
                return null;
            }).when(player).setXRot(anyFloat());
            doAnswer(invocation -> {
                teleports.add(new TeleportCall(position, yaw, pitch));
                position = new Vec3(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3));
                yaw = invocation.getArgument(5);
                pitch = invocation.getArgument(6);
                return true;
            //? if >=1.21.11 {
            /*}).when(player).teleportTo(any(ServerLevel.class), anyDouble(), anyDouble(), anyDouble(),
                    anySet(), anyFloat(), anyFloat(), anyBoolean());
            *///?} else {
            }).when(player).teleportTo(any(ServerLevel.class), anyDouble(), anyDouble(), anyDouble(),
                    anySet(), anyFloat(), anyFloat());
            //?}
        }

        private MockedStatic<PortalRegistrySavedData> openRegistry() {
            MockedStatic<PortalRegistrySavedData> access = mockStatic(PortalRegistrySavedData.class);
            access.when(() -> PortalRegistrySavedData.get(server)).thenReturn(registry);
            return access;
        }

        private void touchField() {
            BlockState field = blocks.get(fieldPos);
            //? if >=1.21.11 {
            /*field.entityInside(level, fieldPos, player, InsideBlockEffectApplier.NOOP, true);
            *///?} else {
            field.entityInside(level, fieldPos, player);
            //?}
        }

        private void addPortal(BlockPos corePos, Direction right, UUID targetId) {
            for (int dy = 0; dy <= 5; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    BlockPos pos = corePos.relative(right, dx).above(dy);
                    boolean border = dy == 0 || dy == 5 || Math.abs(dx) == 2;
                    blocks.put(pos, border ? ModBlocks.PORTAL_FRAME.defaultBlockState()
                            : ModBlocks.PORTAL_FIELD.defaultBlockState().setValue(PortalFieldBlock.AXIS, right.getAxis()));
                }
            }
            blocks.put(corePos, ModBlocks.PORTAL_CORE.defaultBlockState());
            PortalCoreBlockEntity core = mock(PortalCoreBlockEntity.class);
            when(core.getBlockPos()).thenReturn(corePos);
            when(core.isActive()).thenReturn(true);
            when(core.isOutboundTravelEnabled()).thenReturn(true);
            when(core.getTargetPortalId()).thenReturn(targetId);
            when(level.getBlockEntity(corePos)).thenReturn(core);
        }
    }
}
