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

    public static final ExtendedScreenHandlerType<PortalMobileKeyboardMenu, BlockPos> PORTAL_MOBILE_KEYBOARD =
            Registry.register(
                    BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_mobile_keyboard"),
                    new ExtendedScreenHandlerType<>(
                            PortalMobileKeyboardMenu::new,
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

    public static final ExtendedScreenHandlerType<ZpcInterfaceControllerMenu, BlockPos> ZPC_INTERFACE_CONTROLLER =
            Registry.register(
                    BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "zpc_interface_controller"),
                    new ExtendedScreenHandlerType<>(
                            ZpcInterfaceControllerMenu::new,
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

    public static final ExtendedScreenHandlerType<DarkEnergyGeneratorMenu, BlockPos> DARK_ENERGY_GENERATOR =
            Registry.register(
                    BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "dark_energy_generator"),
                    new ExtendedScreenHandlerType<>(
                            DarkEnergyGeneratorMenu::new,
                            BlockPos.STREAM_CODEC
                    )
            );

    public static final ExtendedScreenHandlerType<MobClonerControllerMenu, BlockPos> MOB_CLONER_CONTROLLER =
            Registry.register(
                    BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "mob_cloner_controller"),
                    new ExtendedScreenHandlerType<>(
                            MobClonerControllerMenu::new,
                            BlockPos.STREAM_CODEC
                    )
            );

    public static void register() {}

    private ModMenuTypes() {}
}
