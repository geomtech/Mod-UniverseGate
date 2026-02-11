package fr.geomtech.universegate;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class UniverseGateClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenuTypes.PORTAL_KEYBOARD, PortalKeyboardScreen::new);
		MenuScreens.register(ModMenuTypes.PORTAL_CORE, PortalCoreScreen::new);
		fr.geomtech.universegate.net.UniverseGateClientNetwork.registerClient();
	}
}
