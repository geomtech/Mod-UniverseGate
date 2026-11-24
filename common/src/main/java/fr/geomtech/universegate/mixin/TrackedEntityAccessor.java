package fr.geomtech.universegate.mixin;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {

    @Accessor("serverEntity")
    ServerEntity universegate$getServerEntity();

    @Accessor("entity")
    Entity universegate$getEntity();
}
