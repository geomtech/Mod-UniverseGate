package fr.geomtech.universegate.neoforge;

import fr.geomtech.universegate.DarkMatterFluid;
import fr.geomtech.universegate.UniverseGate;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge requires every modded fluid to expose a {@link FluidType}.
 * The properties mirror the Fabric behaviour of the dark matter fluid (no swimming, no drowning, no current).
 */
public final class NeoForgeFluids {

    public static final ResourceLocation DARK_MATTER_TYPE_ID = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "dark_matter");

    public static final FluidType DARK_MATTER_TYPE = new FluidType(FluidType.Properties.create()
            .descriptionId("block.universegate.dark_matter_block")
            .canSwim(false)
            .canDrown(false)
            .canPushEntity(false)
            .canConvertToSource(false)
            .supportsBoating(false)
            .fallDistanceModifier(1.0F)
            .lightLevel(0)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));

    private static boolean registered = false;

    private NeoForgeFluids() {}

    static void register() {
        if (registered) return;
        registered = true;
        Registry.register(NeoForgeRegistries.FLUID_TYPES, DARK_MATTER_TYPE_ID, DARK_MATTER_TYPE);
    }

    public static final class Source extends DarkMatterFluid.Source {
        @Override
        public FluidType getFluidType() {
            return DARK_MATTER_TYPE;
        }
    }

    public static final class Flowing extends DarkMatterFluid.Flowing {
        @Override
        public FluidType getFluidType() {
            return DARK_MATTER_TYPE;
        }
    }
}
