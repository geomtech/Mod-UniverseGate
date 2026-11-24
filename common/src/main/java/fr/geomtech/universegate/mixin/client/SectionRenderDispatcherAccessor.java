package fr.geomtech.universegate.mixin.client;

import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.SectionBufferBuilderPool;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SectionRenderDispatcher.class)
public interface SectionRenderDispatcherAccessor {

    @Mutable
    @Accessor("bufferPool")
    void universegate$setBufferPool(SectionBufferBuilderPool bufferPool);

    @Mutable
    @Accessor("fixedBuffers")
    void universegate$setFixedBuffers(SectionBufferBuilderPack fixedBuffers);
}
