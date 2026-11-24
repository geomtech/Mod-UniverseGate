package fr.geomtech.universegate.e2e.harness;

import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.PortalFrameHelper;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("harness")
@DisplayName("E2E-1 Harness Self-Check")
class HarnessSelfCheckE2eTest {

    @Test
    @DisplayName("E2eBootstrap is idempotent and initializes built-in registries")
    void bootstrapIsIdempotentAndInitializesBuiltInRegistries() {
        E2eBootstrap.ensureMinecraftBootstrapped();
        E2eBootstrap.ensureMinecraftBootstrapped();

        assertTrue(E2eBootstrap.isBootstrapped());
        assertTrue(SharedConstants.getProtocolVersion() > 0);
        assertFalse(BuiltInRegistries.BLOCK.keySet().isEmpty());
        assertNotNull(E2ePortalFixtures.registryAccess());
    }

    @Test
    @DisplayName("RegistryFriendlyByteBuf and ClientboundLevelChunkWithLightPacket.STREAM_CODEC work headlessly")
    void chunkPacketAndRegistryBufCodecsWorkHeadlessly() {
        E2eBootstrap.ensureMinecraftBootstrapped();

        StreamCodec<RegistryFriendlyByteBuf, ClientboundLevelChunkWithLightPacket> chunkCodec =
                SeamlessContractProbe.requireStreamCodec(ClientboundLevelChunkWithLightPacket.class, "STREAM_CODEC");
        assertNotNull(chunkCodec);
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                StreamCodec.class, "encode", void.class, Object.class, Object.class));
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                StreamCodec.class, "decode", Object.class, Object.class));

        RegistryFriendlyByteBuf buf = E2ePortalFixtures.newRegistryFriendlyByteBuf();
        try {
            assertNotNull(buf.registryAccess());
        } finally {
            buf.release();
        }

        BlockPos expectedPos = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        BlockPos decodedPos = SeamlessContractProbe.roundTripByteBufCodec(BlockPos.STREAM_CODEC, expectedPos);
        assertEquals(expectedPos, decodedPos);

        Direction decodedDir = SeamlessContractProbe.roundTripByteBufCodec(Direction.STREAM_CODEC, Direction.SOUTH);
        assertEquals(Direction.SOUTH, decodedDir);
    }

    @Test
    @DisplayName("SeamlessContractProbe positively resolves PortalFrameDetector and invokes PortalFrameHelper")
    void contractProbePositivelyInvokesPortalFrameDetectorAndFrameHelper() {
        Class<?> detectorClass = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalFrameDetector");
        assertEquals(PortalFrameDetector.class, detectorClass);

        int innerWidth = SeamlessContractProbe.readStaticField(detectorClass, "INNER_WIDTH", int.class);
        int innerHeight = SeamlessContractProbe.readStaticField(detectorClass, "INNER_HEIGHT", int.class);
        assertEquals(E2ePortalFixtures.INNER_WIDTH, innerWidth);
        assertEquals(E2ePortalFixtures.INNER_HEIGHT, innerHeight);

        // Verify signature without invoking with non-null Level so common headless tests do not trigger ModBlocks -> Services.PLATFORM.
        assertNotNull(SeamlessContractProbe.requireStaticMethod(
                detectorClass, "find", Optional.class, Level.class, BlockPos.class));

        Class<?> frameMatchClass = SeamlessContractProbe.requireRecord(
                "fr.geomtech.universegate.PortalFrameDetector$FrameMatch",
                "right",
                "interior"
        );

        for (Direction right : List.of(Direction.EAST, Direction.SOUTH)) {
            BlockPos corePos = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
            List<BlockPos> expectedInterior = E2ePortalFixtures.expectedInterior(corePos, right);
            assertEquals(E2ePortalFixtures.EXPECTED_INTERIOR_CELL_COUNT, expectedInterior.size());

            Object match = SeamlessContractProbe.newInstance(
                    frameMatchClass,
                    new Class<?>[]{Direction.class, List.class},
                    right,
                    expectedInterior
            );
            assertEquals(right, SeamlessContractProbe.invokeInstance(match, "right", Direction.class, new Class<?>[0]));
            assertEquals(expectedInterior, SeamlessContractProbe.invokeInstance(match, "interior", List.class, new Class<?>[0]));

            @SuppressWarnings("unchecked")
            List<BlockPos> frameBlocks = SeamlessContractProbe.invokeStatic(
                    PortalFrameHelper.class,
                    "collectFrame",
                    List.class,
                    new Class<?>[]{PortalFrameDetector.FrameMatch.class, BlockPos.class},
                    match,
                    corePos
            );
            List<BlockPos> expectedFrame = E2ePortalFixtures.expectedFrameBorderExcludingCore(corePos, right);
            assertEquals(E2ePortalFixtures.EXPECTED_FRAME_BLOCK_COUNT, frameBlocks.size());
            assertEquals(expectedFrame, frameBlocks);
        }
    }

    @Test
    @DisplayName("SeamlessContractProbe throws AssertionFailedError (never skips) for missing contracts")
    void contractProbeThrowsAssertionFailedErrorForMissingContracts() {
        assertThrows(AssertionFailedError.class, () ->
                SeamlessContractProbe.requireClass("fr.geomtech.universegate.seamless.NonExistentContract"));
        assertThrows(AssertionFailedError.class, () ->
                SeamlessContractProbe.requireStaticMethod(PortalFrameDetector.class, "nonExistentMethod", void.class));
        assertThrows(AssertionFailedError.class, () ->
                SeamlessContractProbe.readStaticField(PortalFrameDetector.class, "NON_EXISTENT_FIELD", int.class));
        assertThrows(AssertionFailedError.class, () ->
                SeamlessContractProbe.requireRecord(PortalFrameDetector.class.getName(), "right"));
    }

    @Test
    @DisplayName("E2ePortalFixtures geometry constants match PortalGateRingLayer, PortalMembraneLayer, and PortalFrameDetector")
    void portalGeometryFixturesMatchGateLayerConstants() {
        Class<?> ringLayerClass = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalGateRingLayer");
        assertEquals(E2ePortalFixtures.RING_CENTER_H_PX,
                SeamlessContractProbe.readStaticField(ringLayerClass, "CENTER_H", float.class));
        assertEquals(E2ePortalFixtures.RING_CENTER_V_PX,
                SeamlessContractProbe.readStaticField(ringLayerClass, "CENTER_V", float.class));
        assertEquals(E2ePortalFixtures.RING_INNER_RADIUS_PX,
                SeamlessContractProbe.readStaticField(ringLayerClass, "INNER_RADIUS", float.class));
        assertEquals(E2ePortalFixtures.RING_OUTER_RADIUS_PX,
                SeamlessContractProbe.readStaticField(ringLayerClass, "OUTER_RADIUS", float.class));
        assertEquals(E2ePortalFixtures.RING_Z0_PX,
                SeamlessContractProbe.readStaticField(ringLayerClass, "RING_Z0", float.class));
        assertEquals(E2ePortalFixtures.RING_Z1_PX,
                SeamlessContractProbe.readStaticField(ringLayerClass, "RING_Z1", float.class));

        Class<?> membraneLayerClass = SeamlessContractProbe.requireClass("fr.geomtech.universegate.PortalMembraneLayer");
        assertEquals(E2ePortalFixtures.MEMBRANE_RADIUS_PX,
                SeamlessContractProbe.readStaticField(membraneLayerClass, "RADIUS", int.class));
        assertEquals(E2ePortalFixtures.MEMBRANE_DEPTH_PX,
                SeamlessContractProbe.readStaticField(membraneLayerClass, "DEPTH", float.class));

        assertEquals(E2ePortalFixtures.OUTER_WIDTH,
                SeamlessContractProbe.readStaticField(PortalFrameDetector.class, "OUTER_WIDTH", int.class));
        assertEquals(E2ePortalFixtures.OUTER_HEIGHT,
                SeamlessContractProbe.readStaticField(PortalFrameDetector.class, "OUTER_HEIGHT", int.class));
        assertEquals(E2ePortalFixtures.HALF_OUTER_WIDTH,
                SeamlessContractProbe.readStaticField(PortalFrameDetector.class, "HALF_OUTER_WIDTH", int.class));
        assertEquals(E2ePortalFixtures.TOP_Y,
                SeamlessContractProbe.readStaticField(PortalFrameDetector.class, "TOP_Y", int.class));

        BlockPos core = E2ePortalFixtures.DEFAULT_SOURCE_CORE;
        assertEquals(new Vec3(core.getX() + 0.5D, core.getY() + 3.0D, core.getZ() + 0.5D),
                E2ePortalFixtures.portalCenter(core));
        assertEquals(new AABB(core.getX() - 3, core.getY(), core.getZ() - 3,
                        core.getX() + 4, core.getY() + 7, core.getZ() + 4),
                E2ePortalFixtures.portalRenderBounds(core));
        assertEquals(Direction.SOUTH, E2ePortalFixtures.exitNormalFor(Direction.EAST));
    }
}
