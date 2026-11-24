package fr.geomtech.universegate.seamless.server;

import fr.geomtech.universegate.UniverseGate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
//? if >=1.21.11 {
/*import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
*///?} else {
import java.util.Comparator;
//?}

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Reference-counted chunk ticket controller over {@link TicketBackend}.
 *
 * <p>Minecraft's {@code DistanceManager.addRegionTicket} (1.21.1) and {@code TicketStorage.addTicket}
 * (1.21.11) deduplicate identical tickets without reference-counting. This controller tracks per-owner
 * claims and guarantees {@link TicketBackend#addTicket} runs only on {@code 0 -> 1} and
 * {@link TicketBackend#removeTicket} runs only on {@code 1 -> 0}.
 */
public final class RemoteChunkTicketController {

    //? if >=1.21.11 {
    /*private static TicketType PORTAL_STREAM_TICKET;
    *///?} else {
    private static final TicketType<ChunkPos> PORTAL_STREAM_TICKET = TicketType.create(
            UniverseGate.MOD_ID + ":portal_stream",
            Comparator.comparingLong(ChunkPos::toLong)
    );
    //?}

    public record TicketKey(
            ResourceKey<Level> dim,
            int chunkX,
            int chunkZ,
            int radius
    ) {
        public TicketKey {
            Objects.requireNonNull(dim, "dim");
            if (radius < 0) {
                throw new IllegalArgumentException("Ticket radius must be >= 0, got " + radius);
            }
        }
    }

    private final TicketBackend backend;
    private final Map<TicketKey, Set<Object>> ownersByTicket = new HashMap<>();
    private final Map<Object, Set<TicketKey>> ticketsByOwner = new HashMap<>();

    public RemoteChunkTicketController(TicketBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    /**
     * Registers the {@code universegate:portal_stream} {@link TicketType} on 1.21.11+.
     * Must run inside {@link UniverseGate#registerContent()} while registries are unfrozen.
     */
    public static void registerTicketType() {
        //? if >=1.21.11 {
        /*if (PORTAL_STREAM_TICKET == null) {
            PORTAL_STREAM_TICKET = Registry.register(
                    BuiltInRegistries.TICKET_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_stream"),
                    new TicketType(
                            TicketType.NO_TIMEOUT,
                            TicketType.FLAG_LOADING
                                    | TicketType.FLAG_SIMULATION
                                    | TicketType.FLAG_KEEP_DIMENSION_ACTIVE
                    )
            );
        }
        *///?}
    }

    public static TicketBackend createMinecraftBackend(Supplier<MinecraftServer> serverSupplier) {
        Objects.requireNonNull(serverSupplier, "serverSupplier");
        return new MinecraftTicketBackend(serverSupplier);
    }

    public boolean acquire(Object owner, ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {
        return acquire(owner, new TicketKey(dim, chunkX, chunkZ, radius));
    }

    public boolean acquire(Object owner, TicketKey key) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");

        Set<TicketKey> ownerClaims = ticketsByOwner.computeIfAbsent(owner, ignored -> new LinkedHashSet<>());
        if (!ownerClaims.add(key)) {
            return false;
        }

        Set<Object> ticketOwners = ownersByTicket.computeIfAbsent(key, ignored -> new HashSet<>());
        boolean firstClaim = ticketOwners.isEmpty();
        ticketOwners.add(owner);
        if (firstClaim) {
            backend.addTicket(key.dim(), key.chunkX(), key.chunkZ(), key.radius());
        }
        return true;
    }

    public boolean release(Object owner, ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {
        return release(owner, new TicketKey(dim, chunkX, chunkZ, radius));
    }

    public boolean release(Object owner, TicketKey key) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");

        Set<TicketKey> ownerClaims = ticketsByOwner.get(owner);
        if (ownerClaims == null || !ownerClaims.remove(key)) {
            return false;
        }
        if (ownerClaims.isEmpty()) {
            ticketsByOwner.remove(owner);
        }

        Set<Object> ticketOwners = ownersByTicket.get(key);
        if (ticketOwners != null) {
            ticketOwners.remove(owner);
            if (ticketOwners.isEmpty()) {
                ownersByTicket.remove(key);
                backend.removeTicket(key.dim(), key.chunkX(), key.chunkZ(), key.radius());
            }
        }
        return true;
    }

    /**
     * Atomically updates an owner's ticket claim set (e.g., on portal retargeting).
     * Acquires new keys before releasing stale keys so shared chunks never momentarily drop.
     */
    public void setClaims(Object owner, Collection<TicketKey> desiredKeys) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(desiredKeys, "desiredKeys");

        Set<TicketKey> desired = new LinkedHashSet<>(desiredKeys);
        if (desired.isEmpty()) {
            releaseOwner(owner);
            return;
        }

        for (TicketKey key : desired) {
            acquire(owner, key);
        }

        Set<TicketKey> current = ticketsByOwner.get(owner);
        if (current != null) {
            for (TicketKey existing : List.copyOf(current)) {
                if (!desired.contains(existing)) {
                    release(owner, existing);
                }
            }
        }
    }

    public int releaseOwner(Object owner) {
        Objects.requireNonNull(owner, "owner");
        Set<TicketKey> ownerClaims = ticketsByOwner.get(owner);
        if (ownerClaims == null || ownerClaims.isEmpty()) {
            return 0;
        }
        int released = 0;
        for (TicketKey key : List.copyOf(ownerClaims)) {
            if (release(owner, key)) {
                released++;
            }
        }
        return released;
    }

    public void clearAll() {
        if (ownersByTicket.isEmpty()) {
            ticketsByOwner.clear();
            return;
        }
        List<TicketKey> activeKeys = List.copyOf(ownersByTicket.keySet());
        ownersByTicket.clear();
        ticketsByOwner.clear();
        for (TicketKey key : activeKeys) {
            backend.removeTicket(key.dim(), key.chunkX(), key.chunkZ(), key.radius());
        }
    }

    public int refCount(ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {
        return refCount(new TicketKey(dim, chunkX, chunkZ, radius));
    }

    public int refCount(TicketKey key) {
        Objects.requireNonNull(key, "key");
        Set<Object> owners = ownersByTicket.get(key);
        return owners == null ? 0 : owners.size();
    }

    public int activeTicketCount() {
        return ownersByTicket.size();
    }

    public Set<TicketKey> claimsForOwner(Object owner) {
        Objects.requireNonNull(owner, "owner");
        Set<TicketKey> claims = ticketsByOwner.get(owner);
        return claims == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(claims));
    }

    public Set<ResourceKey<Level>> activeDimensions() {
        if (ownersByTicket.isEmpty()) {
            return Set.of();
        }
        Set<ResourceKey<Level>> dims = new HashSet<>();
        for (TicketKey key : ownersByTicket.keySet()) {
            dims.add(key.dim());
        }
        return Collections.unmodifiableSet(dims);
    }

    private static final class MinecraftTicketBackend implements TicketBackend {
        private final Supplier<MinecraftServer> serverSupplier;

        private MinecraftTicketBackend(Supplier<MinecraftServer> serverSupplier) {
            this.serverSupplier = serverSupplier;
        }

        @Override
        public void addTicket(ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {
            MinecraftServer server = serverSupplier.get();
            if (server == null) return;
            ServerLevel targetLevel = server.getLevel(dim);
            if (targetLevel == null) return;

            ChunkPos pos = new ChunkPos(chunkX, chunkZ);
            //? if >=1.21.11 {
            /*if (PORTAL_STREAM_TICKET == null) {
                throw new IllegalStateException("RemoteChunkTicketController.registerTicketType() was not called");
            }
            targetLevel.getChunkSource().addTicketWithRadius(PORTAL_STREAM_TICKET, pos, radius);
            *///?} else {
            targetLevel.getChunkSource().addRegionTicket(PORTAL_STREAM_TICKET, pos, radius, pos);
            targetLevel.resetEmptyTime();
            //?}
        }

        @Override
        public void removeTicket(ResourceKey<Level> dim, int chunkX, int chunkZ, int radius) {
            MinecraftServer server = serverSupplier.get();
            if (server == null) return;
            ServerLevel targetLevel = server.getLevel(dim);
            if (targetLevel == null) return;

            ChunkPos pos = new ChunkPos(chunkX, chunkZ);
            //? if >=1.21.11 {
            /*if (PORTAL_STREAM_TICKET == null) {
                return;
            }
            targetLevel.getChunkSource().removeTicketWithRadius(PORTAL_STREAM_TICKET, pos, radius);
            *///?} else {
            targetLevel.getChunkSource().removeRegionTicket(PORTAL_STREAM_TICKET, pos, radius, pos);
            //?}
        }
    }
}
