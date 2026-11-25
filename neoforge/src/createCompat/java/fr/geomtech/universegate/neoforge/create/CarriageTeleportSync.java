package fr.geomtech.universegate.neoforge.create;

import com.simibubi.create.content.trains.entity.CarriageSyncData;

import java.util.Optional;
import java.util.UUID;

public interface CarriageTeleportSync {
    boolean universegate$applyTeleport(UUID graphId, CarriageSyncData data);

    Optional<CarriageTeleportPayload.Controls> universegate$getControls();
}
