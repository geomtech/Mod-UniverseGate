package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

/**
 * Portail en construction, dessiné par le Core : les segments d'anneau déjà posés (fixes, éteints) et, quand le
 * joueur tient un segment, l'aperçu translucide des segments manquants avec celui qu'il vise en surbrillance.
 */
final class PortalRingSegmentRenderer {

    private static final int PREVIEW_COLOR = 0x6EDCFF;
    private static final int TARGET_COLOR = 0x8CFFB4;
    private static final int BLOCKED_COLOR = 0xFF5A4A;
    private static final double PREVIEW_DISTANCE_SQR = 12.0D * 12.0D;

    private PortalRingSegmentRenderer() {}

    static void render(PortalCoreBlockEntity core, PoseStack poseStack, MultiBufferSource buffer) {
        Level level = core.getLevel();
        if (level == null) return;
        BlockPos corePos = core.getBlockPos();
        Player player = Minecraft.getInstance().player;
        Direction right = PortalRingSegments.resolveRight(level, corePos, holdingSegment(player) ? player : null);
        boolean xAxis = right.getAxis() == Direction.Axis.X;
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer solid = null;
        float brightness = 0.0F;
        int placed = 0, occupied = 0;
        for (int segment = 0; segment < PortalGateGeometry.SEGMENT_COUNT; segment++) {
            if (PortalRingSegments.isFilled(level, corePos, right, segment)) occupied |= 1 << segment;
            if (!PortalRingSegments.isPlaced(level, corePos, right, segment)) continue;
            placed |= 1 << segment;
            if (solid == null) {
                solid = buffer.getBuffer(PortalGateRenderTypes.GATE_SOLID);
                brightness = PortalGateRenderer.sampleBrightness(level, corePos, right);
            }
            PortalGateRingLayer.renderSegment(segment, solid, matrix, xAxis, brightness);
        }
        if (solid != null) PortalGateBrokenJoints.renderConstruction(placed, occupied, solid, matrix, xAxis, brightness);

        renderPreview(core, right, poseStack, buffer);
    }

    static void renderPreview(PortalCoreBlockEntity core, Direction right, PoseStack poseStack, MultiBufferSource buffer) {
        Player player = Minecraft.getInstance().player;
        BlockPos corePos = core.getBlockPos();
        if (!holdingSegment(player) || player.distanceToSqr(corePos.getCenter()) > PREVIEW_DISTANCE_SQR) return;
        Level level = core.getLevel();
        if (level == null) return;
        PortalRingSegments.Target target = PortalRingSegments.target(level, player, corePos);
        VertexConsumer glow = buffer.getBuffer(RenderType.lightning());
        boolean repair = core.getBlockState().getValue(PortalCoreBlock.FORMED);
        Matrix4f matrix = poseStack.last().pose();
        boolean xAxis = right.getAxis() == Direction.Axis.X;
        for (int segment = 0; segment < PortalGateGeometry.SEGMENT_COUNT; segment++) {
            int missing = PortalRingSegments.missingPieces(level, corePos, right, segment);
            if (missing == 0) continue;
            boolean aimed = target != null && target.segment() == segment && target.right() == right;
            int color = !aimed ? PREVIEW_COLOR : target.placeable() ? TARGET_COLOR : BLOCKED_COLOR;
            int alpha = aimed ? 120 : 34;
            if (repair) {
                PortalGateRingLayer.renderRepairPreview(missing, glow, matrix, xAxis, color, alpha);
            } else {
                PortalGateRingLayer.renderSegmentPreview(segment, glow, matrix, xAxis, color, alpha);
            }
        }
    }

    private static boolean holdingSegment(Player player) {
        return player != null && (player.getMainHandItem().is(ModItems.PORTAL_RING_SEGMENT)
                || player.getOffhandItem().is(ModItems.PORTAL_RING_SEGMENT));
    }
}
