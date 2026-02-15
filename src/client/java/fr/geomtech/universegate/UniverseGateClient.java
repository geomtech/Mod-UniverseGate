package fr.geomtech.universegate;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.gui.screens.MenuScreens;

public class UniverseGateClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenuTypes.PORTAL_KEYBOARD, PortalKeyboardScreen::new);
		MenuScreens.register(ModMenuTypes.PORTAL_CORE, PortalCoreScreen::new);
		MenuScreens.register(ModMenuTypes.METEOROLOGICAL_CONTROLLER, MeteorologicalControllerScreen::new);
		MenuScreens.register(ModMenuTypes.ENERGY_MONITOR, EnergyMonitorScreen::new);
		MenuScreens.register(ModMenuTypes.RIFT_REFINER, RiftRefinerScreen::new);
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.PORTAL_FIELD, RenderType.translucent());
		EntityModelLayerRegistry.registerModelLayer(RiftShadeModel.LAYER_LOCATION, RiftShadeModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntityTypes.RIFT_BEAST, RiftBeastRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.RIFT_SHADE, RiftShadeRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.PORTAL_FRAME, PortalFrameGlowRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.METEOROLOGICAL_CATALYST, MeteorologicalCatalystCrystalGlowRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.METEOROLOGICAL_CONTROLLER, MeteorologicalControllerBeamRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.PARABOLA, ParabolaDishRenderer::new);
		fr.geomtech.universegate.net.UniverseGateClientNetwork.registerClient();
	}
}
