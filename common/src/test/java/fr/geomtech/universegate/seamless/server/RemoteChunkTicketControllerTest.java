package fr.geomtech.universegate.seamless.server;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteChunkTicketControllerTest {

    private static final ResourceKey<Level> DIM_OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> DIM_NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
    private static final ResourceKey<Level> DIM_END =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_end"));

    private record BackendEvent(String op, RemoteChunkTicketController.TicketKey key) {}

    private static final class RecordingTicketBackend implements TicketBackend {
        final List<BackendEvent> events = new ArrayList<>();

        @Override
        public void addTicket(net.minecraft.resources.ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {
            events.add(new BackendEvent("ADD", new RemoteChunkTicketController.TicketKey(dim, chunkX, chunkZ, radius)));
        }

        @Override
        public void removeTicket(net.minecraft.resources.ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {
            events.add(new BackendEvent("REMOVE", new RemoteChunkTicketController.TicketKey(dim, chunkX, chunkZ, radius)));
        }

        long count(String op) {
            return events.stream().filter(e -> e.op().equals(op)).count();
        }
    }

    @Test
    void acquireAndReleaseRespectExactZeroOneRefCountTransitions() {
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController controller = new RemoteChunkTicketController(backend);

        assertTrue(controller.acquire("portalA", DIM_NETHER, 3, -4, 2));
        assertEquals(1, backend.count("ADD"));
        assertEquals(0, backend.count("REMOVE"));
        assertEquals(1, controller.refCount(DIM_NETHER, 3, -4, 2));
        assertEquals(1, controller.activeTicketCount());

        assertTrue(controller.acquire("portalB", DIM_NETHER, 3, -4, 2));
        assertEquals(1, backend.count("ADD"));
        assertEquals(0, backend.count("REMOVE"));
        assertEquals(2, controller.refCount(DIM_NETHER, 3, -4, 2));

        assertTrue(controller.release("portalA", DIM_NETHER, 3, -4, 2));
        assertEquals(1, backend.count("ADD"));
        assertEquals(0, backend.count("REMOVE"));
        assertEquals(1, controller.refCount(DIM_NETHER, 3, -4, 2));

        assertTrue(controller.release("portalB", DIM_NETHER, 3, -4, 2));
        assertEquals(1, backend.count("ADD"));
        assertEquals(1, backend.count("REMOVE"));
        assertEquals(0, controller.refCount(DIM_NETHER, 3, -4, 2));
        assertEquals(0, controller.activeTicketCount());
    }

    @Test
    void overlappingPortalsShareOnlyOverlappingChunks() {
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController controller = new RemoteChunkTicketController(backend);

        controller.acquire("portal1", DIM_NETHER, 0, 0, 2);
        controller.acquire("portal1", DIM_NETHER, 1, 0, 2);
        controller.acquire("portal2", DIM_NETHER, 1, 0, 2);
        controller.acquire("portal2", DIM_NETHER, 2, 0, 2);

        assertEquals(3, backend.count("ADD"));
        assertEquals(3, controller.activeTicketCount());
        assertEquals(1, controller.refCount(DIM_NETHER, 0, 0, 2));
        assertEquals(2, controller.refCount(DIM_NETHER, 1, 0, 2));
        assertEquals(1, controller.refCount(DIM_NETHER, 2, 0, 2));
        assertEquals(Set.of(DIM_NETHER), controller.activeDimensions());

        assertEquals(2, controller.releaseOwner("portal1"));
        assertEquals(1, backend.count("REMOVE"));
        assertEquals(new BackendEvent("REMOVE", new RemoteChunkTicketController.TicketKey(DIM_NETHER, 0, 0, 2)),
                backend.events.getLast());
        assertEquals(1, controller.refCount(DIM_NETHER, 1, 0, 2));
        assertEquals(2, controller.activeTicketCount());

        assertEquals(2, controller.releaseOwner("portal2"));
        assertEquals(3, backend.count("REMOVE"));
        assertEquals(0, controller.activeTicketCount());
        assertTrue(controller.activeDimensions().isEmpty());
    }

    @Test
    void idempotentAcquireAndReleasePreventUnderflowAndDuplicateAdd() {
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController controller = new RemoteChunkTicketController(backend);

        assertTrue(controller.acquire("portalA", DIM_END, -2, 5, 2));
        assertFalse(controller.acquire("portalA", DIM_END, -2, 5, 2));
        assertEquals(1, backend.count("ADD"));
        assertEquals(1, controller.refCount(DIM_END, -2, 5, 2));

        assertTrue(controller.release("portalA", DIM_END, -2, 5, 2));
        assertFalse(controller.release("portalA", DIM_END, -2, 5, 2));
        assertEquals(0, controller.releaseOwner("portalA"));
        assertEquals(0, controller.releaseOwner("neverClaimed"));
        assertEquals(1, backend.count("REMOVE"));
        assertEquals(0, controller.refCount(DIM_END, -2, 5, 2));
    }

    @Test
    void setClaimsRetargetsAndAcquiresNewTicketsBeforeReleasingOldTickets() {
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController controller = new RemoteChunkTicketController(backend);

        var shared = new RemoteChunkTicketController.TicketKey(DIM_OVERWORLD, 1, 1, 2);
        var oldOnly = new RemoteChunkTicketController.TicketKey(DIM_OVERWORLD, 0, 0, 2);
        var newOnly = new RemoteChunkTicketController.TicketKey(DIM_NETHER, 8, -3, 2);

        controller.setClaims("portal", List.of(oldOnly, shared));
        backend.events.clear();

        controller.setClaims("portal", List.of(shared, newOnly));
        assertEquals(
                List.of(
                        new BackendEvent("ADD", newOnly),
                        new BackendEvent("REMOVE", oldOnly)
                ),
                backend.events
        );
        assertEquals(Set.of(shared, newOnly), controller.claimsForOwner("portal"));
        assertEquals(0, controller.refCount(oldOnly));
        assertEquals(1, controller.refCount(shared));
        assertEquals(1, controller.refCount(newOnly));

        controller.setClaims("portal", List.of());
        assertEquals(0, controller.activeTicketCount());
        assertTrue(controller.claimsForOwner("portal").isEmpty());
    }

    @Test
    void clearAllReleasesEveryActiveTicketOnce() {
        RecordingTicketBackend backend = new RecordingTicketBackend();
        RemoteChunkTicketController controller = new RemoteChunkTicketController(backend);

        controller.acquire("p1", DIM_OVERWORLD, 0, 0, 2);
        controller.acquire("p2", DIM_OVERWORLD, 0, 0, 2);
        controller.acquire("p2", DIM_NETHER, 4, 4, 2);
        backend.events.clear();

        controller.clearAll();
        assertEquals(2, backend.count("REMOVE"));
        assertEquals(0, controller.activeTicketCount());
        assertTrue(controller.claimsForOwner("p1").isEmpty());
        assertTrue(controller.claimsForOwner("p2").isEmpty());

        controller.clearAll();
        assertEquals(2, backend.count("REMOVE"));
    }
}
