package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Optional;

public class PortalFieldBlinkRenderer implements BlockEntityRenderer<PortalFieldBlockEntity> {

    private static final float CENTER = 0.5F;
    private static final float PORTAL_WIDTH = PortalFrameDetector.INNER_WIDTH;
    private static final float PORTAL_HEIGHT = PortalFrameDetector.INNER_HEIGHT;
    private static final float HALF_INNER_WIDTH = PORTAL_WIDTH * 0.5F;
    private static final float HALF_INNER_HEIGHT = PORTAL_HEIGHT * 0.5F;
    private static final float MIN_INSET = 0.005F;
    private static final float MAX_INSET = 0.32F;
    private static final float EXTEND = 0.0F;
    private static final float MIN_HORIZONTAL = -EXTEND;
    private static final float MAX_HORIZONTAL = PORTAL_WIDTH + EXTEND;
    private static final float MIN_VERTICAL = -EXTEND;
    private static final float MAX_VERTICAL = PORTAL_HEIGHT + EXTEND;

    private static final RenderType PORTAL_OCCLUSION_RENDER_TYPE = RenderType.create(
            "universegate_portal_field_occlusion",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            512,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false)
    );

    private static final RenderType SINGULARITY_RENDER_TYPE = RenderType.create(
            "universegate_portal_field_singularity",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false)
    );

    public PortalFieldBlinkRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PortalFieldBlockEntity blockEntity,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) return;

        BlockState state = blockEntity.getBlockState();
        if (!state.hasProperty(PortalFieldBlock.AXIS)) return;

        Direction.Axis axis = state.getValue(PortalFieldBlock.AXIS);
        boolean unstable = state.hasProperty(PortalFieldBlock.UNSTABLE) && state.getValue(PortalFieldBlock.UNSTABLE);

        PortalCoords coords = resolvePortalCoords(level, blockEntity.getBlockPos(), axis);
        if (coords == null) return;
        if (coords.horizontalIndex() != 0 || coords.verticalIndex() != 0) return;

        float time = level.getGameTime() + partialTick;
        float phase = coords.phase();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer occlusion = buffer.getBuffer(PORTAL_OCCLUSION_RENDER_TYPE);
        VertexConsumer energy = buffer.getBuffer(SINGULARITY_RENDER_TYPE);

        drawOcclusionPlane(occlusion, matrix, axis);
        drawBlueAura(energy, matrix, axis, time, phase, unstable);
        drawVolumetricShell(energy, matrix, axis, time, phase, unstable);
        drawCoreLens(energy, matrix, axis, time, phase, unstable);
        drawCentralFlare(energy, matrix, axis, time, phase, unstable);
        drawEventHorizonFx(energy, matrix, axis, time, phase, unstable);
    }

    private static void drawOcclusionPlane(VertexConsumer consumer,
                                           Matrix4f matrix,
                                           Direction.Axis axis) {
        EnergySample black = new EnergySample(18, 64, 165, 182);
        addPlaneQuadColoredDoubleSided(
                consumer,
                matrix,
                axis,
                0.0F, 0.0F, CENTER, black,
                PORTAL_WIDTH, 0.0F, CENTER, black,
                PORTAL_WIDTH, PORTAL_HEIGHT, CENTER, black,
                0.0F, PORTAL_HEIGHT, CENTER, black
        );
    }

    private static void drawBlueAura(VertexConsumer consumer,
                                     Matrix4f matrix,
                                     Direction.Axis axis,
                                     float time,
                                     float phase,
                                     boolean unstable) {
        int rings = unstable ? 8 : 6;
        float depthSpread = unstable ? 0.27F : 0.21F;

        for (int i = rings - 1; i >= 0; i--) {
            float t = rings == 1 ? 0.5F : (float) i / (rings - 1);
            float signed = (t - 0.5F) * 2.0F;

            float thickness = 0.007F
                    + t * (unstable ? 0.070F : 0.055F)
                    + 0.003F * Mth.sin(time * 0.23F + phase * 1.8F + i * 0.8F);

            float depth = CENTER
                    + signed * depthSpread
                    + 0.014F * Mth.sin(time * 0.33F + phase * 2.4F + i * 1.1F);

            float glow = 0.5F + 0.5F * Mth.sin(time * 0.48F + phase * 2.0F + t * 6.0F);
            int outerR = Mth.clamp((int) (24 + t * 26), 0, 255);
            int outerG = Mth.clamp((int) (132 + t * 82), 0, 255);
            int outerB = 255;

            int innerR = Mth.clamp((int) (196 + (1.0F - t) * 48 + glow * 16), 0, 255);
            int innerG = Mth.clamp((int) (236 + (1.0F - t) * 16 + glow * 10), 0, 255);
            int innerB = 255;

            if (unstable) {
                innerG = Mth.clamp(innerG + 8, 0, 255);
                innerB = Mth.clamp(innerB + 10, 0, 255);
            }

            EnergySample outer = new EnergySample(outerR, outerG, outerB, 255);
            EnergySample inner = new EnergySample(innerR, innerG, innerB, 255);
            drawAuraRing(consumer, matrix, axis, depth, thickness, outer, inner);
        }
    }

    private static void drawAuraRing(VertexConsumer consumer,
                                     Matrix4f matrix,
                                     Direction.Axis axis,
                                     float depth,
                                     float thickness,
                                     EnergySample outer,
                                     EnergySample inner) {
        float leftOuter = 0.0F;
        float rightOuter = PORTAL_WIDTH;
        float bottomOuter = 0.0F;
        float topOuter = PORTAL_HEIGHT;

        float clampedThickness = Mth.clamp(thickness, 0.01F, 1.20F);
        float leftInner = leftOuter + clampedThickness;
        float rightInner = rightOuter - clampedThickness;
        float bottomInner = bottomOuter + clampedThickness;
        float topInner = topOuter - clampedThickness;

        if (rightInner <= leftInner || topInner <= bottomInner) return;

        addPlaneQuadColoredDoubleSided(
                consumer,
                matrix,
                axis,
                leftOuter, bottomOuter, depth, outer,
                leftInner, bottomInner, depth, inner,
                leftInner, topInner, depth, inner,
                leftOuter, topOuter, depth, outer
        );

        addPlaneQuadColoredDoubleSided(
                consumer,
                matrix,
                axis,
                rightInner, bottomInner, depth, inner,
                rightOuter, bottomOuter, depth, outer,
                rightOuter, topOuter, depth, outer,
                rightInner, topInner, depth, inner
        );

        addPlaneQuadColoredDoubleSided(
                consumer,
                matrix,
                axis,
                leftOuter, topOuter, depth, outer,
                rightOuter, topOuter, depth, outer,
                rightInner, topInner, depth, inner,
                leftInner, topInner, depth, inner
        );

        addPlaneQuadColoredDoubleSided(
                consumer,
                matrix,
                axis,
                leftOuter, bottomOuter, depth, outer,
                leftInner, bottomInner, depth, inner,
                rightInner, bottomInner, depth, inner,
                rightOuter, bottomOuter, depth, outer
        );
    }

    private static void drawVolumetricShell(VertexConsumer consumer,
                                            Matrix4f matrix,
                                            Direction.Axis axis,
                                            float time,
                                            float phase,
                                            boolean unstable) {
        int layers = unstable ? 18 : 14;
        float halfDepth = unstable ? 0.19F : 0.15F;

        for (int i = 0; i < layers; i++) {
            float t = layers == 1 ? 0.5F : (float) i / (layers - 1);
            float signed = (t - 0.5F) * 2.0F;
            float profile = 1.0F - Math.abs(signed);

            float depth = CENTER
                    + signed * halfDepth
                    + 0.014F * Mth.sin(time * 0.21F + phase * 1.6F + t * 10.0F);

            float inset = Mth.clamp(
                    -0.055F
                            + (1.0F - profile) * 0.22F
                            + 0.016F * Mth.sin(time * 0.16F + phase * 2.3F + t * 12.0F),
                    MIN_INSET,
                    MAX_INSET
            );

            float brightness = 1.10F + profile * (unstable ? 0.62F : 0.50F);
            float alphaScale = 1.12F + profile * (unstable ? 0.28F : 0.20F);

            drawEnergyPlane(
                    consumer,
                    matrix,
                    axis,
                    depth,
                    inset,
                    time,
                    phase,
                    unstable,
                    signed,
                    profile,
                    brightness,
                    alphaScale
            );
        }
    }

    private static void drawCoreLens(VertexConsumer consumer,
                                     Matrix4f matrix,
                                     Direction.Axis axis,
                                     float time,
                                     float phase,
                                     boolean unstable) {
        int layers = unstable ? 10 : 8;
        float halfDepth = unstable ? 0.075F : 0.060F;

        for (int i = 0; i < layers; i++) {
            float t = layers == 1 ? 0.5F : (float) i / (layers - 1);
            float signed = (t - 0.5F) * 2.0F;
            float profile = 1.0F - Math.abs(signed);

            float depth = CENTER + signed * halfDepth;
            float inset = Mth.clamp(
                    0.82F
                            - profile * 0.36F
                            + 0.020F * Mth.sin(time * 0.29F + phase * 3.2F + t * 9.0F),
                    0.34F,
                    1.0F
            );

            float brightness = 1.55F + profile * (unstable ? 0.85F : 0.72F);
            float alphaScale = 1.24F + profile * (unstable ? 0.34F : 0.24F);

            drawEnergyPlane(
                    consumer,
                    matrix,
                    axis,
                    depth,
                    inset,
                    time,
                    phase,
                    unstable,
                    signed,
                    profile,
                    brightness,
                    alphaScale
            );
        }
    }

    private static void drawCentralFlare(VertexConsumer consumer,
                                         Matrix4f matrix,
                                         Direction.Axis axis,
                                         float time,
                                         float phase,
                                         boolean unstable) {
        int layers = unstable ? 7 : 5;
        float halfDepth = unstable ? 0.060F : 0.045F;

        for (int i = 0; i < layers; i++) {
            float t = layers == 1 ? 0.5F : (float) i / (layers - 1);
            float signed = (t - 0.5F) * 2.0F;
            float pulse = 0.5F + 0.5F * Mth.sin(time * 0.82F + phase * 2.2F + t * 4.0F);

            float depth = CENTER
                    + signed * halfDepth
                    + 0.004F * Mth.sin(time * 1.10F + phase * 1.4F + i * 0.9F);

            float outerHalfW = 0.44F + 0.05F * pulse;
            float outerHalfH = 0.86F + 0.07F * pulse;
            float innerHalfW = 0.24F + 0.03F * pulse;
            float innerHalfH = 0.54F + 0.04F * pulse;

            EnergySample outer = new EnergySample(118, 212, 255, unstable ? 190 : 168);
            EnergySample inner = new EnergySample(238, 250, 255, unstable ? 224 : 204);

            addPlaneQuadColoredDoubleSided(
                    consumer,
                    matrix,
                    axis,
                    HALF_INNER_WIDTH - outerHalfW, HALF_INNER_HEIGHT - outerHalfH, depth, outer,
                    HALF_INNER_WIDTH + outerHalfW, HALF_INNER_HEIGHT - outerHalfH, depth, outer,
                    HALF_INNER_WIDTH + outerHalfW, HALF_INNER_HEIGHT + outerHalfH, depth, outer,
                    HALF_INNER_WIDTH - outerHalfW, HALF_INNER_HEIGHT + outerHalfH, depth, outer
            );

            addPlaneQuadColoredDoubleSided(
                    consumer,
                    matrix,
                    axis,
                    HALF_INNER_WIDTH - innerHalfW, HALF_INNER_HEIGHT - innerHalfH, depth + 0.003F, inner,
                    HALF_INNER_WIDTH + innerHalfW, HALF_INNER_HEIGHT - innerHalfH, depth + 0.003F, inner,
                    HALF_INNER_WIDTH + innerHalfW, HALF_INNER_HEIGHT + innerHalfH, depth + 0.003F, inner,
                    HALF_INNER_WIDTH - innerHalfW, HALF_INNER_HEIGHT + innerHalfH, depth + 0.003F, inner
            );
        }
    }

    private static void drawHelixBands(VertexConsumer consumer,
                                       Matrix4f matrix,
                                       Direction.Axis axis,
                                       float time,
                                       float phase,
                                       boolean unstable) {
        int bandCount = unstable ? 5 : 4;
        int segments = unstable ? 28 : 22;
        float halfDepth = unstable ? 0.23F : 0.18F;
        float baseRadius = unstable ? 0.82F : 0.74F;
        float yScale = (PORTAL_HEIGHT / PORTAL_WIDTH) * 0.78F;
        float bandWidthBase = unstable ? 0.110F : 0.085F;

        for (int band = 0; band < bandCount; band++) {
            float bandPhase = phase + Mth.TWO_PI * band / bandCount;

            for (int segment = 0; segment < segments; segment++) {
                float u0 = (float) segment / segments;
                float u1 = (float) (segment + 1) / segments;

                float signed0 = u0 * 2.0F - 1.0F;
                float signed1 = u1 * 2.0F - 1.0F;
                float envelope0 = 1.0F - 0.55F * Math.abs(signed0);
                float envelope1 = 1.0F - 0.55F * Math.abs(signed1);

                float depth0 = CENTER
                        + signed0 * halfDepth
                        + 0.010F * Mth.sin(time * 0.42F + bandPhase + u0 * 8.0F);
                float depth1 = CENTER
                        + signed1 * halfDepth
                        + 0.010F * Mth.sin(time * 0.42F + bandPhase + u1 * 8.0F);

                float angle0 = time * (unstable ? 1.05F : 0.78F)
                        + bandPhase
                        + u0 * (unstable ? 12.0F : 10.0F);
                float angle1 = time * (unstable ? 1.05F : 0.78F)
                        + bandPhase
                        + u1 * (unstable ? 12.0F : 10.0F);

                float radius0 = baseRadius * envelope0;
                float radius1 = baseRadius * envelope1;

                float h0 = Mth.clamp(HALF_INNER_WIDTH + radius0 * Mth.cos(angle0), MIN_HORIZONTAL, MAX_HORIZONTAL);
                float v0 = Mth.clamp(HALF_INNER_HEIGHT + radius0 * yScale * Mth.sin(angle0), MIN_VERTICAL, MAX_VERTICAL);
                float h1 = Mth.clamp(HALF_INNER_WIDTH + radius1 * Mth.cos(angle1), MIN_HORIZONTAL, MAX_HORIZONTAL);
                float v1 = Mth.clamp(HALF_INNER_HEIGHT + radius1 * yScale * Mth.sin(angle1), MIN_VERTICAL, MAX_VERTICAL);

                float ribbonHalfWidth = bandWidthBase * (0.72F + 0.28F * (envelope0 + envelope1) * 0.5F);

                EnergySample c0 = boost(
                        sampleEnergy(h0, v0, time, phase, unstable, signed0, envelope0),
                        unstable ? 1.48F : 1.22F,
                        unstable ? 1.18F : 1.04F
                );
                EnergySample c1 = boost(
                        sampleEnergy(h1, v1, time, phase, unstable, signed1, envelope1),
                        unstable ? 1.48F : 1.22F,
                        unstable ? 1.18F : 1.04F
                );

                drawRibbonSegment(consumer, matrix, axis, h0, v0, depth0, h1, v1, depth1, ribbonHalfWidth, c0, c1);
            }
        }
    }

    private static void drawEventHorizonFx(VertexConsumer consumer,
                                           Matrix4f matrix,
                                           Direction.Axis axis,
                                           float time,
                                           float phase,
                                           boolean unstable) {
        int spikes = unstable ? 12 : 9;
        float yScale = (PORTAL_HEIGHT / PORTAL_WIDTH) * 0.84F;

        for (int i = 0; i < spikes; i++) {
            float dir = (i & 1) == 0 ? 1.0F : -1.0F;
            float angle = phase * 2.6F
                    + Mth.TWO_PI * i / spikes
                    + time * (unstable ? 1.45F : 1.08F) * dir;

            float startRadius = 0.14F + 0.06F * Mth.sin(time * 0.58F + i * 1.2F);
            float endRadius = 1.06F + 0.18F * Mth.sin(time * 0.27F + i * 0.9F + phase * 1.3F);

            float h0 = Mth.clamp(HALF_INNER_WIDTH + Mth.cos(angle) * startRadius, MIN_HORIZONTAL, MAX_HORIZONTAL);
            float v0 = Mth.clamp(HALF_INNER_HEIGHT + Mth.sin(angle) * startRadius * yScale, MIN_VERTICAL, MAX_VERTICAL);
            float h1 = Mth.clamp(HALF_INNER_WIDTH + Mth.cos(angle) * endRadius, MIN_HORIZONTAL, MAX_HORIZONTAL);
            float v1 = Mth.clamp(HALF_INNER_HEIGHT + Mth.sin(angle) * endRadius * yScale, MIN_VERTICAL, MAX_VERTICAL);

            float depth0 = CENTER + 0.19F * Mth.sin(time * 0.39F + i * 0.73F + phase);
            float depth1 = depth0 + 0.06F * Mth.sin(time * 0.81F + i * 1.13F + phase * 0.4F);

            float halfWidth = unstable ? 0.040F : 0.030F;

            int tipBlue = unstable ? 255 : 248;
            int tipGreen = unstable ? 242 : 222;
            EnergySample start = new EnergySample(84, 178, 255, 220);
            EnergySample end = new EnergySample(236, tipGreen, tipBlue, 245);
            drawRibbonSegment(consumer, matrix, axis, h0, v0, depth0, h1, v1, depth1, halfWidth, start, end);
        }
    }

    private static void drawEnergyPlane(VertexConsumer consumer,
                                        Matrix4f matrix,
                                        Direction.Axis axis,
                                        float depth,
                                        float inset,
                                        float time,
                                        float phase,
                                        boolean unstable,
                                        float depthNorm,
                                        float envelope,
                                        float brightness,
                                        float alphaScale) {
        float h0 = inset;
        float h1 = PORTAL_WIDTH - inset;
        float v0 = inset;
        float v1 = PORTAL_HEIGHT - inset;

        EnergySample c1 = boost(sampleEnergy(h0, v0, time, phase, unstable, depthNorm, envelope), brightness, alphaScale);
        EnergySample c2 = boost(sampleEnergy(h1, v0, time, phase, unstable, depthNorm, envelope), brightness, alphaScale);
        EnergySample c3 = boost(sampleEnergy(h1, v1, time, phase, unstable, depthNorm, envelope), brightness, alphaScale);
        EnergySample c4 = boost(sampleEnergy(h0, v1, time, phase, unstable, depthNorm, envelope), brightness, alphaScale);

        addPlaneQuadColoredDoubleSided(
                consumer,
                matrix,
                axis,
                h0, v0, depth, c1,
                h1, v0, depth, c2,
                h1, v1, depth, c3,
                h0, v1, depth, c4
        );
    }

    private static void drawRibbonSegment(VertexConsumer consumer,
                                          Matrix4f matrix,
                                          Direction.Axis axis,
                                          float h0,
                                          float v0,
                                          float d0,
                                          float h1,
                                          float v1,
                                          float d1,
                                          float halfWidth,
                                          EnergySample start,
                                          EnergySample end) {
        float dh = h1 - h0;
        float dv = v1 - v0;
        float len = Mth.sqrt(dh * dh + dv * dv);
        if (len < 0.0001F) return;

        float invLen = 1.0F / len;
        float offsetH = -dv * invLen * halfWidth;
        float offsetV = dh * invLen * halfWidth;

        float aH = Mth.clamp(h0 - offsetH, MIN_HORIZONTAL, MAX_HORIZONTAL);
        float aV = Mth.clamp(v0 - offsetV, MIN_VERTICAL, MAX_VERTICAL);
        float bH = Mth.clamp(h0 + offsetH, MIN_HORIZONTAL, MAX_HORIZONTAL);
        float bV = Mth.clamp(v0 + offsetV, MIN_VERTICAL, MAX_VERTICAL);
        float cH = Mth.clamp(h1 + offsetH, MIN_HORIZONTAL, MAX_HORIZONTAL);
        float cV = Mth.clamp(v1 + offsetV, MIN_VERTICAL, MAX_VERTICAL);
        float dH = Mth.clamp(h1 - offsetH, MIN_HORIZONTAL, MAX_HORIZONTAL);
        float dV = Mth.clamp(v1 - offsetV, MIN_VERTICAL, MAX_VERTICAL);

        addPlaneQuadColoredDoubleSided(
                consumer,
                matrix,
                axis,
                aH, aV, d0, start,
                bH, bV, d0, start,
                cH, cV, d1, end,
                dH, dV, d1, end
        );
    }

    private static EnergySample sampleEnergy(float horizontal,
                                             float vertical,
                                             float time,
                                             float phase,
                                             boolean unstable,
                                             float depthNorm,
                                             float envelope) {
        float u = Mth.clamp(horizontal, MIN_HORIZONTAL, MAX_HORIZONTAL);
        float v = Mth.clamp(vertical, MIN_VERTICAL, MAX_VERTICAL);

        float nx = (u - HALF_INNER_WIDTH) / HALF_INNER_WIDTH;
        float ny = (v - HALF_INNER_HEIGHT) / HALF_INNER_HEIGHT;
        float r = Mth.sqrt(nx * nx + ny * ny);
        float angle = (float) Math.atan2(ny, nx);

        float swirl = 0.5F + 0.5F * Mth.sin(
                angle * 7.8F
                        - time * (0.46F + envelope * 0.22F)
                        + r * 11.6F
                        + phase
                        + depthNorm * 4.8F
        );

        float filaments = 0.5F + 0.5F * Mth.sin(
                (nx * 2.35F - ny * 1.9F) * 7.5F
                        + time * 0.37F
                        + phase * 1.7F
                        - depthNorm * 5.1F
        );

        float core = Mth.clamp(1.08F - r, 0.0F, 1.0F);
        core *= core;

        float halo = (float) Math.exp(-13.5F * (r - 0.69F) * (r - 0.69F));
        float edge = Mth.clamp((Math.max(Math.abs(nx), Math.abs(ny)) - 0.48F) / 0.52F, 0.0F, 1.0F);
        float liquid = 0.5F + 0.5F * Mth.sin((nx * nx * 4.6F + ny * ny * 6.3F) * 3.4F - time * 0.62F + phase * 1.9F + depthNorm * 6.7F);

        float energy = core * 1.45F + halo * (0.62F + 0.78F * swirl) + filaments * (0.36F + 0.40F * envelope) + liquid * 0.24F;
        float alpha = 0.54F + core * 0.21F + halo * 0.17F + filaments * 0.13F + liquid * 0.07F;

        if (unstable) {
            float chaos = 0.5F + 0.5F * Mth.sin(time * 1.30F + angle * 11.0F + r * 18.0F + phase * 2.4F + depthNorm * 9.0F);
            energy += chaos * 0.30F;
            alpha += chaos * 0.12F;
        }

        alpha = Mth.clamp(alpha + edge * 0.08F, 0.0F, 1.0F);

        int red = Mth.clamp((int) (78 + energy * 126 + core * 88 + halo * 30), 0, 255);
        int green = Mth.clamp((int) (156 + energy * 150 + core * 60 + halo * 28), 0, 255);
        int blue = Mth.clamp((int) (228 + energy * 112 + core * 30 + halo * 20), 0, 255);

        if (unstable) {
            red = Mth.clamp(red + 10, 0, 255);
            green = Mth.clamp(green + 14, 0, 255);
            blue = 255;
        }

        int outAlpha = Mth.clamp((int) (alpha * (unstable ? 186.0F : 166.0F)), 0, 255);
        return new EnergySample(red, green, blue, outAlpha);
    }

    private static EnergySample boost(EnergySample sample, float brightness, float alphaScale) {
        float gain = brightness * alphaScale;
        int red = Mth.clamp((int) (sample.red() * gain), 0, 255);
        int green = Mth.clamp((int) (sample.green() * gain), 0, 255);
        int blue = Mth.clamp((int) (sample.blue() * gain), 0, 255);
        int alpha = Mth.clamp((int) (sample.alpha() * alphaScale), 70, 235);
        return new EnergySample(red, green, blue, alpha);
    }

    private static void addPlaneQuadColoredDoubleSided(VertexConsumer consumer,
                                                        Matrix4f matrix,
                                                        Direction.Axis axis,
                                                        float h1,
                                                        float v1,
                                                        float d1,
                                                        EnergySample c1,
                                                        float h2,
                                                        float v2,
                                                        float d2,
                                                        EnergySample c2,
                                                        float h3,
                                                        float v3,
                                                        float d3,
                                                        EnergySample c3,
                                                        float h4,
                                                        float v4,
                                                        float d4,
                                                        EnergySample c4) {
        addPlaneQuadColored(consumer, matrix, axis, h1, v1, d1, c1, h2, v2, d2, c2, h3, v3, d3, c3, h4, v4, d4, c4);
    }

    private static void addPlaneQuadColored(VertexConsumer consumer,
                                            Matrix4f matrix,
                                            Direction.Axis axis,
                                            float h1,
                                            float v1,
                                            float d1,
                                            EnergySample c1,
                                            float h2,
                                            float v2,
                                            float d2,
                                            EnergySample c2,
                                            float h3,
                                            float v3,
                                            float d3,
                                            EnergySample c3,
                                            float h4,
                                            float v4,
                                            float d4,
                                            EnergySample c4) {
        addPlaneVertex(consumer, matrix, axis, h1, v1, d1, c1);
        addPlaneVertex(consumer, matrix, axis, h2, v2, d2, c2);
        addPlaneVertex(consumer, matrix, axis, h3, v3, d3, c3);
        addPlaneVertex(consumer, matrix, axis, h4, v4, d4, c4);
    }

    private static void addPlaneVertex(VertexConsumer consumer,
                                       Matrix4f matrix,
                                       Direction.Axis axis,
                                       float horizontal,
                                       float vertical,
                                       float depth,
                                       EnergySample color) {
        if (axis == Direction.Axis.X) {
            consumer.addVertex(matrix, horizontal, vertical, depth)
                    .setColor(color.red(), color.green(), color.blue(), color.alpha());
        } else {
            consumer.addVertex(matrix, depth, vertical, horizontal)
                    .setColor(color.red(), color.green(), color.blue(), color.alpha());
        }
    }

    @Nullable
    private static PortalCoords resolvePortalCoords(Level level,
                                                    BlockPos fieldPos,
                                                    Direction.Axis axis) {
        int coreMinY = fieldPos.getY() - PortalFrameDetector.INNER_HEIGHT;
        int coreMaxY = fieldPos.getY() - 1;

        for (int coreY = coreMinY; coreY <= coreMaxY; coreY++) {
            if (axis == Direction.Axis.X) {
                for (int coreX = fieldPos.getX() - 1; coreX <= fieldPos.getX() + 1; coreX++) {
                    PortalCoords resolved = tryResolveFromCore(level, fieldPos, axis, new BlockPos(coreX, coreY, fieldPos.getZ()));
                    if (resolved != null) return resolved;
                }
            } else {
                for (int coreZ = fieldPos.getZ() - 1; coreZ <= fieldPos.getZ() + 1; coreZ++) {
                    PortalCoords resolved = tryResolveFromCore(level, fieldPos, axis, new BlockPos(fieldPos.getX(), coreY, coreZ));
                    if (resolved != null) return resolved;
                }
            }
        }

        return null;
    }

    @Nullable
    private static PortalCoords tryResolveFromCore(Level level,
                                                   BlockPos fieldPos,
                                                   Direction.Axis axis,
                                                   BlockPos corePos) {
        if (!level.getBlockState(corePos).is(ModBlocks.PORTAL_CORE)) return null;

        Optional<PortalFrameDetector.FrameMatch> matchOptional = PortalFrameDetector.find(level, corePos);
        if (matchOptional.isEmpty()) return null;

        PortalFrameDetector.FrameMatch match = matchOptional.get();
        Direction.Axis detectedAxis = match.right() == Direction.EAST ? Direction.Axis.X : Direction.Axis.Z;
        if (detectedAxis != axis) return null;

        int horizontalOffset = (fieldPos.getX() - corePos.getX()) * match.right().getStepX()
                + (fieldPos.getZ() - corePos.getZ()) * match.right().getStepZ();
        int horizontalIndex = horizontalOffset + (PortalFrameDetector.INNER_WIDTH / 2);
        int verticalIndex = fieldPos.getY() - corePos.getY() - 1;

        if (horizontalIndex < 0 || horizontalIndex >= PortalFrameDetector.INNER_WIDTH) return null;
        if (verticalIndex < 0 || verticalIndex >= PortalFrameDetector.INNER_HEIGHT) return null;
        if (!match.interior().contains(fieldPos)) return null;

        float phase = (corePos.asLong() & 255L) * 0.071F;
        return new PortalCoords(horizontalIndex, verticalIndex, phase);
    }

    private record PortalCoords(int horizontalIndex, int verticalIndex, float phase) {}

    private record EnergySample(int red, int green, int blue, int alpha) {}
}
