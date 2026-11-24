package fr.geomtech.universegate.seamless.client;

import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.mixin.client.LevelRendererAccessor;
import fr.geomtech.universegate.seamless.net.SeamlessNetwork;
import fr.geomtech.universegate.seamless.net.SeamlessSwitchHintPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Coordinates loading-screen-free cross-dimension switches on the client when receiving a
 * {@code ClientboundRespawnPacket} preceded by a {@link SeamlessSwitchHintPayload}.
 */
public final class SeamlessSwitchCoordinator {

    public static final long HINT_TTL_MILLIS = 8_000L;

    private static volatile @Nullable PendingSwitch pendingSwitch;
    private static volatile boolean suppressingLoadingScreen = false;
    private static volatile @Nullable ClientRemoteWorldManager.RemoteWorldEntry promotedEntryForSwap;
    private static volatile boolean initialized = false;

    private SeamlessSwitchCoordinator() {}

    public static synchronized void initClient() {
        if (initialized) {
            return;
        }
        initialized = true;
        SeamlessNetwork.setSwitchHintHandler(SeamlessSwitchCoordinator::onSwitchHintReceived);
    }

    public static void onSwitchHintReceived(SeamlessSwitchHintPayload payload) {
        Objects.requireNonNull(payload, "payload");
        pendingSwitch = new PendingSwitch(payload, System.currentTimeMillis() + HINT_TTL_MILLIS);
        UniverseGate.LOGGER.info("[SeamlessPortal] Received switch hint -> dim={} target={}",
                payload.targetDim().location(), payload.targetCorePos());
    }

    /**
     * Checks if an unexpired switch hint matches {@code targetDim}. Pure timing logic is exposed
     * via {@link #consumeMatchingHint(ResourceKey, long)} for deterministic unit tests.
     */
    public static @Nullable SeamlessSwitchHintPayload consumeMatchingHint(ResourceKey<Level> targetDim) {
        return consumeMatchingHint(targetDim, System.currentTimeMillis());
    }

    public static @Nullable SeamlessSwitchHintPayload consumeMatchingHint(ResourceKey<Level> targetDim, long nowMillis) {
        PendingSwitch current = pendingSwitch;
        if (current == null) {
            return null;
        }
        if (nowMillis > current.expiresAtMillis()) {
            pendingSwitch = null;
            return null;
        }
        if (!current.payload().targetDim().equals(targetDim)) {
            return null;
        }
        pendingSwitch = null;
        return current.payload();
    }

    public static boolean hasPendingHintFor(ResourceKey<Level> targetDim) {
        PendingSwitch current = pendingSwitch;
        return current != null
                && System.currentTimeMillis() <= current.expiresAtMillis()
                && current.payload().targetDim().equals(targetDim);
    }

    public static boolean isSuppressingLoadingScreen() {
        return suppressingLoadingScreen;
    }

    public static void setSuppressingLoadingScreen(boolean suppressing) {
        suppressingLoadingScreen = suppressing;
    }

    /**
     * Prepares an atomic promotion of a preloaded remote {@link ClientLevel} (and its compiled
     * {@link RemoteRenderWorldState}) right before vanilla's {@code handleRespawn} switches levels.
     */
    public static @Nullable ClientLevel preparePromotedLevel(ResourceKey<Level> targetDim,
                                                             @Nullable ClientLevel previousActiveLevel,
                                                             SeamlessSwitchHintPayload hint) {
        ClientRemoteWorldManager.RemoteWorldEntry promoted = ClientRemoteWorldManager.getInstance()
                .promoteRemoteWorld(targetDim, previousActiveLevel, hint.targetCorePos());
        if (promoted == null) {
            return null;
        }
        promotedEntryForSwap = promoted;
        return promoted.level;
    }

    /**
     * Installs the promoted world's precompiled {@link net.minecraft.client.renderer.ViewArea} and
     * {@link net.minecraft.client.renderer.chunk.SectionRenderDispatcher} into {@link LevelRenderer}
     * without calling {@link LevelRenderer#setLevel(ClientLevel)} / {@link LevelRenderer#allChanged()}.
     */
    public static boolean trySwapLevelRendererWithoutRebuild(Minecraft mc, ClientLevel newLevel) {
        ClientRemoteWorldManager.RemoteWorldEntry promoted = promotedEntryForSwap;
        promotedEntryForSwap = null;
        if (promoted == null || promoted.level != newLevel || mc.levelRenderer == null) {
            return false;
        }
        RemoteRenderWorldState renderState = promoted.renderState;
        if (renderState == null || renderState.isDisposed()
                || renderState.getViewArea() == null
                || renderState.getSectionRenderDispatcher() == null) {
            return false;
        }
        LevelRendererAccessor lr = (LevelRendererAccessor) mc.levelRenderer;
        lr.universegate$setLevel(newLevel);
        lr.universegate$setViewArea(renderState.getViewArea());
        lr.universegate$setSectionRenderDispatcher(renderState.getSectionRenderDispatcher());
        return true;
    }

    /**
     * Synchronously precompiles the closest chunk sections around {@code destinationCorePos}
     * immediately after a seamless dimension swap so frame 0 renders with zero blank terrain flash
     * while keeping Minecraft's full render distance and {@code SectionOcclusionGraph} intact.
     */
    public static void precompileNearbySections(Minecraft mc, net.minecraft.core.BlockPos destinationCorePos) {
        ClientRemoteWorldManager.RemoteWorldEntry promoted = promotedEntryForSwap;
        promotedEntryForSwap = null;
        if (promoted != null && promoted.renderState != null) {
            promoted.renderState.dispose();
            promoted.renderState = null;
        }
        //? if <1.21.11 {
        if (mc.level == null || mc.levelRenderer == null || destinationCorePos == null) {
            return;
        }
        LevelRendererAccessor lr = (LevelRendererAccessor) mc.levelRenderer;
        var viewArea = lr.universegate$getViewArea();
        var dispatcher = lr.universegate$getSectionRenderDispatcher();
        if (viewArea == null || dispatcher == null || viewArea.sections == null) {
            return;
        }
        net.minecraft.world.phys.Vec3 center = new net.minecraft.world.phys.Vec3(
                destinationCorePos.getX() + 0.5D,
                destinationCorePos.getY() + 3.0D,
                destinationCorePos.getZ() + 0.5D
        );
        viewArea.repositionCamera(center.x, center.z);
        dispatcher.setCamera(center);
        net.minecraft.client.renderer.chunk.RenderRegionCache regionCache =
                new net.minecraft.client.renderer.chunk.RenderRegionCache();
        int built = 0;
        for (var section : viewArea.sections) {
            if (section == null || built >= 16) {
                continue;
            }
            net.minecraft.core.BlockPos origin = section.getOrigin();
            if (origin.distSqr(destinationCorePos) <= 32.0D * 32.0D && section.hasAllNeighbors()) {
                dispatcher.rebuildSectionSync(section, regionCache);
                built++;
            }
        }
        dispatcher.uploadAllPendingUploads();
        //?}
    }

    public static void clear() {
        pendingSwitch = null;
        suppressingLoadingScreen = false;
        promotedEntryForSwap = null;
    }

    private record PendingSwitch(SeamlessSwitchHintPayload payload, long expiresAtMillis) {}
}
