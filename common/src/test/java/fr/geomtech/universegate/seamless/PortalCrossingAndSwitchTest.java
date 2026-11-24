package fr.geomtech.universegate.seamless;

import fr.geomtech.universegate.seamless.client.SeamlessSwitchCoordinator;
import fr.geomtech.universegate.seamless.client.SodiumIrisCompat;
import fr.geomtech.universegate.seamless.math.PortalCrossingMath;
import fr.geomtech.universegate.seamless.math.PortalTransform;
import fr.geomtech.universegate.seamless.net.SeamlessSwitchHintPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalCrossingAndSwitchTest {

    @Test
    void planeCrossingAndCooldownHysteresisPreventPingPong() {
        assertTrue(PortalCrossingMath.hasCrossedPlane(-0.15D, 0.05D));
        assertTrue(PortalCrossingMath.hasCrossedPlane(0.10D, -0.02D));
        assertFalse(PortalCrossingMath.hasCrossedPlane(-0.40D, -0.15D));

        PortalCrossingMath.CrossingCooldownTracker cooldown = new PortalCrossingMath.CrossingCooldownTracker(10L, 0.65D);
        UUID id = UUID.randomUUID();
        BlockPos exitCore = new BlockPos(100, 64, 100);
        cooldown.recordCrossing(id, exitCore, 50L);

        // During cooldown ticks -> blocked
        assertTrue(cooldown.isCoolingDown(id, exitCore, 0.20D, 55L));
        // After cooldown ticks, still inside hysteresis zone at exitCore -> still blocked
        assertTrue(cooldown.isCoolingDown(id, exitCore, 0.30D, 65L));
        // Once moved past hysteresis distance -> released
        assertFalse(cooldown.isCoolingDown(id, exitCore, 0.80D, 66L));
        assertEquals(0, cooldown.size());
    }

    @Test
    void halfThroughCollisionSuppressesOnlyBlocksBehindSourcePortalAperture() {
        PortalTransform transform = new PortalTransform(
                new BlockPos(0, 64, 0),
                Direction.NORTH,
                new BlockPos(50, 64, 50),
                Direction.SOUTH
        );

        // Block directly behind the source portal plane (z = -1) inside the opening (x=0, y=66) -> suppressed
        assertTrue(PortalCrossingMath.shouldSuppressSourceBlock(new BlockPos(0, 66, -1), transform));
        // Block in front of the source portal plane (z = +1) -> NOT suppressed
        assertFalse(PortalCrossingMath.shouldSuppressSourceBlock(new BlockPos(0, 66, 1), transform));
        // Block behind the plane but outside the ring radius (x = 5) -> NOT suppressed
        assertFalse(PortalCrossingMath.shouldSuppressSourceBlock(new BlockPos(5, 66, -1), transform));
    }

    @Test
    void continuousExitPreservesSpeedAndRelativeYaw() {
        PortalTransform transform = new PortalTransform(
                new BlockPos(0, 64, 0),
                Direction.SOUTH,
                new BlockPos(100, 70, -50),
                Direction.WEST
        );
        Vec3 entryVel = new Vec3(0.2D, -0.3D, 0.8D);
        PortalCrossingMath.ExitKinematics out = PortalCrossingMath.computeContinuousExit(
                new Vec3(0.5D, 65.0D, 0.55D),
                entryVel,
                15.0F,
                -10.0F,
                transform,
                0.75D,
                false
        );

        // Magnitude of velocity is strictly conserved
        assertEquals(entryVel.length(), out.velocity().length(), 1.0E-5D);
        assertEquals(-0.3D, out.velocity().y, 1.0E-5D);
        // SOUTH (0) -> WEST (90) adds +90 deg to yaw (15 -> 105)
        assertEquals(105.0F, out.yRot(), 1.0E-3F);
        assertEquals(-10.0F, out.xRot(), 1.0E-3F);
        // Exit position clears destination plane along WEST (-X) by at least 0.75
        assertTrue(transform.signedDistanceToTargetPlane(out.position()) >= 0.749D);
    }

    @Test
    void seamlessSwitchHintPayloadRoundTripsAndCoordinatorEnforcesTtl() {
        ResourceKey<Level> netherDim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
        ResourceKey<Level> endDim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_end"));

        SeamlessSwitchHintPayload original = new SeamlessSwitchHintPayload(
                netherDim,
                new BlockPos(12, 70, -34),
                12.5D, 71.0D, -33.25D,
                -45.5F, 12.25F,
                new Vec3(0.3D, -0.1D, 0.45D)
        );

        ByteBuf raw = Unpooled.buffer();
        try {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(raw, RegistryAccess.EMPTY);
            SeamlessSwitchHintPayload.STREAM_CODEC.encode(buf, original);
            SeamlessSwitchHintPayload decoded = SeamlessSwitchHintPayload.STREAM_CODEC.decode(buf);
            assertEquals(original, decoded);
            assertEquals(0, buf.readableBytes());
        } finally {
            raw.release();
        }

        SeamlessSwitchCoordinator.clear();
        SeamlessSwitchCoordinator.onSwitchHintReceived(original);
        // Wrong dimension does not consume hint
        assertNull(SeamlessSwitchCoordinator.consumeMatchingHint(endDim, System.currentTimeMillis()));
        // Matching dimension consumes hint once
        SeamlessSwitchHintPayload consumed = SeamlessSwitchCoordinator.consumeMatchingHint(netherDim, System.currentTimeMillis());
        assertNotNull(consumed);
        assertEquals(original, consumed);
        assertNull(SeamlessSwitchCoordinator.consumeMatchingHint(netherDim, System.currentTimeMillis()));

        // Expired hint returns null
        SeamlessSwitchCoordinator.onSwitchHintReceived(original);
        assertNull(SeamlessSwitchCoordinator.consumeMatchingHint(
                netherDim,
                System.currentTimeMillis() + SeamlessSwitchCoordinator.HINT_TTL_MILLIS + 100L
        ));
    }

    @Test
    void sodiumIrisFallbackPolicyCoversAllModesCleanly() {
        assertEquals(
                SodiumIrisCompat.FallbackReason.NONE,
                SodiumIrisCompat.evaluateRenderPolicy(true, false, false, false)
        );
        assertEquals(
                SodiumIrisCompat.FallbackReason.IRIS_SHADOW_PASS,
                SodiumIrisCompat.evaluateRenderPolicy(true, false, true, true)
        );
        assertEquals(
                SodiumIrisCompat.FallbackReason.IRIS_SHADERPACK_ACTIVE,
                SodiumIrisCompat.evaluateRenderPolicy(true, false, false, true)
        );
        assertEquals(
                SodiumIrisCompat.FallbackReason.SODIUM_CHUNK_PIPELINE,
                SodiumIrisCompat.evaluateRenderPolicy(true, true, false, false)
        );
        assertEquals(
                SodiumIrisCompat.FallbackReason.STENCIL_UNAVAILABLE,
                SodiumIrisCompat.evaluateRenderPolicy(false, false, false, false)
        );
    }
}
