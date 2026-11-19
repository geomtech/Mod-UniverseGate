package fr.geomtech.universegate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public class FluidPipeBlockEntityRenderer implements BlockEntityRenderer<FluidPipeBlockEntity> {

    public static final ResourceLocation FLUID_TEXTURE = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "block/dark_matter_still");

    public FluidPipeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FluidPipeBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (entity.getFluidAmount() <= 0) return;

        // Calculate fill percentage
        float fill = (float) entity.getFluidAmount() / FluidPipeBlockEntity.CAPACITY;
        
        // Use the fluid texture
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(FLUID_TEXTURE);
        VertexConsumer builder = bufferSource.getBuffer(RenderType.translucent());

        // Draw center "core" of fluid
        // Scale based on fill amount, but clamp minimum to be visible if there's any fluid
        float minSize = 0.1f;
        float maxSize = 6.0f / 16.0f; // Fits inside pipe (which is 6x6)
        float currentSize = minSize + (maxSize - minSize) * fill;
        
        float min = 0.5f - currentSize / 2;
        float max = 0.5f + currentSize / 2;

        renderCube(poseStack, builder, min, min, min, max, max, max, sprite, packedLight, packedOverlay);

        // Draw connections
        // We look at the blockstate to see where pipes are connected.
        // Ideally we only draw fluid in directions where fluid is actually flowing or present,
        // but drawing wherever the pipe connects is a good visual approximation for "full" pipes.
        // A better approach would be to check neighbor fluid levels.
        
        // For visual "flow", we can just draw connections if the pipe has significant fluid.
        if (fill > 0.1f) {
             net.minecraft.world.level.block.state.BlockState state = entity.getBlockState();
             if (state.getBlock() instanceof FluidPipeBlock) {
                 if (state.getValue(FluidPipeBlock.NORTH)) renderConnection(poseStack, builder, Direction.NORTH, currentSize, sprite, packedLight, packedOverlay);
                 if (state.getValue(FluidPipeBlock.SOUTH)) renderConnection(poseStack, builder, Direction.SOUTH, currentSize, sprite, packedLight, packedOverlay);
                 if (state.getValue(FluidPipeBlock.EAST)) renderConnection(poseStack, builder, Direction.EAST, currentSize, sprite, packedLight, packedOverlay);
                 if (state.getValue(FluidPipeBlock.WEST)) renderConnection(poseStack, builder, Direction.WEST, currentSize, sprite, packedLight, packedOverlay);
                 if (state.getValue(FluidPipeBlock.UP)) renderConnection(poseStack, builder, Direction.UP, currentSize, sprite, packedLight, packedOverlay);
                 if (state.getValue(FluidPipeBlock.DOWN)) renderConnection(poseStack, builder, Direction.DOWN, currentSize, sprite, packedLight, packedOverlay);
             }
        }
    }

    private void renderConnection(PoseStack poseStack, VertexConsumer builder, Direction dir, float size, TextureAtlasSprite sprite, int light, int overlay) {
        float min = 0.5f - size / 2;
        float max = 0.5f + size / 2;
        
        float x1 = min, y1 = min, z1 = min;
        float x2 = max, y2 = max, z2 = max;

        switch (dir) {
            case NORTH -> z1 = 0;
            case SOUTH -> z2 = 1;
            case WEST -> x1 = 0;
            case EAST -> x2 = 1;
            case UP -> y2 = 1;
            case DOWN -> y1 = 0;
        }
        
        // Clamp to center if needed (already handled by switch implicitly expanding from center)
        renderCube(poseStack, builder, x1, y1, z1, x2, y2, z2, sprite, light, overlay);
    }

    private void renderCube(PoseStack poseStack, VertexConsumer builder, float x1, float y1, float z1, float x2, float y2, float z2, TextureAtlasSprite sprite, int light, int overlay) {
        // Simple cube rendering
        // In a real scenario, we'd cull internal faces, but for simple pipes it's okay.
        
        // Matrix 4f
        org.joml.Matrix4f m = poseStack.last().pose();
        
        // UVs
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        // Color (Dark Purple/Black for Dark Matter)
        int r = 50, g = 0, b = 80, a = 200;

        // Top
        addVertex(builder, m, x1, y2, z1, u0, v0, r, g, b, a, light, overlay, 0, 1, 0);
        addVertex(builder, m, x1, y2, z2, u0, v1, r, g, b, a, light, overlay, 0, 1, 0);
        addVertex(builder, m, x2, y2, z2, u1, v1, r, g, b, a, light, overlay, 0, 1, 0);
        addVertex(builder, m, x2, y2, z1, u1, v0, r, g, b, a, light, overlay, 0, 1, 0);

        // Bottom
        addVertex(builder, m, x1, y1, z1, u0, v0, r, g, b, a, light, overlay, 0, -1, 0);
        addVertex(builder, m, x2, y1, z1, u1, v0, r, g, b, a, light, overlay, 0, -1, 0);
        addVertex(builder, m, x2, y1, z2, u1, v1, r, g, b, a, light, overlay, 0, -1, 0);
        addVertex(builder, m, x1, y1, z2, u0, v1, r, g, b, a, light, overlay, 0, -1, 0);

        // North
        addVertex(builder, m, x1, y1, z1, u0, v0, r, g, b, a, light, overlay, 0, 0, -1);
        addVertex(builder, m, x1, y2, z1, u0, v1, r, g, b, a, light, overlay, 0, 0, -1);
        addVertex(builder, m, x2, y2, z1, u1, v1, r, g, b, a, light, overlay, 0, 0, -1);
        addVertex(builder, m, x2, y1, z1, u1, v0, r, g, b, a, light, overlay, 0, 0, -1);

        // South
        addVertex(builder, m, x1, y1, z2, u0, v0, r, g, b, a, light, overlay, 0, 0, 1);
        addVertex(builder, m, x2, y1, z2, u1, v0, r, g, b, a, light, overlay, 0, 0, 1);
        addVertex(builder, m, x2, y2, z2, u1, v1, r, g, b, a, light, overlay, 0, 0, 1);
        addVertex(builder, m, x1, y2, z2, u0, v1, r, g, b, a, light, overlay, 0, 0, 1);

        // West
        addVertex(builder, m, x1, y1, z1, u0, v0, r, g, b, a, light, overlay, -1, 0, 0);
        addVertex(builder, m, x1, y1, z2, u1, v0, r, g, b, a, light, overlay, -1, 0, 0);
        addVertex(builder, m, x1, y2, z2, u1, v1, r, g, b, a, light, overlay, -1, 0, 0);
        addVertex(builder, m, x1, y2, z1, u0, v1, r, g, b, a, light, overlay, -1, 0, 0);

        // East
        addVertex(builder, m, x2, y1, z1, u0, v0, r, g, b, a, light, overlay, 1, 0, 0);
        addVertex(builder, m, x2, y2, z1, u0, v1, r, g, b, a, light, overlay, 1, 0, 0);
        addVertex(builder, m, x2, y2, z2, u1, v1, r, g, b, a, light, overlay, 1, 0, 0);
        addVertex(builder, m, x2, y1, z2, u1, v0, r, g, b, a, light, overlay, 1, 0, 0);
    }

    private void addVertex(VertexConsumer builder, org.joml.Matrix4f m, float x, float y, float z, float u, float v, int r, int g, int b, int a, int light, int overlay, float nx, float ny, float nz) {
        builder.addVertex(m, x, y, z)
               .setColor(r, g, b, a)
               .setUv(u, v)
               .setOverlay(overlay)
               .setLight(light)
               .setNormal(nx, ny, nz);
    }
}
