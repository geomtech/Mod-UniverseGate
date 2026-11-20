package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
//? if >=1.21.11 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.ResourceLocation;
*///?} else {
import net.minecraft.client.renderer.RenderStateShard;
//?}
import net.minecraft.client.renderer.RenderType;

/** Rendu du modèle 3D du portail : couleurs pleines, sans texture ni transparence, toujours lumineux. */
final class PortalGateRenderTypes {

    private PortalGateRenderTypes() {}

    //? if >=1.21.11 {
    /*private static final RenderPipeline GATE_SOLID_PIPELINE = RenderPipeline.builder()
            .withLocation(ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "pipeline/portal_gate_solid"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withCull(false)
            .withDepthWrite(true)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();

    static final RenderType GATE_SOLID = RenderType.create(
            "universegate_portal_gate_solid",
            RenderSetup.builder(GATE_SOLID_PIPELINE).bufferSize(262144).createRenderSetup()
    );
    *///?} else {
    static final RenderType GATE_SOLID = RenderType.create(
            "universegate_portal_gate_solid",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            262144,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false)
    );
    //?}
}
