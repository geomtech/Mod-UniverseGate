package fr.geomtech.universegate.neoforge;

import fr.geomtech.universegate.CombustionGeneratorScreen;
import fr.geomtech.universegate.DarkEnergyGeneratorScreen;
import fr.geomtech.universegate.DnaExtractionHudOverlay;
import fr.geomtech.universegate.EnergyMonitorScreen;
import fr.geomtech.universegate.FluidPipeBlockEntityRenderer;
import fr.geomtech.universegate.MeteorologicalCatalystCrystalGlowRenderer;
import fr.geomtech.universegate.MeteorologicalControllerBeamRenderer;
import fr.geomtech.universegate.MeteorologicalControllerScreen;
import fr.geomtech.universegate.MobClonerControllerScreen;
import fr.geomtech.universegate.ModBlockEntities;
import fr.geomtech.universegate.ModBlocks;
import fr.geomtech.universegate.ModEntityTypes;
import fr.geomtech.universegate.ModFluids;
import fr.geomtech.universegate.ModMenuTypes;
import fr.geomtech.universegate.ModTooltips;
import fr.geomtech.universegate.ParabolaDishRenderer;
import fr.geomtech.universegate.PortalCoreScreen;
import fr.geomtech.universegate.PortalKeyboardScreen;
import fr.geomtech.universegate.RiftBeastRenderer;
import fr.geomtech.universegate.RiftRefinerScreen;
import fr.geomtech.universegate.RiftShadeModel;
import fr.geomtech.universegate.RiftShadeRenderer;
import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.ZpcInterfaceControllerRenderer;
import fr.geomtech.universegate.ZpcInterfaceControllerScreen;
import fr.geomtech.universegate.net.UniverseGateClientNetwork;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
//? if >=1.21.11 {
/*import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
*///?}
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import fr.geomtech.universegate.ModParticles;
import fr.geomtech.universegate.UniverseGateSparkParticle;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod(value = UniverseGate.MOD_ID, dist = Dist.CLIENT)
public final class UniverseGateNeoForgeClient {

    private static final ResourceLocation DARK_MATTER_STILL = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "block/dark_matter_still");
    private static final ResourceLocation DARK_MATTER_FLOWING = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "block/dark_matter_flow");
    private static final ResourceLocation DNA_EXTRACTION_LAYER = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "dna_extraction");

    public UniverseGateNeoForgeClient(IEventBus modBus) {
        UniverseGateClientNetwork.registerClient();

        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            //? if >=1.21.11 {
            /*ItemBlockRenderTypes.setRenderLayer(ModFluids.STILL_DARK_MATTER, ChunkSectionLayer.TRANSLUCENT);
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_DARK_MATTER, ChunkSectionLayer.TRANSLUCENT);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.PORTAL_FIELD, ChunkSectionLayer.TRANSLUCENT);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.FLUID_PIPE, ChunkSectionLayer.TRANSLUCENT);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.RIFT_CRYSTAL_CLUSTER, ChunkSectionLayer.CUTOUT);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.ELECTRIC_WIRE, ChunkSectionLayer.CUTOUT);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CROCODILE_CLIP, ChunkSectionLayer.CUTOUT);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.LIGHTNING_ANTENNA, ChunkSectionLayer.CUTOUT);
            *///?} else {
            ItemBlockRenderTypes.setRenderLayer(ModFluids.STILL_DARK_MATTER, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_DARK_MATTER, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.PORTAL_FIELD, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.FLUID_PIPE, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.RIFT_CRYSTAL_CLUSTER, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.ELECTRIC_WIRE, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CROCODILE_CLIP, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.LIGHTNING_ANTENNA, RenderType.cutout());
            //?}
        }));

        modBus.addListener(RegisterParticleProvidersEvent.class, event ->
                event.registerSpriteSet(ModParticles.SPARK, UniverseGateSparkParticle.Provider::new));

        modBus.addListener(RegisterClientExtensionsEvent.class, event -> event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return DARK_MATTER_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return DARK_MATTER_FLOWING;
            }
        }, NeoForgeFluids.DARK_MATTER_TYPE));

        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.<AbstractContainerMenu, PortalKeyboardScreen>register(ModMenuTypes.PORTAL_KEYBOARD, PortalKeyboardScreen::new);
            event.<AbstractContainerMenu, PortalKeyboardScreen>register(ModMenuTypes.PORTAL_NATURAL_KEYBOARD, PortalKeyboardScreen::new);
            event.<AbstractContainerMenu, PortalKeyboardScreen>register(ModMenuTypes.PORTAL_MOBILE_KEYBOARD, PortalKeyboardScreen::new);
            event.register(ModMenuTypes.PORTAL_CORE, PortalCoreScreen::new);
            event.register(ModMenuTypes.METEOROLOGICAL_CONTROLLER, MeteorologicalControllerScreen::new);
            event.register(ModMenuTypes.ENERGY_MONITOR, EnergyMonitorScreen::new);
            event.register(ModMenuTypes.ZPC_INTERFACE_CONTROLLER, ZpcInterfaceControllerScreen::new);
            event.register(ModMenuTypes.RIFT_REFINER, RiftRefinerScreen::new);
            event.register(ModMenuTypes.COMBUSTION_GENERATOR, CombustionGeneratorScreen::new);
            event.register(ModMenuTypes.DARK_ENERGY_GENERATOR, DarkEnergyGeneratorScreen::new);
            event.register(ModMenuTypes.MOB_CLONER_CONTROLLER, MobClonerControllerScreen::new);
        });

        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class, event ->
                event.registerLayerDefinition(RiftShadeModel.LAYER_LOCATION, RiftShadeModel::createBodyLayer));

        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(ModEntityTypes.RIFT_BEAST, RiftBeastRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.RIFT_SHADE, RiftShadeRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.METEOROLOGICAL_CATALYST, MeteorologicalCatalystCrystalGlowRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.METEOROLOGICAL_CONTROLLER, MeteorologicalControllerBeamRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.PARABOLA, ParabolaDishRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.ZPC_INTERFACE_CONTROLLER, ZpcInterfaceControllerRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.FLUID_PIPE, FluidPipeBlockEntityRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.PORTAL_CORE, fr.geomtech.universegate.PortalGateRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.CROCODILE_CLIP, fr.geomtech.universegate.CableTerminalRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.ELECTRIC_WIRE, fr.geomtech.universegate.ElectricWireRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.LIGHTNING_ANTENNA, fr.geomtech.universegate.CableTerminalRenderer::new);
        });

        modBus.addListener(RegisterGuiLayersEvent.class, event ->
                event.registerAboveAll(DNA_EXTRACTION_LAYER, (guiGraphics, deltaTracker) -> DnaExtractionHudOverlay.render(guiGraphics)));

        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, event ->
                ModTooltips.appendTooltip(event.getItemStack(), event.getToolTip()));
    }
}
