package fr.geomtech.universegate;

import fr.geomtech.universegate.platform.PlatformHelper;
import fr.geomtech.universegate.platform.Services;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {

    public static final BlockEntityType<PortalCoreBlockEntity> PORTAL_CORE =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_core"),
                    type(PortalCoreBlockEntity::new, ModBlocks.PORTAL_CORE)
            );

    public static final BlockEntityType<PortalKeyboardBlockEntity> PORTAL_KEYBOARD =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_keyboard"),
                    type(PortalKeyboardBlockEntity::new, ModBlocks.PORTAL_KEYBOARD)
            );

    public static final BlockEntityType<PortalNaturalKeyboardBlockEntity> PORTAL_NATURAL_KEYBOARD =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_natural_keyboard"),
                    type(PortalNaturalKeyboardBlockEntity::new, ModBlocks.PORTAL_NATURAL_KEYBOARD)
            );

    public static final BlockEntityType<PortalFrameBlockEntity> PORTAL_FRAME =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_frame"),
                    type(PortalFrameBlockEntity::new, ModBlocks.PORTAL_FRAME)
            );

    public static final BlockEntityType<PortalFieldBlockEntity> PORTAL_FIELD =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_field"),
                    type(PortalFieldBlockEntity::new, ModBlocks.PORTAL_FIELD)
            );

    public static final BlockEntityType<EnergyCondenserBlockEntity> ENERGY_CONDENSER =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "energy_condenser"),
                    type(EnergyCondenserBlockEntity::new, ModBlocks.ENERGY_CONDENSER)
            );

    public static final BlockEntityType<EnergyMonitorBlockEntity> ENERGY_MONITOR =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "energy_monitor"),
                    type(EnergyMonitorBlockEntity::new, ModBlocks.ENERGY_MONITOR)
            );

    public static final BlockEntityType<ZpcInterfaceControllerBlockEntity> ZPC_INTERFACE_CONTROLLER =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "zpc_interface_controller"),
                    type(ZpcInterfaceControllerBlockEntity::new, ModBlocks.ZPC_INTERFACE_CONTROLLER)
            );

    public static final BlockEntityType<CombustionGeneratorBlockEntity> COMBUSTION_GENERATOR =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "combustion_generator"),
                    type(CombustionGeneratorBlockEntity::new, ModBlocks.COMBUSTION_GENERATOR)
            );

    public static final BlockEntityType<ChargedLightningRodBlockEntity> CHARGED_LIGHTNING_ROD =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "charged_lightning_rod"),
                    type(ChargedLightningRodBlockEntity::new, ModBlocks.CHARGED_LIGHTNING_ROD)
            );

    public static final BlockEntityType<LightningAntennaBlockEntity> LIGHTNING_ANTENNA =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "lightning_antenna"),
                    type(LightningAntennaBlockEntity::new, ModBlocks.LIGHTNING_ANTENNA)
            );

    public static final BlockEntityType<MeteorologicalControllerBlockEntity> METEOROLOGICAL_CONTROLLER =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "meteorological_controller"),
                    type(MeteorologicalControllerBlockEntity::new, ModBlocks.METEOROLOGICAL_CONTROLLER)
            );

    public static final BlockEntityType<MeteorologicalCatalystBlockEntity> METEOROLOGICAL_CATALYST =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "meteorological_catalyst"),
                    type(MeteorologicalCatalystBlockEntity::new, ModBlocks.METEOROLOGICAL_CATALYST)
            );

    public static final BlockEntityType<LightBeamEmitterBlockEntity> LIGHT_BEAM_EMITTER =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "light_beam_emitter"),
                    type(LightBeamEmitterBlockEntity::new, ModBlocks.LIGHT_BEAM_EMITTER)
            );

    public static final BlockEntityType<ParabolaBlockEntity> PARABOLA =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "parabola_block"),
                    type(ParabolaBlockEntity::new, ModBlocks.PARABOLA_BLOCK)
            );

    public static final BlockEntityType<RiftRefinerBlockEntity> RIFT_REFINER =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_refiner"),
                    type(RiftRefinerBlockEntity::new, ModBlocks.RIFT_REFINER)
            );

    public static final BlockEntityType<DarkEnergyGeneratorBlockEntity> DARK_ENERGY_GENERATOR =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "dark_energy_generator"),
                    type(DarkEnergyGeneratorBlockEntity::new, ModBlocks.DARK_ENERGY_GENERATOR)
            );

    public static final BlockEntityType<FluidPipeBlockEntity> FLUID_PIPE =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "fluid_pipe"),
                    type(FluidPipeBlockEntity::new, ModBlocks.FLUID_PIPE)
            );

    public static final BlockEntityType<MobClonerControllerBlockEntity> MOB_CLONER_CONTROLLER =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "mob_cloner_controller"),
                    type(MobClonerControllerBlockEntity::new, ModBlocks.MOB_CLONER_CONTROLLER)
            );

    //? if >=1.21.11 {
    /*private static <T extends BlockEntity> BlockEntityType<T> type(PlatformHelper.BlockEntityFactory<T> factory, Block block) {
        return Services.PLATFORM.createBlockEntityType(factory, block);
    }
    *///?} else {
    private static <T extends BlockEntity> BlockEntityType<T> type(BlockEntityType.BlockEntitySupplier<T> factory, Block block) {
        return BlockEntityType.Builder.of(factory, block).build(null);
    }
    //?}

    public static void register() {}

    private ModBlockEntities() {}
}
