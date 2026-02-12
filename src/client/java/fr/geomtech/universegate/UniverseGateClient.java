package fr.geomtech.universegate;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

public class UniverseGateClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenuTypes.PORTAL_KEYBOARD, PortalKeyboardScreen::new);
		MenuScreens.register(ModMenuTypes.PORTAL_CORE, PortalCoreScreen::new);
		EntityModelLayerRegistry.registerModelLayer(RiftShadeModel.LAYER_LOCATION, RiftShadeModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntityTypes.RIFT_SHADE, RiftShadeRenderer::new);
		fr.geomtech.universegate.net.UniverseGateClientNetwork.registerClient();
	}
}
