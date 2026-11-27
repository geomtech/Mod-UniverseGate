package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortalGateGeometryTest {
    //? if <1.21.11 {
    @Test
    void formedPortalLightingDoesNotReadItsNeighbors() {
        BlockGetter world = mock(BlockGetter.class);
        BlockState dry = ModBlocks.PORTAL_FRAME.defaultBlockState().setValue(PortalFrameBlock.FORMED, true);
        BlockState wet = dry.setValue(PortalFrameBlock.WATERLOGGED, true);
        assertEquals(0, dry.getLightBlock(world, BlockPos.ZERO));
        assertEquals(1, wet.getLightBlock(world, BlockPos.ZERO));
        org.mockito.Mockito.verifyNoInteractions(world);
    }
    //?}

    @Test
    void formedBlocksKeepFallbackCollisionInSingleBlockPhysicsView() {
        BlockGetter physicsView = mock(BlockGetter.class);
        when(physicsView.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        when(physicsView.getBlockState(BlockPos.ZERO)).thenReturn(null);

        BlockState activeFrame = ModBlocks.PORTAL_FRAME.defaultBlockState()
                .setValue(PortalFrameBlock.FORMED, true)
                .setValue(PortalFrameBlock.ACTIVE, true);
        BlockState formedCore = ModBlocks.PORTAL_CORE.defaultBlockState().setValue(PortalCoreBlock.FORMED, true);
        assertSame(Shapes.block(), activeFrame.getCollisionShape(physicsView, BlockPos.ZERO, CollisionContext.empty()));
        assertSame(Shapes.block(), formedCore.getCollisionShape(physicsView, BlockPos.ZERO, CollisionContext.empty()));
        BlockState anchor = PortalRingSegments.anchorState(4, Direction.SOUTH)
                .setValue(PortalFrameBlock.FORMED, true).setValue(PortalFrameBlock.WATERLOGGED, true);
        assertSame(PortalGateGeometry.segmentShape(4, Direction.Axis.Z),
                anchor.getCollisionShape(physicsView, BlockPos.ZERO, CollisionContext.empty()));
    }

    @Test
    void portalCoreFrameAndAnchorsRetainSourceWaterOnPlacement() {
        Level level = mock(Level.class);
        BlockPlaceContext context = mock(BlockPlaceContext.class);
        when(context.getLevel()).thenReturn(level);
        when(context.getClickedPos()).thenReturn(BlockPos.ZERO);
        when(level.getFluidState(BlockPos.ZERO)).thenReturn(Fluids.WATER.getSource(false));

        for (Block block : List.of(ModBlocks.PORTAL_CORE, ModBlocks.PORTAL_FRAME)) {
            BlockState placed = block.getStateForPlacement(context);
            placed.initCache(); // The loader normally initializes the registered states.
            assertTrue(placed.getValue(PortalFrameBlock.WATERLOGGED));
            assertSame(Blocks.WATER, placed.getFluidState().createLegacyBlock().getBlock());
        }
        BlockState anchor = PortalFrameBlock.withPlacementWater(
                PortalRingSegments.anchorState(4, Direction.SOUTH), level, BlockPos.ZERO);
        anchor.initCache();
        assertTrue(anchor.getFluidState().isSource());
        assertTrue(PortalRingSegments.isAnchor(anchor, 4, Direction.SOUTH));

        when(level.getFluidState(BlockPos.ZERO)).thenReturn(Fluids.EMPTY.defaultFluidState());
        for (Block block : List.of(ModBlocks.PORTAL_CORE, ModBlocks.PORTAL_FRAME)) {
            BlockState placed = block.getStateForPlacement(context);
            placed.initCache();
            assertTrue(placed.getFluidState().isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"EAST", "SOUTH"})
    void submergedGateKeepsItsRingAndCoreCube(Direction right) {
        Fixture fixture = new Fixture(right);
        fixture.states.replaceAll((pos, state) -> state.setValue(PortalFrameBlock.WATERLOGGED, true));
        Direction depth = right.getClockWise();
        for (int y = 0; y <= 5; y++) {
            for (int x = -3; x <= 3; x++) {
                BlockPos cell = fixture.core.relative(right, x).above(y);
                fixture.states.putIfAbsent(cell, Blocks.WATER.defaultBlockState());
                fixture.states.put(cell.relative(depth), Blocks.WATER.defaultBlockState());
                fixture.states.put(cell.relative(depth.getOpposite()), Blocks.WATER.defaultBlockState());
            }
        }

        assertTrue(fixture.collides(2.9, 3.0, 0.5));
        assertTrue(fixture.collides(0, 0.9, 0.1));
        assertFalse(fixture.collides(2.55, 0.03, 0.1));
        assertFalse(fixture.collides(0, 3.0, 0.5));
        assertEquals(HitResult.Type.BLOCK, fixture.pick(2.9, 3.0).getType());
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"EAST", "SOUTH"})
    void modelCollisionsReplaceInvisibleCubesIncludingProtrusions(Direction right) {
        Fixture fixture = new Fixture(right);
        assertTrue(fixture.collides(2.9, 3.0, 0.5), "outer ring beyond the frame footprint");
        assertTrue(fixture.collides(1.4, 1.05, 0.5), "ring in an interior air cell");
        assertTrue(fixture.collides(0, 5.6, 0.5), "top of ring");
        assertFalse(fixture.collides(2.55, 0.03, 0.1), "no platform beside the core");
        assertFalse(fixture.collides(0, 3.0, 0.5), "circular opening");
        assertFalse(fixture.collides(2.1, 5.6, 0.5), "invisible frame corner");
        assertFalse(fixture.collides(2.5, 3.0, 0.1), "ring is only seven pixels deep");
        assertTrue(fixture.collides(0, 0.85, 0.1), "the core is a full cube");

        for (BlockPos pos : fixture.states.keySet()) {
            BlockState state = fixture.states.get(pos);
            assertEquals(state.getShape(fixture.world, pos).toAabbs(),
                    state.getCollisionShape(fixture.world, pos, CollisionContext.empty()).toAabbs());
        }
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"EAST", "SOUTH"})
    void brokenFrameLeavesMatchingGapsInSelectionAndCollision(Direction right) {
        Fixture fixture = new Fixture(right);
        fixture.states.computeIfPresent(fixture.core, (pos, core) -> core.setValue(PortalCoreBlock.AXIS, right.getAxis()));
        fixture.states.remove(fixture.core.relative(right));
        fixture.states.remove(PortalRingSegments.cell(fixture.core, right, 17));

        assertEquals(right, PortalGateGeometry.resolveRight(fixture.world, fixture.core));
        int mask = PortalGateGeometry.frameMask(fixture.world, fixture.core, right);
        assertEquals(0, mask & (1 << 17));
        assertEquals(0, mask & (1 << 13));
        assertTrue((mask & (1 << 4)) != 0);
        assertFalse(fixture.collides(2.9, 3.25, 0.5));
        assertFalse(fixture.collides(1, 0.1, 0.1));
        assertEquals(HitResult.Type.MISS, fixture.pick(2.9, 3.25).getType());
        assertTrue(fixture.collides(0, 5.6, 0.5));
        assertEquals(HitResult.Type.BLOCK, fixture.pick(0, 5.6).getType());
        assertTrue(fixture.collides(0, 0.25, 0.5));
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"EAST", "SOUTH"})
    void damagedRingMeshOmitsTheDestroyedPiece(Direction right) {
        Fixture fixture = new Fixture(right);
        fixture.states.remove(PortalRingSegments.cell(fixture.core, right, 4));
        int mask = PortalGateGeometry.frameMask(fixture.world, fixture.core, right);
        VertexConsumer consumer = mock(VertexConsumer.class, RETURNS_SELF);
        MultiBufferSource buffer = mock(MultiBufferSource.class);
        when(buffer.getBuffer(PortalGateRenderTypes.GATE_SOLID)).thenReturn(consumer);
        boolean[] topVertices = new boolean[3];
        when(consumer.addVertex(any(Matrix4f.class), anyFloat(), anyFloat(), anyFloat())).thenAnswer(vertex -> {
            float horizontal = vertex.getArgument(right == Direction.EAST ? 1 : 3);
            float vertical = vertex.getArgument(2);
            if (vertical > 5.0F) {
                if (horizontal > 0.40F && horizontal < 0.60F) topVertices[0] = true;
                if (horizontal > 1.0F && horizontal < 2.0F) topVertices[1] = true;
                if (horizontal > 0.02F && horizontal < 0.39F) topVertices[2] = true;
            }
            return consumer;
        });
        PortalGateRenderContext context = new PortalGateRenderContext(null, null, fixture.core, right,
                0L, 0.0F, 0.0F, false, false, false, 0.0F, 0.0F, 1.0F, false);

        PortalGateRingLayer.renderDamaged(context, new PoseStack(), buffer, mask);

        assertFalse(topVertices[0], "the rods leave an open gap");
        assertTrue(topVertices[1], "the neighboring ring piece is still rendered");
        assertTrue(topVertices[2], "reinforcement extends from the exposed end into the gap");

        topVertices[0] = false;
        topVertices[1] = false;
        int missing = PortalRingSegments.missingPieces(fixture.world, fixture.core, right, 1);
        assertEquals(1 << 4, missing);
        PortalGateRingLayer.renderRepairPreview(missing, consumer, new Matrix4f(), right == Direction.EAST, 0xFFFFFF, 120);
        assertTrue(topVertices[0], "the repair preview fills the missing top piece");
        assertFalse(topVertices[1], "the repair preview does not cover the neighboring piece");
    }

    @Test
    void cutCrystalRimsEndAtEachRingSkin() {
        // Piece 15 exposes the lower right crystal when the frame block directly above it is missing.
        Set<Set<Float>> spans = new HashSet<>();
        Set<Float> depths = new HashSet<>();
        int[] vertices = {0};
        VertexConsumer consumer = mock(VertexConsumer.class, RETURNS_SELF);
        when(consumer.addVertex(any(Matrix4f.class), anyFloat(), anyFloat(), anyFloat())).thenAnswer(vertex -> {
            depths.add(vertex.<Float>getArgument(3) * 16.0F);
            if (++vertices[0] % 4 == 0) {
                spans.add(Set.copyOf(depths));
                depths.clear();
            }
            return consumer;
        });
        PortalGateRenderContext context = new PortalGateRenderContext(null, null, BlockPos.ZERO, Direction.EAST,
                0L, 0.0F, 0.0F, false, false, false, 0.0F, 0.0F, 1.0F, false);
        PortalGateRingLayer.renderDamaged(context, new PoseStack(), type -> consumer, 1 << 15);

        assertTrue(spans.contains(Set.of(3.0F, PortalGateGeometry.RING_Z0)), "front crystal rim");
        assertTrue(spans.contains(Set.of(PortalGateGeometry.RING_Z1, 13.0F)), "back crystal rim");
        assertFalse(spans.contains(Set.of(3.0F, 13.0F)), "a crystal wall would overlap the exposed joint");
    }

    @Test
    void exposedRingJointsHaveRecessesInsideTheUnchangedSkin() {
        VertexConsumer consumer = mock(VertexConsumer.class, RETURNS_SELF);
        MultiBufferSource buffer = mock(MultiBufferSource.class);
        when(buffer.getBuffer(PortalGateRenderTypes.GATE_SOLID)).thenReturn(consumer);
        boolean[] recessAtJoint = {false};
        when(consumer.addVertex(any(Matrix4f.class), anyFloat(), anyFloat(), anyFloat())).thenAnswer(vertex -> {
            float horizontal = vertex.<Float>getArgument(1) * 16.0F;
            float vertical = vertex.<Float>getArgument(2) * 16.0F;
            float depth = vertex.<Float>getArgument(3) * 16.0F;
            // The ring is modelled on a half-pixel grid; only the joint recesses fall between its lines.
            // The smooth inner lip is a separate circle laid over the voxels, so it is left out here.
            double lipRadius = Math.hypot(horizontal - PortalGateGeometry.CENTER_H, vertical - PortalGateGeometry.CENTER_V);
            boolean smoothLip = lipRadius > PortalGateGeometry.INNER_RADIUS - 0.5D
                    && lipRadius < PortalGateGeometry.INNER_RADIUS + 1.9D
                    && (depth < PortalGateRingLayer.RING_Z0 || depth > PortalGateRingLayer.RING_Z1
                        || Math.abs(lipRadius - (PortalGateGeometry.INNER_RADIUS - 0.4D)) < 0.01D);
            if (!smoothLip && (horizontal * 2.0F != Math.round(horizontal * 2.0F) || vertical * 2.0F != Math.round(vertical * 2.0F))) {
                assertTrue(depth > PortalGateRingLayer.RING_Z0 && depth < PortalGateRingLayer.RING_Z1,
                        "cut detail stays inside the outer faces");
                recessAtJoint[0] = true;
            }
            return consumer;
        });
        PortalGateRenderContext context = new PortalGateRenderContext(null, null, BlockPos.ZERO, Direction.EAST,
                0L, 0.0F, 0.0F, false, false, false, 0.0F, 0.0F, 1.0F, false);
        PoseStack pose = new PoseStack();

        new PortalGateRingLayer().render(context, pose, buffer);
        assertFalse(recessAtJoint[0], "the intact ring keeps the original voxel faces");
        int damagedMask = PortalGateGeometry.FULL_FRAME_MASK & ~(1 << 4);
        PortalGateRingLayer.renderDamaged(context, pose, buffer, damagedMask);
        assertTrue(recessAtJoint[0], "a broken frame exposes recessed joints");
        recessAtJoint[0] = false;
        PortalGateRingLayer.renderSegment(0, consumer, pose.last().pose(), true, 1.0F);
        assertTrue(recessAtJoint[0], "an individual segment exposes recessed ends");
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"EAST", "SOUTH"})
    void repairCanFillOneGapAmongIntactFrameBlocks(Direction right) {
        Fixture fixture = new Fixture(right);
        Level level = mock(Level.class);
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation ->
                fixture.states.getOrDefault(invocation.getArgument(0), Blocks.AIR.defaultBlockState()));
        BlockPos missing = PortalRingSegments.cell(fixture.core, right, 4);
        assertFalse(PortalRingSegments.isPlaceable(level, fixture.core, right, 1), "complete segment");
        fixture.states.remove(missing);
        assertTrue(PortalRingSegments.isPlaceable(level, fixture.core, right, 1), "intact frame blocks do not block repair");
        fixture.states.put(missing, Blocks.STONE.defaultBlockState());
        assertFalse(PortalRingSegments.isPlaceable(level, fixture.core, right, 1), "stone still blocks repair");
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"EAST", "SOUTH"})
    void selectionFindsModelInAirCellsButDoesNotSelectInvisibleCorners(Direction right) {
        Fixture fixture = new Fixture(right);
        BlockHitResult outer = fixture.pick(2.9, 3.0);
        assertEquals(HitResult.Type.BLOCK, outer.getType());
        assertEquals(fixture.core.relative(right, 2).above(3), outer.getBlockPos());
        assertEquals(4.5 / 16, fixture.depth(outer.getLocation()), 1.0E-6);
        assertEquals(HitResult.Type.BLOCK, fixture.pick(1.4, 1.05).getType());
        assertEquals(HitResult.Type.MISS, fixture.pick(2.55, 0.03).getType());
        assertEquals(HitResult.Type.MISS, fixture.pick(2.1, 5.6).getType());
        assertEquals(HitResult.Type.MISS, fixture.pick(0, 3.0).getType());
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"EAST", "SOUTH"})
    void selectionDoesNotOverrideCloserOrdinaryBlocks(Direction right) {
        Fixture fixture = new Fixture(right);
        Vec3 from = fixture.point(2.9, 3.0, -2);
        Vec3 to = fixture.point(2.9, 3.0, 2);
        Vec3 wall = fixture.point(2.9, 3.0, -0.5);
        BlockHitResult hit = new BlockHitResult(wall, Direction.NORTH, BlockPos.containing(wall), false);
        assertSame(hit, PortalGateGeometry.clip(fixture.world,
                new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()), hit));
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"EAST", "SOUTH"})
    void unfinishedFrameRetainsCubicShapes(Direction right) {
        Fixture fixture = new Fixture(right);
        BlockPos corner = fixture.core.relative(right, 2).above(5);
        fixture.states.replaceAll((pos, state) -> state.setValue(PortalFrameBlock.FORMED, false));
        assertSame(Shapes.block(), fixture.states.get(corner).getShape(fixture.world, corner));
        assertSame(Shapes.block(), fixture.states.get(fixture.core).getShape(fixture.world, fixture.core));
        assertTrue(fixture.collides(2.1, 5.6, 0.5));
    }

    @Test
    void segmentPiecesCoverEachFrameCellOnceInAngularOrder() {
        Set<List<Integer>> cells = new HashSet<>();
        for (int segment = 0; segment < PortalGateGeometry.SEGMENT_COUNT; segment++) {
            int[] pieces = PortalGateGeometry.segmentPieces(segment);
            assertTrue(pieces.length > 0);
            for (int piece : pieces) {
                int x = PortalGateGeometry.pieceX(piece), y = PortalGateGeometry.pieceY(piece);
                assertTrue(y == 0 || y == 5 || Math.abs(x) == 2, "frame border cell " + piece);
                assertFalse(x == 0 && y == 0, "core cell");
                assertTrue(cells.add(List.of(x, y)), "duplicate cell " + piece);
                assertEquals(segment, PortalGateGeometry.pieceSegment(piece));
                assertEquals(segment, PortalGateGeometry.ringSegment(x * 16 + 7, y * 16 + 7), "piece " + piece);
                for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
                    AABB bounds = PortalGateGeometry.segmentShape(piece, axis).bounds();
                    assertTrue(bounds.minX >= -1 && bounds.minY >= -1 && bounds.minZ >= -1
                            && bounds.maxX <= 2 && bounds.maxY <= 2 && bounds.maxZ <= 2, "piece " + piece);
                }
            }
        }
        assertEquals(PortalGateGeometry.PIECE_COUNT, cells.size());
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"EAST", "SOUTH"})
    void segmentAnchorsFollowTheirArcBeforeFormation(Direction right) {
        Fixture fixture = new Fixture(right).anchors();
        for (int segment = 0; segment < PortalGateGeometry.SEGMENT_COUNT; segment++) {
            assertTrue(PortalRingSegments.isPlaced(fixture.world, fixture.core, right, segment));
            assertTrue(PortalRingSegments.isFilled(fixture.world, fixture.core, right, segment));
            assertFalse(PortalRingSegments.isPlaced(fixture.world, fixture.core, right.getClockWise(), segment));
        }
        assertEquals(right, PortalRingSegments.resolveRight(fixture.world, fixture.core, null));
        assertTrue(fixture.collides(2.9, 3.0, 0.5), "outer ring beyond the anchor cell");
        assertTrue(fixture.collides(0, 5.6, 0.5), "top of ring");
        assertFalse(fixture.collides(2.1, 5.6, 0.5), "invisible corner");
        assertFalse(fixture.collides(0, 3.0, 0.5), "circular opening");
        assertEquals(HitResult.Type.BLOCK, fixture.pick(2.9, 3.0).getType());
        assertEquals(HitResult.Type.MISS, fixture.pick(2.1, 5.6).getType());

        BlockPos top = PortalRingSegments.cell(fixture.core, right, 4);
        fixture.states.remove(top);
        assertFalse(PortalRingSegments.isPlaced(fixture.world, fixture.core, right, 1));
        assertFalse(PortalRingSegments.isFilled(fixture.world, fixture.core, right, 1));
        fixture.states.put(top, ModBlocks.PORTAL_FRAME.defaultBlockState());
        assertTrue(PortalRingSegments.isFilled(fixture.world, fixture.core, right, 1), "classic frame block fills");
        assertFalse(PortalRingSegments.isPlaced(fixture.world, fixture.core, right, 1));
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"EAST", "SOUTH"})
    void clipSeatFollowsTheNearestVisibleSocketLip(Direction right) {
        Fixture fixture = new Fixture(right);
        Direction depth = right.getClockWise();
        assertEquals(null, PortalBuriedFloor.clipSeat(fixture.world, fixture.core, right, 2, true));
        for (int dx = -2; dx <= 2; dx++) {
            BlockPos column = fixture.core.relative(right, dx);
            fixture.states.put(column.relative(depth), Blocks.STONE.defaultBlockState());
            fixture.states.put(column.relative(depth.getOpposite()), Blocks.STONE.defaultBlockState());
        }

        Vec3 positive = PortalBuriedFloor.clipSeat(fixture.world, fixture.core, right, 2, true);
        Vec3 negative = PortalBuriedFloor.clipSeat(fixture.world, fixture.core, right, -2, false);
        assertEquals(17.5 / 16, positive.y);
        assertEquals(17.5 / 16, negative.y);
        assertEquals(14.0 / 16, right == Direction.EAST ? positive.z : positive.x);
        assertEquals(2.0 / 16, right == Direction.EAST ? negative.z : negative.x);
        assertTrue((right == Direction.EAST ? positive.x : positive.z) > 2);
        assertTrue((right == Direction.EAST ? negative.x : negative.z) < -1);

        fixture.states.remove(fixture.core.relative(right, 2));
        Vec3 inner = PortalBuriedFloor.clipSeat(fixture.world, fixture.core, right, 2, true);
        assertTrue((right == Direction.EAST ? inner.x : inner.z) < 2);
        fixture.states.remove(fixture.core.relative(depth));
        assertEquals(null, PortalBuriedFloor.clipSeat(fixture.world, fixture.core, right, 2, true));
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"EAST", "SOUTH"})
    void buriedThresholdFollowsTerrainOnBothSidesAndCanBeUncovered(Direction right) {
        Fixture fixture = new Fixture(right);
        Direction depth = right.getClockWise();
        BlockPos side = fixture.core.relative(right, 2);
        fixture.states.put(fixture.core.relative(depth), Blocks.GRASS_BLOCK.defaultBlockState());
        fixture.states.put(side.below(), Blocks.STONE.defaultBlockState());
        assertFalse(PortalBuriedFloor.isBuried(fixture.world, fixture.core, right));
        assertFalse(fixture.collides(2.1, 0.9, 0.1));

        fixture.states.put(fixture.core.relative(depth.getOpposite()), Blocks.DIRT.defaultBlockState());
        assertTrue(PortalBuriedFloor.isBuried(fixture.world, fixture.core, right));
        assertSame(Blocks.GRASS_BLOCK.defaultBlockState(), PortalBuriedFloor.materialAt(fixture.world, fixture.core, right));
        assertSame(Blocks.STONE.defaultBlockState(), PortalBuriedFloor.materialAt(fixture.world, side, right));
        assertTrue(fixture.collides(0, 0.9, 0.1), "the core remains a cube at ground level");
        assertTrue(fixture.collides(2.1, 0.9, 0.1), "the copied terrain supports the player");
        assertTrue(fixture.collides(2.1, 1.05, 0.05), "the socket rises above the soil");
        assertFalse(fixture.collides(-1.2, 0.9, 0.1), "no terrain to copy in this column");
        fixture.states.remove(side);
        assertFalse(fixture.collides(2.1, 1.05, 0.05), "the missing frame takes its socket with it");
        fixture.states.put(fixture.core.relative(depth.getOpposite()), Blocks.WATER.defaultBlockState());
        assertFalse(PortalBuriedFloor.isBuried(fixture.world, fixture.core, right), "water alone is not ground");
    }

    private static final class Fixture {
        final BlockPos core = new BlockPos(20, 64, 20);
        final Direction right;
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final CollisionGetter world = mock(CollisionGetter.class, CALLS_REAL_METHODS);

        Fixture(Direction right) {
            this.right = right;
            when(world.getBlockState(any(BlockPos.class))).thenAnswer(invocation ->
                    states.getOrDefault(invocation.getArgument(0), Blocks.AIR.defaultBlockState()));
            when(world.getFluidState(any(BlockPos.class))).thenAnswer(invocation ->
                    world.getBlockState(invocation.getArgument(0)).getFluidState());
            when(world.getChunkForCollisions(anyInt(), anyInt())).thenReturn(world);
            for (int y = 0; y <= 5; y++) {
                for (int x = -2; x <= 2; x++) {
                    if (y != 0 && y != 5 && Math.abs(x) != 2) continue;
                    states.put(core.relative(right, x).above(y), (x == 0 && y == 0
                            ? ModBlocks.PORTAL_CORE : ModBlocks.PORTAL_FRAME).defaultBlockState()
                            .setValue(PortalFrameBlock.FORMED, true));
                }
            }
        }

        Fixture anchors() {
            states.put(core, ModBlocks.PORTAL_CORE.defaultBlockState());
            for (int piece = 1; piece <= PortalGateGeometry.PIECE_COUNT; piece++) {
                states.put(PortalRingSegments.cell(core, right, piece), PortalRingSegments.anchorState(piece, right));
            }
            return this;
        }

        Vec3 point(double horizontal, double vertical, double depth) {
            return right == Direction.EAST
                    ? new Vec3(core.getX() + 0.5 + horizontal, core.getY() + vertical, core.getZ() + depth)
                    : new Vec3(core.getX() + depth, core.getY() + vertical, core.getZ() + 0.5 + horizontal);
        }

        double depth(Vec3 point) {
            return right == Direction.EAST ? point.z - core.getZ() : point.x - core.getX();
        }

        boolean collides(double horizontal, double vertical, double depth) {
            Vec3 p = point(horizontal, vertical, depth);
            AABB box = new AABB(p.x - 0.01, p.y - 0.01, p.z - 0.01, p.x + 0.01, p.y + 0.01, p.z + 0.01);
            BlockCollisions<VoxelShape> collisions = new BlockCollisions<>(world, (Entity) null, box, false,
                    (pos, shape) -> shape);
            return collisions.hasNext();
        }

        BlockHitResult pick(double horizontal, double vertical) {
            ClipContext context = new ClipContext(point(horizontal, vertical, -2), point(horizontal, vertical, 2),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty());
            return PortalGateGeometry.clip(world, context, world.clip(context));
        }
    }
}
