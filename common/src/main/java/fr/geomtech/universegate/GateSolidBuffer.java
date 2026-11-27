package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.geomtech.universegate.seamless.client.SodiumIrisCompat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;

/**
 * Tampon des faces pleines du modèle 3D du portail.
 *
 * <p>Sans pack de shaders, tout part dans {@link PortalGateRenderTypes#GATE_SOLID} (couleurs pleines). Avec un pack
 * Iris, ce rendu sans normale ni texture ressort délavé en gris : les faces sombres passent alors par un rendu
 * d'entité opaque (texture blanche, normale vers la caméra) que les packs traitent comme un bloc noir, et les faces
 * claires (lumières, lèvre, cristaux) gardent le rendu plein et lumineux.</p>
 */
final class GateSolidBuffer implements VertexConsumer {

    private static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "textures/block/cable_white.png");
    /** Au-dessus de ce niveau (canal le plus clair), une face reste en rendu plein lumineux. */
    private static final int BRIGHT_CHANNEL = 0x60;

    private final MultiBufferSource source;
    private float[] dark = new float[4 * 4 * 64];
    private float[] bright = new float[4 * 4 * 64];
    private int darkCount;
    private int brightCount;
    private final float[] pending = new float[16];
    private int vertex;

    private GateSolidBuffer(MultiBufferSource source) {
        this.source = source;
    }

    /** Consommateur pour les faces pleines du portail ; appeler {@link #end} une fois tout dessiné. */
    static VertexConsumer begin(MultiBufferSource source) {
        //? if <1.21.11 {
        if (SodiumIrisCompat.isIrisShaderPackActive()) return new GateSolidBuffer(source);
        //?}
        return source.getBuffer(PortalGateRenderTypes.GATE_SOLID);
    }

    static void end(VertexConsumer consumer) {
        if (consumer instanceof GateSolidBuffer buffer) buffer.flush();
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        int o = vertex * 4;
        pending[o] = x;
        pending[o + 1] = y;
        pending[o + 2] = z;
        pending[o + 3] = Float.intBitsToFloat(0xFF000000);
        vertex++;
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        if (vertex == 0) return this;
        pending[(vertex - 1) * 4 + 3] = Float.intBitsToFloat((a << 24) | (r << 16) | (g << 8) | b);
        if (vertex == 4) {
            boolean isBright = Math.max(r, Math.max(g, b)) >= BRIGHT_CHANNEL;
            if (isBright) {
                bright = append(bright, brightCount++);
            } else {
                dark = append(dark, darkCount++);
            }
            vertex = 0;
        }
        return this;
    }

    private float[] append(float[] target, int quad) {
        if ((quad + 1) * 16 > target.length) target = Arrays.copyOf(target, target.length * 2);
        System.arraycopy(pending, 0, target, quad * 16, 16);
        return target;
    }

    @Override public VertexConsumer setUv(float u, float v) { return this; }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    //? if >=1.21.11 {
    /*@Override public VertexConsumer setLineWidth(float width) { return this; }
    @Override public VertexConsumer setColor(int argb) {
        return setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }
    *///?}

    private void flush() {
        //? if <1.21.11 {
        if (darkCount > 0) {
            VertexConsumer lit = source.getBuffer(RenderType.entityCutoutNoCull(WHITE));
            for (int q = 0; q < darkCount; q++) {
                int o = q * 16;
                // Normale de la face, tournée vers la caméra (origine) : les packs l'éclairent comme une face visible.
                float ax = dark[o + 4] - dark[o], ay = dark[o + 5] - dark[o + 1], az = dark[o + 6] - dark[o + 2];
                float bx = dark[o + 12] - dark[o], by = dark[o + 13] - dark[o + 1], bz = dark[o + 14] - dark[o + 2];
                float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length < 1.0E-12F) { nx = 0.0F; ny = 1.0F; nz = 0.0F; length = 1.0F; }
                float cx = dark[o] + dark[o + 8], cy = dark[o + 1] + dark[o + 9], cz = dark[o + 2] + dark[o + 10];
                if (nx * cx + ny * cy + nz * cz > 0.0F) length = -length;
                nx /= length; ny /= length; nz /= length;
                for (int v = 0; v < 4; v++) {
                    int p = o + v * 4;
                    lit.addVertex(dark[p], dark[p + 1], dark[p + 2], Float.floatToRawIntBits(dark[p + 3]),
                            0.5F, 0.5F, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, nx, ny, nz);
                }
            }
        }
        if (brightCount > 0) {
            VertexConsumer solid = source.getBuffer(PortalGateRenderTypes.GATE_SOLID);
            for (int i = 0; i < brightCount * 4; i++) {
                int p = i * 4;
                int argb = Float.floatToRawIntBits(bright[p + 3]);
                solid.addVertex(bright[p], bright[p + 1], bright[p + 2])
                        .setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
            }
        }
        //?}
        darkCount = 0;
        brightCount = 0;
    }
}
