package fr.geomtech.universegate.neoforge.create.mixin;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageSyncData;
import com.simibubi.create.content.trains.graph.TrackGraph;
import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.neoforge.create.CarriageTeleportPayload;
import fr.geomtech.universegate.neoforge.create.CarriageTeleportSync;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

@Mixin(value = CarriageContraptionEntity.class, remap = false)
abstract class CarriageContraptionEntityMixin implements CarriageTeleportSync {
    @Shadow @Final private static EntityDataAccessor<Optional<UUID>> TRACK_GRAPH;
    @Shadow @Final private static EntityDataAccessor<CarriageSyncData> CARRIAGE_DATA;
    @Unique private TrackGraph universegate$previousGraph;
    @Unique private BlockPos universegate$controlsPosition;

    @Inject(method = "startControlling", at = @At("RETURN"))
    private void universegate$rememberControls(BlockPos controlsLocalPos, Player player,
                                               CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) {
            universegate$controlsPosition = controlsLocalPos.immutable();
        }
    }

    @Override
    public Optional<CarriageTeleportPayload.Controls> universegate$getControls() {
        var entity = (CarriageContraptionEntity) (Object) this;
        var controller = entity.getControllingPlayer();
        if (controller.isEmpty()) return Optional.empty();
        if (universegate$controlsPosition == null) {
            UniverseGate.LOGGER.warn("Could not capture active controls for Create carriage {}", entity.getId());
            return Optional.empty();
        }
        return controller.map(id -> new CarriageTeleportPayload.Controls(id, universegate$controlsPosition));
    }

    @Override
    public boolean universegate$applyTeleport(UUID graphId, CarriageSyncData data) {
        var entity = (CarriageContraptionEntity) (Object) this;
        var carriage = entity.getCarriage();
        TrackGraph graph = Create.RAILWAYS.sided(entity.level()).trackNetworks.get(graphId);
        if (!entity.level().isClientSide() || carriage == null || graph == null || data.fallbackLocations != null) return false;
        int wheelCount = carriage.isOnTwoBogeys() ? 4 : 2;
        for (int i = 0; i < wheelCount; i++) {
            var pair = data.wheelLocations.get(i);
            if (pair == null) return false;
            var first = graph.getNode(pair.getFirst().getFirst());
            var second = graph.getNode(pair.getFirst().getSecond());
            if (first == null || second == null) return false;
            var connections = graph.getConnectionsFrom(first);
            if (connections == null || connections.get(second) == null) return false;
        }

        carriage.train.graph = graph;
        entity.getEntityData().set(TRACK_GRAPH, Optional.of(graphId), true);
        var dimensional = carriage.getDimensional(entity.level());
        dimensional.pointsInitialised = false;
        entity.getEntityData().set(CARRIAGE_DATA, data, true);
        carriage.updateContraptionAnchors();
        entity.firstPositionUpdate = true;
        dimensional.alignEntity(entity);
        return true;
    }

    @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("HEAD"))
    private void universegate$rememberTrackGraph(EntityDataAccessor<?> key, CallbackInfo callback) {
        var entity = (CarriageContraptionEntity) (Object) this;
        if (TRACK_GRAPH.equals(key) && entity.level().isClientSide() && entity.getCarriage() != null) {
            universegate$previousGraph = entity.getCarriage().train.graph;
        }
    }

    @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("RETURN"))
    private void universegate$refreshWheelPositions(EntityDataAccessor<?> key, CallbackInfo callback) {
        var entity = (CarriageContraptionEntity) (Object) this;
        if (!TRACK_GRAPH.equals(key) || !entity.level().isClientSide()) return;
        var carriage = entity.getCarriage();
        var sync = entity.getCarriageData();
        if (carriage != null && sync != null && carriage.train.graph != null && carriage.train.graph != universegate$previousGraph) {
            carriage.getDimensional(entity.level()).pointsInitialised = false;
            entity.firstPositionUpdate = true;
            sync.apply(entity, carriage);
        }
        universegate$previousGraph = null;
    }
}
