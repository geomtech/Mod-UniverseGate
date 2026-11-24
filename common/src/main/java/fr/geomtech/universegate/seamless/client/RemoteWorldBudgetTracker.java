package fr.geomtech.universegate.seamless.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure Java tracker for client-side remote world budgets, LRU eviction,
 * ring-buffer chunk bounds, and view-subscription hysteresis.
 */
public final class RemoteWorldBudgetTracker {

    public static final int DEFAULT_MAX_REMOTE_WORLDS = 4;
    public static final int DEFAULT_REMOTE_CHUNK_RADIUS = 5;
    public static final double DEFAULT_SUBSCRIBE_RANGE = 48.0D;
    public static final double DEFAULT_UNSUBSCRIBE_RANGE = 56.0D;

    private final int maxRemoteWorlds;
    private final double subscribeRangeSqr;
    private final double unsubscribeRangeSqr;

    private final LinkedHashMap<ResourceKey<Level>, Long> lruWorlds = new LinkedHashMap<>(16, 0.75F, true);
    private final Set<BlockPos> subscribedPortals = new LinkedHashSet<>();

    public RemoteWorldBudgetTracker() {
        this(DEFAULT_MAX_REMOTE_WORLDS, DEFAULT_SUBSCRIBE_RANGE, DEFAULT_UNSUBSCRIBE_RANGE);
    }

    public RemoteWorldBudgetTracker(int maxRemoteWorlds, double subscribeRange, double unsubscribeRange) {
        if (maxRemoteWorlds <= 0) {
            throw new IllegalArgumentException("maxRemoteWorlds must be positive");
        }
        if (subscribeRange <= 0.0D || unsubscribeRange < subscribeRange) {
            throw new IllegalArgumentException("Invalid subscription range hysteresis");
        }
        this.maxRemoteWorlds = maxRemoteWorlds;
        this.subscribeRangeSqr = subscribeRange * subscribeRange;
        this.unsubscribeRangeSqr = unsubscribeRange * unsubscribeRange;
    }

    /**
     * Touches a remote dimension and returns any least-recently-used dimensions that must be evicted
     * to stay within {@code maxRemoteWorlds}.
     */
    public List<ResourceKey<Level>> touchAndCollectEvictions(ResourceKey<Level> dim, long timestampMillis) {
        Objects.requireNonNull(dim, "dim");
        lruWorlds.put(dim, timestampMillis);
        if (lruWorlds.size() <= maxRemoteWorlds) {
            return List.of();
        }
        List<ResourceKey<Level>> evicted = new ArrayList<>();
        var it = lruWorlds.entrySet().iterator();
        while (lruWorlds.size() > maxRemoteWorlds && it.hasNext()) {
            Map.Entry<ResourceKey<Level>, Long> oldest = it.next();
            if (!oldest.getKey().equals(dim)) {
                evicted.add(oldest.getKey());
                it.remove();
            }
        }
        return evicted;
    }

    public void removeWorld(ResourceKey<Level> dim) {
        if (dim != null) {
            lruWorlds.remove(dim);
        }
    }

    public Set<ResourceKey<Level>> trackedWorlds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(lruWorlds.keySet()));
    }

    /**
     * Checks whether chunk {@code (chunkX, chunkZ)} fits inside a {@code ClientChunkCache.Storage}
     * ring-buffer centered at {@code (centerX, centerZ)} with chunk radius {@code viewRadius}.
     * Matches vanilla's {@code Math.abs(x - viewCenterX) <= chunkRadius && Math.abs(z - viewCenterZ) <= chunkRadius}.
     */
    public static boolean isWithinRingBuffer(int centerX, int centerZ, int viewRadius, int chunkX, int chunkZ) {
        int effectiveRadius = Math.max(2, viewRadius) + 3;
        return Math.abs(chunkX - centerX) <= effectiveRadius
                && Math.abs(chunkZ - centerZ) <= effectiveRadius;
    }

    /**
     * Computes incremental C2S subscribe / unsubscribe actions using hysteresis so a player
     * hovering on the range boundary does not spam packets.
     */
    public SubscriptionDiff computeSubscriptionDiff(Collection<BlockPos> candidateActivePortals,
                                                    double playerX,
                                                    double playerY,
                                                    double playerZ) {
        Set<BlockPos> candidates = new LinkedHashSet<>(candidateActivePortals);
        List<BlockPos> toSubscribe = new ArrayList<>();
        List<BlockPos> toUnsubscribe = new ArrayList<>();

        // Unsubscribe portals that disappeared or exceeded the outer hysteresis range
        for (BlockPos existing : new ArrayList<>(subscribedPortals)) {
            if (!candidates.contains(existing) || distSqr(existing, playerX, playerY, playerZ) > unsubscribeRangeSqr) {
                subscribedPortals.remove(existing);
                toUnsubscribe.add(existing);
            }
        }

        // Subscribe portals within the inner subscribe range
        for (BlockPos candidate : candidates) {
            if (!subscribedPortals.contains(candidate)
                    && distSqr(candidate, playerX, playerY, playerZ) <= subscribeRangeSqr) {
                subscribedPortals.add(candidate);
                toSubscribe.add(candidate);
            }
        }

        return new SubscriptionDiff(List.copyOf(toSubscribe), List.copyOf(toUnsubscribe));
    }

    public Set<BlockPos> subscribedPortals() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(subscribedPortals));
    }

    public void clear() {
        lruWorlds.clear();
        subscribedPortals.clear();
    }

    private static double distSqr(BlockPos corePos, double px, double py, double pz) {
        double dx = (corePos.getX() + 0.5D) - px;
        double dy = (corePos.getY() + 3.0D) - py;
        double dz = (corePos.getZ() + 0.5D) - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    public record SubscriptionDiff(List<BlockPos> toSubscribe, List<BlockPos> toUnsubscribe) {}
}
