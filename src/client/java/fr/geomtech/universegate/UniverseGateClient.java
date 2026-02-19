package fr.geomtech.universegate;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class UniverseGateClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ResourceLocation darkMatterStill = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "block/dark_matter_still");
		ResourceLocation darkMatterFlowing = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "block/dark_matter_flow");
		FluidRenderHandlerRegistry.INSTANCE.register(
				ModFluids.STILL_DARK_MATTER,
				ModFluids.FLOWING_DARK_MATTER,
				new SimpleFluidRenderHandler(darkMatterStill, darkMatterFlowing)
		);
		BlockRenderLayerMap.INSTANCE.putFluids(RenderType.translucent(), ModFluids.STILL_DARK_MATTER, ModFluids.FLOWING_DARK_MATTER);

		MenuScreens.<AbstractContainerMenu, PortalKeyboardScreen>register(ModMenuTypes.PORTAL_KEYBOARD, PortalKeyboardScreen::new);
		MenuScreens.<AbstractContainerMenu, PortalKeyboardScreen>register(ModMenuTypes.PORTAL_NATURAL_KEYBOARD, PortalKeyboardScreen::new);
		MenuScreens.register(ModMenuTypes.PORTAL_CORE, PortalCoreScreen::new);
		MenuScreens.register(ModMenuTypes.METEOROLOGICAL_CONTROLLER, MeteorologicalControllerScreen::new);
		MenuScreens.register(ModMenuTypes.ENERGY_MONITOR, EnergyMonitorScreen::new);
		MenuScreens.register(ModMenuTypes.RIFT_REFINER, RiftRefinerScreen::new);
		MenuScreens.register(ModMenuTypes.COMBUSTION_GENERATOR, CombustionGeneratorScreen::new);
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.PORTAL_FIELD, RenderType.translucent());
		EntityModelLayerRegistry.registerModelLayer(RiftShadeModel.LAYER_LOCATION, RiftShadeModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntityTypes.RIFT_BEAST, RiftBeastRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.RIFT_SHADE, RiftShadeRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.PORTAL_CORE, PortalCoreGlowRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.PORTAL_FRAME, PortalFrameGlowRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.METEOROLOGICAL_CATALYST, MeteorologicalCatalystCrystalGlowRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.METEOROLOGICAL_CONTROLLER, MeteorologicalControllerBeamRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.PARABOLA, ParabolaDishRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.FLUID_PIPE, FluidPipeBlockEntityRenderer::new);
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.FLUID_PIPE, RenderType.translucent());
		fr.geomtech.universegate.net.UniverseGateClientNetwork.registerClient();
		ModTooltips.register();
	}
}
