package fr.geomtech.universegate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {

    public static final BlockEntityType<PortalCoreBlockEntity> PORTAL_CORE =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_core"),
                    BlockEntityType.Builder.of(PortalCoreBlockEntity::new, ModBlocks.PORTAL_CORE).build(null)
            );

    public static final BlockEntityType<PortalKeyboardBlockEntity> PORTAL_KEYBOARD =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_keyboard"),
                    BlockEntityType.Builder.of(PortalKeyboardBlockEntity::new, ModBlocks.PORTAL_KEYBOARD).build(null)
            );

    public static final BlockEntityType<PortalNaturalKeyboardBlockEntity> PORTAL_NATURAL_KEYBOARD =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_natural_keyboard"),
                    BlockEntityType.Builder.of(PortalNaturalKeyboardBlockEntity::new, ModBlocks.PORTAL_NATURAL_KEYBOARD).build(null)
            );

    public static final BlockEntityType<PortalFrameBlockEntity> PORTAL_FRAME =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_frame"),
                    BlockEntityType.Builder.of(PortalFrameBlockEntity::new, ModBlocks.PORTAL_FRAME).build(null)
            );

    public static final BlockEntityType<PortalFieldBlockEntity> PORTAL_FIELD =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_field"),
                    BlockEntityType.Builder.of(PortalFieldBlockEntity::new, ModBlocks.PORTAL_FIELD).build(null)
            );

    public static final BlockEntityType<EnergyCondenserBlockEntity> ENERGY_CONDENSER =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "energy_condenser"),
                    BlockEntityType.Builder.of(EnergyCondenserBlockEntity::new, ModBlocks.ENERGY_CONDENSER).build(null)
            );

    public static final BlockEntityType<EnergyMonitorBlockEntity> ENERGY_MONITOR =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "energy_monitor"),
                    BlockEntityType.Builder.of(EnergyMonitorBlockEntity::new, ModBlocks.ENERGY_MONITOR).build(null)
            );

    public static final BlockEntityType<AcidNeonLampBlockEntity> ACID_NEON_LAMP =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "acid_neon_lamp"),
                    BlockEntityType.Builder.of(AcidNeonLampBlockEntity::new, ModBlocks.ACID_NEON_LAMP).build(null)
            );

    public static final BlockEntityType<AcidBeaconBlockEntity> ACID_BEACON = NeoForgeRegistryBootstrap.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "acid_beacon"),
            BlockEntityType.Builder.of(AcidBeaconBlockEntity::new, ModBlocks.ACID_BEACON).build(null)
    );

    public static final BlockEntityType<ZpcInterfaceControllerBlockEntity> ZPC_INTERFACE_CONTROLLER =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "zpc_interface_controller"),
                    BlockEntityType.Builder.of(ZpcInterfaceControllerBlockEntity::new, ModBlocks.ZPC_INTERFACE_CONTROLLER).build(null)
            );

    public static final BlockEntityType<CombustionGeneratorBlockEntity> COMBUSTION_GENERATOR =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "combustion_generator"),
                    BlockEntityType.Builder.of(CombustionGeneratorBlockEntity::new, ModBlocks.COMBUSTION_GENERATOR).build(null)
            );

    public static final BlockEntityType<ChargedLightningRodBlockEntity> CHARGED_LIGHTNING_ROD =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "charged_lightning_rod"),
                    BlockEntityType.Builder.of(ChargedLightningRodBlockEntity::new, ModBlocks.CHARGED_LIGHTNING_ROD).build(null)
            );

    public static final BlockEntityType<MeteorologicalControllerBlockEntity> METEOROLOGICAL_CONTROLLER =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "meteorological_controller"),
                    BlockEntityType.Builder.of(MeteorologicalControllerBlockEntity::new, ModBlocks.METEOROLOGICAL_CONTROLLER).build(null)
            );

    public static final BlockEntityType<MeteorologicalCatalystBlockEntity> METEOROLOGICAL_CATALYST =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "meteorological_catalyst"),
                    BlockEntityType.Builder.of(MeteorologicalCatalystBlockEntity::new, ModBlocks.METEOROLOGICAL_CATALYST).build(null)
            );

    public static final BlockEntityType<LightBeamEmitterBlockEntity> LIGHT_BEAM_EMITTER =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "light_beam_emitter"),
                    BlockEntityType.Builder.of(LightBeamEmitterBlockEntity::new, ModBlocks.LIGHT_BEAM_EMITTER).build(null)
            );

    public static final BlockEntityType<ParabolaBlockEntity> PARABOLA =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "parabola_block"),
                    BlockEntityType.Builder.of(ParabolaBlockEntity::new, ModBlocks.PARABOLA_BLOCK).build(null)
            );

    public static final BlockEntityType<RiftRefinerBlockEntity> RIFT_REFINER =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_refiner"),
                    BlockEntityType.Builder.of(RiftRefinerBlockEntity::new, ModBlocks.RIFT_REFINER).build(null)
            );

    public static final BlockEntityType<DarkEnergyGeneratorBlockEntity> DARK_ENERGY_GENERATOR =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "dark_energy_generator"),
                    BlockEntityType.Builder.of(DarkEnergyGeneratorBlockEntity::new, ModBlocks.DARK_ENERGY_GENERATOR).build(null)
            );

    public static final BlockEntityType<FluidPipeBlockEntity> FLUID_PIPE =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "fluid_pipe"),
                    BlockEntityType.Builder.of(FluidPipeBlockEntity::new, ModBlocks.FLUID_PIPE).build(null)
            );

    public static final BlockEntityType<MobClonerControllerBlockEntity> MOB_CLONER_CONTROLLER =
            NeoForgeRegistryBootstrap.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "mob_cloner_controller"),
                    BlockEntityType.Builder.of(MobClonerControllerBlockEntity::new, ModBlocks.MOB_CLONER_CONTROLLER).build(null)
            );

    public static void register() {}

    private ModBlockEntities() {}
}
