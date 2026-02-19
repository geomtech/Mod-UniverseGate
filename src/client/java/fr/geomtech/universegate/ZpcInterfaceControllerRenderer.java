package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ZpcInterfaceControllerRenderer implements BlockEntityRenderer<ZpcInterfaceControllerBlockEntity> {

    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final float[][] SLOT_OFFSETS = {
            {-0.350F, 0.180F},
            {0.000F, -0.350F},
            {0.350F, 0.180F}
    };
    private static final float[] SLOT_YAW = {-30.0F, 180.0F, 30.0F};

    private final ItemRenderer itemRenderer;

    public ZpcInterfaceControllerRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ZpcInterfaceControllerBlockEntity blockEntity,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);

        if (blockEntity.getBlockState().hasProperty(ZpcInterfaceControllerBlock.FACING)) {
            Direction facing = blockEntity.getBlockState().getValue(ZpcInterfaceControllerBlock.FACING);
            float blockstateRotation = switch (facing) {
                case NORTH -> 0.0F;
                case EAST -> 90.0F;
                case SOUTH -> 180.0F;
                case WEST -> 270.0F;
                default -> 0.0F;
            };
            poseStack.mulPose(Axis.YP.rotationDegrees(-blockstateRotation));
        }

        int light = blockEntity.hasEngagedZpc() ? FULL_BRIGHT : packedLight;
        boolean renderedAny = false;

        for (int slot = 0; slot < Math.min(ZpcInterfaceControllerBlockEntity.MAX_ZPCS, SLOT_OFFSETS.length); slot++) {
            ItemStack stack = blockEntity.getRenderZpcStack(slot);
            if (stack.isEmpty()) continue;
            renderedAny = true;

            float progress = blockEntity.getInsertionProgressForRender(slot, partialTick);
            float y = 0.85F - (0.09F * progress);

            poseStack.pushPose();
            poseStack.translate(SLOT_OFFSETS[slot][0], y, SLOT_OFFSETS[slot][1]);
            poseStack.mulPose(Axis.YP.rotationDegrees(SLOT_YAW[slot]));

            float scale = 0.56F;
            poseStack.scale(scale, scale, scale);

            this.itemRenderer.renderStatic(stack,
                    ItemDisplayContext.FIXED,
                    light,
                    packedOverlay,
                    poseStack,
                    buffer,
                    blockEntity.getLevel(),
                    0);
            poseStack.popPose();
        }

        if (!renderedAny) {
            ItemStack fallback = blockEntity.getRenderZpcStack(0);
            if (!fallback.isEmpty()) {
                float progress = blockEntity.getInsertionProgressForRender(0, partialTick);
                float y = 0.85F - (0.09F * progress);

                poseStack.pushPose();
                poseStack.translate(0.00D, y, -0.50D);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                float scale = 0.56F;
                poseStack.scale(scale, scale, scale);
                this.itemRenderer.renderStatic(fallback,
                        ItemDisplayContext.FIXED,
                        light,
                        packedOverlay,
                        poseStack,
                        buffer,
                        blockEntity.getLevel(),
                        0);
                poseStack.popPose();
            }
        }

        poseStack.popPose();
    }
}
