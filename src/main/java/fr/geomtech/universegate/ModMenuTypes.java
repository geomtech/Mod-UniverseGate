package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;

public final class ModMenuTypes {
    public static final MenuType<PortalKeyboardMenu> PORTAL_KEYBOARD = register("portal_keyboard",
            (id, inv, buf) -> new PortalKeyboardMenu(id, inv, BlockPos.STREAM_CODEC.decode(buf)));
    public static final MenuType<PortalNaturalKeyboardMenu> PORTAL_NATURAL_KEYBOARD = register("portal_natural_keyboard",
            (id, inv, buf) -> new PortalNaturalKeyboardMenu(id, inv, BlockPos.STREAM_CODEC.decode(buf)));
    public static final MenuType<PortalMobileKeyboardMenu> PORTAL_MOBILE_KEYBOARD = register("portal_mobile_keyboard",
            (id, inv, buf) -> new PortalMobileKeyboardMenu(id, inv, BlockPos.STREAM_CODEC.decode(buf)));
    public static final MenuType<PortalCoreMenu> PORTAL_CORE = register("portal_core",
            (id, inv, buf) -> new PortalCoreMenu(id, inv, BlockPos.STREAM_CODEC.decode(buf)));
    public static final MenuType<MeteorologicalControllerMenu> METEOROLOGICAL_CONTROLLER = register("meteorological_controller",
            (id, inv, buf) -> new MeteorologicalControllerMenu(id, inv, BlockPos.STREAM_CODEC.decode(buf)));
    public static final MenuType<EnergyMonitorMenu> ENERGY_MONITOR = register("energy_monitor",
            (id, inv, buf) -> new EnergyMonitorMenu(id, inv, BlockPos.STREAM_CODEC.decode(buf)));
    public static final MenuType<ZpcInterfaceControllerMenu> ZPC_INTERFACE_CONTROLLER = register("zpc_interface_controller",
            (id, inv, buf) -> new ZpcInterfaceControllerMenu(id, inv, BlockPos.STREAM_CODEC.decode(buf)));
    public static final MenuType<RiftRefinerMenu> RIFT_REFINER = register("rift_refiner",
            (id, inv, buf) -> new RiftRefinerMenu(id, inv, BlockPos.STREAM_CODEC.decode(buf)));
    public static final MenuType<CombustionGeneratorMenu> COMBUSTION_GENERATOR = register("combustion_generator",
            (id, inv, buf) -> new CombustionGeneratorMenu(id, inv, BlockPos.STREAM_CODEC.decode(buf)));
    public static final MenuType<DarkEnergyGeneratorMenu> DARK_ENERGY_GENERATOR = register("dark_energy_generator",
            (id, inv, buf) -> new DarkEnergyGeneratorMenu(id, inv, BlockPos.STREAM_CODEC.decode(buf)));
    public static final MenuType<MobClonerControllerMenu> MOB_CLONER_CONTROLLER = register("mob_cloner_controller",
            (id, inv, buf) -> new MobClonerControllerMenu(id, inv, BlockPos.STREAM_CODEC.decode(buf)));

    @SuppressWarnings("unchecked")
    private static <T extends AbstractContainerMenu> MenuType<T> register(String name, IContainerFactory<T> factory) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, name);
        return (MenuType<T>) NeoForgeRegistryBootstrap.register(BuiltInRegistries.MENU, id, IMenuTypeExtension.create(factory));
    }

    public static void register() {}

    private ModMenuTypes() {}
}
