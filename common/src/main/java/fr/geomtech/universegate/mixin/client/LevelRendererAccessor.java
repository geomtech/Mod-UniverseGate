package fr.geomtech.universegate.mixin.client;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

//? if <1.21.11 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
//?}

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    @Accessor("level")
    ClientLevel universegate$getLevel();

    @Accessor("level")
    void universegate$setLevel(ClientLevel level);

    @Accessor("viewArea")
    ViewArea universegate$getViewArea();

    @Accessor("viewArea")
    void universegate$setViewArea(ViewArea viewArea);

    @Accessor("sectionRenderDispatcher")
    SectionRenderDispatcher universegate$getSectionRenderDispatcher();

    @Accessor("sectionRenderDispatcher")
    void universegate$setSectionRenderDispatcher(SectionRenderDispatcher dispatcher);

    @Accessor("visibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> universegate$getVisibleSections();

    @Invoker("compileSections")
    void universegate$compileSections(Camera camera);

    //? if <1.21.11 {
    @Accessor("cullingFrustum")
    Frustum universegate$getCullingFrustum();

    @Accessor("cullingFrustum")
    void universegate$setCullingFrustum(Frustum frustum);

    @Invoker("setupRender")
    void universegate$setupRender(Camera camera, Frustum frustum, boolean capturedFrustum, boolean spectator);

    @Invoker("renderSectionLayer")
    void universegate$renderSectionLayer(RenderType renderType, double x, double y, double z, Matrix4f frustumMatrix, Matrix4f projectionMatrix);

    @Invoker("renderEntity")
    void universegate$renderEntity(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource);
    //?}
}
