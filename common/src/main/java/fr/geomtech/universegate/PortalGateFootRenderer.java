package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class PortalGateFootRenderer {
    private PortalGateFootRenderer() {}

    /** Also used for the destination threshold, where the second ring and membrane must be skipped. */
    public static void render(PortalCoreBlockEntity core, @Nullable Direction right, PoseStack poses, MultiBufferSource buffer) {
        Level level = core.getLevel();
        BlockState state = core.getBlockState();
        if (level == null || !state.is(ModBlocks.PORTAL_CORE) || !state.getValue(PortalCoreBlock.FORMED)) return;
        BlockPos origin = core.getBlockPos();
        if (right == null || !PortalBuriedFloor.isBuried(level, origin, right)) {
            renderBlock(level, origin, state.setValue(PortalCoreBlock.FORMED, false), poses, buffer);
            return;
        }

        int covered = 0;
        for (int dx = -2; dx <= 2; dx++) {
            BlockPos pos = origin.relative(right, dx);
            BlockState frame = level.getBlockState(pos);
            if (dx != 0 && !(frame.is(ModBlocks.PORTAL_FRAME) && frame.getValue(PortalFrameBlock.FORMED))) continue;
            BlockState ground = PortalBuriedFloor.materialAt(level, pos, right);
            if (ground == null) continue;
            covered |= 1 << (dx + 2);
            poses.pushPose();
            poses.translate(dx * right.getStepX(), 0, dx * right.getStepZ());
            renderBlock(level, pos, ground, poses, buffer);
            poses.popPose();
        }
        float brightness = PortalGateRenderer.sampleBrightness(level, origin, right);
        int top = PortalGateMesh.shade(0x2B2B2B, brightness), side = PortalGateMesh.shade(0x141414, brightness);
        var solid = buffer.getBuffer(PortalGateRenderTypes.GATE_SOLID);
        for (var box : PortalBuriedFloor.SOCKETS) {
            if ((covered & (1 << (box.column() + 2))) == 0) continue;
            PortalGateMesh.box(solid, poses.last().pose(), right.getAxis() == Direction.Axis.X,
                    box.h0(), box.h1(), 16, box.top(), box.z0(), box.z1(), box.floor() ? 0 : top, box.floor() ? 0 : side);
        }
    }

    private static void renderBlock(Level level, BlockPos pos, BlockState state, PoseStack poses, MultiBufferSource buffer) {
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        var vertices = buffer.getBuffer(ItemBlockRenderTypes.getMovingBlockRenderType(state));
        var random = RandomSource.create(state.getSeed(pos));
        // Use the world tessellator for biome tint, local light and the same face culling as the adjacent terrain.
        //? if >=1.21.11 {
        /*dispatcher.renderBatched(state, pos, level, poses, vertices, true, dispatcher.getBlockModel(state).collectParts(random));
        *///?} else {
        dispatcher.renderBatched(state, pos, level, poses, vertices, true, random);
        //?}
    }
}
