package fr.geomtech.universegate.seamless;

import fr.geomtech.universegate.seamless.client.RemoteWorldBudgetTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteWorldBudgetTest {

    private static ResourceKey<Level> dim(String name) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("universegate", name));
    }

    @Test
    void lruEvictsLeastRecentlyUsedWorldWhenBudgetExceeded() {
        RemoteWorldBudgetTracker tracker = new RemoteWorldBudgetTracker(2, 32.0D, 40.0D);
        ResourceKey<Level> d1 = dim("dim_a");
        ResourceKey<Level> d2 = dim("dim_b");
        ResourceKey<Level> d3 = dim("dim_c");

        assertTrue(tracker.touchAndCollectEvictions(d1, 100L).isEmpty());
        assertTrue(tracker.touchAndCollectEvictions(d2, 200L).isEmpty());
        // Touch d1 again so d2 becomes least recently used
        assertTrue(tracker.touchAndCollectEvictions(d1, 300L).isEmpty());

        List<ResourceKey<Level>> evicted = tracker.touchAndCollectEvictions(d3, 400L);
        assertEquals(List.of(d2), evicted);
        assertTrue(tracker.trackedWorlds().contains(d1));
        assertTrue(tracker.trackedWorlds().contains(d3));
        assertFalse(tracker.trackedWorlds().contains(d2));
    }

    @Test
    void ringBufferBoundsMatchVanillaStorageMargin() {
        assertTrue(RemoteWorldBudgetTracker.isWithinRingBuffer(10, -5, 4, 10, -5));
        assertTrue(RemoteWorldBudgetTracker.isWithinRingBuffer(10, -5, 4, 17, 2));
        assertFalse(RemoteWorldBudgetTracker.isWithinRingBuffer(10, -5, 4, 18, -5));
        assertFalse(RemoteWorldBudgetTracker.isWithinRingBuffer(10, -5, 4, 10, -13));
    }

    @Test
    void subscriptionDiffAppliesRangeHysteresisWithoutFlapping() {
        RemoteWorldBudgetTracker tracker = new RemoteWorldBudgetTracker(4, 20.0D, 30.0D);
        BlockPos portal = new BlockPos(0, 64, 0);

        // Inside subscribe range (10 blocks away) -> subscribe
        var diff1 = tracker.computeSubscriptionDiff(List.of(portal), 10.5D, 67.0D, 0.5D);
        assertEquals(List.of(portal), diff1.toSubscribe());
        assertTrue(diff1.toUnsubscribe().isEmpty());

        // Move into hysteresis band (25 blocks away: > 20, <= 30) -> stay subscribed, zero traffic
        var diff2 = tracker.computeSubscriptionDiff(List.of(portal), 25.5D, 67.0D, 0.5D);
        assertTrue(diff2.toSubscribe().isEmpty());
        assertTrue(diff2.toUnsubscribe().isEmpty());

        // Move past unsubscribe range (35 blocks away) -> unsubscribe
        var diff3 = tracker.computeSubscriptionDiff(List.of(portal), 35.5D, 67.0D, 0.5D);
        assertTrue(diff3.toSubscribe().isEmpty());
        assertEquals(List.of(portal), diff3.toUnsubscribe());

        // Move back to 25 blocks (> 20) -> still unsubscribed until <= 20
        var diff4 = tracker.computeSubscriptionDiff(List.of(portal), 25.5D, 67.0D, 0.5D);
        assertTrue(diff4.toSubscribe().isEmpty());
        assertTrue(diff4.toUnsubscribe().isEmpty());
    }
}
