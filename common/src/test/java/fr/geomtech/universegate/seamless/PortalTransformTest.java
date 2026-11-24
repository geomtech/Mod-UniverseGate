package fr.geomtech.universegate.seamless;

import fr.geomtech.universegate.seamless.math.PortalTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalTransformTest {

    private static final double EPS = 1.0E-5D;

    @Test
    void rotationMatrixAlwaysHasDeterminantPlusOne() {
        Direction[] horizontals = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        BlockPos src = new BlockPos(10, 64, 20);
        BlockPos dst = new BlockPos(-40, 80, 120);

        for (Direction srcNormal : horizontals) {
            for (Direction exitNormal : horizontals) {
                PortalTransform transform = new PortalTransform(src, srcNormal, dst, exitNormal);
                Matrix4f rot = transform.rotationMatrix();
                assertEquals(1.0F, rot.determinant(), 1.0E-4F,
                        "Expected det=+1 for " + srcNormal + " -> " + exitNormal);

                // Verify JOML matrix matches transformDirection() for an asymmetric 3D vector
                Vec3 dir = new Vec3(0.7D, -0.3D, -1.2D);
                Vec3 expectedDir = transform.transformDirection(dir);
                org.joml.Vector4f matDir = new org.joml.Vector4f((float) dir.x, (float) dir.y, (float) dir.z, 0.0F);
                rot.transform(matDir);
                assertEquals(expectedDir.x, matDir.x, 1.0E-4D, "rot.x mismatch for " + srcNormal + " -> " + exitNormal);
                assertEquals(expectedDir.y, matDir.y, 1.0E-4D, "rot.y mismatch for " + srcNormal + " -> " + exitNormal);
                assertEquals(expectedDir.z, matDir.z, 1.0E-4D, "rot.z mismatch for " + srcNormal + " -> " + exitNormal);

                // Round-trip point through transform and its inverse
                Vec3 sample = new Vec3(12.25D, 66.5D, 18.75D);
                Vec3 mapped = transform.transformPoint(sample);
                Vec3 roundTripped = transform.inverse().transformPoint(mapped);
                assertEquals(sample.x, roundTripped.x, EPS);
                assertEquals(sample.y, roundTripped.y, EPS);
                assertEquals(sample.z, roundTripped.z, EPS);
            }
        }
    }

    @Test
    void ninetyDegreeTurnMapsForwardRightAndYawContinuously() {
        // Walking NORTH into source portal -> exiting EAST at destination portal (+90 deg yaw)
        PortalTransform transform = new PortalTransform(
                new BlockPos(0, 64, 0),
                Direction.NORTH,
                new BlockPos(100, 70, 200),
                Direction.EAST
        );

        // Moving NORTH (0, 0, -1) exits EAST (+1, 0, 0)
        Vec3 forward = transform.transformVelocity(new Vec3(0.0D, -0.2D, -1.5D));
        assertEquals(1.5D, forward.x, EPS);
        assertEquals(-0.2D, forward.y, EPS);
        assertEquals(0.0D, forward.z, EPS);

        // Viewer's right hand (EAST) maps to SOUTH on exit
        Vec3 right = transform.transformDirection(new Vec3(1.0D, 0.0D, 0.0D));
        assertEquals(0.0D, right.x, EPS);
        assertEquals(0.0D, right.y, EPS);
        assertEquals(1.0D, right.z, EPS);

        // Yaw continuity: looking NORTH (180 deg) becomes EAST (-90 deg)
        assertEquals(-90.0F, transform.transformYaw(180.0F), 1.0E-3F);
        // Looking 15 degrees right of NORTH (-165 deg) becomes 15 degrees right of EAST (-75 deg)
        assertEquals(-75.0F, transform.transformYaw(-165.0F), 1.0E-3F);
    }

    @Test
    void apertureVisibilityAndContainmentRespectRingRadius() {
        PortalTransform transform = new PortalTransform(
                new BlockPos(0, 64, 0),
                Direction.NORTH,
                new BlockPos(10, 64, 10),
                Direction.SOUTH
        );
        Vec3 center = transform.sourceCenter(); // (0.5, 67.0, 0.5)

        assertTrue(transform.isWithinSourceAperture(center, 0.0D));
        assertTrue(transform.isWithinSourceAperture(center.add(2.0D, 0.0D, 0.0D), 0.0D));
        assertFalse(transform.isWithinSourceAperture(center.add(2.5D, 0.0D, 0.0D), 0.0D));

        // Camera 10 blocks in front of portal (z = +10.5) looking NORTH (-Z)
        assertTrue(transform.isAperturePotentiallyVisible(
                new Vec3(0.5D, 67.0D, 10.5D),
                new Vec3(0.0D, 0.0D, -1.0D),
                48.0D
        ));
        // Camera looking directly away (+Z)
        assertFalse(transform.isAperturePotentiallyVisible(
                new Vec3(0.5D, 67.0D, 10.5D),
                new Vec3(0.0D, 0.0D, 1.0D),
                48.0D
        ));
        // Camera beyond max distance
        assertFalse(transform.isAperturePotentiallyVisible(
                new Vec3(0.5D, 67.0D, 80.5D),
                new Vec3(0.0D, 0.0D, -1.0D),
                48.0D
        ));
    }
}
