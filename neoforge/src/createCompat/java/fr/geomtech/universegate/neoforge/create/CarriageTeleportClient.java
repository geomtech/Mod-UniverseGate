package fr.geomtech.universegate.neoforge.create;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.foundation.utility.ControlsUtil;
import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.neoforge.create.mixin.ControlsHandlerAccessor;
import fr.geomtech.universegate.platform.ClientServices;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.lang.ref.WeakReference;
import java.util.UUID;

public final class CarriageTeleportClient {
    private static final long CONTROL_GRACE_TICKS = 100;
    private static PendingControls pendingControls;

    private CarriageTeleportClient() {}

    public static void register() {
        ClientServices.CLIENT.registerClientReceiver(CarriageTeleportPayload.TYPE, payload ->
                Minecraft.getInstance().execute(() -> apply(payload)));
    }

    private static void apply(CarriageTeleportPayload payload) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        payload.controls()
                .filter(controls -> player != null && controls.playerId().equals(player.getUUID()) && !wantsToLeave())
                .ifPresent(controls -> pendingControls = new PendingControls(payload.entityId(), payload.trainId(),
                        controls.localPos(), minecraft.level == null ? 0 : minecraft.level.getGameTime() + CONTROL_GRACE_TICKS));

        var level = minecraft.level;
        if (level == null || !(level.getEntity(payload.entityId()) instanceof CarriageContraptionEntity entity)
                || !payload.trainId().equals(entity.trainId)) {
            UniverseGate.LOGGER.warn("Could not synchronize teleported Create carriage {}", payload.entityId());
            return;
        }
        if (!((CarriageTeleportSync) entity).universegate$applyTeleport(payload.graphId(), payload.data())) {
            UniverseGate.LOGGER.warn("Could not synchronize Create carriage {} with track graph {}",
                    payload.entityId(), payload.graphId());
        }
        holdControls();
    }

    /**
     * The client may discard and respawn the carriage entity after a long jump, which silently breaks Create's
     * weak reference to the controlled contraption. Re-attach to the respawned entity, and suspend Create's
     * controls tick while it is missing so the session is not stopped in the meantime.
     *
     * @return true when Create's controls tick must be skipped this tick
     */
    public static boolean holdControls() {
        PendingControls pending = pendingControls;
        if (pending == null) return false;
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null || player.isSpectator() || wantsToLeave()
                || level.getGameTime() > pending.expiresAt()) {
            pendingControls = null;
            return false;
        }

        var current = ControlsHandler.getContraption();
        if (current != null && !current.isRemoved()) {
            if (current.getId() != pending.entityId()) pendingControls = null;
            return false;
        }

        if (level.getEntity(pending.entityId()) instanceof CarriageContraptionEntity entity && !entity.isRemoved()
                && pending.trainId().equals(entity.trainId)) {
            ControlsHandlerAccessor.universegate$setEntityRef(new WeakReference<>(entity));
            ControlsHandlerAccessor.universegate$setControlsPos(pending.localPos());
            return false;
        }

        ControlsUtil.getControls().forEach(key -> key.setDown(false));
        return true;
    }

    private static boolean wantsToLeave() {
        var minecraft = Minecraft.getInstance();
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE)
                || ControlsUtil.isActuallyPressed(minecraft.options.keyShift);
    }

    private record PendingControls(int entityId, UUID trainId, BlockPos localPos, long expiresAt) {}
}
