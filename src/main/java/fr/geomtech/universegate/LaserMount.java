package fr.geomtech.universegate;

import net.minecraft.world.item.ItemStack;

public interface LaserMount {
    boolean universegate$hasLaserHarness();
    void universegate$setLaserHarness(boolean equipped);
    int universegate$getLaserShots();
    void universegate$setLaserShots(int shots);
    ItemStack universegate$getSaddle();
}
