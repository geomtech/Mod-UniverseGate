package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.geomtech.universegate.compat.ImmediateBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Modèle 3D d'un portail construit, dessiné par le Core : socle, anneau qui tourne et membrane, sous forme de
 * couches ({@link PortalGateLayers}). Les blocs du cadre et le Core sont alors invisibles (propriété FORMED).
 */
public class PortalGateRenderer extends ImmediateBlockEntityRenderer<PortalCoreBlockEntity> {

    public PortalGateRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected boolean rendersOffScreen() {
        // L'anneau dépasse largement du bloc du Core.
        return true;
    }

    @Override
    public void render(PortalCoreBlockEntity core,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       int packedOverlay) {
        Level level = core.getLevel();
        if (level == null) return;
        BlockState state = core.getBlockState();
        if (!state.is(ModBlocks.PORTAL_CORE) || !state.getValue(PortalCoreBlock.FORMED)) return;
        Direction right = PortalGateAnimator.resolveRight(level, core.getBlockPos());
        if (right == null) return;

        long tick = level.getGameTime();
        boolean opening = core.isOpening();
        boolean active = core.isActive();
        float progress = 0.0F;
        if (opening) {
            long start = core.getOpeningStartedGameTime(), end = core.getOpeningCompleteGameTime();
            progress = end > start ? Mth.clamp((tick + partialTick - start) / (float) (end - start), 0.0F, 1.0F) : 0.0F;
        }
        PortalGateAnimator animator = core.getClientGateAnimator();
        float angle = animator == null ? 0.0F : animator.angle(partialTick);

        PortalGateRenderContext context = new PortalGateRenderContext(level, core, core.getBlockPos(), right,
                tick, partialTick, tick + partialTick, opening, active, active && core.isClientAmbientUnstable(),
                progress, angle);
        for (PortalGateLayer layer : PortalGateLayers.all()) {
            layer.render(context, poseStack, buffer);
        }
    }
}
