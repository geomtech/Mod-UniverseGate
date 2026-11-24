package fr.geomtech.universegate.mixin.client;

import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.seamless.client.SeamlessSwitchCoordinator;
import fr.geomtech.universegate.seamless.net.SeamlessSwitchHintPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.11 {
/*import net.minecraft.client.gui.screens.LevelLoadingScreen;
*///?} else {
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
//?}

/**
 * Intercepts {@link ClientPacketListener#handleRespawn} and {@code startWaitingForNewLevel}
 * during seamless cross-dimension portal crossings to suppress loading screens and pre-seed the
 * fresh vanilla {@link ClientLevel} with cached chunks from the remote preview.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerRespawnMixin {

    @Unique
    private @Nullable SeamlessSwitchHintPayload universegate$activeHint;

    @Unique
    private java.util.List<fr.geomtech.universegate.seamless.net.RemoteChunkDataPayload> universegate$preloadedChunks = java.util.List.of();

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void universegate$beginSeamlessRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        // handleRespawn is first invoked on the Netty IO thread before PacketUtils.ensureRunningOnSameThread
        // reschedules it on the render thread; never consume the switch hint off the main client thread!
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        ResourceKey<Level> targetDim = packet.commonPlayerSpawnInfo().dimension();
        SeamlessSwitchHintPayload hint = SeamlessSwitchCoordinator.consumeMatchingHint(targetDim);
        if (hint == null) {
            this.universegate$activeHint = null;
            this.universegate$preloadedChunks = java.util.List.of();
            return;
        }
        this.universegate$activeHint = hint;
        this.universegate$preloadedChunks = fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager
                .getInstance().takePreloadedChunksAndDispose(targetDim);
        SeamlessSwitchCoordinator.setSuppressingLoadingScreen(true);
        UniverseGate.LOGGER.info("[SeamlessPortal] Seamless dimension switch to {} (preloadedChunks={}, loadingScreenSuppressed=true)",
                targetDim.location(), this.universegate$preloadedChunks.size());
    }

    //? if >=1.21.11 {
    /*@Inject(method = "startWaitingForNewLevel", at = @At("HEAD"), cancellable = true)
    private void universegate$suppressWaitingScreen(LocalPlayer player,
                                                    ClientLevel newLevel,
                                                    LevelLoadingScreen.Reason reason,
                                                    CallbackInfo ci) {
        if (SeamlessSwitchCoordinator.isSuppressingLoadingScreen()) {
            ci.cancel();
        }
    }
    *///?} else {
    @Inject(method = "startWaitingForNewLevel", at = @At("HEAD"), cancellable = true)
    private void universegate$suppressWaitingScreen(LocalPlayer player,
                                                    ClientLevel newLevel,
                                                    ReceivingLevelScreen.Reason reason,
                                                    CallbackInfo ci) {
        if (SeamlessSwitchCoordinator.isSuppressingLoadingScreen()) {
            ci.cancel();
        }
    }
    //?}

    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void universegate$finishSeamlessRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        SeamlessSwitchHintPayload hint = this.universegate$activeHint;
        java.util.List<fr.geomtech.universegate.seamless.net.RemoteChunkDataPayload> preloadedChunks =
                this.universegate$preloadedChunks;
        this.universegate$activeHint = null;
        this.universegate$preloadedChunks = java.util.List.of();
        SeamlessSwitchCoordinator.setSuppressingLoadingScreen(false);

        if (hint != null) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null) {
                // Snap both current and previous-tick pos/rotations to prevent 1-tick camera whip/lerp
                player.setPosRaw(hint.x(), hint.y(), hint.z());
                player.xo = hint.x();
                player.yo = hint.y();
                player.zo = hint.z();
                player.xOld = hint.x();
                player.yOld = hint.y();
                player.zOld = hint.z();
                player.setYRot(hint.yRot());
                player.yRotO = hint.yRot();
                player.setYHeadRot(hint.yRot());
                player.yHeadRotO = hint.yRot();
                player.yBodyRot = hint.yRot();
                player.yBodyRotO = hint.yRot();
                player.setXRot(hint.xRot());
                player.xRotO = hint.xRot();
                player.setDeltaMovement(hint.velocity());
            }
            // Pre-seed the authentic vanilla ClientLevel with cached chunks + light from our remote
            // preview, then synchronously compile the nearest sections so frame 0 renders seamlessly!
            if (mc.level != null && !preloadedChunks.isEmpty()) {
                fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager.getInstance()
                        .preseedActiveLevel((ClientPacketListener) (Object) this, mc.level, hint.targetCorePos(), preloadedChunks);
            }
            SeamlessSwitchCoordinator.precompileNearbySections(mc, hint.targetCorePos());
        }
    }
}
