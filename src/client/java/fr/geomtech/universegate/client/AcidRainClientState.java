package fr.geomtech.universegate.client;

import fr.geomtech.universegate.AcidRainSavedData;
import net.minecraft.util.Mth;

public final class AcidRainClientState {

    private static boolean active;
    private static boolean warning;
    private static int warningTicks;

    private AcidRainClientState() {}

    public static boolean isActive() {
        return active;
    }

    public static void setState(boolean value, int remainingWarningTicks) {
        active = value;
        warning = !value && remainingWarningTicks > 0;
        warningTicks = Mth.clamp(remainingWarningTicks, 0, AcidRainSavedData.WARNING_TICKS);
    }

    public static void tick() {
        if (warningTicks > 0) warningTicks--;
    }

    public static float intensity(float partialTick) {
        if (active) return 1.0F;
        if (!warning) return 0.0F;
        return Mth.clamp(1.0F - (warningTicks - partialTick) / AcidRainSavedData.WARNING_TICKS, 0.0F, 1.0F);
    }
}
