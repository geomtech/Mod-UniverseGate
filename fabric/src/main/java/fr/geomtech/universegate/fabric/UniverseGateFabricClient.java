package fr.geomtech.universegate.fabric;

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
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import fr.geomtech.universegate.ModParticles;
import fr.geomtech.universegate.UniverseGateSparkParticle;
//? if >=1.21.11 {
/*import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
*///?} else {
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
//?}
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class UniverseGateFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ResourceLocation darkMatterStill = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "block/dark_matter_still");
		ResourceLocation darkMatterFlowing = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "block/dark_matter_flow");
		FluidRenderHandlerRegistry.INSTANCE.register(
				ModFluids.STILL_DARK_MATTER,
				ModFluids.FLOWING_DARK_MATTER,
				new SimpleFluidRenderHandler(darkMatterStill, darkMatterFlowing)
		);
		//? if >=1.21.11 {
		/*BlockRenderLayerMap.putFluids(ChunkSectionLayer.TRANSLUCENT, ModFluids.STILL_DARK_MATTER, ModFluids.FLOWING_DARK_MATTER);
		*///?} else {
		BlockRenderLayerMap.INSTANCE.putFluids(RenderType.translucent(), ModFluids.STILL_DARK_MATTER, ModFluids.FLOWING_DARK_MATTER);
		//?}
		ParticleFactoryRegistry.getInstance().register(ModParticles.SPARK, UniverseGateSparkParticle.Provider::new);

		MenuScreens.<AbstractContainerMenu, PortalKeyboardScreen>register(ModMenuTypes.PORTAL_KEYBOARD, PortalKeyboardScreen::new);
		MenuScreens.<AbstractContainerMenu, PortalKeyboardScreen>register(ModMenuTypes.PORTAL_NATURAL_KEYBOARD, PortalKeyboardScreen::new);
		MenuScreens.<AbstractContainerMenu, PortalKeyboardScreen>register(ModMenuTypes.PORTAL_MOBILE_KEYBOARD, PortalKeyboardScreen::new);
		MenuScreens.register(ModMenuTypes.PORTAL_CORE, PortalCoreScreen::new);
		MenuScreens.register(ModMenuTypes.METEOROLOGICAL_CONTROLLER, MeteorologicalControllerScreen::new);
		MenuScreens.register(ModMenuTypes.ENERGY_MONITOR, EnergyMonitorScreen::new);
		MenuScreens.register(ModMenuTypes.ZPC_INTERFACE_CONTROLLER, ZpcInterfaceControllerScreen::new);
		MenuScreens.register(ModMenuTypes.RIFT_REFINER, RiftRefinerScreen::new);
		MenuScreens.register(ModMenuTypes.COMBUSTION_GENERATOR, CombustionGeneratorScreen::new);
		MenuScreens.register(ModMenuTypes.DARK_ENERGY_GENERATOR, DarkEnergyGeneratorScreen::new);
		MenuScreens.register(ModMenuTypes.MOB_CLONER_CONTROLLER, MobClonerControllerScreen::new);
		//? if >=1.21.11 {
		/*BlockRenderLayerMap.putBlock(ModBlocks.PORTAL_FIELD, ChunkSectionLayer.TRANSLUCENT);
		*///?} else {
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.PORTAL_FIELD, RenderType.translucent());
		//?}
		EntityModelLayerRegistry.registerModelLayer(RiftShadeModel.LAYER_LOCATION, RiftShadeModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntityTypes.RIFT_BEAST, RiftBeastRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.RIFT_SHADE, RiftShadeRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.METEOROLOGICAL_CATALYST, MeteorologicalCatalystCrystalGlowRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.METEOROLOGICAL_CONTROLLER, MeteorologicalControllerBeamRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.PARABOLA, ParabolaDishRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.ZPC_INTERFACE_CONTROLLER, ZpcInterfaceControllerRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.FLUID_PIPE, FluidPipeBlockEntityRenderer::new);
		//? if >=1.21.11 {
		/*BlockRenderLayerMap.putBlock(ModBlocks.FLUID_PIPE, ChunkSectionLayer.TRANSLUCENT);
		BlockRenderLayerMap.putBlock(ModBlocks.RIFT_CRYSTAL_CLUSTER, ChunkSectionLayer.CUTOUT);
		*///?} else {
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.FLUID_PIPE, RenderType.translucent());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.RIFT_CRYSTAL_CLUSTER, RenderType.cutout());
		//?}
		UniverseGateClientNetwork.registerClient();
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> ModTooltips.appendTooltip(stack, lines));
		HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> DnaExtractionHudOverlay.render(guiGraphics));
	}
}
