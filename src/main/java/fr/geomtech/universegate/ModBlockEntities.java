package fr.geomtech.universegate;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {

    public static final BlockEntityType<PortalCoreBlockEntity> PORTAL_CORE =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_core"),
                    BlockEntityType.Builder.of(PortalCoreBlockEntity::new, ModBlocks.PORTAL_CORE).build(null)
            );

    public static final BlockEntityType<PortalKeyboardBlockEntity> PORTAL_KEYBOARD =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_keyboard"),
                    BlockEntityType.Builder.of(PortalKeyboardBlockEntity::new, ModBlocks.PORTAL_KEYBOARD).build(null)
            );

    public static final BlockEntityType<PortalFrameBlockEntity> PORTAL_FRAME =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_frame"),
                    BlockEntityType.Builder.of(PortalFrameBlockEntity::new, ModBlocks.PORTAL_FRAME).build(null)
            );

    public static final BlockEntityType<PortalFieldBlockEntity> PORTAL_FIELD =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_field"),
                    BlockEntityType.Builder.of(PortalFieldBlockEntity::new, ModBlocks.PORTAL_FIELD).build(null)
            );

    public static final BlockEntityType<ChargedLightningRodBlockEntity> CHARGED_LIGHTNING_ROD =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "charged_lightning_rod"),
                    BlockEntityType.Builder.of(ChargedLightningRodBlockEntity::new, ModBlocks.CHARGED_LIGHTNING_ROD).build(null)
            );

    public static final BlockEntityType<MeteorologicalControllerBlockEntity> METEOROLOGICAL_CONTROLLER =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "meteorological_controller"),
                    BlockEntityType.Builder.of(MeteorologicalControllerBlockEntity::new, ModBlocks.METEOROLOGICAL_CONTROLLER).build(null)
            );

    public static final BlockEntityType<MeteorologicalCatalystBlockEntity> METEOROLOGICAL_CATALYST =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "meteorological_catalyst"),
                    BlockEntityType.Builder.of(MeteorologicalCatalystBlockEntity::new, ModBlocks.METEOROLOGICAL_CATALYST).build(null)
            );

    public static final BlockEntityType<LightBeamEmitterBlockEntity> LIGHT_BEAM_EMITTER =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "light_beam_emitter"),
                    BlockEntityType.Builder.of(LightBeamEmitterBlockEntity::new, ModBlocks.LIGHT_BEAM_EMITTER).build(null)
            );

    public static final BlockEntityType<ParabolaBlockEntity> PARABOLA =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "parabola_block"),
                    BlockEntityType.Builder.of(ParabolaBlockEntity::new, ModBlocks.PARABOLA_BLOCK).build(null)
            );

    public static void register() {}

    private ModBlockEntities() {}
}
