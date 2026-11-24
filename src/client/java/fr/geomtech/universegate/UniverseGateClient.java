package fr.geomtech.universegate;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class UniverseGateClient {
	public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
		modEventBus.addListener(UniverseGateClient::registerMenuScreens);
		modEventBus.addListener(UniverseGateClient::registerLayerDefinitions);
		modEventBus.addListener(UniverseGateClient::registerRenderers);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AbstractHorseRenderer<?, ?> horseRenderer) registerHorseLaserLayer(helper, horseRenderer, context);
		});
		modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(UniverseGateClient::initialize));
	}

	private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(RiftShadeModel.LAYER_LOCATION, RiftShadeModel::createBodyLayer);
	}

	private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(ModEntityTypes.RIFT_BEAST, RiftBeastRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.RIFT_SHADE, RiftShadeRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.ACID_RIFT_BEAST, RiftBeastRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.ACID_RIFT_SHADE, RiftShadeRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.NITRO_ACID_EXPLOSIVE, ThrownItemRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.RUSTED_GOLEM, RustedGolemRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.ACID_CREEPER, AcidCreeperRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntities.ACID_BEACON, AcidBeaconRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntities.METEOROLOGICAL_CATALYST, MeteorologicalCatalystCrystalGlowRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntities.METEOROLOGICAL_CONTROLLER, MeteorologicalControllerBeamRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntities.PARABOLA, ParabolaDishRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntities.ZPC_INTERFACE_CONTROLLER, ZpcInterfaceControllerRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntities.FLUID_PIPE, FluidPipeBlockEntityRenderer::new);
	}

	private static void registerMenuScreens(RegisterMenuScreensEvent event) {
		event.<PortalKeyboardMenu, PortalKeyboardScreen<PortalKeyboardMenu>>register(
				ModMenuTypes.PORTAL_KEYBOARD, PortalKeyboardScreen<PortalKeyboardMenu>::new);
		event.<PortalNaturalKeyboardMenu, PortalKeyboardScreen<PortalNaturalKeyboardMenu>>register(
				ModMenuTypes.PORTAL_NATURAL_KEYBOARD, PortalKeyboardScreen<PortalNaturalKeyboardMenu>::new);
		event.<PortalMobileKeyboardMenu, PortalKeyboardScreen<PortalMobileKeyboardMenu>>register(
				ModMenuTypes.PORTAL_MOBILE_KEYBOARD, PortalKeyboardScreen<PortalMobileKeyboardMenu>::new);
		event.register(ModMenuTypes.PORTAL_CORE, PortalCoreScreen::new);
		event.register(ModMenuTypes.METEOROLOGICAL_CONTROLLER, MeteorologicalControllerScreen::new);
		event.register(ModMenuTypes.ENERGY_MONITOR, EnergyMonitorScreen::new);
		event.register(ModMenuTypes.ZPC_INTERFACE_CONTROLLER, ZpcInterfaceControllerScreen::new);
		event.register(ModMenuTypes.RIFT_REFINER, RiftRefinerScreen::new);
		event.register(ModMenuTypes.COMBUSTION_GENERATOR, CombustionGeneratorScreen::new);
		event.register(ModMenuTypes.DARK_ENERGY_GENERATOR, DarkEnergyGeneratorScreen::new);
		event.register(ModMenuTypes.MOB_CLONER_CONTROLLER, MobClonerControllerScreen::new);
	}

	private static void initialize() {
		ResourceLocation darkMatterStill = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "block/dark_matter_still");
		ResourceLocation darkMatterFlowing = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "block/dark_matter_flow");
		FluidRenderHandlerRegistry.INSTANCE.register(
				ModFluids.STILL_DARK_MATTER,
				ModFluids.FLOWING_DARK_MATTER,
				new SimpleFluidRenderHandler(darkMatterStill, darkMatterFlowing)
		);
		BlockRenderLayerMap.INSTANCE.putFluids(RenderType.translucent(), ModFluids.STILL_DARK_MATTER, ModFluids.FLOWING_DARK_MATTER);
		ResourceLocation acidRainStill = ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");
		ResourceLocation acidRainFlowing = ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_flow");
		FluidRenderHandlerRegistry.INSTANCE.register(
				ModFluids.STILL_ACID_RAIN,
				ModFluids.FLOWING_ACID_RAIN,
				new SimpleFluidRenderHandler(acidRainStill, acidRainFlowing, 0xFFD8FF66)
		);
		BlockRenderLayerMap.INSTANCE.putFluids(RenderType.translucent(), ModFluids.STILL_ACID_RAIN, ModFluids.FLOWING_ACID_RAIN);

		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.PORTAL_FIELD, RenderType.translucent());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ACID_BEACON, RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.FLUID_PIPE, RenderType.translucent());
		fr.geomtech.universegate.net.UniverseGateClientNetwork.registerClient();
		ModTooltips.register();
		DnaExtractionHudOverlay.register();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void registerHorseLaserLayer(LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper helper,
			AbstractHorseRenderer renderer, net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
		helper.register(new HorseLaserLayer(renderer, context.getItemRenderer()));
	}
}
