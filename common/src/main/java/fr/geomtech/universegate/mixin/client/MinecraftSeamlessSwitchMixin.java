package fr.geomtech.universegate.mixin.client;

import fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager;
import fr.geomtech.universegate.seamless.client.PortalStencilRenderer;
import fr.geomtech.universegate.seamless.client.SeamlessSwitchCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses {@code ReceivingLevelScreen} / {@code LevelLoadingScreen} during seamless
 * cross-dimension portal crossings (covering both Fabric's 3-arg and NeoForge's 5-arg
 * {@code startWaitingForNewLevel}) and cleans up all remote worlds & GPU buffers on disconnect.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftSeamlessSwitchMixin {

    //? if <1.21.11 {
    @Shadow
    private void updateScreenAndTick(Screen screen) {}

    @Redirect(
            method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/gui/screens/ReceivingLevelScreen$Reason;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;updateScreenAndTick(Lnet/minecraft/client/gui/screens/Screen;)V"
            )
    )
    private void universegate$suppressReceivingScreen(Minecraft self, Screen screen) {
        if (!SeamlessSwitchCoordinator.isSuppressingLoadingScreen()) {
            this.updateScreenAndTick(screen);
        }
    }
    //?}

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void universegate$suppressLoadingScreenOnSetScreen(@Nullable Screen screen, CallbackInfo ci) {
        if (!SeamlessSwitchCoordinator.isSuppressingLoadingScreen()) {
            return;
        }
        //? if >=1.21.11 {
        /*if (screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) {
            ci.cancel();
        }
        *///?} else {
        if (screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) {
            ci.cancel();
        }
        //?}
    }

    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void universegate$onClearClientLevel(Screen screen, CallbackInfo ci) {
        ClientRemoteWorldManager.getInstance().onClientDisconnect();
        SeamlessSwitchCoordinator.clear();
        PortalStencilRenderer.onClientDisconnect();
    }
}
