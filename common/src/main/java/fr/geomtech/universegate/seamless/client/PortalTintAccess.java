package fr.geomtech.universegate.seamless.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;

public interface PortalTintAccess {
    int universegate$getVanillaTint(BlockPos pos, ColorResolver resolver);
}
