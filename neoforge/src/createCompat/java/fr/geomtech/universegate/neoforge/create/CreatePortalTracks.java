package fr.geomtech.universegate.neoforge.create;

import com.simibubi.create.Create;
import com.simibubi.create.api.event.TrackGraphMergeEvent;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.signal.SignalPropagator;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import fr.geomtech.universegate.PortalCoreBlockEntity;
import fr.geomtech.universegate.PortalFrameDetector;
import fr.geomtech.universegate.PortalOrientationHelper;
import fr.geomtech.universegate.PortalRegistrySavedData;
import fr.geomtech.universegate.PortalRegistrySavedData.PortalEntry;
import fr.geomtech.universegate.compat.SavedDataCompat;
import fr.geomtech.universegate.seamless.net.PortalRemoteEndpointPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CreatePortalTracks {
    private static final TicketType<ChunkPos> DRAIN_TICKET = TicketType.create(
            "universegate:create_portal", Comparator.comparingLong(ChunkPos::toLong), 40);
    private static MinecraftServer runningServer;

    private CreatePortalTracks() {}

    public static void tick(MinecraftServer server) {
        runningServer = server;
        GraphLinks data = GraphLinks.get(server);
        PortalRegistrySavedData portals = PortalRegistrySavedData.get(server);
        Set<UUID> linkedPortals = new HashSet<>();
        for (var iterator = data.links.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            Link link = entry.getKey();
            Set<UUID> crossingTrains = entry.getValue();
            TrackGraph graph = graphAt(server, link.first());
            TrackNode first = graph == null ? null : graph.locateNode(link.first());
            TrackNode second = graph == null ? null : graph.locateNode(link.second());
            boolean connected = first != null && second != null
                    && graph.getConnectionsFrom(first).containsKey(second)
                    && graph.getConnectionsFrom(second).containsKey(first);
            if (crossingTrains.removeIf(id -> !stillCrossing(Create.RAILWAYS.trains.get(id), graph, link))) {
                data.setDirty();
            }
            boolean open = connected && isOpen(server, portals, link.firstPortal(), link.secondPortal())
                    && matchesTrack(server, portals.get(link.firstPortal()), link.first(), link.second())
                    && matchesTrack(server, portals.get(link.secondPortal()), link.second(), link.first());
            if (open || (connected && !crossingTrains.isEmpty())) {
                linkedPortals.add(link.firstPortal());
                linkedPortals.add(link.secondPortal());
                if (!crossingTrains.isEmpty()) {
                    keepLoaded(server, link.first());
                    keepLoaded(server, link.second());
                }
                continue;
            }
            if (graph != null && second != null) {
                removeConnection(server, data, graph, first, second);
            }
            iterator.remove();
            data.setDirty();
        }

        if (server.getTickCount() % 5 != 0) return;
        for (PortalEntry portal : portals.listAll()) {
            if (linkedPortals.contains(portal.id())) continue;
            PortalCoreBlockEntity core = getCore(server, portal);
            UUID targetId = core == null ? null : core.getTargetPortalId();
            if (targetId == null || portal.id().compareTo(targetId) >= 0 || linkedPortals.contains(targetId)
                    || !isOpen(server, portals, portal.id(), targetId)) continue;
            PortalEntry target = portals.get(targetId);
            if (portal.dim().equals(target.dim())) continue;
            Endpoint first = findEndpoint(server, portal, null);
            Endpoint second = findEndpoint(server, target, null);
            if (first == null || second == null) continue;
            Link link = new Link(portal.id(), targetId, first.node().getLocation(), second.node().getLocation());
            addConnection(first, second);
            data.links.put(link, new HashSet<>());
            data.setDirty();
            linkedPortals.add(portal.id());
            linkedPortals.add(targetId);
        }
    }

    public static void syncRemovedConnections(MinecraftServer server) {
        GraphLinks data = GraphLinks.get(server);
        for (UUID graphId : data.graphsToRefresh) {
            TrackGraph graph = Create.RAILWAYS.trackNetworks.get(graphId);
            if (graph == null) continue;
            for (var player : server.getPlayerList().getPlayers()) {
                Create.RAILWAYS.sync.sendFullGraphTo(graph, player);
            }
        }
        data.graphsToRefresh.clear();
    }

    public static void stop(MinecraftServer server) {
        if (runningServer == server) runningServer = null;
    }

    public static TrainFront trainFront(Train train, boolean backwards) {
        if (train.graph == null || train.derailed || train.invalid || train.carriages.isEmpty()) return null;
        TravellingPoint front = leadingWheel(train, backwards);
        TrackEdge edge = front.edge;
        if (edge == null || edge.isInterDimensional() || edge.isTurn() || edge.getLength() <= 0) return null;
        return new TrainFront(edge.node1.getLocation().getDimension(), edge.getPosition(null, front.position / edge.getLength()));
    }

    public static LocalCrossing findLocalCrossing(MinecraftServer server, Train train, TrainFront previous, boolean backwards) {
        if (previous == null) return null;
        TrainFront current = trainFront(train, backwards);
        if (current == null || !previous.dimension().equals(current.dimension())) return null;
        Vec3 movement = current.position().subtract(previous.position());
        if (movement.lengthSqr() < 1e-8) return null;
        ServerLevel level = server.getLevel(current.dimension());
        if (level == null) return null;

        PortalRegistrySavedData portals = PortalRegistrySavedData.get(server);
        LocalCrossing crossing = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (PortalEntry portal : portals.listAll()) {
            if (!portal.dim().equals(current.dimension())) continue;
            Vec3 center = Vec3.atBottomCenterOf(portal.pos().above());
            if (previous.position().distanceToSqr(center) > movement.lengthSqr() + 1e-4) continue;
            double fraction = center.subtract(previous.position()).dot(movement) / movement.lengthSqr();
            if (fraction < 0 || fraction >= nearest || fraction >= 1
                    || previous.position().add(movement.scale(fraction)).distanceToSqr(center) > 1e-4) continue;

            PortalCoreBlockEntity core = getCore(server, portal);
            if (!isStable(core) || core.getConnectionId() == null) continue;
            UUID targetId = core.getTargetPortalId();
            PortalEntry target = targetId == null ? null : portals.get(targetId);
            if (target == null || !target.dim().equals(portal.dim())) continue;
            var frame = trackFrame(level, portal.pos());
            if (frame == null) continue;
            Direction axis = trackAxis(frame.right());
            if (!hasThroughTrack(level, portal.pos().above(), axis)
                    || Math.abs(movement.normalize().dot(Vec3.atLowerCornerOf(axis.getNormal()))) < 0.999) continue;
            crossing = new LocalCrossing(portal.id(), targetId, core.getConnectionId(), current, backwards);
            nearest = fraction;
        }
        return crossing;
    }

    public static boolean retryLocalCrossing(MinecraftServer server, Train train, LocalCrossing crossing) {
        TrainFront front = trainFront(train, crossing.backwards());
        if (front == null || !front.dimension().equals(crossing.front().dimension())
                || front.position().distanceToSqr(crossing.front().position()) > 1) return true;
        PortalRegistrySavedData portals = PortalRegistrySavedData.get(server);
        PortalEntry source = portals.get(crossing.source());
        PortalEntry target = portals.get(crossing.target());
        if (source == null || target == null) return true;
        PortalCoreBlockEntity core = getCore(server, source);
        if (!isStable(core) || !crossing.connection().equals(core.getConnectionId())
                || !crossing.target().equals(core.getTargetPortalId())) return true;

        ServerLevel level = server.getLevel(front.dimension());
        if (level == null) return true;
        keepLoaded(level, new ChunkPos(target.pos()));
        if (getCore(server, target) == null) return false;
        if (!isOpen(server, portals, source.id(), target.id())) return true;
        TrackExit exit = findLocalExit(server, level, target);
        if (exit == null) return false;
        TrackEdge frontEdge = leadingWheel(train, crossing.backwards()).edge;
        Vec3 entryAxis = frontEdge.getDirection(true).scale(crossing.backwards() ? 1 : -1);
        return CreateLocalTrainTransfer.transfer(level, train, front.position(), entryAxis, exit.graph(), exit.edge(),
                exit.offset(), crossing.backwards());
    }

    public static void tryLocalTransfer(MinecraftServer server, Train train, boolean backwards) {
        if (train.graph == null || train.derailed || train.invalid || train.carriages.isEmpty()) return;
        TravellingPoint front = leadingWheel(train, backwards);
        if (!front.blocked || front.edge == null || front.edge.isInterDimensional()) return;
        TrackNode terminal = backwards ? front.node1 : front.node2;
        PortalRegistrySavedData portals = PortalRegistrySavedData.get(server);
        for (PortalEntry portal : portals.listAll()) {
            if (!portal.dim().equals(terminal.getLocation().getDimension())
                    || Vec3.atBottomCenterOf(portal.pos().above()).distanceToSqr(terminal.getLocation().getLocation()) > 0.26) continue;
            PortalCoreBlockEntity core = getCore(server, portal);
            UUID targetId = core == null ? null : core.getTargetPortalId();
            PortalEntry target = targetId == null ? null : portals.get(targetId);
            if (target == null || !portal.dim().equals(target.dim()) || !isOpen(server, portals, portal.id(), targetId)) continue;
            Endpoint source = findEndpoint(server, portal, null);
            if (source == null || source.graph() != train.graph || source.node() != terminal) continue;
            ServerLevel level = server.getLevel(portal.dim());
            keepLoaded(level, new ChunkPos(target.pos()));
            TrackExit destination = findLocalExit(server, level, target);
            if (destination != null) {
                CreateLocalTrainTransfer.transfer(level, train, terminal.getLocation().getLocation(), source.local().getDirection(true),
                        destination.graph(), destination.edge(), destination.offset(), backwards);
            }
            return;
        }
    }

    private static TravellingPoint leadingWheel(Train train, boolean backwards) {
        return backwards ? train.carriages.getLast().getTrailingPoint() : train.carriages.getFirst().getLeadingPoint();
    }

    private static TrackExit findLocalExit(MinecraftServer server, ServerLevel level, PortalEntry portal) {
        Endpoint endpoint = findEndpoint(server, portal, null);
        if (endpoint != null) return new TrackExit(endpoint.graph(), endpoint.local(), 0);

        var frame = trackFrame(level, portal.pos());
        if (frame == null) return null;
        BlockPos rail = portal.pos().above();
        Direction axis = trackAxis(frame.right());
        if (!hasThroughTrack(level, rail, axis)) return null;
        Direction outward = PortalOrientationHelper.resolveFrontNormal(level, portal.pos(), frame.right());
        Vec3 axisVector = Vec3.atLowerCornerOf(axis.getNormal());
        Vec3 outwardVector = Vec3.atLowerCornerOf(outward.getNormal());
        var location = TrackGraphHelper.getGraphLocationAt(level, rail, outward.getOpposite().getAxisDirection(), axisVector);
        if (location == null) return null;
        TrackGraph graph = location.graph;
        TrackNode first = graph.locateNode(location.edge.getFirst());
        TrackNode second = graph.locateNode(location.edge.getSecond());
        if (first == null || second == null) return null;
        TrackEdge edge = graph.getConnectionsFrom(first).get(second);
        if (edge == null) return null;
        if (edge.getDirection(true).dot(outwardVector) < 0) edge = graph.getConnectionsFrom(second).get(first);
        if (edge == null || edge.isInterDimensional() || edge.isTurn() || edge.getDirection(true).dot(outwardVector) < 0.999) return null;

        Vec3 exit = Vec3.atBottomCenterOf(rail).add(outwardVector.scale(0.5));
        Vec3 fromNode = exit.subtract(edge.node1.getLocation().getLocation());
        double offset = fromNode.dot(outwardVector);
        if (offset < -1e-4 || offset > edge.getLength() + 1e-4
                || fromNode.subtract(outwardVector.scale(offset)).lengthSqr() > 1e-4) return null;
        return new TrackExit(graph, edge, Math.clamp(offset, 0, edge.getLength()));
    }

    public static boolean blocksEntry(Train train, TrackNodeLocation first, TrackNodeLocation second) {
        MinecraftServer server = runningServer;
        if (server == null || !server.isSameThread()) return false;
        GraphLinks data = GraphLinks.get(server);
        for (var entry : data.links.entrySet()) {
            Link link = entry.getKey();
            if (!(link.first().equals(first) && link.second().equals(second))
                    && !(link.first().equals(second) && link.second().equals(first))) continue;
            Set<UUID> crossingTrains = entry.getValue();
            if (crossingTrains.contains(train.id)) return false;
            PortalRegistrySavedData portals = PortalRegistrySavedData.get(server);
            if (!isOpen(server, portals, link.firstPortal(), link.secondPortal())
                    || !matchesTrack(server, portals.get(link.firstPortal()), link.first(), link.second())
                    || !matchesTrack(server, portals.get(link.secondPortal()), link.second(), link.first())) return true;
            crossingTrains.add(train.id);
            data.setDirty();
            return false;
        }
        return false;
    }

    private static boolean stillCrossing(Train train, TrackGraph graph, Link link) {
        if (train == null || graph == null || train.graph != graph) return false;
        Set<ResourceKey<Level>> dimensions = new HashSet<>();
        train.forEachTravellingPoint(point -> {
            if (point.node1 != null) dimensions.add(point.node1.getLocation().getDimension());
            if (point.node2 != null) dimensions.add(point.node2.getLocation().getDimension());
        });
        return dimensions.contains(link.first().getDimension()) && dimensions.contains(link.second().getDimension());
    }

    private static void keepLoaded(MinecraftServer server, TrackNodeLocation location) {
        ServerLevel level = server.getLevel(location.getDimension());
        if (level == null) return;
        keepLoaded(level, new ChunkPos(BlockPos.containing(location.getLocation())));
    }

    static void keepLoaded(ServerLevel level, ChunkPos chunk) {
        level.getChunkSource().addRegionTicket(DRAIN_TICKET, chunk, 2, chunk);
    }

    private static boolean isOpen(MinecraftServer server, PortalRegistrySavedData portals, UUID firstId, UUID secondId) {
        PortalEntry first = portals.get(firstId);
        PortalEntry second = portals.get(secondId);
        if (first == null || second == null) return false;
        PortalCoreBlockEntity firstCore = getCore(server, first);
        PortalCoreBlockEntity secondCore = getCore(server, second);
        return isStable(firstCore) && isStable(secondCore)
                && firstCore.getConnectionId() != null && firstCore.getConnectionId().equals(secondCore.getConnectionId())
                && secondId.equals(firstCore.getTargetPortalId()) && firstId.equals(secondCore.getTargetPortalId());
    }

    private static boolean isStable(PortalCoreBlockEntity core) {
        return core != null && core.isActive() && (core.getEndpointStateFlags() & PortalRemoteEndpointPayload.FLAG_UNSTABLE) == 0;
    }

    private static PortalCoreBlockEntity getCore(MinecraftServer server, PortalEntry portal) {
        ServerLevel level = server.getLevel(portal.dim());
        return level != null && level.hasChunkAt(portal.pos())
                && level.getBlockEntity(portal.pos()) instanceof PortalCoreBlockEntity core
                && portal.id().equals(core.getPortalId()) ? core : null;
    }

    private static boolean matchesTrack(MinecraftServer server, PortalEntry portal, TrackNodeLocation location,
                                        TrackNodeLocation connectedTo) {
        Endpoint endpoint = findEndpoint(server, portal, connectedTo);
        return endpoint != null && endpoint.node().getLocation().equals(location);
    }

    private static Endpoint findEndpoint(MinecraftServer server, PortalEntry portal, TrackNodeLocation connectedTo) {
        ServerLevel level = server.getLevel(portal.dim());
        BlockPos core = portal.pos();
        var frame = trackFrame(level, core);
        if (frame == null) return null;
        Direction forward = trackAxis(frame.right());
        TrackShape shape = trackShape(forward);
        BlockPos rail = core.above();
        if (!isStraightTrack(level.getBlockState(rail), shape)) return null;
        BlockState forwardState = level.getBlockState(rail.relative(forward));
        BlockState backwardState = level.getBlockState(rail.relative(forward.getOpposite()));
        if (isStraightTrack(forwardState, shape) && !(backwardState.getBlock() instanceof TrackBlock)) {
            return terminal(level, rail, forward.getOpposite(), connectedTo);
        }
        if (isStraightTrack(backwardState, shape) && !(forwardState.getBlock() instanceof TrackBlock)) {
            return terminal(level, rail, forward, connectedTo);
        }
        return null;
    }

    private static PortalFrameDetector.FrameMatch trackFrame(ServerLevel level, BlockPos core) {
        if (level == null || !level.hasChunkAt(core.offset(-2, 0, 0)) || !level.hasChunkAt(core.offset(2, 0, 0))
                || !level.hasChunkAt(core.offset(0, 0, -2)) || !level.hasChunkAt(core.offset(0, 0, 2))) return null;
        return PortalFrameDetector.find(level, core).orElse(null);
    }

    private static Direction trackAxis(Direction right) {
        return right.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
    }

    private static TrackShape trackShape(Direction axis) {
        return axis.getAxis() == Direction.Axis.X ? TrackShape.XO : TrackShape.ZO;
    }

    private static boolean hasThroughTrack(ServerLevel level, BlockPos rail, Direction axis) {
        TrackShape shape = trackShape(axis);
        return isStraightTrack(level.getBlockState(rail), shape)
                && isStraightTrack(level.getBlockState(rail.relative(axis)), shape)
                && isStraightTrack(level.getBlockState(rail.relative(axis.getOpposite())), shape);
    }

    private static boolean isStraightTrack(BlockState state, TrackShape shape) {
        return state.getBlock() instanceof TrackBlock && state.getValue(TrackBlock.SHAPE) == shape;
    }

    private static Endpoint terminal(ServerLevel level, BlockPos rail, Direction end, TrackNodeLocation connectedTo) {
        Vec3 outward = Vec3.atLowerCornerOf(end.getNormal());
        TrackNodeLocation location = new TrackNodeLocation(Vec3.atBottomCenterOf(rail).add(outward.scale(0.5))).in(level);
        TrackGraph graph = Create.RAILWAYS.getGraph(level, location);
        if (graph == null) return null;
        TrackNode node = graph.locateNode(location);
        TrackEdge local = null;
        for (var entry : graph.getConnectionsFrom(node).entrySet()) {
            if (entry.getKey().getLocation().equals(connectedTo) && entry.getValue().isInterDimensional()) continue;
            if (local != null || entry.getValue().isInterDimensional()
                    || entry.getValue().getDirection(true).dot(outward) > -0.99) return null;
            local = entry.getValue();
        }
        return local == null ? null : new Endpoint(graph, node, local);
    }

    private static TrackGraph graphAt(MinecraftServer server, TrackNodeLocation node) {
        return Create.RAILWAYS.getGraph(server.overworld(), node);
    }

    private static void addConnection(Endpoint first, Endpoint second) {
        var manager = Create.RAILWAYS;
        TrackGraph graph = first.graph();
        TrackGraph other = second.graph();
        if (graph != other) {
            NeoForge.EVENT_BUS.post(new TrackGraphMergeEvent(other, graph));
            other.transferAll(graph);
            manager.removeGraphAndGroup(other);
            manager.sync.graphRemoved(other);
        }
        TrackNode firstNode = graph.locateNode(first.node().getLocation());
        TrackNode secondNode = graph.locateNode(second.node().getLocation());
        addEdge(graph, firstNode, secondNode, first.local());
        addEdge(graph, secondNode, firstNode, second.local());
        SignalPropagator.notifySignalsOfNewNode(graph, firstNode);
        SignalPropagator.notifySignalsOfNewNode(graph, secondNode);
        graph.markDirty();
    }

    private static void addEdge(TrackGraph graph, TrackNode from, TrackNode to, TrackEdge approach) {
        TrackEdge edge = new TrackEdge(from, to, null, approach.getTrackMaterial());
        graph.putConnection(from, to, edge);
        Create.RAILWAYS.sync.edgeAdded(graph, from, to, edge);
    }

    private static void removeConnection(MinecraftServer server, GraphLinks data, TrackGraph graph,
                                         TrackNode first, TrackNode second) {
        var manager = Create.RAILWAYS;
        var trains = new ArrayList<Train>();
        for (Train train : manager.trains.values()) {
            if (train.graph == graph && train.navigation.destination != null) trains.add(train);
        }
        graph.disconnectNodes(first, second);
        manager.updateSplitGraph(server.overworld(), graph);
        TrackGraph firstGraph = graphAt(server, first.getLocation());
        TrackGraph secondGraph = graphAt(server, second.getLocation());
        SignalPropagator.notifySignalsOfNewNode(firstGraph, first);
        SignalPropagator.notifySignalsOfNewNode(secondGraph, second);
        data.graphsToRefresh.add(firstGraph.id);
        data.graphsToRefresh.add(secondGraph.id);
        manager.markTracksDirty();
        for (Train train : trains) {
            train.navigation.startNavigation(train.navigation.findPathTo(train.navigation.destination, Double.MAX_VALUE));
        }
    }

    private record Endpoint(TrackGraph graph, TrackNode node, TrackEdge local) {}

    private record TrackExit(TrackGraph graph, TrackEdge edge, double offset) {}

    public record TrainFront(ResourceKey<Level> dimension, Vec3 position) {}

    public record LocalCrossing(UUID source, UUID target, UUID connection, TrainFront front, boolean backwards) {}

    private record Link(UUID firstPortal, UUID secondPortal, TrackNodeLocation first, TrackNodeLocation second) {}

    private static final class GraphLinks extends SavedData {
        private final Map<Link, Set<UUID>> links = new LinkedHashMap<>();
        private final Set<UUID> graphsToRefresh = new HashSet<>();

        static GraphLinks get(MinecraftServer server) {
            return SavedDataCompat.computeIfAbsent(server.overworld(), "universegate_create_links", GraphLinks::new,
                    GraphLinks::load, GraphLinks::save);
        }

        static GraphLinks load(CompoundTag tag, HolderLookup.Provider registries) {
            GraphLinks data = new GraphLinks();
            ListTag links = tag.getList("Links", Tag.TAG_COMPOUND);
            for (int index = 0; index < links.size(); index++) {
                CompoundTag entry = links.getCompound(index);
                Link link = new Link(entry.getUUID("FirstPortal"), entry.getUUID("SecondPortal"),
                        readNode(entry.getCompound("First")), readNode(entry.getCompound("Second")));
                Set<UUID> trains = new HashSet<>();
                ListTag savedTrains = entry.getList("Trains", Tag.TAG_COMPOUND);
                for (int trainIndex = 0; trainIndex < savedTrains.size(); trainIndex++) {
                    trains.add(savedTrains.getCompound(trainIndex).getUUID("Id"));
                }
                data.links.put(link, trains);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag savedLinks = new ListTag();
            links.forEach((link, trains) -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("FirstPortal", link.firstPortal());
                entry.putUUID("SecondPortal", link.secondPortal());
                entry.put("First", writeNode(link.first()));
                entry.put("Second", writeNode(link.second()));
                ListTag savedTrains = new ListTag();
                for (UUID train : trains) {
                    CompoundTag savedTrain = new CompoundTag();
                    savedTrain.putUUID("Id", train);
                    savedTrains.add(savedTrain);
                }
                entry.put("Trains", savedTrains);
                savedLinks.add(entry);
            });
            tag.put("Links", savedLinks);
            return tag;
        }

        private static CompoundTag writeNode(TrackNodeLocation node) {
            CompoundTag tag = node.write(null);
            tag.putString("Dimension", node.getDimension().location().toString());
            return tag;
        }

        private static TrackNodeLocation readNode(CompoundTag tag) {
            return TrackNodeLocation.read(tag, null).in(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(tag.getString("Dimension"))));
        }
    }
}
