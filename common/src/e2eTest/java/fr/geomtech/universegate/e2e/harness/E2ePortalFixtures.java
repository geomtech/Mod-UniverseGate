package fr.geomtech.universegate.e2e.harness;

import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.UniverseGateDimensions;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reusable opaque-box geometry constants, dimension fixtures, and network buffer helpers
 * grounded in UniverseGate portal specifications.
 */
public final class E2ePortalFixtures {

    // Frame dimensions (PortalFrameDetector / PortalFrameHelper)
    public static final int INNER_WIDTH = PortalFrameDetector.INNER_WIDTH;
    public static final int INNER_HEIGHT = PortalFrameDetector.INNER_HEIGHT;
    public static final int OUTER_WIDTH = INNER_WIDTH + 2;
    public static final int OUTER_HEIGHT = INNER_HEIGHT + 2;
    public static final int HALF_OUTER_WIDTH = OUTER_WIDTH / 2;
    public static final int TOP_Y = OUTER_HEIGHT - 1;
    public static final int EXPECTED_INTERIOR_CELL_COUNT = INNER_WIDTH * INNER_HEIGHT;
    public static final int EXPECTED_FRAME_BLOCK_COUNT = OUTER_WIDTH * OUTER_HEIGHT - EXPECTED_INTERIOR_CELL_COUNT - 1;

    // Ring & membrane dimensions (PortalGateRingLayer / PortalMembraneLayer)
    public static final float RING_CENTER_H_PX = 8.0F;
    public static final float RING_CENTER_V_PX = 48.0F;
    public static final float RING_INNER_RADIUS_PX = 35.0F;
    public static final float RING_OUTER_RADIUS_PX = 47.0F;
    public static final float RING_Z0_PX = 4.5F;
    public static final float RING_Z1_PX = 11.5F;
    public static final int MEMBRANE_RADIUS_PX = 36;
    public static final float MEMBRANE_DEPTH_PX = 8.0F;

    public static final double RING_CENTER_OFFSET_X = RING_CENTER_H_PX / 16.0D;
    public static final double RING_CENTER_OFFSET_Y = RING_CENTER_V_PX / 16.0D;
    public static final double RING_CENTER_OFFSET_Z = MEMBRANE_DEPTH_PX / 16.0D;
    public static final double RING_INNER_RADIUS_BLOCKS = RING_INNER_RADIUS_PX / 16.0D;
    public static final double RING_OUTER_RADIUS_BLOCKS = RING_OUTER_RADIUS_PX / 16.0D;
    public static final double MEMBRANE_RADIUS_BLOCKS = MEMBRANE_RADIUS_PX / 16.0D;
    public static final double MEMBRANE_DEPTH_BLOCKS = MEMBRANE_DEPTH_PX / 16.0D;

    // Deterministic portal locations & dimension keys
    public static final BlockPos DEFAULT_SOURCE_CORE = new BlockPos(100, 64, 200);
    public static final BlockPos DEFAULT_TARGET_CORE = new BlockPos(-320, 72, 480);
    public static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;
    public static final ResourceKey<Level> NETHER = Level.NETHER;
    public static final ResourceKey<Level> END = Level.END;
    public static final ResourceKey<Level> RIFT = UniverseGateDimensions.RIFT;

    private E2ePortalFixtures() {}

    public static Direction exitNormalFor(Direction right) {
        return right.getClockWise();
    }

    public static Vec3 portalCenter(BlockPos corePos) {
        return new Vec3(
                corePos.getX() + RING_CENTER_OFFSET_X,
                corePos.getY() + RING_CENTER_OFFSET_Y,
                corePos.getZ() + RING_CENTER_OFFSET_Z
        );
    }

    public static AABB portalRenderBounds(BlockPos corePos) {
        return new AABB(
                corePos.getX() - 3,
                corePos.getY(),
                corePos.getZ() - 3,
                corePos.getX() + 4,
                corePos.getY() + 7,
                corePos.getZ() + 4
        );
    }

    public static List<BlockPos> expectedInterior(BlockPos corePos, Direction right) {
        List<BlockPos> result = new ArrayList<>(EXPECTED_INTERIOR_CELL_COUNT);
        int halfInnerWidth = INNER_WIDTH / 2;
        for (int dy = 1; dy <= INNER_HEIGHT; dy++) {
            for (int dx = -halfInnerWidth; dx <= halfInnerWidth; dx++) {
                result.add(corePos.offset(right.getStepX() * dx, dy, right.getStepZ() * dx));
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static List<BlockPos> expectedFrameBorderExcludingCore(BlockPos corePos, Direction right) {
        List<BlockPos> result = new ArrayList<>(EXPECTED_FRAME_BLOCK_COUNT);
        for (int dy = 0; dy <= TOP_Y; dy++) {
            for (int dx = -HALF_OUTER_WIDTH; dx <= HALF_OUTER_WIDTH; dx++) {
                boolean isBorder = dy == 0 || dy == TOP_Y || dx == -HALF_OUTER_WIDTH || dx == HALF_OUTER_WIDTH;
                if (!isBorder || (dx == 0 && dy == 0)) {
                    continue;
                }
                result.add(corePos.offset(right.getStepX() * dx, dy, right.getStepZ() * dx));
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static PortalFrameDetector.FrameMatch frameMatch(BlockPos corePos, Direction right) {
        return new PortalFrameDetector.FrameMatch(right, expectedInterior(corePos, right));
    }

    public static RegistryAccess.Frozen registryAccess() {
        return E2eBootstrap.registryAccess();
    }

    public static RegistryFriendlyByteBuf newRegistryFriendlyByteBuf() {
        return E2eBootstrap.newRegistryFriendlyByteBuf();
    }

    public static RegistryFriendlyByteBuf newRegistryFriendlyByteBuf(RegistryAccess access) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
    }
}
