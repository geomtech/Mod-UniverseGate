package fr.geomtech.universegate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class ModMenuTypes {

    public static final ExtendedScreenHandlerType<PortalKeyboardMenu, BlockPos> PORTAL_KEYBOARD =
            Registry.register(
                    BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_keyboard"),
                    new ExtendedScreenHandlerType<>(
                            PortalKeyboardMenu::new,
                            BlockPos.STREAM_CODEC
                    )
            );

    public static final ExtendedScreenHandlerType<PortalNaturalKeyboardMenu, BlockPos> PORTAL_NATURAL_KEYBOARD =
            Registry.register(
                    BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_natural_keyboard"),
                    new ExtendedScreenHandlerType<>(
                            PortalNaturalKeyboardMenu::new,
                            BlockPos.STREAM_CODEC
                    )
            );

    public static final ExtendedScreenHandlerType<PortalCoreMenu, BlockPos> PORTAL_CORE =
            Registry.register(
                    BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_core"),
                    new ExtendedScreenHandlerType<>(
                            PortalCoreMenu::new,
                            BlockPos.STREAM_CODEC
                    )
            );

    public static final ExtendedScreenHandlerType<MeteorologicalControllerMenu, BlockPos> METEOROLOGICAL_CONTROLLER =
            Registry.register(
                    BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "meteorological_controller"),
                    new ExtendedScreenHandlerType<>(
                            MeteorologicalControllerMenu::new,
                            BlockPos.STREAM_CODEC
                    )
            );

    public static final ExtendedScreenHandlerType<EnergyMonitorMenu, BlockPos> ENERGY_MONITOR =
            Registry.register(
                    BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "energy_monitor"),
                    new ExtendedScreenHandlerType<>(
                            EnergyMonitorMenu::new,
                            BlockPos.STREAM_CODEC
                    )
            );

    public static final ExtendedScreenHandlerType<RiftRefinerMenu, BlockPos> RIFT_REFINER =
            Registry.register(
                    BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_refiner"),
                    new ExtendedScreenHandlerType<>(
                            RiftRefinerMenu::new,
                            BlockPos.STREAM_CODEC
                    )
            );

    public static final ExtendedScreenHandlerType<CombustionGeneratorMenu, BlockPos> COMBUSTION_GENERATOR =
            Registry.register(
                    BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "combustion_generator"),
                    new ExtendedScreenHandlerType<>(
                            CombustionGeneratorMenu::new,
                            BlockPos.STREAM_CODEC
                    )
            );

    public static void register() {}

    private ModMenuTypes() {}
}
