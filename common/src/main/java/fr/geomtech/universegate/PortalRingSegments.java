package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Construction of a gate from six ring segments: each segment is a piece of the 3D ring held by invisible
 * anchor frame blocks ({@link PortalFrameBlock#PIECE}). A complete set of anchors is an ordinary frame, so
 * formation, energy and redstone keep working unchanged.
 */
public final class PortalRingSegments {

    /** Aimed segment of an unfinished or damaged gate. */
    public record Target(BlockPos core, Direction right, int segment, boolean placeable, double distance) {}

    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<BlockPos> NO_DROP = new ThreadLocal<>();

    private PortalRingSegments() {}

    public static BlockPos cell(BlockPos core, Direction right, int piece) {
        return core.relative(right, PortalGateGeometry.pieceX(piece)).above(PortalGateGeometry.pieceY(piece));
    }

    public static BlockState anchorState(int piece, Direction right) {
        return ModBlocks.PORTAL_FRAME.defaultBlockState()
                .setValue(PortalFrameBlock.PIECE, piece)
                .setValue(PortalFrameBlock.AXIS, right.getAxis());
    }

    public static boolean isAnchor(BlockState state, int piece, Direction right) {
        return state.is(ModBlocks.PORTAL_FRAME) && state.getValue(PortalFrameBlock.PIECE) == piece
                && state.getValue(PortalFrameBlock.AXIS) == right.getAxis();
    }

    /** The segment's anchors are all in place (this is what gets drawn as an arc). */
    public static boolean isPlaced(BlockGetter level, BlockPos core, Direction right, int segment) {
        for (int piece : PortalGateGeometry.segmentPieces(segment)) {
            if (!isAnchor(level.getBlockState(cell(core, right, piece)), piece, right)) return false;
        }
        return true;
    }

    /** Every cell of the segment already holds a frame (anchors or classic frame blocks). */
    public static boolean isFilled(BlockGetter level, BlockPos core, Direction right, int segment) {
        return missingPieces(level, core, right, segment) == 0;
    }

    static int missingPieces(BlockGetter level, BlockPos core, Direction right, int segment) {
        int missing = 0;
        for (int piece : PortalGateGeometry.segmentPieces(segment)) {
            if (!level.getBlockState(cell(core, right, piece)).is(ModBlocks.PORTAL_FRAME)) missing |= 1 << piece;
        }
        return missing;
    }

    public static boolean isPlaceable(Level level, BlockPos core, Direction right, int segment) {
        int[] pieces = PortalGateGeometry.segmentPieces(segment);
        int missing = missingPieces(level, core, right, segment);
        if (missing == 0) return false;
        if (!isRepair(level, core) && Integer.bitCount(missing) != pieces.length) return false;
        for (int piece : pieces) {
            if ((missing & (1 << piece)) == 0) continue;
            BlockPos pos = cell(core, right, piece);
            if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRepair(BlockGetter level, BlockPos core) {
        BlockState state = level.getBlockState(core);
        return state.is(ModBlocks.PORTAL_CORE) && state.getValue(PortalCoreBlock.FORMED);
    }

    /**
     * Orientation of an unassembled gate: the one already started (anchors or frame blocks), otherwise facing
     * the player.
     */
    public static Direction resolveRight(BlockGetter level, BlockPos core, @Nullable Player player) {
        Direction remembered = PortalGateGeometry.resolveRight(level, core);
        if (remembered != null) return remembered;
        int east = frameCount(level, core, Direction.EAST);
        int south = frameCount(level, core, Direction.SOUTH);
        if (east != south) return east > south ? Direction.EAST : Direction.SOUTH;
        if (player == null) return Direction.EAST;
        return player.getDirection().getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
    }

    private static int frameCount(BlockGetter level, BlockPos core, Direction right) {
        int count = 0;
        for (int piece = 1; piece <= PortalGateGeometry.PIECE_COUNT; piece++) {
            if (PortalGateGeometry.pieceX(piece) == 0) continue; // shared by both orientations
            if (level.getBlockState(cell(core, right, piece)).is(ModBlocks.PORTAL_FRAME)) count++;
        }
        return count;
    }

    private static boolean isBuildableCore(Level level, BlockPos core) {
        BlockState state = level.getBlockState(core);
        return state.is(ModBlocks.PORTAL_CORE) && (!state.getValue(PortalCoreBlock.FORMED)
                || PortalFrameDetector.find(level, core).isEmpty());
    }

    /** Segment aimed at by the player around the nearest unassembled core in reach, or null. */
    public static @Nullable Target find(Level level, Player player) {
        double reach = player.blockInteractionRange();
        Vec3 eye = player.getEyePosition();
        BlockHitResult blockHit = pick(level, player, reach);
        int radius = (int) Math.ceil(reach) + 3;
        BlockPos origin = BlockPos.containing(eye);
        Target best = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -radius - 3; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.setWithOffset(origin, dx, dy, dz);
                    if (!level.isLoaded(cursor) || !isBuildableCore(level, cursor)) continue;
                    Target target = target(level, player, cursor.immutable(), reach, blockHit);
                    if (target != null && (best == null || target.distance() < best.distance())) best = target;
                }
            }
        }
        return best;
    }

    /** Segment of this core aimed at by the player, or null if the player does not aim at its ring. */
    public static @Nullable Target target(Level level, Player player, BlockPos core) {
        double reach = player.blockInteractionRange();
        return target(level, player, core, reach, pick(level, player, reach));
    }

    private static BlockHitResult pick(Level level, Player player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
        return level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    private static @Nullable Target target(Level level, Player player, BlockPos core, double reach,
                                           BlockHitResult blockHit) {
        if (!isBuildableCore(level, core)) return null;
        Direction right = resolveRight(level, core, player);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double depthLook = right.getAxis() == Direction.Axis.X ? look.z : look.x;
        if (Math.abs(depthLook) < 1.0E-6D) return null;
        double plane = (right.getAxis() == Direction.Axis.X ? core.getZ() : core.getX()) + 0.5D;
        double t = (plane - (right.getAxis() == Direction.Axis.X ? eye.z : eye.x)) / depthLook;
        if (t <= 0.0D || t > reach) return null;
        if (blockHit.getType() != HitResult.Type.MISS
                && eye.distanceTo(blockHit.getLocation()) < t - 1.0E-3D
                && !isPartOf(blockHit.getBlockPos(), core, right)) {
            return null;
        }
        Vec3 point = eye.add(look.scale(t));
        double h = ((right.getAxis() == Direction.Axis.X ? point.x - core.getX() : point.z - core.getZ())) * 16.0D;
        double v = (point.y - core.getY()) * 16.0D;
        double dh = h - PortalGateGeometry.CENTER_H, dv = v - PortalGateGeometry.CENTER_V;
        double r = Math.sqrt(dh * dh + dv * dv);
        if (r < PortalGateGeometry.INNER_RADIUS - 12.0D || r > PortalGateGeometry.OUTER_RADIUS + 10.0D) return null;
        int aimed = PortalGateGeometry.segmentAtAngle(Math.toDegrees(Math.atan2(dv, dh)));
        if (isRepair(level, core)) {
            int piece = PortalGateGeometry.pieceAt(PortalGateGeometry.owner(h, v));
            if (piece > 0 && !level.getBlockState(cell(core, right, piece)).is(ModBlocks.PORTAL_FRAME)) {
                aimed = PortalGateGeometry.pieceSegment(piece);
            }
        }
        int segment = nearestMissing(level, core, right, aimed);
        if (segment < 0) return null;
        return new Target(core, right, segment, isPlaceable(level, core, right, segment), t);
    }

    private static boolean isPartOf(BlockPos pos, BlockPos core, Direction right) {
        if (pos.equals(core)) return true;
        for (int piece = 1; piece <= PortalGateGeometry.PIECE_COUNT; piece++) {
            if (pos.equals(cell(core, right, piece))) return true;
        }
        return false;
    }

    private static int nearestMissing(BlockGetter level, BlockPos core, Direction right, int aimed) {
        for (int d = 0; d <= PortalGateGeometry.SEGMENT_COUNT / 2; d++) {
            for (int sign : new int[]{1, -1}) {
                int segment = Math.floorMod(aimed + sign * d, PortalGateGeometry.SEGMENT_COUNT);
                if (!isFilled(level, core, right, segment)) return segment;
            }
        }
        return -1;
    }

    /** Places the aimed segment for the player. Server side only. */
    public static boolean place(ServerLevel level, Player player, ItemStack stack) {
        Target target = find(level, player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.universegate.ring_segment_no_core"), true);
            return false;
        }
        if (!target.placeable()) {
            player.displayClientMessage(Component.translatable("message.universegate.ring_segment_blocked"), true);
            return false;
        }
        int[] pieces = PortalGateGeometry.segmentPieces(target.segment());
        int missing = missingPieces(level, target.core(), target.right(), target.segment());
        boolean repair = isRepair(level, target.core());
        for (int piece : pieces) {
            if ((missing & (1 << piece)) == 0) continue;
            BlockPos pos = cell(target.core(), target.right(), piece);
            BlockState state = anchorState(piece, target.right());
            if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, stack)
                    || !level.isUnobstructed(state, pos, CollisionContext.of(player))) {
                player.displayClientMessage(Component.translatable("message.universegate.ring_segment_blocked"), true);
                return false;
            }
        }
        BlockPos sound = null;
        for (int piece : pieces) {
            if ((missing & (1 << piece)) == 0) continue;
            BlockPos pos = cell(target.core(), target.right(), piece);
            BlockState state = PortalFrameBlock.withPlacementWater(anchorState(piece, target.right()), level, pos)
                    .setValue(PortalFrameBlock.FORMED, repair);
            if (!level.setBlock(pos, state, Block.UPDATE_ALL)) continue;
            if (sound == null) sound = pos;
            level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
        }
        if (sound == null) return false;
        SoundType soundType = anchorState(pieces[0], target.right()).getSoundType();
        level.playSound(null, sound, soundType.getPlaceSound(), SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return true;
    }

    /** Next removal of the anchor at {@code pos} gives no item (creative mode or wrong tool). */
    static void suppressNextDrop(BlockPos pos) {
        NO_DROP.set(pos.immutable());
    }

    /** An anchor went away: the rest of its segment goes too, and the segment drops once. */
    static void onAnchorRemoved(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos noDrop = NO_DROP.get();
        NO_DROP.remove();
        if (REMOVING.get()) return;
        int piece = state.getValue(PortalFrameBlock.PIECE);
        Direction right = state.getValue(PortalFrameBlock.AXIS) == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        BlockPos core = pos.relative(right, -PortalGateGeometry.pieceX(piece)).below(PortalGateGeometry.pieceY(piece));
        removeSegment(level, core, right, PortalGateGeometry.pieceSegment(piece));
        if (!pos.equals(noDrop)) Block.popResource(level, pos, new ItemStack(ModItems.PORTAL_RING_SEGMENT));
    }

    /** The core is gone: segments placed around it are given back. */
    static void dropSegments(ServerLevel level, BlockPos core) {
        for (Direction right : new Direction[]{Direction.EAST, Direction.SOUTH}) {
            for (int segment = 0; segment < PortalGateGeometry.SEGMENT_COUNT; segment++) {
                if (removeSegment(level, core, right, segment)) {
                    Block.popResource(level, core, new ItemStack(ModItems.PORTAL_RING_SEGMENT));
                }
            }
        }
    }

    private static boolean removeSegment(ServerLevel level, BlockPos core, Direction right, int segment) {
        boolean removed = false;
        REMOVING.set(true);
        try {
            for (int piece : PortalGateGeometry.segmentPieces(segment)) {
                BlockPos pos = cell(core, right, piece);
                if (!level.isLoaded(pos) || !isAnchor(level.getBlockState(pos), piece, right)) continue;
                level.removeBlock(pos, false);
                removed = true;
            }
        } finally {
            REMOVING.set(false);
        }
        return removed;
    }
}
