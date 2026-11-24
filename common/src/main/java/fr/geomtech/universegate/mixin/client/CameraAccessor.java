package fr.geomtech.universegate.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
//? if >=1.21.11 {
/*import net.minecraft.world.level.Level;
*///?} else {
import net.minecraft.world.level.BlockGetter;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {

    @Invoker("setPosition")
    void universegate$setPosition(double x, double y, double z);

    @Invoker("setRotation")
    void universegate$setRotation(float yRot, float xRot);

    @Accessor("entity")
    void universegate$setEntity(Entity entity);

    //? if >=1.21.11 {
    /*@Accessor("level")
    void universegate$setLevel(Level level);
    *///?} else {
    @Accessor("level")
    void universegate$setLevel(BlockGetter level);
    //?}
}
