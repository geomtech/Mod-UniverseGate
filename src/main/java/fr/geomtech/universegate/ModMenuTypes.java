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

    public static void register() {}

    private ModMenuTypes() {}
}
