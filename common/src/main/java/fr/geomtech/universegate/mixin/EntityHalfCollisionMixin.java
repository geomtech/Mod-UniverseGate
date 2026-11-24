package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.seamless.physics.PortalHalfCollisionHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Intercepts collider collection inside {@link Entity#collideBoundingBox} so entities straddling an
 * active outbound portal ignore blocks directly behind the source aperture and collide with the
 * destination portal's exit geometry instead.
 */
@Mixin(Entity.class)
public abstract class EntityHalfCollisionMixin {

    @Shadow
    private static List<VoxelShape> collectColliders(@Nullable Entity entity,
                                                     Level level,
                                                     List<VoxelShape> collisions,
                                                     AABB boundingBox) {
        throw new AssertionError();
    }

    @Redirect(
            method = "collideBoundingBox",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;collectColliders(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Ljava/util/List;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private static List<VoxelShape> universegate$applyPortalHalfCollision(@Nullable Entity entity,
                                                                          Level level,
                                                                          List<VoxelShape> initialCollisions,
                                                                          AABB sweptBox) {
        List<VoxelShape> vanilla = collectColliders(entity, level, initialCollisions, sweptBox);
        if (entity == null || level == null) {
            return vanilla;
        }
        List<VoxelShape> adjusted = PortalHalfCollisionHandler.buildHalfThroughCollisionShapes(
                entity, level, sweptBox, vanilla);
        return adjusted != null ? adjusted : vanilla;
    }
}
