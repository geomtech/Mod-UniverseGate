package fr.geomtech.universegate.seamless.client;

//? if <1.21.11 {
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.seamless.math.PortalTransform;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
//?}

public final class PortalDistortionRenderer {
    private PortalDistortionRenderer() {}

    //? if <1.21.11 {
    private static final Bounds FULL_SCREEN = new Bounds(-1.0F, -1.0F, 1.0F, 1.0F);
    public static final ResourceLocation SHADER_ID = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_vortex");
    private static ShaderInstance vortexShader;

    public static void setShader(ShaderInstance shader) {
        vortexShader = shader;
    }

    record Bounds(float minX, float minY, float maxX, float maxY) {
        float width() { return maxX - minX; }
        float height() { return maxY - minY; }

        Matrix4f crop(Matrix4f projection) {
            return new Matrix4f().translation(-(minX + maxX) / width(), -(minY + maxY) / height(), 0.0F)
                    .scale(2.0F / width(), 2.0F / height(), 1.0F).mul(projection);
        }
    }

    static Bounds project(PortalTransform transform, Vec3 camera, Matrix4f viewProjection) {
        Vec3 center = transform.sourceCenter().subtract(camera);
        double radius = PortalTransform.APERTURE_RADIUS;
        double dx = radius * transform.sourceRight().getStepX();
        double dz = radius * transform.sourceRight().getStepZ();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        Vector4f corner = new Vector4f();
        for (int h = -1; h <= 1; h += 2) {
            for (int v = -1; v <= 1; v += 2) {
                corner.set((float) (center.x + dx * h), (float) (center.y + radius * v),
                        (float) (center.z + dz * h), 1.0F).mul(viewProjection);
                if (corner.w <= 0.05F) return FULL_SCREEN;
                float x = corner.x / corner.w;
                float y = corner.y / corner.w;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        float padX = (maxX - minX) * 0.04F;
        float padY = (maxY - minY) * 0.04F;
        minX = Math.max(-1.0F, minX - padX);
        minY = Math.max(-1.0F, minY - padY);
        maxX = Math.min(1.0F, maxX + padX);
        maxY = Math.min(1.0F, maxY + padY);
        return maxX > minX && maxY > minY ? new Bounds(minX, minY, maxX, maxY) : null;
    }

    static void draw(RenderTarget scene, Bounds bounds, Vector4f screenRect, int width, int height,
                     Matrix4f apertureView, Matrix4f projection, double time, float approach) {
        float maxU = (float) width / scene.width;
        float maxV = (float) height / scene.height;
        float insetU = 0.5F / scene.width;
        float insetV = 0.5F / scene.height;
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        ShaderInstance shader = vortexShader;
        RenderSystem.setProjectionMatrix(shader != null ? projection : new Matrix4f(),
                shader != null ? VertexSorting.DISTANCE_TO_ORIGIN : VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.setShader(() -> shader != null ? shader : GameRenderer.getPositionTexShader());
        RenderSystem.setShaderTexture(0, scene.getColorTextureId());
        if (shader != null) {
            shader.safeGetUniform("SceneRect").set(insetU, insetV, maxU - 2.0F * insetU, maxV - 2.0F * insetV);
            shader.safeGetUniform("ScreenRect").set(screenRect);
            shader.safeGetUniform("Phase").set((float) ((time % 160.0D) * (Math.PI * 2.0D / 160.0D)));
            shader.safeGetUniform("Approach").set(approach);
        }
        RenderSystem.setShaderColor(0.78F, 0.78F, 0.78F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        try {
            BufferBuilder mesh = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            if (shader != null) {
                float radius = (float) PortalTransform.APERTURE_RADIUS;
                mesh.addVertex(apertureView, -radius, -radius, 0.0F).setUv(0, 0);
                mesh.addVertex(apertureView, radius, -radius, 0.0F).setUv(1, 0);
                mesh.addVertex(apertureView, radius, radius, 0.0F).setUv(1, 1);
                mesh.addVertex(apertureView, -radius, radius, 0.0F).setUv(0, 1);
            } else {
                mesh.addVertex(bounds.minX(), bounds.minY(), 0.0F).setUv(insetU, insetV);
                mesh.addVertex(bounds.maxX(), bounds.minY(), 0.0F).setUv(maxU - insetU, insetV);
                mesh.addVertex(bounds.maxX(), bounds.maxY(), 0.0F).setUv(maxU - insetU, maxV - insetV);
                mesh.addVertex(bounds.minX(), bounds.maxY(), 0.0F).setUv(insetU, maxV - insetV);
            }
            BufferUploader.drawWithShader(mesh.buildOrThrow());
        } finally {
            RenderSystem.enableCull();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        }
    }

    //?}
}
