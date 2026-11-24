package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.seamless.physics.PortalHalfCollisionHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Intercepts {@link Entity#collideBoundingBox} at {@code HEAD} (compatible with Lithium's
 * {@code @Overwrite} on {@code collideBoundingBox}) so entities straddling an active portal
 * ignore blocks directly behind the source aperture and collide with the destination portal's
 * exit geometry instead.
 */
@Mixin(Entity.class)
public abstract class EntityHalfCollisionMixin {

    @Shadow
    private static Vec3 collideWithShapes(Vec3 movement, AABB boundingBox, List<VoxelShape> shapes) {
        throw new AssertionError();
    }

    @Inject(method = "collideBoundingBox", at = @At("HEAD"), cancellable = true)
    private static void universegate$applyPortalHalfCollision(@Nullable Entity entity,
                                                              Vec3 movement,
                                                              AABB boundingBox,
                                                              Level level,
                                                              List<VoxelShape> initialCollisions,
                                                              CallbackInfoReturnable<Vec3> cir) {
        if (entity == null || level == null) {
            return;
        }
        AABB sweptBox = boundingBox.expandTowards(movement);
        List<VoxelShape> adjusted = PortalHalfCollisionHandler.resolveHalfThroughColliders(
                entity, level, sweptBox, initialCollisions);
        if (adjusted != null) {
            cir.setReturnValue(collideWithShapes(movement, boundingBox, adjusted));
        }
    }
}
