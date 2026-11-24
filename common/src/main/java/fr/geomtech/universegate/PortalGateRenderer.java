package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.geomtech.universegate.compat.ImmediateBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

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
    protected AABB renderBounds(PortalCoreBlockEntity core) {
        // Tout le portail (anneau qui dépasse d'un demi-bloc compris), quelle que soit son orientation :
        // sinon NeoForge cache l'anneau dès que le bloc du Core sort de l'écran.
        var p = core.getBlockPos();
        return new AABB(p.getX() - 3, p.getY(), p.getZ() - 3, p.getX() + 4, p.getY() + 7, p.getZ() + 4);
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
        boolean cooldown = animator != null && animator.isCooldown();

        float brightness = sampleBrightness(level, core.getBlockPos(), right);
        PortalGateRenderContext context = new PortalGateRenderContext(level, core, core.getBlockPos(), right,
                tick, partialTick, tick + partialTick, opening, active, active && core.isClientAmbientUnstable(),
                progress, angle, brightness, cooldown);
        for (PortalGateLayer layer : PortalGateLayers.all()) {
            layer.render(context, poseStack, buffer);
        }
    }

    /** Natural ambient brightness around the gate (matches Minecraft's sky darkening and block light). */
    static float sampleBrightness(Level level, BlockPos corePos, Direction right) {
        Direction depth = right.getClockWise();
        BlockPos center = corePos.above(2);
        int best = Math.max(
                effectiveLight(level, center),
                Math.max(effectiveLight(level, center.relative(depth)), effectiveLight(level, center.relative(depth.getOpposite())))
        );
        float f = best / 15.0F;
        float ramp = Math.max(level.dimensionType().ambientLight(), f * (0.45F + 0.55F * f));
        return 0.08F + 0.92F * ramp;
    }

    private static int effectiveLight(Level level, BlockPos pos) {
        int sky = level.dimensionType().hasSkyLight()
                ? Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken())
                : 0;
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        return Math.max(sky, block);
    }
}
