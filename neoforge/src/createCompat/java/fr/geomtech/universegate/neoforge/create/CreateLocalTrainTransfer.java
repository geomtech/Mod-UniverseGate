package fr.geomtech.universegate.neoforge.create;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class CreateLocalTrainTransfer {
    private static final double EPSILON = 1e-4;

    private CreateLocalTrainTransfer() {}

    static boolean transfer(ServerLevel level, Train train, Vec3 entryStart, Vec3 entryAxis, TrackGraph destination,
                            TrackEdge exit, double exitOffset, boolean backwards) {
        Vec3 exitAxis = exit.getDirection(true);
        Vec3 exitStart = exit.node1.getLocation().getLocation().add(exitAxis.scale(exitOffset));
        var points = new ArrayList<TravellingPoint>();
        train.forEachTravellingPoint(points::add);
        if (backwards) points = new ArrayList<>(points.reversed());

        Map<TravellingPoint, Double> offsets = new LinkedHashMap<>();
        double length = 0;
        for (TravellingPoint point : points) {
            if (point.edge == null || !isStraight(point.edge, entryAxis)
                    || !point.node1.getLocation().getDimension().equals(level.dimension())) return false;
            Vec3 position = point.edge.getPosition(null, point.position / point.edge.getLength()).subtract(entryStart);
            double offset = position.dot(entryAxis);
            if (position.subtract(entryAxis.scale(offset)).lengthSqr() > EPSILON || offset < length - EPSILON) return false;
            length = Math.max(length, offset);
            offsets.put(point, length);
        }

        Vec3 exitFront = exitStart.add(exitAxis.scale(length));
        if (!loadExit(level, exitStart, exitFront.add(exitAxis.scale(16)))) return false;

        Map<TravellingPoint, TravellingPoint> placements = new LinkedHashMap<>();
        Set<UUID> signalGroups = new HashSet<>();
        TrackEdge segment = exit;
        double segmentStart = -exitOffset;
        double occupiedFrom = exitOffset;
        for (var entry : offsets.entrySet().stream().toList().reversed()) {
            double distance = length - entry.getValue();
            while (distance > segmentStart + segment.getLength() + EPSILON) {
                if (!collectGroups(destination, segment, occupiedFrom, segment.getLength(), signalGroups, train)) return false;
                segmentStart += segment.getLength();
                segment = straightContinuation(destination, segment, exitAxis);
                if (segment == null) return false;
                occupiedFrom = 0;
            }
            double position = Math.min(distance - segmentStart, segment.getLength());
            TrackEdge oriented = backwards ? destination.getConnectionsFrom(segment.node2).get(segment.node1) : segment;
            if (oriented == null || oriented.getTrackMaterial().trackType != entry.getKey().edge.getTrackMaterial().trackType) return false;
            placements.put(entry.getKey(), new TravellingPoint(oriented.node1, oriented.node2, oriented,
                    backwards ? oriented.getLength() - position : position, entry.getKey().upsideDown));
        }
        if (!collectGroups(destination, segment, occupiedFrom, Math.min(length - segmentStart, segment.getLength()), signalGroups, train)) return false;
        AABB exitArea = new AABB(exitStart, exitFront).inflate(1.0, 3.0, 1.0);
        if (occupied(level, train, exitArea)) return false;

        Map<Carriage, Map<ServerPlayer, Vec3>> onboardPlayers = new LinkedHashMap<>();
        Set<ServerPlayer> capturedPlayers = new HashSet<>();
        for (Carriage carriage : train.carriages) {
            Map<ServerPlayer, Vec3> players = captureOnboardPlayers(level, carriage);
            players.keySet().removeIf(capturedPlayers::contains);
            capturedPlayers.addAll(players.keySet());
            onboardPlayers.put(carriage, players);
        }

        train.speed = 0;
        train.targetSpeed = 0;
        train.speedBeforeStall = null;
        train.leaveStation();
        train.occupiedSignalBlocks.keySet().forEach(id -> removeSignalOccupancy(train, id));
        train.reservedSignalBlocks.forEach(id -> removeSignalOccupancy(train, id));
        train.navigation.cancelNavigation();
        train.navigation.waitingForSignal = null;
        train.graph = destination;
        placements.forEach((point, placed) -> {
            point.node1 = placed.node1;
            point.node2 = placed.node2;
            point.edge = placed.edge;
            point.position = placed.position;
            point.blocked = false;
        });
        for (Carriage carriage : train.carriages) {
            moveCarriage(level, carriage, onboardPlayers.getOrDefault(carriage, Map.of()));
        }
        train.collectInitiallyOccupiedSignalBlocks();
        for (UUID id : signalGroups) {
            train.occupiedSignalBlocks.putIfAbsent(id, null);
            Create.RAILWAYS.signalEdgeGroups.get(id).trains.add(train);
        }
        train.status.trackOK();
        return true;
    }

    private static boolean isStraight(TrackEdge edge, Vec3 axis) {
        return !edge.isInterDimensional() && !edge.isTurn() && edge.getLength() > EPSILON
                && Math.abs(edge.getDirection(true).dot(axis)) > 1 - EPSILON;
    }

    private static TrackEdge straightContinuation(TrackGraph graph, TrackEdge current, Vec3 axis) {
        TrackEdge continuation = null;
        for (TrackEdge candidate : graph.getConnectionsFrom(current.node2).values()) {
            if (candidate.node2 == current.node1 || !current.canTravelTo(candidate)) continue;
            if (continuation != null || !isStraight(candidate, axis) || candidate.getDirection(true).dot(axis) < 0) return null;
            continuation = candidate;
        }
        return continuation;
    }

    private static boolean loadExit(ServerLevel level, Vec3 start, Vec3 end) {
        ChunkPos first = new ChunkPos(BlockPos.containing(start));
        ChunkPos last = new ChunkPos(BlockPos.containing(end));
        boolean ready = true;
        for (int chunkX = Math.min(first.x, last.x); chunkX <= Math.max(first.x, last.x); chunkX++) {
            for (int chunkZ = Math.min(first.z, last.z); chunkZ <= Math.max(first.z, last.z); chunkZ++) {
                ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                CreatePortalTracks.keepLoaded(level, chunk);
                ready &= level.isPositionEntityTicking(chunk.getMiddleBlockPosition(BlockPos.containing(start).getY()));
            }
        }
        return ready;
    }

    private static boolean collectGroups(TrackGraph graph, TrackEdge edge, double start, double end, Set<UUID> ids, Train train) {
        if (!addGroup(edge.getEdgeData().getGroupAtPosition(graph, start), ids, train)) return false;
        for (var point : edge.getEdgeData().getPoints()) {
            if (point instanceof SignalBoundary signal && signal.getLocationOn(edge) >= start && signal.getLocationOn(edge) <= end
                    && !addGroup(signal.getGroup(edge.node2), ids, train)) return false;
        }
        return true;
    }

    private static boolean addGroup(UUID id, Set<UUID> ids, Train train) {
        var group = Create.RAILWAYS.signalEdgeGroups.get(id);
        if (group == null || ids.contains(id)) return true;
        if (!group.fallbackGroup && group.isOccupiedUnless(train)) return false;
        ids.add(id);
        return true;
    }

    private static void removeSignalOccupancy(Train train, UUID id) {
        var group = Create.RAILWAYS.signalEdgeGroups.get(id);
        if (group != null) group.trains.remove(train);
    }

    private static boolean occupied(ServerLevel level, Train train, AABB exitArea) {
        for (Train other : Create.RAILWAYS.trains.values()) {
            if (other == train) continue;
            Vec3 previous = null;
            for (Carriage carriage : other.carriages) {
                for (TravellingPoint point : new TravellingPoint[]{carriage.getLeadingPoint(), carriage.getTrailingPoint()}) {
                    if (point.edge == null || point.edge.isInterDimensional()
                            || !point.node1.getLocation().getDimension().equals(level.dimension())) {
                        previous = null;
                        continue;
                    }
                    Vec3 position = point.getPosition(other.graph);
                    if (exitArea.contains(position) || (previous != null && exitArea.clip(previous, position).isPresent())) return true;
                    previous = position;
                }
            }
        }
        return false;
    }

    private static Map<ServerPlayer, Vec3> captureOnboardPlayers(ServerLevel level, Carriage carriage) {
        var dimensional = carriage.getDimensionalIfPresent(level.dimension());
        var entity = dimensional == null ? null : dimensional.entity.get();
        if (entity == null || !entity.isAlive()) return Map.of();

        Set<ServerPlayer> passengers = new HashSet<>();
        for (var passenger : entity.getIndirectPassengers()) {
            if (passenger instanceof ServerPlayer player) passengers.add(player);
        }
        Set<ServerPlayer> collidingPlayers = new HashSet<>();
        for (var colliding : entity.collidingEntities.keySet()) {
            if (colliding instanceof ServerPlayer player && !passengers.contains(player)) collidingPlayers.add(player);
        }
        Set<ServerPlayer> controllers = new HashSet<>();
        entity.getControllingPlayer()
                .map(level.getServer().getPlayerList()::getPlayer)
                .filter(player -> player != null && !passengers.contains(player))
                .ifPresent(controllers::add);

        AABB oldBounds = entity.getBoundingBox().inflate(0.1);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level || !player.isAlive() || passengers.contains(player)
                    || !oldBounds.intersects(player.getBoundingBox())) continue;
            collidingPlayers.add(player);
        }

        Map<ServerPlayer, Vec3> playerOffsets = new LinkedHashMap<>();
        for (ServerPlayer player : collidingPlayers) {
            if (player.level() == level && player.isAlive()) {
                playerOffsets.put(player, entity.toLocalVector(player.position(), 1));
            }
        }
        for (ServerPlayer player : controllers) {
            if (player.level() != level || !player.isAlive() || collidingPlayers.contains(player)) continue;
            playerOffsets.put(player, entity.toLocalVector(player.position(), 1));
        }
        return playerOffsets;
    }

    private static void moveCarriage(ServerLevel level, Carriage carriage, Map<ServerPlayer, Vec3> playerOffsets) {
        var dimensional = carriage.getDimensionalIfPresent(level.dimension());
        var entity = dimensional == null ? null : dimensional.entity.get();
        if (entity == null || !entity.isAlive()) return;
        var controls = ((CarriageTeleportSync) entity).universegate$getControls().filter(control -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(control.playerId());
            return player != null && player.level() == level && player.isAlive() && !player.isSpectator();
        });
        carriage.updateContraptionAnchors();
        float previousYaw = entity.yaw;
        entity.setPos(dimensional.positionAnchor);
        entity.firstPositionUpdate = true;
        dimensional.alignEntity(entity);
        entity.teleportTo(entity.getX(), entity.getY(), entity.getZ());
        float yawDelta = Mth.wrapDegrees(entity.yaw - previousYaw);
        Set<ServerPlayer> seatedPlayers = new HashSet<>();
        for (var passenger : entity.getIndirectPassengers()) {
            if (passenger instanceof ServerPlayer player && player.level() == level && player.isAlive()) {
                seatedPlayers.add(player);
            }
        }
        controls.ifPresent(control -> entity.setControllingPlayer(control.playerId()));
        entity.syncCarriage();
        var payload = new CarriageTeleportPayload(entity.getId(), carriage.train.id, carriage.train.graph.id,
                entity.getCarriageData().copy(), controls);
        PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
        for (ServerPlayer player : seatedPlayers) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        for (ServerPlayer player : playerOffsets.keySet()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        for (ServerPlayer player : seatedPlayers) {
            teleportPlayer(player, player.position(), yawDelta);
        }
        for (var entry : playerOffsets.entrySet()) {
            ServerPlayer player = entry.getKey();
            if (player.level() != level || !player.isAlive() || seatedPlayers.contains(player)) continue;
            Vec3 exit = entity.toGlobalVector(entry.getValue(), 1);
            teleportPlayer(player, exit, yawDelta);
        }
    }

    private static void teleportPlayer(ServerPlayer player, Vec3 position, float yawDelta) {
        float yaw = player.getYRot() + yawDelta;
        player.connection.teleport(position.x, position.y, position.z, yaw, player.getXRot(), RelativeMovement.ROTATION);
        player.setYHeadRot(yaw);
    }
}
