package fr.geomtech.universegate;

import fr.geomtech.universegate.platform.PosMenuFactory;
import fr.geomtech.universegate.platform.Services;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public final class ModMenuTypes {

    public static final MenuType<PortalKeyboardMenu> PORTAL_KEYBOARD = register("portal_keyboard", PortalKeyboardMenu::new);

    public static final MenuType<PortalNaturalKeyboardMenu> PORTAL_NATURAL_KEYBOARD = register("portal_natural_keyboard", PortalNaturalKeyboardMenu::new);

    public static final MenuType<PortalMobileKeyboardMenu> PORTAL_MOBILE_KEYBOARD = register("portal_mobile_keyboard", PortalMobileKeyboardMenu::new);

    public static final MenuType<PortalCoreMenu> PORTAL_CORE = register("portal_core", PortalCoreMenu::new);

    public static final MenuType<MeteorologicalControllerMenu> METEOROLOGICAL_CONTROLLER = register("meteorological_controller", MeteorologicalControllerMenu::new);

    public static final MenuType<EnergyMonitorMenu> ENERGY_MONITOR = register("energy_monitor", EnergyMonitorMenu::new);

    public static final MenuType<ZpcInterfaceControllerMenu> ZPC_INTERFACE_CONTROLLER = register("zpc_interface_controller", ZpcInterfaceControllerMenu::new);

    public static final MenuType<RiftRefinerMenu> RIFT_REFINER = register("rift_refiner", RiftRefinerMenu::new);

    public static final MenuType<CombustionGeneratorMenu> COMBUSTION_GENERATOR = register("combustion_generator", CombustionGeneratorMenu::new);

    public static final MenuType<DarkEnergyGeneratorMenu> DARK_ENERGY_GENERATOR = register("dark_energy_generator", DarkEnergyGeneratorMenu::new);

    public static final MenuType<MobClonerControllerMenu> MOB_CLONER_CONTROLLER = register("mob_cloner_controller", MobClonerControllerMenu::new);

    private static <M extends AbstractContainerMenu> MenuType<M> register(String name, PosMenuFactory<M> factory) {
        return Registry.register(
                BuiltInRegistries.MENU,
                ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, name),
                Services.PLATFORM.createPosMenuType(factory)
        );
    }

    public static void register() {}

    private ModMenuTypes() {}
}
